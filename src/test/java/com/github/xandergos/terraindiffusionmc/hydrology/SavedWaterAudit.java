package com.github.xandergos.terraindiffusionmc.hydrology;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Read-only region-file water/ice transects. No Minecraft world is opened. */
public final class SavedWaterAudit {
    static NbtCompound chunk(Path root,int x,int z)throws IOException{
        Path file=root.resolve("region/r."+Math.floorDiv(x,32)+"."+Math.floorDiv(z,32)+".mca");
        if(!Files.exists(file))return null;
        try(var f=new RandomAccessFile(file.toFile(),"r")){
            f.seek(4L*(Math.floorMod(z,32)*32+Math.floorMod(x,32)));int location=f.readInt();
            if(location==0)return null;f.seek((long)(location>>>8)*4096);int len=f.readInt(),type=f.readUnsignedByte();
            if(len<1||len>16_777_216)throw new IOException("Invalid region payload");
            byte[] a=new byte[len-1];f.readFully(a);
            InputStream in=switch(type){case 1->new GZIPInputStream(new ByteArrayInputStream(a));case 2->new InflaterInputStream(new ByteArrayInputStream(a));case 3->new ByteArrayInputStream(a);default->throw new IOException("Unsupported region compression "+type);};
            try(var data=new DataInputStream(in)){return NbtIo.readCompound(data,NbtSizeTracker.of(32L*1024*1024));}
        }
    }
    static int[] waterHeights(NbtCompound c){return heights(c,true);}
    static int[] heights(NbtCompound c,boolean waterOnly){
        int[] tops=new int[256];Arrays.fill(tops,-9999);if(c==null)return tops;
        for(var item:c.getListOrEmpty("sections")){
            if(!(item instanceof NbtCompound section))continue;
            int sy=section.getByte("Y",(byte)0)*16;
            if(sy<600)continue; // This audit targets the mountain lake, not cave water.
            var states=section.getCompoundOrEmpty("block_states");var palette=states.getListOrEmpty("palette");
            if(palette.isEmpty())continue;boolean[] wet=new boolean[palette.size()];boolean any=false;
            for(int i=0;i<wet.length;i++)if(palette.get(i) instanceof NbtCompound p){String name=p.getString("Name","");wet[i]=waterOnly?(name.equals("minecraft:water")||name.equals("minecraft:ice")||name.equals("minecraft:frosted_ice")):!name.endsWith("air")&&!name.equals("minecraft:water");any|=wet[i];}
            if(!any)continue;
            long[] packed=states.getLongArray("data").orElse(new long[0]);int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.size()-1)),per=64/bits;long mask=(1L<<bits)-1;
            for(int i=0;i<4096;i++){
                int id=palette.size()==1?0:(int)((packed[i/per]>>>((i%per)*bits))&mask);
                if(wet[id])tops[i&255]=Math.max(tops[i&255],sy+(i>>>8));
            }
        }
        return tops;
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Map<Long,int[]> cache=new HashMap<>();
        for(int x=12332;x<=12340;x++){
            var c=chunk(root,Math.floorDiv(x,16),Math.floorDiv(2577,16));int i=Math.floorMod(2577,16)*16+Math.floorMod(x,16);
            System.out.println("Edge X="+x+", Z=2577: solid/ice top="+heights(c,false)[i]+", water/ice top="+waterHeights(c)[i]);
        }
        for(int z:new int[]{2520,2544,2568,2577,2592,2616,2640}){
            StringBuilder s=new StringBuilder("Z="+z+": ");int last=-99999;
            for(int x=12240;x<=12600;x++){
                int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);long key=((long)cx<<32)^(cz&0xffffffffL);
                int[] heights=cache.get(key);if(heights==null){heights=waterHeights(chunk(root,cx,cz));cache.put(key,heights);}
                int y=heights[Math.floorMod(z,16)*16+Math.floorMod(x,16)];
                if(y!=last){s.append("X=").append(x).append(" -> ").append(y).append("; ");last=y;}
            }
            System.out.println(s);
        }
        System.out.println("Read "+cache.size()+" chunks; -9999 means no water/ice or chunk not present. No writes.");
        for(int x:new int[]{12348,12360,12372,12384,12396}){
            StringBuilder s=new StringBuilder("X="+x+": ");int last=-99999;
            for(int z=2500;z<=2640;z++){
                int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);long key=((long)cx<<32)^(cz&0xffffffffL);
                int[] heights=cache.get(key);if(heights==null){heights=waterHeights(chunk(root,cx,cz));cache.put(key,heights);}
                int y=heights[Math.floorMod(z,16)*16+Math.floorMod(x,16)];
                if(y!=last){s.append("Z=").append(z).append(" -> ").append(y).append("; ");last=y;}
            }
            System.out.println(s);
        }
    }
}
