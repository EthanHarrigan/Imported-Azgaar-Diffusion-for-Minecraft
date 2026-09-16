package com.github.xandergos.terraindiffusionmc.world;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.*;
import org.slf4j.LoggerFactory;

/** Exact quart-resolution biome samples. Searches still run vanilla's algorithm and consume its RNG normally. */
public final class BiomeTileCache {
    private static final int MAGIC=0x54444231;
    private static volatile BiomeTileCache active;
    private final Path directory;
    private final int side;
    private final Object[] locks=new Object[64];
    private final Map<Long,short[]> memory=new LinkedHashMap<>(128,.75f,true);
    private final AtomicInteger working=new AtomicInteger(), computed=new AtomicInteger(), restored=new AtomicInteger();
    public BiomeTileCache(Path directory,int side){
        if(side<1||side>1024)throw new IllegalArgumentException("Invalid biome cache side");
        this.directory=directory;this.side=side;
        Arrays.setAll(locks,i->new Object());
    }
    public static void activate(BiomeTileCache cache){active=cache;}
    public static BiomeTileCache active(){return active;}
    public static String progress(){
        BiomeTileCache c=active;
        return c==null||c.working.get()==0?"":"Terrain/biome preparation: "+c.working.get()+" active, "+c.computed.get()+" saved, "+c.restored.get()+" restored";
    }
    public short[] get(int x,int z,Supplier<short[]> generate){
        long key=((long)x<<32)^(z&0xffffffffL);
        synchronized(memory){short[] hit=memory.get(key);if(hit!=null)return hit;}
        synchronized(locks[Math.floorMod(Long.hashCode(key),locks.length)]){
            synchronized(memory){short[] hit=memory.get(key);if(hit!=null)return hit;}
            Path file=directory.resolve(x+"_"+z+".bin.gz");
            short[] data=read(file);
            if(data!=null)restored.incrementAndGet();
            else{
                working.incrementAndGet();
                try{data=generate.get();if(data.length!=side*side)throw new IllegalArgumentException("Invalid generated biome tile");write(file,data);computed.incrementAndGet();}
                finally{working.decrementAndGet();}
            }
            synchronized(memory){memory.put(key,data);while(memory.size()>128)memory.remove(memory.keySet().iterator().next());}
            return data;
        }
    }
    private short[] read(Path file){
        if(!Files.exists(file))return null;
        try(InputStream raw=Files.newInputStream(file);
            DataInputStream in=new DataInputStream(new GZIPInputStream(raw))){
            if(in.readInt()!=MAGIC||in.readInt()!=side)throw new IOException("Invalid cache header");
            short[] data=new short[side*side];for(int i=0;i<data.length;i++)data[i]=in.readShort();
            if(in.read()!=-1)throw new IOException("Trailing cache data");return data;
        }catch(IOException e){LoggerFactory.getLogger(BiomeTileCache.class).warn("Recomputing unreadable biome cache {}: {}",file,e.toString());return null;}
    }
    private void write(Path file,short[] data){
        Path temp=null;
        try{
            Files.createDirectories(directory);temp=Files.createTempFile(directory,"biome-",".tmp");
            try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))){
                out.writeInt(MAGIC);out.writeInt(side);for(short value:data)out.writeShort(value);
            }
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){LoggerFactory.getLogger(BiomeTileCache.class).warn("Could not persist biome cache {}: {}",file,e.toString());}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
}
