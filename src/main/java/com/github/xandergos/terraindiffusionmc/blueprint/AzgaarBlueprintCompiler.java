package com.github.xandergos.terraindiffusionmc.blueprint;

import com.google.gson.*;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeCatalog;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.zip.GZIPOutputStream;

/** Validates and compiles an Azgaar Full JSON export into independently reloadable tiles. */
public final class AzgaarBlueprintCompiler {
    private static final long MAX_JSON_BYTES=512L*1024*1024;
    private static final int TILE=128, MAGIC=0x54444250, CHANNELS=10;
    private static final float NODATA=-9999f;
    private static final float[][] VARIABILITY={
            {8,30},{5,80},{15,33},{5,28.6f},{10,25},{3,26.7f},{8,22.2f},
            {2,16},{6,25},{15,20},{15,25},{10,30},{8,20}};

    private AzgaarBlueprintCompiler(){}
    public static CompiledBlueprint compile(Path json,Path output,BlueprintCompileOptions options)throws IOException{
        return compile(json,output,options,BlueprintCompileOptions.COARSE_KM_PER_PIXEL);
    }

    static CompiledBlueprint compile(Path json,Path output,BlueprintCompileOptions options,double coarseKm)throws IOException{
        output=output.toAbsolutePath().normalize();
        if(Files.exists(output)){
            if(!Files.isDirectory(output)||Files.isSymbolicLink(output))throw new IOException("Blueprint output is not an empty directory: "+output);
            try(var entries=Files.list(output)){if(entries.findAny().isPresent())throw new IOException("Refusing to overwrite existing blueprint/output data: "+output);}
        }
        if(!Files.isRegularFile(json))throw new IOException("Azgaar Full JSON file does not exist: "+json);
        long size=Files.size(json);if(size<=0||size>MAX_JSON_BYTES)throw new IOException("Azgaar file is empty or exceeds the 512 MiB safety limit");
        JsonObject root;
        try(Reader reader=Files.newBufferedReader(json)){root=JsonParser.parseReader(reader).getAsJsonObject();}
        catch(RuntimeException e){throw new IOException("Unsupported or malformed Azgaar JSON: "+e.getMessage(),e);}
        JsonObject info=requireObject(root,"info"),settings=requireObject(root,"settings");
        JsonObject grid=requireObject(root,"grid"),coords=requireObject(root,"mapCoordinates");
        JsonObject pack=root.has("pack")&&root.get("pack").isJsonObject()?root.getAsJsonObject("pack"):null;
        double sourceW=number(info,"width"),sourceH=number(info,"height"),exponent=number(settings,"heightExponent");
        if(sourceW<=0||sourceH<=0||exponent<=0)throw new IOException("Invalid info dimensions or settings.heightExponent");
        Graph gridGraph=readGraph(grid,"grid",false);
        Graph packGraph=pack==null?null:readGraph(pack,"pack",false);
        if(gridGraph==null&&packGraph==null)throw new IOException("Missing usable Azgaar cells and vertices (re-export using Tools -> Export -> Export To JSON -> Full)");
        if(gridGraph==null)gridGraph=packGraph;

        double aspect=sourceW/sourceH;
        int width=Math.max(16,(int)Math.round(options.physicalWidthKm()/coarseKm)),height=Math.max(8,(int)Math.round(width/Math.max(2,aspect)));
        if((long)width*height>100_000_000L)throw new IOException("Compiled blueprint would exceed 100 million pixels; reduce physical width");
        if((width*16L+1)*(height*16L+1)>8_500_000L)throw new IOException("This map exceeds the full-river preparation limit. Reduce physical width to approximately 1900 km or less; world scale can still enlarge the Minecraft dimensions.");
        double lonSpan=optionalNumber(coords,"lonT",360);
        if(!(lonSpan>0&&lonSpan<=360.001))lonSpan=360;
        // Azgaar's world-size slider changes BOTH latitude and longitude span. Using only
        // lonT used to squeeze maps with regional coordinates (e.g. 213 x 109.6 degrees).
        // Fit the drawing uniformly; geographic coverage is metadata, not an X-only scale.
        int contentWidth=Math.max(2,Math.min(width,(int)Math.round(height*aspect)));
        int contentMin=(width-contentWidth)/2,contentMax=contentMin+contentWidth-1;
        List<String>warnings=new ArrayList<>();
        if(Math.abs(aspect-2)/2>.05)warnings.add(String.format(Locale.ROOT,
                "Source aspect ratio %.3f differs from 2:1; the drawing is fitted proportionally with coarse-grid rounding.",aspect));
        if(lonSpan<359.99)warnings.add(String.format(Locale.ROOT,
                "Source coordinates span %.2f° longitude. Map geometry uses its drawing aspect ratio, not longitude-only compression; Minecraft latitude labels follow the configured climate projection.",lonSpan));

        int n=width*height;
        float[] elevation=filled(n,-4000),sourceTemp=filled(n,NODATA),sourcePrecip=filled(n,NODATA),
                anchor=filled(n,0),river=filled(n,0);
        boolean[] content=new boolean[n];
        for(int y=0;y<height;y++)for(int x=contentMin;x<=contentMax;x++){
            int i=y*width+x;content[i]=true;elevation[i]=Float.NaN;
        }
        rasterize(gridGraph,sourceW,sourceH,width,height,contentMin,contentWidth,cell->value(cell,"h",Double.NaN),
                (idx,v)->elevation[idx]=convertAzgaarHeight((float)v,exponent));
        rasterize(gridGraph,sourceW,sourceH,width,height,contentMin,contentWidth,cell->value(cell,"temp",Double.NaN),
                (idx,v)->sourceTemp[idx]=(float)v);
        rasterize(gridGraph,sourceW,sourceH,width,height,contentMin,contentWidth,cell->value(cell,"prec",Double.NaN),
                (idx,v)->sourcePrecip[idx]=(float)(v*100));
        Graph biomeGraph=packGraph!=null?packGraph:gridGraph;
        Map<Integer,Double> lakeHeights=new HashMap<>();
        if(pack!=null&&pack.has("features"))for(JsonElement f:pack.getAsJsonArray("features")){
            if(!f.isJsonObject())continue;JsonObject feature=f.getAsJsonObject();
            if(feature.has("type")&&"lake".equals(feature.get("type").getAsString())&&feature.has("height"))
                lakeHeights.put(feature.get("i").getAsInt(),(double)convertAzgaarLakeHeight(feature.get("height").getAsFloat(),exponent));
        }
        // Inland lake cells are not ocean-depth cells: preserve their elevated basin before diffusion.
        if(!lakeHeights.isEmpty())rasterize(biomeGraph,sourceW,sourceH,width,height,contentMin,contentWidth,
                cell->lakeHeights.getOrDefault((int)value(cell,"f",-1),Double.NaN),
                (idx,v)->elevation[idx]=(float)Math.max(1,v-45));
        Map<Integer,Short> explicitBiomeMappings=explicitBiomeMappings(root);
        Set<Integer> sandSeaIds=sandSeaIds(root);
        rasterize(biomeGraph,sourceW,sourceH,width,height,contentMin,contentWidth,cell->value(cell,"biome",Double.NaN),
                (idx,v)->{int raw=(int)Math.max(0,Math.min(999,Math.round(v)));Short mapped=raw>12?explicitBiomeMappings.get(raw):null;anchor[idx]=sandSeaIds.contains(raw)?1:mapped==null?raw:1000+mapped;});
        rasterize(biomeGraph,sourceW,sourceH,width,height,contentMin,contentWidth,cell->value(cell,"r",0)>0?1:Double.NaN,
                (idx,v)->river[idx]=1);
        fillMissing(elevation,content,width,height,Float.NaN);
        fillMissingOptional(sourceTemp,content,width,height,NODATA);
        fillMissingOptional(sourcePrecip,content,width,height,NODATA);
        fillMissing(anchor,content,width,height,NODATA);
        dilateRiver(river,width,height);
        float[] coast=coastDistance(elevation,width,height,(float)coarseKm);

        long climateSeed=hashSeed(sha256(json));
        float[][] climate=PlanetaryClimate.generate(elevation,width,height,climateSeed,
                options.southClimateLatitudeDeg(),options.southPrecipitationMultiplier());
        for(int i=0;i<n;i++){
            int biome=Math.max(0,Math.min(12,Math.round(anchor[i])));
            if(content[i]&&sourceTemp[i]!=NODATA){
                climate[0][i]=blend(climate[0][i],sourceTemp[i],.58f);
                climate[1][i]=blend(climate[1][i],VARIABILITY[biome][0]*100,.65f);
            }
            if(content[i]&&sourcePrecip[i]!=NODATA){
                climate[2][i]=Math.max(30,Math.min(4500,blend(climate[2][i],sourcePrecip[i],.30f)));
                climate[3][i]=blend(climate[3][i],VARIABILITY[biome][1],.65f);
            }
        }
        List<BiomeReservation> reservations=BiomeCoveragePlanner.plan(elevation,climate,anchor,river,coast,
                width,height,contentMin,contentMax);

        Files.createDirectories(output.getParent());
        Path staging=Files.createTempDirectory(output.getParent(),output.getFileName()+".compiling-");
        try {
        Files.createDirectories(staging.resolve("tiles"));
        int detailFactor=Math.max(1,Math.min(16,4096/width));
        int dw=width*detailFactor,dh=height*detailFactor,dn=dw*dh;
        float[][] detail=sandSeaIds.isEmpty()?new float[][]{filled(dn,-4000),filled(dn,0),filled(dn,0),filled(dn,-1)}:
                new float[][]{filled(dn,-4000),filled(dn,0),filled(dn,0),filled(dn,-1),filled(dn,0)};
        rasterize(gridGraph,sourceW,sourceH,dw,dh,contentMin*detailFactor,contentWidth*detailFactor,
                cell->value(cell,"h",Double.NaN),(idx,v)->detail[0][idx]=convertAzgaarHeight((float)v,exponent));
        rasterize(biomeGraph,sourceW,sourceH,dw,dh,contentMin*detailFactor,contentWidth*detailFactor,
                cell->value(cell,"biome",0),(idx,v)->{int raw=(int)v;Short mapped=raw>12?explicitBiomeMappings.get(raw):null;detail[1][idx]=sandSeaIds.contains(raw)?1:mapped==null?raw:1000+mapped;if(detail.length>4)detail[4][idx]=sandSeaIds.contains(raw)?1:0;});
        rasterize(biomeGraph,sourceW,sourceH,dw,dh,contentMin*detailFactor,contentWidth*detailFactor,
                cell->value(cell,"r",0)>0?1:0,(idx,v)->detail[2][idx]=(float)v);
        if(!lakeHeights.isEmpty())rasterize(biomeGraph,sourceW,sourceH,dw,dh,contentMin*detailFactor,contentWidth*detailFactor,
                cell->lakeHeights.getOrDefault((int)value(cell,"f",-1),Double.NaN),
                (idx,v)->{detail[3][idx]=(float)v;detail[0][idx]=(float)Math.max(1,v-45);});
        new BlueprintDetailMap(dw,dh,detail).write(staging.resolve("ecology.bin.gz"));
        // Hash every generation-bearing sidecar together with the ordered tile payloads.
        MessageDigest dataDigest=digest();
        dataDigest.update(Files.readAllBytes(staging.resolve("ecology.bin.gz")));
        for(int ty=0;ty<(height+TILE-1)/TILE;ty++)for(int tx=0;tx<(width+TILE-1)/TILE;tx++){
            int tw=Math.min(TILE,width-tx*TILE),th=Math.min(TILE,height-ty*TILE);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(24+CHANNELS*tw*th*4);
            try(DataOutputStream d=new DataOutputStream(bytes)){
                d.writeInt(MAGIC);d.writeInt(BlueprintManifest.FORMAT_VERSION);d.writeInt(tw);d.writeInt(th);d.writeInt(CHANNELS);
                for(int ch=0;ch<CHANNELS;ch++)for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
                    int i=(ty*TILE+y)*width+tx*TILE+x;
                    float v=switch(ch){case 0->signedSqrt(elevation[i]);case 1,2,3,4->climate[ch-1][i];
                        case 5->sourceTemp[i];case 6->sourcePrecip[i];case 7->anchor[i];case 8->river[i];default->coast[i];};
                    d.writeInt(Integer.reverseBytes(Float.floatToRawIntBits(v)));
                }
            }
            byte[] raw=bytes.toByteArray();dataDigest.update(raw);
            Path tile=staging.resolve("tiles").resolve(tx+"_"+ty+".tdbp.gz");
            try(OutputStream out=new GZIPOutputStream(Files.newOutputStream(tile))){out.write(raw);}
        }
        String sourceHash=sha256(json);
        BlueprintManifest manifest=new BlueprintManifest(1,width,height,TILE,options.physicalWidthKm(),coarseKm,
                json.getFileName().toString(),sourceHash,HexFormat.of().formatHex(dataDigest.digest()),
                options.elevationNoiseRatio(),options.climateNoiseRatio(),options.edgeBlendKm(),
                options.southClimateLatitudeDeg(),options.southPrecipitationMultiplier(),
                BlueprintManifest.CLIMATE_VERSION,CHANNELS,lonSpan,contentMin,contentMax,reservations,13,
                summitPlan(root,sourceHash,detail[0],dw,dh,width,contentMin,contentWidth,options.physicalWidthKm()));
        if(!manifest.effectiveExceptionalSummits().isEmpty())warnings.add("Exceptional summits enabled for the marked Ceteviles ranges. Scale 3 gives full height; larger scales reduce the boost to retain build-height headroom.");
        else if(json.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("cetevil"))
            warnings.add("This export differs from the reviewed Ceteviles map: special summit annotations were not applied. Normal mountains still generate.");
        Files.writeString(staging.resolve("manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        // The picker supplies an empty temporary output directory. Delete only that empty
        // directory; never recursively remove an existing output or a previous world.
        if(Files.exists(output))Files.delete(output);
        try{Files.move(staging,output,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(staging,output);}
        return new CompiledBlueprint(output,manifest,List.copyOf(warnings));
        } finally {
            // This unique staging directory was created by this invocation alone.
            if(Files.exists(staging))deleteTree(staging);
        }
    }

    private record Cell(JsonObject data,JsonArray vertices){}
    private record Graph(Map<Integer,double[]> vertices,List<Cell> cells){}
    @FunctionalInterface private interface PixelWriter{void set(int index,double value);}

    private static Graph readGraph(JsonObject graph,String name,boolean required)throws IOException{
        if(!graph.has("vertices")||!graph.has("cells")){if(required)throw new IOException("Missing required Azgaar "+name+" cells or vertices");return null;}
        return new Graph(readVertices(graph.get("vertices"),name),readCells(graph.get("cells"),name));
    }
    private static Map<Integer,double[]> readVertices(JsonElement e,String name)throws IOException{
        JsonArray rows;
        if(e.isJsonObject()&&e.getAsJsonObject().has("p"))rows=e.getAsJsonObject().getAsJsonArray("p");
        else if(e.isJsonArray())rows=e.getAsJsonArray();else throw new IOException("Invalid Azgaar "+name+" vertices");
        Map<Integer,double[]> out=new HashMap<>();
        for(int i=0;i<rows.size();i++){
            JsonElement row=rows.get(i);int id=i;JsonArray p;
            if(row.isJsonArray())p=row.getAsJsonArray();
            else if(row.isJsonObject()&&row.getAsJsonObject().has("p")){JsonObject o=row.getAsJsonObject();p=o.getAsJsonArray("p");if(o.has("i"))id=o.get("i").getAsInt();}
            else throw new IOException("Invalid Azgaar "+name+" vertex record "+i);
            if(p.size()<2)throw new IOException("Invalid Azgaar "+name+" vertex coordinates "+i);
            out.put(id,new double[]{p.get(0).getAsDouble(),p.get(1).getAsDouble()});
        }
        return out;
    }
    private static List<Cell> readCells(JsonElement e,String name)throws IOException{
        List<Cell> out=new ArrayList<>();
        if(e.isJsonArray()){
            JsonArray rows=e.getAsJsonArray();for(int i=0;i<rows.size();i++){
                if(!rows.get(i).isJsonObject())throw new IOException("Invalid Azgaar "+name+" cell record "+i);
                JsonObject o=rows.get(i).getAsJsonObject();if(!o.has("v")||!o.get("v").isJsonArray())continue;
                out.add(new Cell(o,o.getAsJsonArray("v")));
            }
        }else if(e.isJsonObject()){
            JsonObject columns=e.getAsJsonObject();JsonArray vertices=requireArray(columns,"v");
            for(int i=0;i<vertices.size();i++){
                JsonObject row=new JsonObject();
                for(var entry:columns.entrySet())if(entry.getValue().isJsonArray()&&entry.getValue().getAsJsonArray().size()>i)
                    row.add(entry.getKey(),entry.getValue().getAsJsonArray().get(i));
                out.add(new Cell(row,vertices.get(i).getAsJsonArray()));
            }
        }else throw new IOException("Azgaar "+name+" cells must be an object or array");
        if(out.isEmpty())throw new IOException("Azgaar "+name+" contains no polygon cells");
        return out;
    }
    private static void rasterize(Graph graph,double sourceW,double sourceH,int w,int h,int x0,int contentW,
                                  ToDoubleFunction<JsonObject> value,PixelWriter writer)throws IOException{
        for(int ci=0;ci<graph.cells.size();ci++){
            Cell cell=graph.cells.get(ci);double v=value.applyAsDouble(cell.data);if(!Double.isFinite(v))continue;
            JsonArray ids=cell.vertices;if(ids.size()<3)continue;
            double[] px=new double[ids.size()],py=new double[ids.size()];double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,maxX=-1,maxY=-1;
            for(int k=0;k<ids.size();k++){
                int id=ids.get(k).getAsInt();double[] p=graph.vertices.get(id);
                if(p==null)throw new IOException("Cell "+ci+" references invalid vertex "+id);
                px[k]=x0+p[0]/sourceW*contentW;py[k]=p[1]/sourceH*h;
                minX=Math.min(minX,px[k]);maxX=Math.max(maxX,px[k]);minY=Math.min(minY,py[k]);maxY=Math.max(maxY,py[k]);
            }
            for(int y=Math.max(0,(int)Math.floor(minY));y<=Math.min(h-1,(int)Math.ceil(maxY));y++)
                for(int x=Math.max(x0,(int)Math.floor(minX));x<=Math.min(x0+contentW-1,(int)Math.ceil(maxX));x++)
                    if(inside(x+.5,y+.5,px,py))writer.set(y*w+x,v);
        }
    }
    private static double value(JsonObject o,String key,double fallback){return o.has(key)&&o.get(key).isJsonPrimitive()?o.get(key).getAsDouble():fallback;}
    private static Map<Integer,Short> explicitBiomeMappings(JsonObject root){
        Map<Integer,Short> out=new HashMap<>();
        biomeNames(root).forEach((id,name)->putMapping(out,id,name));
        if(root.has("biomesData")&&root.get("biomesData").isJsonObject()){
            JsonObject b=root.getAsJsonObject("biomesData");
            if(b.has("i")&&b.has("name")&&b.get("i").isJsonArray()&&b.get("name").isJsonArray()){
                JsonArray ids=b.getAsJsonArray("i"),names=b.getAsJsonArray("name");
                for(int i=0;i<Math.min(ids.size(),names.size());i++)putMapping(out,ids.get(i).getAsInt(),names.get(i).getAsString());
            }
        }
        if(root.has("terrainDiffusion")&&root.get("terrainDiffusion").isJsonObject()){
            JsonObject td=root.getAsJsonObject("terrainDiffusion");
            if(td.has("biomeAnchors")&&td.get("biomeAnchors").isJsonArray())for(JsonElement e:td.getAsJsonArray("biomeAnchors")){
                if(!e.isJsonObject())continue;JsonObject a=e.getAsJsonObject();if(!a.has("azgaarId"))continue;
                String name=a.has("canonicalName")?a.get("canonicalName").getAsString():a.has("sourceName")?a.get("sourceName").getAsString():null;
                putMapping(out,a.get("azgaarId").getAsInt(),name);
            }
        }
        return out;
    }
    private static Set<Integer> sandSeaIds(JsonObject root)throws IOException{
        Set<String> names=new HashSet<>();names.add("sand sea");
        if(root.has("terrainDiffusion")){
            JsonObject td=root.getAsJsonObject("terrainDiffusion");
            if(td.has("biomeProfiles"))for(JsonElement e:td.getAsJsonArray("biomeProfiles")){
                JsonObject p=e.getAsJsonObject();
                for(String key:p.keySet())if(!Set.of("sourceName","minecraftBiome","archetype").contains(key))
                    throw new IOException("Unsupported biome profile setting: "+key+"; this version uses the default regional wind regime");
                if(!p.has("sourceName")||!p.has("archetype")||!"sand_sea".equals(p.get("archetype").getAsString())
                        ||!p.has("minecraftBiome")||!"minecraft:desert".equals(p.get("minecraftBiome").getAsString()))
                    throw new IOException("Unsupported terrainDiffusion biome profile; expected a named sand_sea mapped to minecraft:desert");
                String sourceName=p.get("sourceName").getAsString().strip().toLowerCase(Locale.ROOT);
                if(biomeNames(root).values().stream().noneMatch(name->name.strip().toLowerCase(Locale.ROOT).equals(sourceName)))
                    throw new IOException("Biome profile sourceName not found in the Azgaar biome list: "+sourceName);
                names.add(sourceName);
            }
        }
        Set<Integer> ids=new HashSet<>();
        for(var entry:biomeNames(root).entrySet())if(names.contains(entry.getValue().strip().toLowerCase(Locale.ROOT))){
            if(entry.getKey()<=12)throw new IOException("Sand Sea must be a new custom biome, not a renamed standard biome");ids.add(entry.getKey());
        }
        return ids;
    }
    private static Map<Integer,String> biomeNames(JsonObject root){
        Map<Integer,String> out=new HashMap<>();
        JsonElement b=root.has("pack")&&root.getAsJsonObject("pack").has("biomes")?root.getAsJsonObject("pack").get("biomes"):root.get("biomesData");
        if(b==null)return out;
        if(b.isJsonArray()){
            for(JsonElement e:b.getAsJsonArray())if(e.isJsonObject()){
                JsonObject a=e.getAsJsonObject();if(a.has("i")&&a.has("name"))out.put(a.get("i").getAsInt(),a.get("name").getAsString());
            }
        }else if(b.isJsonObject()){
            JsonObject a=b.getAsJsonObject();if(a.has("name")){
                JsonArray names=a.getAsJsonArray("name"),ids=a.has("i")?a.getAsJsonArray("i"):null;
                for(int i=0;i<names.size();i++)out.put(ids!=null&&i<ids.size()?ids.get(i).getAsInt():i,names.get(i).getAsString());
            }
        }
        return out;
    }
    private static List<ExceptionalSummit> summitPlan(JsonObject root,String hash,float[] heights,int w,int h,int coarseWidth,int left,int content,double physicalWidth)throws IOException{
        double factor=physicalWidth/750.0;
        if(root.has("terrainDiffusion")&&root.getAsJsonObject("terrainDiffusion").has("summitAnchors")){
            List<ExceptionalSummit> out=new ArrayList<>();
            JsonArray anchors=root.getAsJsonObject("terrainDiffusion").getAsJsonArray("summitAnchors");
            if(anchors.size()>16)throw new IOException("At most 16 exceptional summit anchors are supported");
            for(JsonElement e:anchors){JsonObject a=e.getAsJsonObject();double u=number(a,"u"),v=number(a,"v"),boost=number(a,"boostMetres"),radius=number(a,"radiusNativeAt750");
                if(u<0||u>1||v<0||v>1||boost<0||boost>15000||radius<100||radius>5000)throw new IOException("Invalid exceptional summit anchor");
                ExceptionalSummit.near(out,heights,w,h,coarseWidth*256,(left+content*u)/coarseWidth,v,(float)boost,radius*factor);
            }
            return List.copyOf(out);
        }
        return ExceptionalSummit.plan(hash,heights,w,h,coarseWidth*256,left,content,coarseWidth).stream()
                .map(s->new ExceptionalSummit(s.u(),s.v(),s.radiusNative()*factor,s.boostMetres())).toList();
    }
    private static void putMapping(Map<Integer,Short> out,int id,String name){Short mapped=BiomeCatalog.idByName(name);if(mapped!=null)out.put(id,mapped);}
    private static float[] coastDistance(float[] elevation,int w,int h,float km){
        int n=w*h;int[] d=new int[n];Arrays.fill(d,Integer.MAX_VALUE);ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;boolean land=elevation[i]>=0;
            if((x>0&&(elevation[i-1]>=0)!=land)||(x+1<w&&(elevation[i+1]>=0)!=land)||(y>0&&(elevation[i-w]>=0)!=land)||(y+1<h&&(elevation[i+w]>=0)!=land)){d[i]=0;q.add(i);}}
        while(!q.isEmpty()){int i=q.remove(),x=i%w,y=i/w,nd=d[i]+1;int[] ns={i-1,i+1,i-w,i+w};
            for(int p:ns)if(p>=0&&p<n&&(p/w==y||p%w==x)&&d[p]>nd){d[p]=nd;q.add(p);}}
        float[] out=new float[n];for(int i=0;i<n;i++)out[i]=(elevation[i]>=0?1:-1)*d[i]*km;return out;
    }
    private static void dilateRiver(float[] river,int w,int h){float[] src=river.clone();for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;if(src[i]>0)continue;float m=0;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx>=0&&xx<w&&yy>=0&&yy<h)m=Math.max(m,src[yy*w+xx]*.45f);}river[i]=m;}}
    private static void fillMissing(float[] a,boolean[] allowed,int w,int h,float nodata)throws IOException{
        ArrayDeque<Integer> q=new ArrayDeque<>();boolean nan=Float.isNaN(nodata);
        for(int i=0;i<a.length;i++)if(allowed[i]&&!missing(a[i],nodata,nan))q.add(i);
        if(q.isEmpty())throw new IOException("No valid Azgaar cell polygons intersected a required output layer");
        while(!q.isEmpty()){int i=q.remove(),x=i%w,y=i/w;int[] ns={i-1,i+1,i-w,i+w};for(int p:ns)
            if(p>=0&&p<a.length&&allowed[p]&&(p/w==y||p%w==x)&&missing(a[p],nodata,nan)){a[p]=a[i];q.add(p);}}
    }
    private static void fillMissingOptional(float[] a,boolean[] allowed,int w,int h,float nodata){
        try{fillMissing(a,allowed,w,h,nodata);}catch(IOException ignored){/* source layer absent: planetary climate remains authoritative */}
    }
    private static boolean missing(float v,float nodata,boolean nan){return nan?Float.isNaN(v):v==nodata;}
    private static float[] filled(int n,float v){float[] a=new float[n];Arrays.fill(a,v);return a;}
    private static float blend(float a,float b,float wb){return a*(1-wb)+b*wb;}
    public static float convertAzgaarHeight(float h,double exponent){return h>=20?(float)Math.pow(h-18,exponent):(float)(-4000*Math.pow((20-h)/20.0,1.5));}
    static float convertAzgaarLakeHeight(float h,double exponent){
        // Azgaar can export a freshwater lake surface at 19.9. Its feature type,
        // not the ocean-cell threshold, determines how that surface is interpreted.
        return Math.max(1,(float)Math.pow(Math.max(0,h-18),exponent));
    }
    private static float signedSqrt(float v){return(float)Math.copySign(Math.sqrt(Math.abs(v)),v);}
    private static boolean inside(double x,double y,double[] px,double[] py){boolean in=false;for(int i=0,j=px.length-1;i<px.length;j=i++)if((py[i]>y)!=(py[j]>y)&&x<(px[j]-px[i])*(y-py[i])/(py[j]-py[i])+px[i])in=!in;return in;}
    private static JsonObject requireObject(JsonObject o,String k)throws IOException{if(!o.has(k)||!o.get(k).isJsonObject())throw new IOException("Missing required Azgaar object: "+k);return o.getAsJsonObject(k);}
    private static JsonArray requireArray(JsonObject o,String k)throws IOException{if(!o.has(k)||!o.get(k).isJsonArray())throw new IOException("Missing required Azgaar array: "+k);return o.getAsJsonArray(k);}
    private static double number(JsonObject o,String k)throws IOException{
        if(!o.has(k)||!o.get(k).isJsonPrimitive())throw new IOException("Missing required Azgaar number: "+k);
        try{double value=o.get(k).getAsDouble();if(!Double.isFinite(value))throw new NumberFormatException();return value;}
        catch(RuntimeException e){throw new IOException("Invalid finite Azgaar number: "+k,e);}
    }
    private static double optionalNumber(JsonObject o,String k,double d){return o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsDouble():d;}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new AssertionError(e);}}
    public static String sha256(Path p)throws IOException{MessageDigest d=digest();try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[65536];for(int n;(n=in.read(b))>0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static long hashSeed(String h){return Long.parseUnsignedLong(h.substring(0,16),16);}
    private static void deleteTree(Path p)throws IOException{Path absolute=p.toAbsolutePath().normalize();if(absolute.getParent()==null)throw new IOException("Refusing unsafe blueprint cleanup: "+absolute);try(var s=Files.walk(absolute)){for(Path x:s.sorted(Comparator.reverseOrder()).toList())Files.delete(x);}}
}
