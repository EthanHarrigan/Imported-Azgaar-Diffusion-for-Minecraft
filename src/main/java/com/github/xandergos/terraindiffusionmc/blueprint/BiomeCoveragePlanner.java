package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import java.util.ArrayList;
import java.util.List;

/** Picks deterministic, ecologically closest candidate sites; final terrain may still veto a site. */
public final class BiomeCoveragePlanner {
    private BiomeCoveragePlanner() {}

    /** Re-site reservations using generated elevations once whole-map drainage preparation completes. */
    public static List<BiomeReservation> fromGeneratedTerrain(com.github.xandergos.terraindiffusionmc.hydrology.DrainageGrid grid,BlueprintTileStore store)throws java.io.IOException{
        var m=store.manifest();int w=m.width()*2,h=m.height()*2,n=w*h;
        float[] e=new float[n],a=new float[n],r=new float[n],coast=new float[n],slopes=new float[n];float[][] climate=new float[4][n];
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x,gx=(int)((x+.5)/w*(grid.width-1)),gz=(int)((z+.5)/h*(grid.height-1)),p=gz*grid.width+gx;
            e[i]=grid.terrain[p];a[i]=WorldBlueprintManager.detail().nearest(1,(x+.5)/w,(z+.5)/h);
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
                int xx=Math.max(0,Math.min(grid.width-1,gx+dx)),zz=Math.max(0,Math.min(grid.height-1,gz+dz)),q=zz*grid.width+xx;
                if(grid.river[q]||grid.lakeDepth(q)>15)r[i]=1;
            }
            coast[i]=store.value(9,x/2,z/2);
            int left=gz*grid.width+Math.max(0,gx-1),right=gz*grid.width+Math.min(grid.width-1,gx+1);
            int top=Math.max(0,gz-1)*grid.width+gx,bottom=Math.min(grid.height-1,gz+1)*grid.width+gx;
            slopes[i]=(float)Math.hypot(grid.terrain[right]-grid.terrain[left],grid.terrain[bottom]-grid.terrain[top])/960f;
            for(int ch=0;ch<4;ch++)climate[ch][i]=store.value(ch+1,x/2,z/2);
            float q=store.value(0,x/2,z/2),source=(float)Math.copySign(q*q,q);
            climate[0][i]-=.0065f*(Math.max(0,e[i])-Math.max(0,source));
        }
        return plan(e,climate,a,r,coast,w,h,m.effectiveContentMinX()*2,m.effectiveContentMaxX()*2+1,128,slopes);
    }

    static List<BiomeReservation> plan(float[] elevation,float[][] climate,float[] anchor,
                                       float[] river,float[] coast,int w,int h,int minX,int maxX) {
        return plan(elevation,climate,anchor,river,coast,w,h,minX,maxX,256,null);
    }

    private static List<BiomeReservation> plan(float[] elevation,float[][] climate,float[] anchor,
                                       float[] river,float[] coast,int w,int h,int minX,int maxX,int nativeBlocksPerCell,float[] slopes) {
        List<BiomeReservation> out=new ArrayList<>();
        List<int[]> used=new ArrayList<>();
        for(short id:BiomeIds.VANILLA_SURFACE){
            // Hydrology places these on actual water; a dry reservation is not a river.
            if(id==BiomeIds.RIVER||id==BiomeIds.FROZEN_RIVER)continue;
            double best=-Double.MAX_VALUE; int bx=(minX+maxX)/2,by=h/2;
            for(int y=1;y<h-1;y++)for(int x=Math.max(1,minX);x<=Math.min(w-2,maxX);x++){
                int i=y*w+x; boolean water=elevation[i]<0;
                if(slopes!=null&&anchor[i]!=1000+id&&!generatedSiteAllowed(id,elevation[i],climate[0][i]))continue;
                if(slopes!=null&&!water&&river[i]>.7f)continue; // Reserve dry habitat, not a lake/river bed.
                if(slopes!=null&&flatCandidate(id)&&slopes[i]>.24f)continue;
                if(BiomeIds.isOcean(id)!=water && !BiomeIds.isShore(id))continue;
                if(BiomeIds.isShore(id)&&water)continue;
                if((id==BiomeIds.RIVER||id==BiomeIds.FROZEN_RIVER)&&elevation[i]>120)continue;
                if(BiomeIds.isShore(id)&&elevation[i]>240)continue;
                if((id==BiomeIds.FROZEN_PEAKS||id==BiomeIds.JAGGED_PEAKS||id==BiomeIds.STONY_PEAKS)&&elevation[i]<500)continue;
                if(id==BiomeIds.MUSHROOM_FIELDS&&anchor[i]!=1000+id&&!(climate[2][i]>900&&(anchor[i]==6||anchor[i]==8||anchor[i]==12)))continue;
                if(id==BiomeIds.WARM_OCEAN&&climate[0][i]<14)continue;
                if((id==BiomeIds.DESERT||id==BiomeIds.JUNGLE||id==BiomeIds.BAMBOO_JUNGLE)&&climate[0][i]<12)continue;
                if((id==BiomeIds.ICE_SPIKES||id==BiomeIds.SNOWY_TAIGA||id==BiomeIds.SNOWY_PLAINS)&&climate[0][i]>8)continue;
                double s=score(id,elevation,climate,anchor,river,coast,w,h,x,y);
                // Reserve enough space for the actual patch radii. A fixed two-cell gap made
                // large fallback regions overwrite the centres of nearby small rare biomes.
                for(int[] p:used){
                    double d=Math.hypot(x-p[0],y-p[1]);
                    double separation=.8*(radius(id)+p[2])/(double)nativeBlocksPerCell;
                    if(d<separation)s-=10000*(1-d/separation);
                }
                if(s>best){best=s;bx=x;by=y;}
            }
            if(best==-Double.MAX_VALUE)continue; // Never silently invent an invalid centre-of-map fallback.
            used.add(new int[]{bx,by,radius(id)});
            out.add(new BiomeReservation(id,(bx+.5)/w,(by+.5)/h,radius(id)));
        }
        return List.copyOf(out);
    }

    private static double score(short id,float[] e,float[][] c,float[] a,float[] river,float[] coast,
                                int w,int h,int x,int y){
        int i=y*w+x; double z=e[i], alt=Math.max(0,z), depth=Math.max(0,-z);
        double temp=c[0][i],prec=c[2][i],anc=a[i],cd=Math.abs(coast[i]);
        double relief=Math.max(Math.abs(z-e[i-1]),Math.abs(z-e[i+1]));
        relief=Math.max(relief,Math.max(Math.abs(z-e[i-w]),Math.abs(z-e[i+w])));
        double flat=-relief/80.0, high=alt/300.0, cold=-temp, wet=prec/250.0, dry=-prec/250.0;
        // Explicit Minecraft biome labels from Azgaar must dominate the
        // ecological fallback score. Add this after the switch because every
        // switch arm assigns rather than increments the base score.
        double explicitAnchorBonus=Math.round(anc)==1000+id?1000:0;
        double s;
        switch(id){
            case BiomeIds.WARM_OCEAN -> s=temp*3-depth/2000;
            case BiomeIds.LUKEWARM_OCEAN -> s=-Math.abs(temp-20)*3-depth/2500;
            case BiomeIds.OCEAN -> s=-Math.abs(temp-11)*2-depth/3000;
            case BiomeIds.COLD_OCEAN -> s=-Math.abs(temp-1)*3-depth/2200;
            case BiomeIds.FROZEN_OCEAN -> s=cold*3-depth/2500;
            case BiomeIds.DEEP_LUKEWARM_OCEAN -> s=-Math.abs(temp-20)*2+depth/400+flat;
            case BiomeIds.DEEP_OCEAN -> s=-Math.abs(temp-11)*2+depth/400+flat;
            case BiomeIds.DEEP_COLD_OCEAN -> s=-Math.abs(temp-1)*2+depth/400+flat;
            case BiomeIds.DEEP_FROZEN_OCEAN -> s=cold*2+depth/400+flat;
            case BiomeIds.BEACH -> s=-cd/2+flat+temp-alt/20;
            case BiomeIds.SNOWY_BEACH -> s=-cd/2+flat+cold*2-alt/20;
            case BiomeIds.STONY_SHORE -> s=-cd/2+relief/60-Math.max(0,temp-23)-alt/50;
            case BiomeIds.RIVER -> s=river[i]*80-cd/4+wet-alt/250;
            case BiomeIds.FROZEN_RIVER -> s=river[i]*80-cd/4+cold*2-alt/250;
            case BiomeIds.DESERT -> s=(anc==1?50:0)+temp*2+dry+flat;
            case BiomeIds.BADLANDS,BiomeIds.ERODED_BADLANDS,BiomeIds.WOODED_BADLANDS -> s=(anc==1?35:0)+temp+dry+high+(id==BiomeIds.ERODED_BADLANDS?relief/30:flat);
            case BiomeIds.SAVANNA,BiomeIds.SAVANNA_PLATEAU,BiomeIds.WINDSWEPT_SAVANNA -> s=(anc==3?50:0)-Math.abs(temp-23)+dry/3+(id==BiomeIds.SAVANNA_PLATEAU?high+flat:id==BiomeIds.WINDSWEPT_SAVANNA?relief/40:0);
            case BiomeIds.JUNGLE,BiomeIds.SPARSE_JUNGLE,BiomeIds.BAMBOO_JUNGLE -> s=(anc==7||anc==5?45:0)+temp+wet+(id==BiomeIds.BAMBOO_JUNGLE?flat:0);
            case BiomeIds.SWAMP,BiomeIds.MANGROVE_SWAMP -> s=(anc==12?55:0)+wet+flat-alt/100+(id==BiomeIds.MANGROVE_SWAMP?temp:0);
            case BiomeIds.SNOWY_PLAINS,BiomeIds.ICE_SPIKES -> s=(anc==10||anc==11?45:0)+cold*2+flat-alt/60;
            case BiomeIds.TAIGA,BiomeIds.OLD_GROWTH_PINE_TAIGA,BiomeIds.OLD_GROWTH_SPRUCE_TAIGA -> s=(anc==9?50:0)-Math.abs(temp-5)+wet+(id==BiomeIds.OLD_GROWTH_PINE_TAIGA?dry/5:wet/4);
            case BiomeIds.SNOWY_TAIGA -> s=(anc==9||anc==10?40:0)+cold+wet;
            case BiomeIds.FROZEN_PEAKS,BiomeIds.JAGGED_PEAKS,BiomeIds.SNOWY_SLOPES -> s=high+cold*2+relief/(id==BiomeIds.SNOWY_SLOPES?100:35);
            case BiomeIds.STONY_PEAKS -> s=high+relief/35-Math.abs(temp-8);
            case BiomeIds.WINDSWEPT_HILLS,BiomeIds.WINDSWEPT_GRAVELLY_HILLS,BiomeIds.WINDSWEPT_FOREST -> s=high+relief/35+(id==BiomeIds.WINDSWEPT_FOREST?wet:dry/4);
            case BiomeIds.MEADOW,BiomeIds.CHERRY_GROVE,BiomeIds.GROVE -> s=high+flat-Math.abs(temp-10)+wet;
            case BiomeIds.MUSHROOM_FIELDS -> s=wet*3+flat-alt/200+(anc==8||anc==12?30:0);
            case BiomeIds.PLAINS,BiomeIds.SUNFLOWER_PLAINS -> s=(anc==4?45:0)-Math.abs(temp-15)+flat+wet/4;
            default -> s=(anc==6||anc==8?40:0)-Math.abs(temp-14)+wet+flat;
        }
        return s+explicitAnchorBonus;
    }

    private static int radius(short id){
        return switch(id){
            case BiomeIds.PLAINS,BiomeIds.DESERT,BiomeIds.SAVANNA,BiomeIds.TAIGA,
                 BiomeIds.SNOWY_PLAINS,BiomeIds.DARK_FOREST -> 700;
            case BiomeIds.DEEP_OCEAN,BiomeIds.DEEP_COLD_OCEAN,BiomeIds.DEEP_FROZEN_OCEAN,
                 BiomeIds.DEEP_LUKEWARM_OCEAN -> 650;
            case BiomeIds.WINDSWEPT_FOREST,BiomeIds.WINDSWEPT_GRAVELLY_HILLS,
                 BiomeIds.WINDSWEPT_SAVANNA -> 480;
            case BiomeIds.SUNFLOWER_PLAINS -> 120;
            case BiomeIds.PALE_GARDEN,BiomeIds.BAMBOO_JUNGLE,BiomeIds.ICE_SPIKES -> 220;
            case BiomeIds.BEACH,BiomeIds.SNOWY_BEACH,BiomeIds.STONY_SHORE,
                 BiomeIds.RIVER,BiomeIds.FROZEN_RIVER -> 100;
            default -> 320;
        };
    }
    private static boolean flatCandidate(short id){
        return id==BiomeIds.PLAINS||id==BiomeIds.DESERT||id==BiomeIds.SAVANNA||id==BiomeIds.TAIGA||id==BiomeIds.SNOWY_PLAINS||id==BiomeIds.SWAMP||id==BiomeIds.JUNGLE||id==BiomeIds.DARK_FOREST;
    }
    static boolean generatedSiteAllowed(short id,float elevation,float temp){
        // Coverage is a safety net, not permission to put green forest on a frozen summit.
        if(BiomeIds.isOcean(id)||BiomeIds.isShore(id))return true;
        return switch(id){
            case BiomeIds.FROZEN_PEAKS,BiomeIds.JAGGED_PEAKS -> elevation>1800&&temp<3;
            case BiomeIds.STONY_PEAKS -> elevation>1800&&temp>-4;
            case BiomeIds.SNOWY_SLOPES -> elevation>1000&&temp<5;
            case BiomeIds.WINDSWEPT_FOREST -> elevation<3500&&temp>0&&temp<24;
            case BiomeIds.WINDSWEPT_HILLS,BiomeIds.WINDSWEPT_GRAVELLY_HILLS -> elevation<3800&&temp>-4;
            case BiomeIds.WINDSWEPT_SAVANNA,BiomeIds.BADLANDS,BiomeIds.ERODED_BADLANDS,BiomeIds.WOODED_BADLANDS -> elevation<3500&&temp>12;
            case BiomeIds.TAIGA,BiomeIds.OLD_GROWTH_PINE_TAIGA,BiomeIds.OLD_GROWTH_SPRUCE_TAIGA -> elevation<2600&&temp>-4&&temp<16;
            case BiomeIds.SNOWY_TAIGA -> elevation<3000&&temp<5;
            case BiomeIds.GROVE -> elevation>400&&elevation<3000&&temp<6;
            case BiomeIds.FOREST,BiomeIds.FLOWER_FOREST,BiomeIds.BIRCH_FOREST,BiomeIds.OLD_GROWTH_BIRCH_FOREST,BiomeIds.DARK_FOREST,BiomeIds.PALE_GARDEN,BiomeIds.CHERRY_GROVE -> elevation<3000&&temp>2;
            default -> elevation<3200;
        };
    }
}
