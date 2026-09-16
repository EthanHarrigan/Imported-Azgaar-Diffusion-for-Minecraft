package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.mixin.UniformHeightProviderAccessor;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.YOffset;
import net.minecraft.world.gen.heightprovider.*;

/** Rebase vanilla underground anchors before jigsaw pieces and biome checks are created. */
public final class UndergroundStructureHeight {
    private UndergroundStructureHeight() {}
    public static HeightProvider adjust(HeightProvider provider, boolean terrainWorld,
            boolean projectedToSurface, Identifier pool, int seaLevel) {
        if(!terrainWorld || projectedToSurface || pool==null || !pool.getNamespace().equals("minecraft"))return provider;
        if(!pool.getPath().equals("trial_chambers/chamber/end") &&
                !pool.getPath().equals("ancient_city/city_center"))return provider;
        int delta=seaLevel-63;
        if(delta==0)return provider;
        if(provider instanceof ConstantHeightProvider constant){
            YOffset old=constant.getOffset(), mapped=rebase(old,delta);
            return mapped==old?provider:ConstantHeightProvider.create(mapped);
        }
        if(provider instanceof UniformHeightProvider){
            var bounds=(UniformHeightProviderAccessor)provider;
            YOffset lo=bounds.terrainDiffusion$minOffset(),hi=bounds.terrainDiffusion$maxOffset();
            YOffset mappedLo=rebase(lo,delta),mappedHi=rebase(hi,delta);
            return mappedLo==lo&&mappedHi==hi?provider:UniformHeightProvider.create(mappedLo,mappedHi);
        }
        return provider;
    }
    private static YOffset rebase(YOffset offset,int delta){
        // Relative anchors and already rebased custom datapack heights keep their coordinates.
        if(offset instanceof YOffset.Fixed fixed && fixed.y()>=-64 && fixed.y()<=319)
            return YOffset.fixed(fixed.y()+delta);
        return offset;
    }
}
