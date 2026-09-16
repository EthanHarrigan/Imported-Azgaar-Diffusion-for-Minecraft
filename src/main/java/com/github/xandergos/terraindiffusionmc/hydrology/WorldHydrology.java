package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipeline;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One immutable world-wide drainage graph, checkpointed before the first new chunk is generated. */
public final class WorldHydrology {
    public static final int VERSION=2, STEP=16, PREVIEW_STEP=8, TILE=512;
    private static final Logger LOG=LoggerFactory.getLogger(WorldHydrology.class);
    private static volatile String progress="";
    private static volatile String cacheNotice="";
    private static volatile WorldHydrology preview;
    public static WorldHydrology preview(){return preview;}
    public static void clearPreview(){preview=null;cacheNotice="";}
    public static void publishPreview(WorldHydrology value){preview=value;}
    /** Read-only interpolation of already prepared terrain; never triggers inference. */
    public float previewElevation(double nativeX,double nativeZ){
        if(previewTerrain!=null){
            double x=(nativeX+nativeWidth/2.0)/PREVIEW_STEP,z=(nativeZ+nativeHeight/2.0)/PREVIEW_STEP;
            float e=interpolate(previewTerrain,previewWidth,previewHeight,x,z);
            // Authored lake excavation is stored on the routing grid; apply its smooth
            // interpolation without discarding the denser terrain between graph nodes.
            double gx=(nativeX+nativeWidth/2.0)/STEP,gz=(nativeZ+nativeHeight/2.0)/STEP;
            e-=interpolate(lakeCuts,grid.width,grid.height,gx,gz);
            if(WorldBlueprintManager.generationVersion()>=10){
                LakeSample lake=naturalLake(gx,gz);
                e=lakeShoreBed(e,lake.depth,lake.level,lakeClearance(nativeX,nativeZ));
            }
            return e;
        }
        double x=(nativeX+nativeWidth/2.0)/STEP,z=(nativeZ+nativeHeight/2.0)/STEP;
        return interpolate(grid.terrain,grid.width,grid.height,x,z);
    }
    public float previewWater(double nativeX,double nativeZ){
        double x=(nativeX+nativeWidth/2.0)/STEP,z=(nativeZ+nativeHeight/2.0)/STEP;
        if(WorldBlueprintManager.generationVersion()>=10){
            LakeSample lake=naturalLake(x,z);float bed=previewElevation(nativeX,nativeZ);
            return lake.depth>8&&bed>0&&bed<lake.level-lakeClearance(nativeX,nativeZ)?lake.level:Float.NaN;
        }
        int ix=Math.max(0,Math.min(grid.width-1,(int)Math.round(x))),iz=Math.max(0,Math.min(grid.height-1,(int)Math.round(z)));
        int i=iz*grid.width+ix;
        return grid.lakeDepth(i)>15?grid.surface[i]:Float.NaN;
    }
    /** Bounded decoration-only query. Uses immutable prepared fields, never loads chunks/models. */
    public boolean freshwaterNear(double nativeX,double nativeZ,float groundMetres){
        final int radius=8,size=17;int x0=(int)Math.floor(nativeX)-radius,z0=(int)Math.floor(nativeZ)-radius;
        if(x0< -nativeWidth/2||z0< -nativeHeight/2||x0+size>=nativeWidth/2||z0+size>=nativeHeight/2)return false;
        float[] terrain=new float[size*size];
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            double nx=x0+x,nz=z0+z;
            terrain[z*size+x]=previewTerrain!=null?interpolate(previewTerrain,previewWidth,previewHeight,(nx+nativeWidth/2.0)/PREVIEW_STEP,(nz+nativeHeight/2.0)/PREVIEW_STEP)
                    :interpolate(grid.terrain,grid.width,grid.height,(nx+nativeWidth/2.0)/STEP,(nz+nativeHeight/2.0)/STEP)+interpolate(lakeCuts,grid.width,grid.height,(nx+nativeWidth/2.0)/STEP,(nz+nativeHeight/2.0)/STEP);
        }
        var water=carve(terrain,z0,x0,size,size,1).water();
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            float level=water[z*size+x];
            if(com.github.xandergos.terraindiffusionmc.world.SurfaceAccentRules.freshwater(groundMetres,level,Math.hypot(x0+x-nativeX,z0+z-nativeZ),1))return true;
        }
        return false;
    }
    public static float interpolate(float[] field,int width,int height,double x,double z){
        x=Math.max(0,Math.min(width-1,x));z=Math.max(0,Math.min(height-1,z));
        int ix=(int)x,iz=(int)z,jx=Math.min(width-1,ix+1),jz=Math.min(height-1,iz+1);
        double a=x-ix,b=z-iz;
        return (float)((field[iz*width+ix]*(1-a)+field[iz*width+jx]*a)*(1-b)+(field[jz*width+ix]*(1-a)+field[jz*width+jx]*a)*b);
    }
    public static String progress(){return progress;}
    public static String cacheNotice(){return cacheNotice;}
    private final DrainageGrid grid;
    private final int nativeWidth,nativeHeight;
    private final float[] previewTerrain;
    private final int previewWidth,previewHeight;
    private final int[] outlet;
    private final float[] channelLevels;
    private final Set<Integer> majorOutlets;
    private float[] lakeCuts;
    WorldHydrology(DrainageGrid grid,int nativeWidth,int nativeHeight){this(grid,nativeWidth,nativeHeight,null,0,0);}
    public WorldHydrology(DrainageGrid grid,int nativeWidth,int nativeHeight,float[] previewTerrain,int previewWidth,int previewHeight){
        this.grid=grid;this.nativeWidth=nativeWidth;this.nativeHeight=nativeHeight;
        this.previewTerrain=previewTerrain;this.previewWidth=previewWidth;this.previewHeight=previewHeight;
        this.channelLevels=new float[grid.parent.length];
        this.outlet=resolveOutlets(grid,channelLevels);
        Map<Integer,Float> discharge=new HashMap<>();
        for(int i=0;i<grid.river.length;i++)if(grid.river[i])discharge.merge(outlet[i],grid.flow[i],Math::max);
        this.majorOutlets=discharge.entrySet().stream().sorted((a,b)->{int c=Float.compare(b.getValue(),a.getValue());return c!=0?c:Integer.compare(a.getKey(),b.getKey());})
                .limit(2).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.lakeCuts=new float[grid.terrain.length];
    }
    private static int[] resolveOutlets(DrainageGrid grid,float[] levels){
        int[] out=new int[grid.parent.length];Arrays.fill(out,-1);int[] path=new int[grid.parent.length];
        for(int start=0;start<out.length;start++){
            int p=start,n=0;
            while(out[p]<0&&grid.parent[p]>=0){path[n++]=p;p=grid.parent[p];}
            int root=out[p]>=0?out[p]:p;
            if(out[p]<0)levels[p]=baseChannelLevel(grid,p);
            out[p]=root;
            // Reuse this downstream-first traversal: no extra per-column graph walks.
            while(n>0){int i=path[--n];out[i]=root;levels[i]=Math.max(baseChannelLevel(grid,i),levels[grid.parent[i]]);}
        }
        return out;
    }
    /** Whether this location belongs to either of the two highest-discharge outlet systems. */
    public boolean isMajorRiverAt(double blockX,double blockZ,int scale){
        double gx=(blockX/scale+nativeWidth/2.0)/STEP,gz=(blockZ/scale+nativeHeight/2.0)/STEP;
        int cx=(int)Math.round(gx),cz=(int)Math.round(gz),best=-1;double distance=Double.POSITIVE_INFINITY;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            int x=cx+dx,z=cz+dz;if(x<0||z<0||x>=grid.width||z>=grid.height)continue;int i=z*grid.width+x;
            if(!grid.river[i])continue;double d=Math.hypot(gx-x,gz-z);if(d<distance){distance=d;best=i;}
        }
        return best>=0&&majorOutlets.contains(outlet[best]);
    }
    public static WorldHydrology prepare(WorldPipeline pipeline,long seed)throws IOException{
        cacheNotice="";
        try {
            return prepareInternal(pipeline,seed);
        } finally {
            // A failed preparation must not leave a permanent progress overlay on unrelated screens.
            progress="";
            cacheNotice="";
        }
    }
    private static WorldHydrology prepareInternal(WorldPipeline pipeline,long seed)throws IOException{
        long started=System.nanoTime(),initialWindows=pipeline.getTotalComputedWindowCount();
        BlueprintTileStore store=WorldBlueprintManager.activeStore();
        if(store==null||WorldBlueprintManager.generationVersion()<3)return null;
        var m=store.manifest();int nw=m.width()*256,nh=m.height()*256;
        int w=nw/STEP+1,h=nh/STEP+1;
        if((long)w*h>8_500_000)throw new IOException("River preparation exceeds its bounded 8.5-million-node limit; reduce physical map width");
        boolean densePreview=WorldBlueprintManager.generationVersion()>=4;
        int cacheVersion=WorldBlueprintManager.generationVersion()>=13?4:WorldBlueprintManager.generationVersion()>=10?3:densePreview?VERSION:1;
        String cachePrefix=WorldBlueprintManager.fingerprint()+"-"+Long.toUnsignedString(seed)+"-p"+com.github.xandergos.terraindiffusionmc.world.VerticalProfile.identity();
        String cacheSuffix=WorldBlueprintManager.generationVersion()>=9?"-s"+WorldScaleManager.getCurrentScale():"";
        String key=cachePrefix+"-v"+cacheVersion+cacheSuffix;
        Path dir=store.directory().resolve("hydrology").resolve(key);Files.createDirectories(dir);
        SharedHydrologyCache shared=SharedHydrologyCache.open(WorldBlueprintManager.fingerprint()+"-p"+com.github.xandergos.terraindiffusionmc.world.VerticalProfile.identity(),seed,
                WorldScaleManager.getCurrentScale(),nw,nh,cacheVersion);
        int previewW=densePreview?nw/PREVIEW_STEP+1:0,previewH=densePreview?nh/PREVIEW_STEP+1:0;
        Path solvedFile=dir.resolve("prepared-routing-v1.bin.gz");
        shared.restoreRouting(solvedFile);
        WorldHydrology saved=readPrepared(solvedFile,key,nw,nh);
        if(saved!=null){
            WorldBlueprintManager.setPreparedReservations(BiomeCoveragePlanner.fromGeneratedTerrain(saved.grid,store));
            preview=saved;LOG.info("Restored saved river routing; no river recomputation: {}",solvedFile);
            return saved;
        }
        int oldVersion=shared.olderVersion(dir.getParent(),cachePrefix,cacheSuffix,
                WorldBlueprintManager.fingerprint()+"-p"+com.github.xandergos.terraindiffusionmc.world.VerticalProfile.identity(),
                seed,WorldScaleManager.getCurrentScale(),nw,nh,cacheVersion);
        if(oldVersion>0){
            cacheNotice="Terrain updated: older river cache can't be reused. Preparing updated rivers.";
            LOG.info("River cache version changed: v{} -> v{}. Older matching cache found; preparing current routing.",oldVersion,cacheVersion);
        }
        progress="Preparing rivers: checking saved checkpoints...";
        // Hydrate the world-local directory before building the expensive neural prefetch list.
        for(int gz=0;gz<h;gz+=32) for(int gx=0;gx<w;gx+=32){
            int tw=Math.min(32,w-gx),th=Math.min(32,h-gz);
            shared.copyToWorld(dir.resolve(gx+"_"+gz+".bin.gz"),tw*th);
            if(densePreview){
                int pw=Math.min(64,previewW-gx*2),ph=Math.min(64,previewH-gz*2);
                if(pw>0&&ph>0) shared.copyToWorld(dir.resolve("preview_"+gx+"_"+gz+".bin.gz"),pw*ph);
            }
        }
        float[] elev=new float[w*h],rain=new float[w*h],hint=new float[w*h];
        float[] previewElev=densePreview?new float[previewW*previewH]:null;
        boolean[] sourceLakes=new boolean[w*h];
        int done=0,restored=0,computed=0,total=((w+31)/32)*((h+31)/32);
        progress="Preparing rivers: 0 / "+total+" terrain tiles (checkpoints saved automatically)";
        LOG.info("Preparing connected rivers: {} terrain checkpoints; shared-stage prefetch={}. Completed checkpoints are reusable.",total,WorldBlueprintManager.generationVersion()>=6);
        // Each checkpoint has fixed native bounds; query order and screen zoom never affect this field.
        // Visit 2x2 checkpoints together. Prefetching runs each neural stage once for
        // their shared halo, while each checkpoint keeps its historical query bounds.
        int group=WorldBlueprintManager.generationVersion()>=6?64:32;
        for(int bz=0;bz<h;bz+=group)for(int bx=0;bx<w;bx+=group){
        if(group==64){
            List<int[]> missingRegions=new ArrayList<>();
            for(int gz=bz;gz<Math.min(h,bz+group);gz+=32)for(int gx=bx;gx<Math.min(w,bx+group);gx+=32){
                int tw=Math.min(32,w-gx),th=Math.min(32,h-gz);
                if((!Files.exists(dir.resolve(gx+"_"+gz+".bin.gz"))||
                        densePreview&&!Files.exists(dir.resolve("preview_"+gx+"_"+gz+".bin.gz")))
                        &&!deepOceanRegion(gx,gz,tw,th,nw,nh)){
                    int pw0=densePreview?Math.min(64,previewW-gx*2):0,ph0=densePreview?Math.min(64,previewH-gz*2):0;
                    int ow=Math.max((tw-1)*STEP+1,densePreview?(pw0-1)*PREVIEW_STEP+1:0);
                    int oh=Math.max((th-1)*STEP+1,densePreview?(ph0-1)*PREVIEW_STEP+1:0);
                    int x0=gx*STEP-nw/2,z0=gz*STEP-nh/2;
                    missingRegions.add(new int[]{z0,x0,z0+oh,x0+ow});
                }
            }
            if(!missingRegions.isEmpty())progress="Preparing detailed river terrain: "+done+" / "+total+" (processing "+missingRegions.size()+" neighboring regions together)";
            pipeline.prepareElevationRegions(missingRegions);
        }
        for(int gz=bz;gz<Math.min(h,bz+group);gz+=32)for(int gx=bx;gx<Math.min(w,bx+group);gx+=32){
            if(Thread.currentThread().isInterrupted())throw new IOException("River preparation interrupted");
                int tw=Math.min(32,w-gx),th=Math.min(32,h-gz);Path file=dir.resolve(gx+"_"+gz+".bin.gz");
                if (!Files.exists(file)) shared.copyToWorld(file,tw*th);
            float[] sample=readCheckpoint(file,tw*th);
            int pgx=gx*2,pgz=gz*2,pw=densePreview?Math.min(64,previewW-pgx):0,ph=densePreview?Math.min(64,previewH-pgz):0;
            Path previewFile=dir.resolve("preview_"+gx+"_"+gz+".bin.gz");
            if (densePreview && !Files.exists(previewFile)) shared.copyToWorld(previewFile,pw*ph);
            float[] previewSample=densePreview?readCheckpoint(previewFile,pw*ph):null;
            if(sample==null||densePreview&&previewSample==null)computed++;else restored++;
            if(sample==null||densePreview&&previewSample==null){
                int x0=gx*STEP-nw/2,z0=gz*STEP-nh/2;
                int outW=Math.max((tw-1)*STEP+1,densePreview?(pw-1)*PREVIEW_STEP+1:0);
                int outH=Math.max((th-1)*STEP+1,densePreview?(ph-1)*PREVIEW_STEP+1:0);
                float[] field=null;
                if(deepOceanRegion(gx,gz,tw,th,nw,nh)){
                    // Deep ocean does not need neural relief, but both grids still receive
                    // independently sampled source bathymetry.
                }else{
                    field=pipeline.get(z0,x0,z0+outH,x0+outW,false)[0];
                }
                if(sample==null){
                    sample=new float[tw*th];
                    for(int z=0;z<th;z++)for(int x=0;x<tw;x++){
                        float e=field==null?WorldBlueprintManager.detail().sample(0,(gx+x)*STEP/(double)nw,(gz+z)*STEP/(double)nh):field[z*STEP*outW+x*STEP];
                        double nx=(gx+x)*STEP-nw/2.0,nz=(gz+z)*STEP-nh/2.0;
                        sample[z*tw+x]=correctCoast(enhanceMountainRelief(e,nx,nz,seed),nx,nz,nw,nh);
                    }
                    writeCheckpoint(file,sample); shared.publish(file,tw*th);
                }
                if(densePreview&&previewSample==null){
                    previewSample=new float[pw*ph];
                    for(int z=0;z<ph;z++)for(int x=0;x<pw;x++){
                        float e=field==null?WorldBlueprintManager.detail().sample(0,(pgx+x)*PREVIEW_STEP/(double)nw,(pgz+z)*PREVIEW_STEP/(double)nh):field[z*PREVIEW_STEP*outW+x*PREVIEW_STEP];
                        double nx=(pgx+x)*PREVIEW_STEP-nw/2.0,nz=(pgz+z)*PREVIEW_STEP-nh/2.0;
                        previewSample[z*pw+x]=refineCoast(correctCoast(enhanceMountainRelief(e,nx,nz,seed),nx,nz,nw,nh),nx,nz,nw,nh);
                    }
                    writeCheckpoint(previewFile,previewSample); shared.publish(previewFile,pw*ph);
                }
            }
            for(int z=0;z<th;z++)for(int x=0;x<tw;x++){
                int i=(gz+z)*w+gx+x;double u=(gx+x)*STEP/(double)nw,v=(gz+z)*STEP/(double)nh;
                // Checkpoints retain the original soft-corrected model sample. This inexpensive
                // shoreline refinement is also applied to block terrain, not baked twice into caches.
                elev[i]=refineCoast(sample[z*tw+x],(gx+x)*STEP-nw/2.0,(gz+z)*STEP-nh/2.0,nw,nh);
                int cx=Math.min(m.width()-1,(gx+x)*STEP/256),cy=Math.min(m.height()-1,(gz+z)*STEP/256);
                rain[i]=store.value(3,cx,cy);hint[i]=WorldBlueprintManager.detail().sample(2,u,v);
                sourceLakes[i]=WorldBlueprintManager.detail().nearest(3,u,v)>=0;
            }
            if(densePreview)for(int z=0;z<ph;z++)System.arraycopy(previewSample,z*pw,previewElev,(pgz+z)*previewW+pgx,pw);
            done++;progress="Preparing rivers: "+done+" / "+total+" terrain tiles ("+(100*done/total)+"%)";
            if(done%8==0||done==total)LOG.info("River terrain preparation {}/{} ({}%)",done,total,100*done/total);
        }
        }
        float[] lakeCuts=LakeBasinPlanner.cuts(elev,sourceLakes,w,h);
        for(int i=0;i<elev.length;i++)elev[i]-=lakeCuts[i];
        DrainageGrid graph=new DrainageGrid(w,h,STEP,elev,rain,hint);graph.validate();
        WorldBlueprintManager.setPreparedReservations(BiomeCoveragePlanner.fromGeneratedTerrain(graph,store));
        LOG.info("River network ready: {} nodes, {} channel segments",w*h,graph.riverCount());
        LOG.info("River preparation completed in {} seconds: {} new checkpoints, {} restored, {} neural windows",
                (System.nanoTime()-started)/1_000_000_000.0,computed,restored,pipeline.getTotalComputedWindowCount()-initialWindows);
        progress="";
        WorldHydrology result=new WorldHydrology(graph,nw,nh,previewElev,previewW,previewH);result.lakeCuts=lakeCuts;result.writePrepared(solvedFile,key);try{shared.publishRouting(solvedFile);}catch(IOException cacheFailure){LOG.warn("Could not publish shared routing cache",cacheFailure);}preview=result;return result;
    }
    /** Fail closed on corrupt saved routing: never silently substitute a different river graph. */
    static WorldHydrology readPrepared(Path path,String identity,int nw,int nh)throws IOException{
        if(!Files.exists(path))return null;
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))){
            if(in.readInt()!=0x54445231||!in.readUTF().equals(identity))throw new IOException("Saved routing identity mismatch");
            int w=nw/STEP+1,h=nh/STEP+1,n=Math.multiplyExact(w,h);
            if(in.readInt()!=w||in.readInt()!=h)throw new IOException("Saved routing dimensions mismatch");
            float[] terrain=new float[n],surface=new float[n],flow=new float[n],cuts=new float[n];
            int[] parent=new int[n];boolean[] river=new boolean[n];
            for(int i=0;i<n;i++){terrain[i]=in.readFloat();surface[i]=in.readFloat();flow[i]=in.readFloat();parent[i]=in.readInt();river[i]=in.readBoolean();cuts[i]=in.readFloat();if(!Float.isFinite(cuts[i])||cuts[i]<0)throw new IOException("Invalid lake cut");}
            int pw=in.readInt(),ph=in.readInt();
            if(!((pw==0&&ph==0)||(pw==nw/PREVIEW_STEP+1&&ph==nh/PREVIEW_STEP+1)))throw new IOException("Invalid preview dimensions");
            float[] pe=pw==0?null:new float[Math.multiplyExact(pw,ph)];
            if(pe!=null)for(int i=0;i<pe.length;i++){pe[i]=in.readFloat();if(!Float.isFinite(pe[i]))throw new IOException("Invalid preview elevation");}
            if(in.read()!=-1)throw new IOException("Trailing saved routing data");
            var result=new WorldHydrology(new DrainageGrid(w,h,STEP,terrain,surface,flow,parent,river),nw,nh,pe,pw,ph);
            result.lakeCuts=cuts;return result;
        }catch(IllegalStateException|IllegalArgumentException e){throw new IOException("Invalid saved routing: "+path,e);}
    }
    void writePrepared(Path path,String identity)throws IOException{
        Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),"routing-",".tmp");
        try{
            try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))){
                out.writeInt(0x54445231);out.writeUTF(identity);out.writeInt(grid.width);out.writeInt(grid.height);
                for(int i=0;i<grid.terrain.length;i++){out.writeFloat(grid.terrain[i]);out.writeFloat(grid.surface[i]);out.writeFloat(grid.flow[i]);out.writeInt(grid.parent[i]);out.writeBoolean(grid.river[i]);out.writeFloat(lakeCuts[i]);}
                out.writeInt(previewWidth);out.writeInt(previewHeight);
                if(previewTerrain!=null)for(float e:previewTerrain)out.writeFloat(e);
            }
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
    private static boolean deepOceanRegion(int gx,int gz,int w,int h,int nw,int nh){
        BlueprintDetailMap d=WorldBlueprintManager.detail();
        // Include a full coarse-cell coastal halo. Never omit a source island or an inland lake.
        for(int z=gz-16;z<=gz+h+16;z++)for(int x=gx-16;x<=gx+w+16;x++)
            if(d.sample(0,x*STEP/(double)nw,z*STEP/(double)nh)>-500)return false;
        return true;
    }
    private static float[] readCheckpoint(Path file,int n)throws IOException{
        if(!Files.exists(file))return null;
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))){
            if(in.readInt()!=0x54444831||in.readInt()!=n)throw new IOException("Invalid river checkpoint: "+file);
            float[] a=new float[n];for(int i=0;i<n;i++){a[i]=in.readFloat();if(!Float.isFinite(a[i]))throw new IOException("Invalid river elevation");}
            if(in.read()!=-1)throw new IOException("Trailing river checkpoint data");return a;
        }
    }
    static boolean validCheckpoint(Path file,int n){
        try{return readCheckpoint(file,n)!=null;}catch(IOException|RuntimeException e){return false;}
    }
    private static void writeCheckpoint(Path path,float[] a)throws IOException{
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))){
            out.writeInt(0x54444831);out.writeInt(a.length);for(float v:a)out.writeFloat(v);
        }
        try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    /** Soft coastal protection uses continuous source elevations, never a square cell mask. */
    public static float correctCoast(float e,double nx,double nz,int nw,int nh){
        if(WorldBlueprintManager.generationVersion()>=7)return e; // One shared profile, not two stacked corrections.
        BlueprintDetailMap detail=WorldBlueprintManager.detail();
        if(detail==null||nx<=-nw/2.0||nx>=nw/2.0||nz<=-nh/2.0||nz>=nh/2.0)return e;
        double u=nx/nw+.5,v=nz/nh+.5;
        float source=detail.sample(0,u,v),lake=detail.nearest(3,u,v);
        if(lake>=0)return e; // A lake is handled by drainage, not by a sea-level clamp.
        float weight=(float)Math.exp(-Math.abs(source)/350.0);
        float target=source>=0?Math.max(e,Math.min(65,source*.55f)):Math.min(e,Math.max(-100,source*.1f));
        return e+(target-e)*weight*.8f;
    }
    /** Retain authored land/ocean away from a smooth ~96-block coastal band at scale 3.
     * Model relief above the modest shoreline floor is untouched; source lakes remain drainage-controlled. */
    public static float refineCoast(float e,double nx,double nz,int nw,int nh){
        BlueprintDetailMap d=WorldBlueprintManager.detail();
        if(d==null||nx<=-nw/2.0||nx>=nw/2.0||nz<=-nh/2.0||nz>=nh/2.0)return e;
        double u=nx/nw+.5,v=nz/nh+.5;
        if(WorldBlueprintManager.generationVersion()<9&&d.nearest(3,u,v)>=0)return e;
        float source=d.sample(0,u,v);
        if(WorldBlueprintManager.generationVersion()>=7){
            double distance=d.signedCoastDistance(u,v)*nw/d.width;
            double cliff=edgeNoise(nx,nz,0x434F415354434CL,700);
            // River corridors keep a low mouth instead of a raised cliff sill.
            if(d.sample(2,u,v)>.1)cliff=-1;
            float coast=CoastalProfile.height(e,source,distance,cliff,edgeNoise(nx,nz,0x42555454524553L,65));
            float result=WorldBlueprintManager.generationVersion()>=9
                    ?LakeEdgeProfile.height(e,coast,d.signedLakeDistance(u,v)*nw/d.width):coast;
            return WorldBlueprintManager.generationVersion()>=12
                    ?DesertTerrain.beach(result,nx,nz,nw,nh,com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getSeed()):result;
        }
        float target=source>=0?Math.max(e,Math.min(65,source*.55f)):Math.min(e,Math.max(-100,source*.1f));
        double t=Math.max(0,Math.min(1,d.coastDistance(u,v)/2));t=t*t*(3-2*t);
        return (float)(e+(target-e)*t);
    }
    public record Result(float[] bed,float[] water){}
    public float concavityAt(double nativeX,double nativeZ){
        double x=(nativeX+nativeWidth/2.0)/STEP,z=(nativeZ+nativeHeight/2.0)/STEP;
        if(x<4||z<4||x+5>=grid.width||z+5>=grid.height)return Float.NaN;
        float sum=0;int count=0;
        for(int dz=-4;dz<=4;dz+=4)for(int dx=-4;dx<=4;dx+=4){if(dx==0&&dz==0)continue;sum+=sampleTerrain(x+dx,z+dz);count++;}
        return sum/count-sampleTerrain(x,z);
    }
    private float sampleTerrain(double x,double z){
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z),p=iz*grid.width+ix;
        double fx=x-ix,fz=z-iz;float[] e=grid.terrain;
        return (float)((e[p]*(1-fx)+e[p+1]*fx)*(1-fz)+(e[p+grid.width]*(1-fx)+e[p+grid.width+1]*fx)*fz);
    }
    /** Rasterize complete segments, including halo nodes, into the requested block tile. */
    public Result carve(float[] terrain,int z0,int x0,int h,int w,int scale){
        boolean texturedBanks=WorldBlueprintManager.generationVersion()>=8;
        float[] bed=terrain.clone(),water=new float[w*h];Arrays.fill(water,Float.NaN);
        double startX=(x0/(double)scale+nativeWidth/2.0)/STEP,startZ=(z0/(double)scale+nativeHeight/2.0)/STEP;
        int minX=Math.max(0,(int)Math.floor(startX)-4),maxX=Math.min(grid.width-1,(int)Math.ceil(startX+w/(double)(scale*STEP))+4);
        int minZ=Math.max(0,(int)Math.floor(startZ)-4),maxZ=Math.min(grid.height-1,(int)Math.ceil(startZ+h/(double)(scale*STEP))+4);
        // Lake basins use priority-flood spill elevations, not the JSON's potentially incompatible height.
        for(int r=0;r<h;r++)for(int c=0;c<w;c++){
            double gx=startX+c/(double)(scale*STEP),gz=startZ+r/(double)(scale*STEP);
            int x=(int)Math.floor(gx),z=(int)Math.floor(gz);
            if(x<0||z<0||x+1>=grid.width||z+1>=grid.height)continue;
            int i=z*grid.width+x;float level=Math.min(Math.min(grid.surface[i],grid.surface[i+1]),Math.min(grid.surface[i+grid.width],grid.surface[i+grid.width+1]));
            LakeSample smoothLake=WorldBlueprintManager.generationVersion()>=10?naturalLake(gx,gz):null;
            boolean lakeCell=smoothLake!=null?smoothLake.depth>0:
                    grid.lakeDepth(i)>15||grid.lakeDepth(i+1)>15||grid.lakeDepth(i+grid.width)>15||grid.lakeDepth(i+grid.width+1)>15;
            if(lakeCell){
                int p=r*w+c;
                if(smoothLake!=null)level=smoothLake.level;
                if(lakeCuts[i]>0||lakeCuts[i+1]>0||lakeCuts[i+grid.width]>0||lakeCuts[i+grid.width+1]>0){
                    double fx=gx-x,fz=gz-z;
                    float cut=(float)((lakeCuts[i]*(1-fx)+lakeCuts[i+1]*fx)*(1-fz)+(lakeCuts[i+grid.width]*(1-fx)+lakeCuts[i+grid.width+1]*fx)*fz);
                    bed[p]-=cut;
                }
                // Only flood below the common spill plane; never remove hills to draw a rectangular lake.
                double shore=edgeNoise(x0+c,z0+r,0x4C414B4553484F52L,Math.max(18,24.0*scale));
                float clearance=(float)(7+shore*3);
                if(smoothLake!=null)bed[p]=lakeShoreBed(bed[p],smoothLake.depth,level,clearance);
                if(terrain[p]>0&&bed[p]<level-clearance&&(smoothLake==null||smoothLake.depth>8)){
                    water[p]=level;
                    // A shallow variable shelf grades into the basin instead of a single hard lip.
                    float depth=(float)(12+Math.max(0,level-bed[p]-clearance)*.22);
                    bed[p]=Math.min(bed[p],level-Math.min(28,depth));
                }
            }
        }
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            int i=z*grid.width+x,p=grid.parent[i];if(!grid.river[i]||p<0)continue;
            double[] aPos=nodePosition(i,scale),bPos=nodePosition(p,scale);
            double ax=aPos[0],az=aPos[1],bx=bPos[0],bz=bPos[1];
            float ra=grid.radiusNative(i)*scale,rb=grid.radiusNative(p)*scale;
            double bank=8.0*scale,range=Math.max(ra,rb)+bank+(texturedBanks?4.0*scale:0);
            double length=Math.hypot(bx-ax,bz-az);double[] ta=grid.tangent(i),tb=grid.tangent(p);
            double[] curveX=new double[5],curveZ=new double[5];
            for(int k=0;k<=4;k++){
                double t=k/4.0,u=1-t;
                curveX[k]=u*u*u*ax+3*u*u*t*(ax+ta[0]*length*.28)+3*u*t*t*(bx-tb[0]*length*.28)+t*t*t*bx;
                curveZ[k]=u*u*u*az+3*u*u*t*(az+ta[1]*length*.28)+3*u*t*t*(bz-tb[1]*length*.28)+t*t*t*bz;
            }
            range+=length*.28;
            int c0=Math.max(0,(int)Math.floor(Math.min(ax,bx)-range)-x0),c1=Math.min(w-1,(int)Math.ceil(Math.max(ax,bx)+range)-x0);
            int r0=Math.max(0,(int)Math.floor(Math.min(az,bz)-range)-z0),r1=Math.min(h-1,(int)Math.ceil(Math.max(az,bz)+range)-z0);
            for(int r=r0;r<=r1;r++)for(int c=c0;c<=c1;c++){
                double t=0,distance=Double.POSITIVE_INFINITY,cross=0;
                for(int k=0;k<4;k++){
                    double dx=curveX[k+1]-curveX[k],dz=curveZ[k+1]-curveZ[k],len2=dx*dx+dz*dz;
                    double u=len2==0?0:Math.max(0,Math.min(1,((x0+c-curveX[k])*dx+(z0+r-curveZ[k])*dz)/len2));
                    double rx=x0+c-curveX[k]-u*dx,rz=z0+r-curveZ[k]-u*dz,d=Math.hypot(rx,rz);
                    if(d<distance){distance=d;t=(k+u)/4;cross=dx*rz-dz*rx;}
                }
                double radius=ra+(rb-ra)*t;
                // Each bank has its own smooth world-space irregularity. Keep it small
                // relative to channel width so narrow streams cannot be pinched closed.
                long side=cross<0?0x51A7L:0xA17EL;
                double irregular=edgeNoise(x0+c,z0+r,side,Math.max(12,22.0*scale));
                radius=Math.max(2.0*scale,radius+irregular*Math.min(radius*.14,2.2*scale));
                double medium=0,fine=0,localBank=bank;
                if(texturedBanks){
                    if(distance>radius+bank*1.25+1.55*scale)continue;
                    if(distance>radius*.45){
                        medium=edgeNoise((x0+c)/(double)scale,(z0+r)/(double)scale,side^0x475241494EL,6);
                        fine=edgeNoise((x0+c)/(double)scale,(z0+r)/(double)scale,side^0x504542424CL,2);
                        radius=RiverEdgeShape.radius(radius,scale,medium,fine);
                        localBank=RiverEdgeShape.bankWidth(bank,medium);
                    }
                }
                if(distance>radius+localBank)continue;int at=r*w+c;
                float level=(float)(channelLevel(i)*(1-t)+channelLevel(p)*t);
                float depth=(float)(30*(1.0+Math.log1p(grid.flow[i]/300)*.35));
                if(distance<=radius){
                    float current=water[at];
                    // Lowest confluence wins; tributaries approach that same endpoint level.
                    if(!Float.isFinite(current)||level<current)water[at]=level;
                    if(texturedBanks)depth=RiverEdgeShape.depth(depth,distance,radius,fine);
                    bed[at]=Math.min(bed[at],level-depth);
                }else{
                    double a=(distance-radius)/localBank;a=a*a*(3-2*a);
                    double lip=texturedBanks?12+medium*4+fine*2:18;
                    float bankTarget=(float)((level+lip)*(1-a)+terrain[at]*a);
                    // Raise a narrow containing bank where sub-grid detail falls below the water plane.
                    bed[at]=Float.isFinite(water[at])?bed[at]:Math.min(bed[at],Math.max(bankTarget,level+3));
                    if(terrain[at]<level+3&&!Float.isFinite(water[at]))bed[at]=Math.max(bed[at],bankTarget);
                }
            }
        }
        float minimumWaterDepth=texturedBanks?Math.max(10,30f/WorldScaleManager.getCurrentScale()):18;
        for(int i=0;i<bed.length;i++)if(Float.isFinite(water[i]))bed[i]=Math.min(bed[i],water[i]-minimumWaterDepth);
        return new Result(bed,water);
    }
    private volatile boolean[] suppressedDesertLakes;
    private boolean[] desertLakeMask(){
        var mask=suppressedDesertLakes;if(mask!=null)return mask;
        synchronized(this){
            if(suppressedDesertLakes==null)suppressedDesertLakes=DesertLakePolicy.suppressed(grid,lakeCuts,i->{
                double nx=(i%grid.width)*STEP-nativeWidth/2.0,nz=(i/grid.width)*STEP-nativeHeight/2.0;
                return DesertTerrain.at(nx,nz,com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getSeed()).dry()>.55;
            });
            return suppressedDesertLakes;
        }
    }
    record LakeSample(float depth,float level){}
    private static float lakeClearance(double nx,double nz){
        return (float)(7+edgeNoise(nx,nz,0x4C414B4553484F52L,24)*3);
    }
    /** Interpolate basin membership, never the water plane with dry hillside elevations. */
    LakeSample naturalLake(double gx,double gz){
        gx=Math.max(0,Math.min(grid.width-1.000001,gx));gz=Math.max(0,Math.min(grid.height-1.000001,gz));
        int x=(int)Math.floor(gx),z=(int)Math.floor(gz);double fx=gx-x,fz=gz-z;
        double depth=0,level=Double.POSITIVE_INFINITY;
        boolean[] removed=desertLakeMask();
        for(int n=0;n<4;n++){
            int q=(z+(n>>1))*grid.width+x+(n&1);
            double w=((n&1)==0?1-fx:fx)*(n<2?1-fz:fz);
            double d=removed[q]?0:Math.max(0,grid.lakeDepth(q)-15);
            if(d>0&&w>0)level=Math.min(level,grid.surface[q]);
        }
        // Separate spill planes must not become a sloping lake. At a divide the lower
        // basin owns the overlap; only its own wet nodes contribute shoreline membership.
        for(int n=0;n<4;n++){
            int q=(z+(n>>1))*grid.width+x+(n&1);
            double w=((n&1)==0?1-fx:fx)*(n<2?1-fz:fz);
            if(!removed[q]&&grid.surface[q]==level)depth+=Math.max(0,grid.lakeDepth(q)-15)*w;
        }
        return new LakeSample((float)depth,Double.isFinite(level)?(float)level:0);
    }
    private static float lakeShoreBed(float bed,float depth,float level,float clearance){
        if(depth<=0||bed>=level+4)return bed;
        // A dry crest before the wet shelf contains the full water height, not just 18 m.
        double enter=Math.max(0,Math.min(1,depth/8.0));enter=enter*enter*(3-2*enter);
        double inner=Math.max(0,Math.min(1,(depth-8)/48.0));inner=inner*inner*(3-2*inner);
        double target=level+4+(Math.min(bed,level-clearance-12)-level-4)*inner;
        return (float)(bed+(target-bed)*enter);
    }
    float channelLevel(int i){return channelLevels[i];}
    private static float baseChannelLevel(DrainageGrid grid,int i){
        if(grid.terrain[i]<=0)return 0;
        // Priority-flood spill height routes water; an ordinary channel surface belongs
        // slightly below its valley floor. True basin/lake nodes retain one common plane.
        if(grid.lakeDepth(i)>15)return grid.surface[i];
        return Math.max(0,Math.min(grid.surface[i],grid.terrain[i]-6));
    }
    /** Shared displaced node positions keep tributaries connected while breaking flat-grid lines. */
    private double[] nodePosition(int i,int scale){
        int x=i%grid.width,z=i/grid.width;
        double px=(x*STEP-nativeWidth/2.0)*scale,pz=(z*STEP-nativeHeight/2.0)*scale;
        int p=grid.parent[i];
        if(p<0||grid.terrain[i]<=0)return new double[]{px,pz};
        double[] tangent=grid.tangent(i);
        double grade=Math.abs(grid.surface[i]-grid.surface[p])/Math.max(1.0,STEP);
        double freedom=Math.max(0,Math.min(1,1-grade/2.5));
        double offset=edgeNoise(x,z,0x6D3AL,3.7)*STEP*.34*scale*freedom;
        return new double[]{px-tangent[1]*offset,pz+tangent[0]*offset};
    }
    /** Smooth deterministic value noise; stable across chunks and query order. */
    public static double edgeNoise(double x,double z,long salt,double period){
        double gx=x/period,gz=z/period;long x0=(long)Math.floor(gx),z0=(long)Math.floor(gz);
        double fx=gx-x0,fz=gz-z0;fx=fx*fx*(3-2*fx);fz=fz*fz*(3-2*fz);
        double a=hashNoise(x0,z0,salt),b=hashNoise(x0+1,z0,salt),c=hashNoise(x0,z0+1,salt),d=hashNoise(x0+1,z0+1,salt);
        return (a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz;
    }
    /** Adds bounded ridge-scale relief inside existing highlands without changing range footprints. */
    public static float enhanceMountainRelief(float elevation,double nativeX,double nativeZ,long seed){
        if(WorldBlueprintManager.generationVersion()>=10)
            elevation=TerrainArchetypes.shape(elevation,nativeX,nativeZ,seed);
        if(elevation<=(WorldBlueprintManager.generationVersion()>=10?1100:1400))return elevation;
        double high=Math.max(0,Math.min(1,(elevation-1400)/1600.0));high=high*high*(3-2*high);
        double rx=nativeX*.82+nativeZ*.36,rz=-nativeX*.36+nativeZ*.82;
        double broad=edgeNoise(rx,rz,seed^0x5249444745435245L,110);
        double ridge=Math.pow(1-Math.abs(broad),3);
        double detail=edgeNoise(nativeX,nativeZ,seed^0x5045414B4445544CL,42);
        float refined=(float)(elevation+high*((ridge-.25)*180+detail*55));
        if(WorldBlueprintManager.generationVersion()>=9){
            var m=WorldBlueprintManager.activeStore().manifest();
            if(WorldBlueprintManager.generationVersion()>=13)return com.github.xandergos.terraindiffusionmc.blueprint.ExceptionalSummit.shapedHeight(
                    m.effectiveExceptionalSummits(),refined,nativeX,nativeZ,m.width()*256,m.height()*256,
                    com.github.xandergos.terraindiffusionmc.world.WorldScaleManager.getCurrentScale(),seed);
            refined+=WorldBlueprintManager.generationVersion()>=10
                    ?com.github.xandergos.terraindiffusionmc.blueprint.ExceptionalSummit.broadBoost(
                        m.effectiveExceptionalSummits(),refined,nativeX,nativeZ,m.width()*256,m.height()*256,
                        com.github.xandergos.terraindiffusionmc.world.WorldScaleManager.getCurrentScale(),seed)
                    :com.github.xandergos.terraindiffusionmc.blueprint.ExceptionalSummit.boost(
                        m.effectiveExceptionalSummits(),refined,nativeX,nativeZ,m.width()*256,m.height()*256,
                        com.github.xandergos.terraindiffusionmc.world.WorldScaleManager.getCurrentScale());
        }
        return refined;
    }
    private static double hashNoise(long x,long z,long salt){
        long h=x*0x9E3779B97F4A7C15L^z*0xC2B2AE3D27D4EB4FL^salt;
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;h^=h>>>31;
        return ((h>>>11)*0x1.0p-53)*2-1;
    }
}
