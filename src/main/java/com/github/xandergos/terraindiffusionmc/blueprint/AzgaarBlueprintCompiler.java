package com.github.xandergos.terraindiffusionmc.blueprint;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Validates and compiles an Azgaar Full JSON export into independently reloadable conditioning tiles. */
public final class AzgaarBlueprintCompiler {
    private static final long MAX_JSON_BYTES = 512L * 1024 * 1024;
    private static final int TILE = 128;
    private static final int MAGIC = 0x54444250; // TDBP

    private AzgaarBlueprintCompiler() { }

    public static CompiledBlueprint compile(Path json, Path output, BlueprintCompileOptions options) throws IOException {
        return compile(json, output, options, BlueprintCompileOptions.COARSE_KM_PER_PIXEL);
    }

    static CompiledBlueprint compile(Path json, Path output, BlueprintCompileOptions options, double coarseKm) throws IOException {
        if (!Files.isRegularFile(json)) throw new IOException("Azgaar Full JSON file does not exist: " + json);
        long size = Files.size(json);
        if (size <= 0 || size > MAX_JSON_BYTES) throw new IOException("Azgaar file is empty or exceeds the 512 MiB safety limit");
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(json)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Unsupported or malformed Azgaar JSON: " + e.getMessage(), e);
        }
        JsonObject info = requireObject(root, "info");
        JsonObject settings = requireObject(root, "settings");
        JsonObject grid = requireObject(root, "grid");
        // Current Azgaar Full JSON uses grid.vertices.p. Some exports omit the
        // derived grid graph but retain the equivalent pack graph; accept it
        // because its cells/vertices topology has the same polygon schema.
        JsonObject graph = grid;
        if (!graph.has("vertices") || !graph.has("cells")) {
            if (root.has("pack") && root.get("pack").isJsonObject()) {
                JsonObject pack = root.getAsJsonObject("pack");
                if (pack.has("vertices") && pack.has("cells")) graph = pack;
            }
        }
        JsonElement vertices = graph.get("vertices");
        if (vertices == null) throw new IOException("Missing required Azgaar object: vertices (expected grid.vertices.p; re-export using Tools -> Export -> Export To JSON -> Full)");
        JsonElement cellsElement = graph.get("cells");
        if (cellsElement == null) throw new IOException("Missing required Azgaar object: cells (expected grid.cells or pack.cells)");
        requireObject(root, "mapCoordinates");
        double sourceW = number(info, "width");
        double sourceH = number(info, "height");
        double exponent = number(settings, "heightExponent");
        if (sourceW <= 0 || sourceH <= 0 || exponent <= 0) throw new IOException("Invalid info dimensions or settings.heightExponent");
        JsonArray vx = vertexArray(vertices);
        JsonArray cellVertices;
        JsonArray cellHeights;
        if (cellsElement.isJsonArray()) {
            // Newer Azgaar exports serialize each cell as an object with v/h.
            cellVertices = new JsonArray();
            cellHeights = new JsonArray();
            JsonArray records = cellsElement.getAsJsonArray();
            for (int i = 0; i < records.size(); i++) {
                if (!records.get(i).isJsonObject()) throw new IOException("Invalid Azgaar grid.cells record " + i);
                JsonObject record = records.get(i).getAsJsonObject();
                if (!record.has("v") || !record.get("v").isJsonArray()) throw new IOException("Azgaar grid.cells record " + i + " is missing v");
                if (!record.has("h") || !record.get("h").isJsonPrimitive()) throw new IOException("Azgaar grid.cells record " + i + " is missing h");
                cellVertices.add(record.get("v"));
                cellHeights.add(record.get("h"));
            }
        } else if (cellsElement.isJsonObject()) {
            JsonObject cells = cellsElement.getAsJsonObject();
            cellVertices = requireArray(cells, "v");
            cellHeights = requireArray(cells, "h");
        } else {
            throw new IOException("Azgaar cells must be an object or array");
        }
        if (cellVertices.size() != cellHeights.size()) throw new IOException("grid.cells.v and grid.cells.h lengths differ");

        int width = Math.max(16, (int)Math.round(options.physicalWidthKm() / coarseKm));
        int height = Math.max(8, width / 2);
        if ((long)width * height > 100_000_000L) throw new IOException("Compiled blueprint would exceed 100 million pixels; reduce physical width");
        List<String> warnings = new ArrayList<>();
        if (Math.abs(sourceW / sourceH - 2.0) / 2.0 > .05)
            warnings.add(String.format(Locale.ROOT, "Source aspect ratio %.3f differs from 2:1 by more than 5%%; it will be stretched equirectangularly.", sourceW/sourceH));

        float[] elevation = new float[width * height];
        Arrays.fill(elevation, Float.NaN);
        double[][] points = new double[vx.size()][2];
        for (int i=0;i<vx.size();i++) {
            JsonArray p = vx.get(i).getAsJsonArray();
            if (p.size()<2) throw new IOException("Invalid grid.vertices.p entry " + i);
            points[i][0] = p.get(0).getAsDouble() / sourceW * width;
            points[i][1] = p.get(1).getAsDouble() / sourceH * height;
        }
        for (int ci=0;ci<cellVertices.size();ci++) {
            JsonArray indices = cellVertices.get(ci).getAsJsonArray();
            if (indices.size()<3) throw new IOException("Invalid polygon for Azgaar cell " + ci + ": fewer than three vertices");
            double[] px = new double[indices.size()], py = new double[indices.size()];
            double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,maxX=-1,maxY=-1;
            for (int k=0;k<indices.size();k++) {
                int vi=indices.get(k).getAsInt();
                if (vi<0 || vi>=points.length) throw new IOException("Cell " + ci + " references invalid vertex " + vi);
                px[k]=points[vi][0]; py[k]=points[vi][1];
                minX=Math.min(minX,px[k]); maxX=Math.max(maxX,px[k]); minY=Math.min(minY,py[k]); maxY=Math.max(maxY,py[k]);
            }
            float h = convertAzgaarHeight(cellHeights.get(ci).getAsFloat(), exponent);
            for (int y=Math.max(0,(int)Math.floor(minY));y<=Math.min(height-1,(int)Math.ceil(maxY));y++)
                for (int x=Math.max(0,(int)Math.floor(minX));x<=Math.min(width-1,(int)Math.ceil(maxX));x++)
                    if (inside(x+.5,y+.5,px,py)) elevation[y*width+x]=h;
        }
        fillMissing(elevation,width,height);
        float[][] climate = PlanetaryClimate.generate(elevation,width,height,hashSeed(sha256(json)),
                options.southClimateLatitudeDeg(), options.southPrecipitationMultiplier());

        if (Files.exists(output)) deleteTree(output);
        Files.createDirectories(output.resolve("tiles"));
        MessageDigest dataDigest = digest();
        for (int ty=0;ty<(height+TILE-1)/TILE;ty++) for (int tx=0;tx<(width+TILE-1)/TILE;tx++) {
            int tw=Math.min(TILE,width-tx*TILE), th=Math.min(TILE,height-ty*TILE);
            Path tile=output.resolve("tiles").resolve(tx+"_"+ty+".tdbp.gz");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(24+5*tw*th*4);
            try (DataOutputStream d=new DataOutputStream(bytes)) {
                d.writeInt(MAGIC); d.writeInt(BlueprintManifest.FORMAT_VERSION); d.writeInt(tw); d.writeInt(th); d.writeInt(5);
                for (int ch=0;ch<5;ch++) for (int y=0;y<th;y++) for(int x=0;x<tw;x++) {
                    int idx=(ty*TILE+y)*width+tx*TILE+x;
                    float value=ch==0 ? signedSqrt(elevation[idx]) : climate[ch-1][idx];
                    d.writeInt(Integer.reverseBytes(Float.floatToRawIntBits(value)));
                }
            }
            byte[] raw=bytes.toByteArray(); dataDigest.update(raw);
            try(OutputStream out=new GZIPOutputStream(Files.newOutputStream(tile))) { out.write(raw); }
        }
        String sourceHash=sha256(json);
        BlueprintManifest manifest=new BlueprintManifest(1,width,height,TILE,options.physicalWidthKm(),coarseKm,
                json.getFileName().toString(),sourceHash,HexFormat.of().formatHex(dataDigest.digest()),
                options.elevationNoiseRatio(),options.climateNoiseRatio(),options.edgeBlendKm(),
                options.southClimateLatitudeDeg(), options.southPrecipitationMultiplier(),
                BlueprintManifest.CLIMATE_VERSION);
        Files.writeString(output.resolve("manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        return new CompiledBlueprint(output,manifest,List.copyOf(warnings));
    }

    public static float convertAzgaarHeight(float h,double exponent) {
        return h>=20 ? (float)Math.pow(h-18,exponent) : (float)(-4000*Math.pow((20-h)/20.0,1.5));
    }
    private static float signedSqrt(float v){ return (float)Math.copySign(Math.sqrt(Math.abs(v)),v); }
    private static boolean inside(double x,double y,double[] px,double[] py){ boolean in=false; for(int i=0,j=px.length-1;i<px.length;j=i++) if((py[i]>y)!=(py[j]>y) && x<(px[j]-px[i])*(y-py[i])/(py[j]-py[i])+px[i]) in=!in; return in; }
    private static void fillMissing(float[] a,int w,int h) throws IOException { ArrayDeque<Integer> q=new ArrayDeque<>(); for(int i=0;i<a.length;i++) if(!Float.isNaN(a[i])) q.add(i); if(q.isEmpty()) throw new IOException("No valid Azgaar cell polygons intersected the output raster"); while(!q.isEmpty()){int i=q.remove(),x=i%w,y=i/w; int[] ns={i-1,i+1,i-w,i+w}; for(int n:ns) if(n>=0&&n<a.length&&Float.isNaN(a[n])&&(n/w==y||n%w==x)){a[n]=a[i];q.add(n);}} }
    private static JsonObject requireObject(JsonObject o,String k)throws IOException{if(!o.has(k)||!o.get(k).isJsonObject())throw new IOException("Missing required Azgaar object: "+k);return o.getAsJsonObject(k);}
    private static JsonArray requireArray(JsonObject o,String k)throws IOException{if(!o.has(k)||!o.get(k).isJsonArray())throw new IOException("Missing required Azgaar array: "+k);return o.getAsJsonArray(k);}
    private static JsonArray vertexArray(JsonElement e)throws IOException{
        if(e.isJsonObject()&&e.getAsJsonObject().has("p")&&e.getAsJsonObject().get("p").isJsonArray())return e.getAsJsonObject().getAsJsonArray("p");
        if(e.isJsonArray()){
            JsonArray input=e.getAsJsonArray();
            JsonArray points=new JsonArray();
            for(int i=0;i<input.size();i++){
                JsonElement item=input.get(i);
                if(item.isJsonArray()) points.add(item);
                else if(item.isJsonObject()&&item.getAsJsonObject().has("p")&&item.getAsJsonObject().get("p").isJsonArray()) points.add(item.getAsJsonObject().get("p"));
                else throw new IOException("Invalid Azgaar vertex record " + i + ": expected p=[x,y]");
            }
            return points;
        }
        throw new IOException("Azgaar vertices must contain p=[x,y] coordinates");
    }
    private static double number(JsonObject o,String k)throws IOException{if(!o.has(k)||!o.get(k).isJsonPrimitive())throw new IOException("Missing required Azgaar number: "+k);return o.get(k).getAsDouble();}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new AssertionError(e);}}
    public static String sha256(Path p)throws IOException{MessageDigest d=digest();try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[65536];for(int n;(n=in.read(b))>0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static long hashSeed(String h){return Long.parseUnsignedLong(h.substring(0,16),16);}
    private static void deleteTree(Path p)throws IOException{try(var s=Files.walk(p)){for(Path x:s.sorted(Comparator.reverseOrder()).toList())Files.delete(x);}}
}
