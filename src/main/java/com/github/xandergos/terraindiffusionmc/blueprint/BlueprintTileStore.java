package com.github.xandergos.terraindiffusionmc.blueprint;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Lazily loads compiled blueprint tiles and retains only a small LRU working set. */
public final class BlueprintTileStore {
    private static final int MAGIC=0x54444250;
    private final Path root;
    private final BlueprintManifest manifest;
    private final Map<Long,Tile> cache=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,Tile> e){return size()>32;}});
    private record Tile(int w,int h,float[][] channels){}
    public BlueprintTileStore(Path root)throws IOException{
        this.root=root;
        try(Reader r=Files.newBufferedReader(root.resolve("manifest.json"))){manifest=new Gson().fromJson(r,BlueprintManifest.class);}
        if(manifest==null||manifest.version()!=BlueprintManifest.FORMAT_VERSION)throw new IOException("Unsupported blueprint manifest version");
    }
    public BlueprintManifest manifest(){return manifest;}
    public float value(int ch,int x,int y)throws IOException{
        if(ch<0||ch>=5||x<0||x>=manifest.width()||y<0||y>=manifest.height())throw new IndexOutOfBoundsException();
        int tx=x/manifest.tileSize(),ty=y/manifest.tileSize();Tile t=load(tx,ty);return t.channels[ch][(y%manifest.tileSize())*t.w+x%manifest.tileSize()];
    }
    private Tile load(int tx,int ty)throws IOException{long key=((long)ty<<32)|(tx&0xffffffffL);Tile hit=cache.get(key);if(hit!=null)return hit;Path p=root.resolve("tiles").resolve(tx+"_"+ty+".tdbp.gz");try(DataInputStream d=new DataInputStream(new GZIPInputStream(Files.newInputStream(p)))){if(d.readInt()!=MAGIC||d.readInt()!=1)throw new IOException("Invalid blueprint tile: "+p);int w=d.readInt(),h=d.readInt(),c=d.readInt();if(c!=5)throw new IOException("Unsupported blueprint channel count");float[][]a=new float[5][w*h];for(int ch=0;ch<5;ch++)for(int i=0;i<w*h;i++)a[ch][i]=Float.intBitsToFloat(Integer.reverseBytes(d.readInt()));Tile t=new Tile(w,h,a);cache.put(key,t);return t;}}
}
