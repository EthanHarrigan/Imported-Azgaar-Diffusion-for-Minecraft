package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.*;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarinePlacementTest {
    @Test void livingCoralRequiresWaterAndDoesNotReplaceStructures(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        Map<BlockPos,BlockState> blocks=new HashMap<>();
        StructureWorldAccess world=(StructureWorldAccess)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{StructureWorldAccess.class},(proxy,method,args)->{
            return switch(method.getName()){
                case "getBlockState" -> blocks.getOrDefault((BlockPos)args[0],Blocks.AIR.getDefaultState());
                case "getFluidState" -> blocks.getOrDefault((BlockPos)args[0],Blocks.AIR.getDefaultState()).getFluidState();
                case "setBlockState" -> {blocks.put(((BlockPos)args[0]).toImmutable(),(BlockState)args[1]);yield true;}
                case "getBottomY" -> -64;
                case "getHeight" -> 1472;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        BlockPos floor=new BlockPos(0,40,0);
        blocks.put(floor,Blocks.SAND.getDefaultState());
        MarineDecorator.placeColumn(world,floor,0,2);
        assertFalse(blocks.containsKey(floor.up()),"No coral on dry land");
        for(int y=41;y<47;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)blocks.put(new BlockPos(x,y,z),Blocks.WATER.getDefaultState());
        MarineDecorator.placeColumn(world,floor,0,2);
        assertTrue(blocks.get(floor.up()).isOf(Blocks.TUBE_CORAL_BLOCK));
        assertTrue(blocks.get(floor.up(2)).isOf(Blocks.TUBE_CORAL_BLOCK));
        assertTrue(blocks.get(floor.up(3)).isOf(Blocks.TUBE_CORAL_FAN));
        assertFalse(blocks.get(floor.up(3)).getFluidState().isEmpty(),"Fan must remain waterlogged");
        blocks.put(floor.up(),Blocks.PRISMARINE.getDefaultState());
        MarineDecorator.placeColumn(world,floor,1,2);
        assertTrue(blocks.get(floor.up()).isOf(Blocks.PRISMARINE),"Do not replace monument blocks");
    }
}
