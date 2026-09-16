package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Read-only saved checkpoint audit. No model inference, Minecraft launch or save writes. */
public final class SavedValleyAudit {
    static float[] read(Path p,int n)throws IOException{
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(p)))){
            if(in.readInt()!=0x54444831||in.readInt()!=n)throw new IOException("Bad checkpoint "+p);
            float[] a=new float[n];for(int i=0;i<n;i++)a[i]=in.readFloat();return a;
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),out=Path.of(args[1]);
        if(out.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))throw new IOException("Audit output must be outside save");
        var store=new BlueprintTileStore(root);var detail=BlueprintDetailMap.read(root.resolve("ecology.bin.gz"));
        for(var pair:Map.of("store",(Object)store,"detail",detail).entrySet()){
            var f=WorldBlueprintManager.class.getDeclaredField(pair.getKey());f.setAccessible(true);f.set(null,pair.getValue());
        }
        Path cache;try(var dirs=Files.list(root.resolve("hydrology"))){cache=dirs.filter(Files::isDirectory).findFirst().orElseThrow();}
        int nw=store.manifest().width()*256,nh=store.manifest().height()*256,w=nw/16+1,h=nh/16+1;
        float[] sourceHeight=new float[detail.width*detail.height];
        for(int z=0;z<detail.height;z++)for(int x=0;x<detail.width;x++)sourceHeight[z*detail.width+x]=detail.nearest(0,(x+.5)/detail.width,(z+.5)/detail.height);
        var summits=ExceptionalSummit.plan(store.manifest().sourceSha256(),sourceHeight,detail.width,detail.height,nw);
        float maxBoost=0,maxNext=0;
        float[] peakBoost=new float[summits.size()],peakHeight=new float[summits.size()];
        float[] soft=new float[w*h],e=new float[w*h],next=new float[w*h],rain=new float[w*h],hint=new float[w*h];boolean[] lakes=new boolean[w*h];
        for(int gz=0;gz<h;gz+=32)for(int gx=0;gx<w;gx+=32){
            int tw=Math.min(32,w-gx),th=Math.min(32,h-gz);float[] a=read(cache.resolve(gx+"_"+gz+".bin.gz"),tw*th);
            for(int z=0;z<th;z++)for(int x=0;x<tw;x++){
                int xx=gx+x,zz=gz+z,i=zz*w+xx;double u=xx*16.0/nw,v=zz*16.0/nh;
                soft[i]=a[z*tw+x];e[i]=WorldHydrology.refineCoast(soft[i],xx*16-nw/2,zz*16-nh/2,nw,nh);
                rain[i]=store.value(3,Math.min(xx/16,store.manifest().width()-1),Math.min(zz/16,store.manifest().height()-1));
                hint[i]=detail.sample(2,u,v);lakes[i]=detail.nearest(3,u,v)>=0;
                float source=detail.sample(0,u,v),raw=soft[i];
                // Invert the old piecewise-linear soft correction to recover enhanced
                // model samples, without any new inference or modification to the cache.
                if(store.manifest().effectiveBiomeClassifierVersion()<7&&!lakes[i]&&xx>0&&zz>0&&xx<w-1&&zz<h-1){
                    float target=source>=0?Math.min(65,source*.55f):Math.max(-100,source*.1f);
                    float strength=(float)Math.exp(-Math.abs(source)/350.0)*.8f;
                    if(source>=0&&raw<target||source<0&&raw>target)raw=(raw-strength*target)/(1-strength);
                }
                double nx=xx*16-nw/2.0,nz=zz*16-nh/2.0,cliff=hint[i]>.1?-1:WorldHydrology.edgeNoise(nx,nz,0x434F415354434CL,700);
                float boost=ExceptionalSummit.boost(summits,raw,nx,nz,nw,nh,3);maxBoost=Math.max(maxBoost,boost);raw+=boost;
                float coastProfile=CoastalProfile.height(raw,source,detail.signedCoastDistance(u,v)*nw/detail.width,cliff,WorldHydrology.edgeNoise(nx,nz,0x42555454524553L,65));
                next[i]=LakeEdgeProfile.height(raw,coastProfile,detail.signedLakeDistance(u,v)*nw/detail.width);
                maxNext=Math.max(maxNext,next[i]);
                for(int k=0;k<summits.size();k++)if(summits.get(k).weight(nx,nz,nw,nh)>0){peakBoost[k]=Math.max(peakBoost[k],boost);peakHeight[k]=Math.max(peakHeight[k],next[i]);}
            }
        }
        float[] cuts=LakeBasinPlanner.cuts(e,lakes,w,h),bed=e.clone();for(int i=0;i<bed.length;i++)bed[i]-=cuts[i];
        DrainageGrid grid=new DrainageGrid(w,h,16,bed,rain,hint);grid.validate();
        var hydro=new WorldHydrology(grid,nw,nh);var f=WorldHydrology.class.getDeclaredField("lakeCuts");f.setAccessible(true);f.set(hydro,cuts);
        float[] nextCuts=LakeBasinPlanner.cuts(next,lakes,w,h),nextBed=next.clone();for(int i=0;i<next.length;i++)nextBed[i]-=nextCuts[i];
        var nextGrid=new DrainageGrid(w,h,16,nextBed,rain,hint);nextGrid.validate();
        var nextHydro=new WorldHydrology(nextGrid,nw,nh);f.set(nextHydro,nextCuts);
        int bx=Integer.parseInt(args[2]),bz=Integer.parseInt(args[3]),scale=3;
        int size=512,x0=bx/scale-size/2,z0=bz/scale-size/2;
        float[] field=new float[size*size],proposed=new float[size*size];
        for(int z=0;z<size;z++)for(int x=0;x<size;x++)field[z*size+x]=WorldHydrology.interpolate(e,w,h,(x0+x+nw/2.0)/16,(z0+z+nh/2.0)/16);
        var result=hydro.carve(field,z0,x0,size,size,1);
        for(int z=0;z<size;z++)for(int x=0;x<size;x++)proposed[z*size+x]=WorldHydrology.interpolate(next,w,h,(x0+x+nw/2.0)/16,(z0+z+nh/2.0)/16);
        var nextResult=nextHydro.carve(proposed,z0,x0,size,size,1);
        var picture=new java.awt.image.BufferedImage(size*4,size+28,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=picture.createGraphics();graphics.setColor(java.awt.Color.WHITE);
        String[] labels={"Authored land/water","Cached terrain before strict coast","Current coast + rivers","New coast + rebuilt rivers (48-block samples)"};
        for(int p=0;p<4;p++)graphics.drawString(labels[p],p*size+8,18);graphics.dispose();
        Files.createDirectories(out);StringBuilder csv=new StringBuilder("x,z,source,soft,coast,afterCarve,water,lakeCut,riverNode,sourceLake,coastDistanceNative,proposed,proposedCarved\n");
        float maxCoast=0,maxCarve=0,maxLake=0;int riverNodes=0;
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            int p=z*size+x;double gx=(x0+x+nw/2.0)/16,gz=(z0+z+nh/2.0)/16;int i=(int)Math.round(gz)*w+(int)Math.round(gx);
            float s=WorldHydrology.interpolate(soft,w,h,gx,gz),cut=field[p]-result.bed()[p];
            double u=(x0+x)/(double)nw+.5,v=(z0+z)/(double)nh+.5;
            float source=detail.sample(0,u,v);double cliff=WorldHydrology.edgeNoise(x0+x,z0+z,0x434F415354434CL,700);
            if(detail.sample(2,u,v)>.1)cliff=-1;
            float[] panels={source,s,result.bed()[p],nextResult.bed()[p]};
            for(int panel=0;panel<4;panel++){
                float value=panels[panel];int color=value<=0?0x163c65:java.awt.Color.HSBtoRGB(.31f-Math.min(1,value/1800)*.22f,.65f,.45f+Math.min(1,value/1800)*.5f);
                picture.setRGB(panel*size+x,z+28,color);
            }
            maxCoast=Math.max(maxCoast,s-field[p]);maxCarve=Math.max(maxCarve,cut);maxLake=Math.max(maxLake,cuts[i]);
            if(x==size/2||z==size/2){
                csv.append((x0+x)*scale).append(',').append((z0+z)*scale).append(',').append(source).append(',').append(s).append(',').append(field[p]).append(',').append(result.bed()[p]).append(',').append(result.water()[p]).append(',').append(cuts[i]).append(',').append(grid.river[i]).append(',').append(detail.nearest(3,u,v)).append(',').append(detail.signedCoastDistance(u,v)*nw/detail.width).append(',').append(proposed[p]).append(',').append(nextResult.bed()[p]).append('\n');
            }
        }
        for(int z=(z0+nh/2)/16;z<(z0+size+nh/2)/16;z++)for(int x=(x0+nw/2)/16;x<(x0+size+nw/2)/16;x++)if(grid.river[z*w+x])riverNodes++;
        String summary="Area: 1536 x 1536 blocks around "+bx+", "+bz+"; scale 3\nMax additional coast lowering metres="+maxCoast+"\nMax river/lake carving metres="+maxCarve+"\nMax authored lake cut metres="+maxLake+"\nRiver graph nodes in area="+riverNodes+"\nInputs are cached 48-block terrain samples, not exact per-block pre-carve output.\n";
        javax.imageio.ImageIO.write(picture,"png",out.resolve("coast-stages.png").toFile());
        summary+="Saved-source summit anchors="+summits+"\nMaximum sampled boost at scale 3="+maxBoost/10+" blocks; proposed highest sampled surface Y="+(com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL+maxNext/10)+" (dimension top "+com.github.xandergos.terraindiffusionmc.world.VerticalProfile.TOP_Y+")\n";
        for(int k=0;k<summits.size();k++)summary+="Summit "+(k+1)+": sampled maximum boost="+peakBoost[k]/10+" blocks; sampled highest Y="+(com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL+peakHeight[k]/10)+"\n";
        WorldHydrology.publishPreview(nextHydro);
        var reefs=com.github.xandergos.terraindiffusionmc.world.ReefRegions.plan(store,3480947619940662356L);
        summary+="Potential reef provinces using prepared terrain="+reefs+"\n";
        summary+="New coast full-world drainage reconstructed from recovered cached samples: validated acyclic, downstream-connected; river segments="+nextGrid.riverCount()+"\n";
        long start=System.nanoTime();int wetSites=0;
        for(int n=0;n<200;n++){
            double nx=bx/3.0+(n%20-10)*16,nz=bz/3.0+(n/20-5)*16;
            float ground=nextHydro.previewElevation(nx,nz);
            boolean a=nextHydro.freshwaterNear(nx,nz,ground),b=nextHydro.freshwaterNear(nx,nz,ground);
            if(a!=b)throw new AssertionError("Freshwater query changed");if(a)wetSites++;
        }
        summary+="400 deterministic freshwater decoration queries: "+((System.nanoTime()-start)/1e6)+" ms; wet candidates="+wetSites+"\n";
        int riverChecks=0,riverHits=0;
        for(int i=0;i<nextGrid.river.length&&riverChecks<100;i++)if(nextGrid.river[i]&&nextGrid.terrain[i]>80&&nextGrid.lakeDepth(i)<10){
            riverChecks++;double nx=(i%w)*16-nw/2.0,nz=(i/w)*16-nh/2.0;
            if(nextHydro.freshwaterNear(nx,nz,nextGrid.terrain[i]))riverHits++;
        }
        if(riverChecks>0&&riverHits==0)throw new AssertionError("Freshwater decorator cannot find known river nodes");
        summary+="Known river-node freshwater checks="+riverHits+" / "+riverChecks+"; old total river segments="+grid.riverCount()+"\n";
        Files.writeString(out.resolve("cross-sections.csv"),csv);Files.writeString(out.resolve("summary.txt"),summary);System.out.println(summary);
    }
}
