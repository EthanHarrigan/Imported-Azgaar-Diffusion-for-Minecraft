package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.*;
import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.*;
import net.minecraft.world.chunk.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SandyPlacementTest {
    @Test void rareSnagFitsInChunkAndCannotReplaceAnObstruction(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        var lookup=BuiltinRegistries.createWrapperLookup();
        var biomes=new SimpleRegistry<Biome>(RegistryKeys.BIOME,com.mojang.serialization.Lifecycle.stable());
        Registry.register(biomes,BiomeKeys.PLAINS,lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS).value());biomes.freeze();
        var registries=new DynamicRegistryManager.ImmutableImpl(List.of(biomes));
        long seed=LocalTerrainProvider.getSeed();int cx=0;while(!SurfaceAccentRules.deadWood(seed,cx,0,false))cx++;
        for(boolean blocked:new boolean[]{false,true}){
            var chunk=new ProtoChunk(new ChunkPos(cx,0),UpgradeData.NO_UPGRADE_DATA,HeightLimitView.create(-64,1472),PalettesFactory.fromRegistryManager(registries),null);
            int sx=cx*16;short[][] e=new short[16][16],ids=new short[16][16];
            for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                e[z][x]=100;ids[z][x]=BiomeIds.DESERT;
                chunk.setBlockState(new BlockPos(sx+x,70,z),Blocks.SAND.getDefaultState());
                if(blocked)chunk.setBlockState(new BlockPos(sx+x,71,z),Blocks.PRISMARINE.getDefaultState());
            }
            List<BlockPos> writes=new ArrayList<>();
            StructureWorldAccess world=(StructureWorldAccess)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{StructureWorldAccess.class},(proxy,method,args)->{
                return switch(method.getName()){
                    case "getBlockState" -> chunk.getBlockState((BlockPos)args[0]);
                    case "setBlockState" -> {BlockPos p=((BlockPos)args[0]).toImmutable();writes.add(p);chunk.setBlockState(p,(BlockState)args[1]);yield true;}
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            SandyDecorator.decorate(world,chunk,null,new LocalTerrainProvider.HeightmapData(e,ids,16,16),sx,0);
            if(blocked)assertTrue(writes.isEmpty());
            else{
                assertTrue(writes.size()>=4&&writes.size()<=8);
                for(BlockPos p:writes){assertTrue(p.getX()>=sx&&p.getX()<sx+16&&p.getZ()>=0&&p.getZ()<16);assertTrue(p.getY()>=71&&p.getY()<=76);}
            }
        }
    }
}
