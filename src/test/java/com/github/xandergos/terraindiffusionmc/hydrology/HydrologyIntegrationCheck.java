package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;

/** Isolated real-model smoke test. Never opens or modifies a Minecraft save. */
public final class HydrologyIntegrationCheck {
    private static void set(String name,Object value)throws Exception{
        Field f=WorldBlueprintManager.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);
    }
    public static void main(String[] args)throws Exception{
        Path source=Path.of(args[0]),out=Path.of(args[1]);
        if(!Files.exists(out.resolve("manifest.json")))
            AzgaarBlueprintCompiler.compile(source,out,new BlueprintCompileOptions(Double.parseDouble(args.length>2?args[2]:"100"),.35f,.2f,10,-20,2));
        var store=new BlueprintTileStore(out);set("store",store);set("detail",BlueprintDetailMap.read(out.resolve("ecology.bin.gz")));
        if(args.length>3&&!args[3].isBlank())reuseVerifiedTestCheckpoints(Path.of(args[3]),store,522448165476033769L);
        if(args.length>4&&Boolean.parseBoolean(args[4])){System.out.println("PASS: current blueprint compiled and inputs verified; inference intentionally not started.");return;}
        set("fingerprint",store.manifest().generationFingerprint());
        ModelAssetManager.ensureAssetsReady();PipelineModels.load();LocalTerrainProvider.init(522448165476033769L);
        var provider=LocalTerrainProvider.getInstance();
        var a=provider.fetchHeightmap(-32,-32,32,32);
        var b=provider.fetchHeightmap(-16,-16,16,16);
        for(int z=0;z<32;z++)for(int x=0;x<32;x++){
            if(Math.abs(a.heightmap[z+16][x+16]-b.heightmap[z][x])>1)throw new AssertionError("Height differs by request bounds");
            if(a.waterSurface[z+16][x+16]!=b.waterSurface[z][x])throw new AssertionError("Water seam");
            if(a.biomeIds[z+16][x+16]!=b.biomeIds[z][x])throw new AssertionError("Biome seam");
        }
        Field hf=LocalTerrainProvider.class.getDeclaredField("hydrology");hf.setAccessible(true);
        Field gf=WorldHydrology.class.getDeclaredField("grid");gf.setAccessible(true);
        DrainageGrid grid=(DrainageGrid)gf.get(hf.get(provider));
        if(args.length>5&&Boolean.parseBoolean(args[5])){verifyWaterBiomes(grid,store,provider,out);return;}
        writeOverview(grid,store,out);
        int chosen=-1;float best=0;
        for(int i=0;i<grid.terrain.length;i++)if(grid.river[i]&&grid.terrain[i]>80&&grid.lakeDepth(i)<10){
            float score=grid.flow[i];if(score>best){best=score;chosen=i;}
        }
        if(chosen<0)throw new AssertionError("No testable inland river exists");
        int x0=((chosen%grid.width)*16-store.manifest().width()*128)*3-128;
        int z0=((chosen/grid.width)*16-store.manifest().height()*128)*3-128;
        var river=provider.fetchHeightmap(z0,x0,z0+256,x0+256);
        int wet=0;java.awt.image.BufferedImage picture=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int z=0;z<256;z++)for(int x=0;x<256;x++){
            boolean water=river.waterSurface[z][x]!=Short.MIN_VALUE;if(water){wet++;if(river.heightmap[z][x]>=river.waterSurface[z][x])throw new AssertionError("Dry river bed");}
            int color=BiomeCatalog.color(river.biomeIds[z][x]);
            if(water)color=0x3288c9;
            else{
                int left=river.heightmap[z][Math.max(0,x-1)],right=river.heightmap[z][Math.min(255,x+1)];
                double shade=Math.max(.45,Math.min(1.4,1+(left-right)*.025));
                int red=Math.min(255,(int)(((color>>16)&255)*shade)),green=Math.min(255,(int)(((color>>8)&255)*shade)),blue=Math.min(255,(int)((color&255)*shade));
                color=(red<<16)|(green<<8)|blue;
            }
            picture.setRGB(x,z,color);
        }
        if(wet<100)throw new AssertionError("Selected river contains too little water");
        javax.imageio.ImageIO.write(picture,"png",out.resolve("river-preview.png").toFile());
        var cross=provider.fetchHeightmap(z0+120,x0+120,z0+152,x0+152);
        for(int z=0;z<32;z++)for(int x=0;x<32;x++){
            if(river.waterSurface[z+120][x+120]!=cross.waterSurface[z][x])throw new AssertionError("Wet river tile seam");
            if(Math.abs(river.heightmap[z+120][x+120]-cross.heightmap[z][x])>1)throw new AssertionError("River bed seam");
        }
        Files.writeString(out.resolve("integration-results.txt"),"Scale=3; physical width="+store.manifest().physicalWidthKm()+" km; source="+source+"\nRiver nodes="+grid.riverCount()+"; wet columns in 256x256 sample="+wet+"\nSample origin X="+x0+" Z="+z0+"\nThis is a real-model field check, not an in-game fluid/structure test.\n");
        List<Map<String,Object>> coverage=new ArrayList<>();
        for(var reservation:WorldBlueprintManager.biomeReservations()){
            var site=WorldBlueprintManager.reservationCenter(reservation);int x=(int)Math.round(site.x()),z=(int)Math.round(site.z());
            var sample=provider.fetchHeightmap(z,x,z+4,x+4);
            Map<String,Object> row=new LinkedHashMap<>();row.put("expected",BiomeCatalog.name(reservation.biomeId()));row.put("actual",BiomeCatalog.name(sample.biomeIds[0][0]));
            row.put("matches",reservation.biomeId()==sample.biomeIds[0][0]);row.put("x",x);row.put("z",z);row.put("elevationMetres",sample.heightmap[0][0]);coverage.add(row);
        }
        Files.writeString(out.resolve("coverage-sites.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(coverage));
        System.out.println("PASS: real-model scale-3 overlapping terrain, inland water and biome query checks. Source="+source);
    }

    private static void verifyWaterBiomes(DrainageGrid grid,BlueprintTileStore store,LocalTerrainProvider provider,Path out)throws Exception{
        List<Map<String,Object>> rows=new ArrayList<>();
        for(short target:new short[]{BiomeIds.RIVER,BiomeIds.FROZEN_RIVER}){
            int chosen=-1;float best=-1;
            for(int i=0;i<grid.terrain.length;i++)if(grid.river[i]&&grid.terrain[i]>80&&grid.lakeDepth(i)<10){
                int cx=Math.min(store.manifest().width()-1,i%grid.width/16),cz=Math.min(store.manifest().height()-1,i/grid.width/16);
                float q=store.value(0,cx,cz),source=(float)Math.copySign(q*q,q);
                float temp=store.value(1,cx,cz)-.0065f*(Math.max(0,grid.terrain[i])-Math.max(0,source));
                if((target==BiomeIds.RIVER?temp>10:temp<-10)&&grid.flow[i]>best){best=grid.flow[i];chosen=i;}
            }
            if(chosen<0)throw new AssertionError("No suitable water-biome candidate for "+BiomeCatalog.name(target));
            int x=(chosen%grid.width*16-store.manifest().width()*128)*3,z=(chosen/grid.width*16-store.manifest().height()*128)*3;
            var sample=provider.fetchHeightmap(z,x,z+4,x+4);
            Map<String,Object> row=new LinkedHashMap<>();row.put("expected",BiomeCatalog.name(target));row.put("actual",BiomeCatalog.name(sample.biomeIds[0][0]));
            row.put("matches",target==sample.biomeIds[0][0]&&sample.waterSurface[0][0]!=Short.MIN_VALUE);row.put("x",x);row.put("z",z);rows.add(row);
        }
        Files.writeString(out.resolve("water-biome-sites.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
        if(rows.stream().anyMatch(row->!Boolean.TRUE.equals(row.get("matches"))))throw new AssertionError("Water biome site mismatch: "+rows);
        int peak=0;for(int i=1;i<grid.terrain.length;i++)if(grid.terrain[i]>grid.terrain[peak])peak=i;
        int px=(peak%grid.width*16-store.manifest().width()*128)*3-256,pz=(peak/grid.width*16-store.manifest().height()*128)*3-256;
        var detail=provider.fetchHeightmap(pz,px,pz+512,px+512);int maxMetres=0,mx=0,mz=0;
        for(int z=0;z<512;z++)for(int x=0;x<512;x++)if(detail.heightmap[z][x]>maxMetres){maxMetres=detail.heightmap[z][x];mx=px+x;mz=pz+z;}
        int maxY=com.github.xandergos.terraindiffusionmc.world.HeightConverter.convertToMinecraftHeight((short)maxMetres,3)-1;
        Map<String,Object> peakResult=Map.of("maximumMetresIn512BlockPeakSample",maxMetres,"highestSolidBlockY",maxY,"x",mx,"z",mz,"scale3Ceiling",1407,"headroomBlocks",1407-maxY);
        Files.writeString(out.resolve("peak-detail-check.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(peakResult));
        if(maxY>=1407)throw new AssertionError("Detailed peak hits the scale-3 build ceiling: "+peakResult);
        System.out.println("PASS: both River and Frozen River verified on actual inland water.");
    }

    /** Test-only reuse for unchanged sampler inputs. Never used to migrate a save. */
    private static void reuseVerifiedTestCheckpoints(Path previous,BlueprintTileStore current,long seed)throws Exception{
        var old=new BlueprintTileStore(previous);var gson=new com.google.gson.Gson();
        var a=gson.toJsonTree(old.manifest()).getAsJsonObject();var b=gson.toJsonTree(current.manifest()).getAsJsonObject();
        a.remove("biomeReservations");b.remove("biomeReservations");a.remove("dataSha256");b.remove("dataSha256");
        if(!a.equals(b))throw new IllegalArgumentException("Cannot reuse diagnostic checkpoints: terrain/climate settings differ");
        for(int y=0;y<current.manifest().height();y++)for(int x=0;x<current.manifest().width();x++)for(int ch=0;ch<current.manifest().effectiveChannelCount();ch++)
            if(Float.floatToIntBits(old.value(ch,x,y))!=Float.floatToIntBits(current.value(ch,x,y)))throw new IllegalArgumentException("Cannot reuse diagnostic checkpoints: conditioning pixels differ");
        var before=BlueprintDetailMap.read(previous.resolve("ecology.bin.gz"));var after=BlueprintDetailMap.read(current.directory().resolve("ecology.bin.gz"));
        if(before.width!=after.width||before.height!=after.height)throw new IllegalArgumentException("Diagnostic detail dimensions differ");
        for(int y=0;y<before.height;y++)for(int x=0;x<before.width;x++)for(int ch=0;ch<3;ch++){
            double u=(x+.5)/before.width,v=(y+.5)/before.height;
            if(before.nearest(ch,u,v)!=after.nearest(ch,u,v))throw new IllegalArgumentException("Diagnostic coast/ecology pixels differ");
        }
        String suffix="-"+Long.toUnsignedString(seed)+"-v"+WorldHydrology.VERSION;
        Path from=previous.resolve("hydrology").resolve(old.manifest().generationFingerprint()+suffix);
        Path to=current.directory().resolve("hydrology").resolve(current.manifest().generationFingerprint()+suffix);
        Files.createDirectories(to);int copied=0,invalidated=0;
        try(var files=Files.list(from)){
            for(Path file:files.filter(p->p.getFileName().toString().endsWith(".bin.gz")).toList()){
                String[] xy=file.getFileName().toString().replace(".bin.gz","").split("_");
                int gx=Integer.parseInt(xy[0]),gz=Integer.parseInt(xy[1]),gw=current.manifest().width()*16+1,gh=current.manifest().height()*16+1;
                boolean changed=false;
                for(int z=gz;z<Math.min(gz+32,gh);z++)for(int x=gx;x<Math.min(gx+32,gw);x++){
                    double u=x/(double)(gw-1),v=z/(double)(gh-1);
                    if((before.nearest(3,u,v)>=0)!=(after.nearest(3,u,v)>=0))changed=true;
                }
                if(changed){invalidated++;continue;}
                Path dest=to.resolve(file.getFileName());if(!Files.exists(dest)){Files.copy(file,dest);copied++;}
            }
        }
        System.out.println("Reused "+copied+" verified diagnostic elevation checkpoints; "+invalidated+" lake-mask checkpoints require fresh inference; previous test data preserved.");
    }

    /** Fast review artifact: real generated drainage elevations, but coarse conditioning climate.
     * It is deliberately labelled approximate; individual biome sites below use full inference. */
    private static void writeOverview(DrainageGrid grid,BlueprintTileStore store,Path out)throws Exception{
        int w=grid.width,h=grid.height,n=w*h,step=WorldHydrology.STEP*3;
        float[] climate=new float[4*n],padded=new float[(w+2)*(h+2)];
        float maxElevation=-Float.MAX_VALUE,maxLakeDepth=0;int maxIndex=0,land=0,lake=0,sourceLand=0,lostLand=0;
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x,cx=Math.min(store.manifest().width()-1,x/16),cz=Math.min(store.manifest().height()-1,z/16);
            for(int ch=0;ch<4;ch++)climate[ch*n+i]=store.value(ch+1,cx,cz);
            float q=store.value(0,cx,cz),source=(float)Math.copySign(q*q,q);
            climate[i]-=.0065f*(Math.max(0,grid.terrain[i])-Math.max(0,source));
            if(grid.terrain[i]>maxElevation){maxElevation=grid.terrain[i];maxIndex=i;}
            if(grid.terrain[i]>0)land++;
            if(grid.lakeDepth(i)>15)lake++;
            maxLakeDepth=Math.max(maxLakeDepth,grid.lakeDepth(i));
            if(WorldBlueprintManager.detail().sample(0,x/(double)(w-1),z/(double)(h-1))>0){sourceLand++;if(grid.terrain[i]<=0)lostLand++;}
        }
        for(int z=0;z<h+2;z++)for(int x=0;x<w+2;x++)
            padded[z*(w+2)+x]=grid.terrain[Math.max(0,Math.min(h-1,z-1))*w+Math.max(0,Math.min(w-1,x-1))];
        int x0=-store.manifest().width()*128*3,z0=-store.manifest().height()*128*3;
        short[] ids=BiomeClassifier.classify(grid.terrain,climate,z0,x0,padded,h,w,480,step);
        var picture=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        Map<String,Integer> counts=new TreeMap<>();
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x;short id=ids[i];
            if(grid.river[i]||grid.lakeDepth(i)>15)id=climate[i]<0?BiomeIds.FROZEN_RIVER:BiomeIds.RIVER;
            counts.merge(BiomeCatalog.name(id),1,Integer::sum);
            picture.setRGB(x,z,BiomeCatalog.color(id));
        }
        javax.imageio.ImageIO.write(picture,"png",out.resolve("approximate-biomes-and-drainage.png").toFile());
        Map<String,Object> metrics=new LinkedHashMap<>();
        metrics.put("previewCaveat","48-block samples; generated drainage elevation with compiled, not diffused, climate. Not an exact biome census or chunk rendering.");
        metrics.put("gridWidth",w);metrics.put("gridHeight",h);metrics.put("riverSegments",grid.riverCount());
        metrics.put("landSamples",land);metrics.put("lakeSamples",lake);metrics.put("maximumLakeDepthMetres",maxLakeDepth);
        metrics.put("maximumSampledElevationMetres",maxElevation);
        metrics.put("maximumSampledElevationX",x0+maxIndex%w*step);metrics.put("maximumSampledElevationZ",z0+maxIndex/w*step);
        metrics.put("sourceLandSamples",sourceLand);metrics.put("sourceLandSamplesBelowSeaAfterDiffusion",lostLand);
        metrics.put("approximateBiomeSampleCounts",counts);
        Files.writeString(out.resolve("drainage-overview-metrics.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(metrics));
    }
}
