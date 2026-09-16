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
        if(manifest.width()<1||manifest.height()<1||(long)manifest.width()*manifest.height()>100_000_000||manifest.tileSize()!=128)
            throw new IOException("Invalid blueprint dimensions");
        verifyIntegrity();
    }
    public Path directory(){return root;}
    private void verifyIntegrity()throws IOException{
        try{
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            if(manifest.effectiveBiomeClassifierVersion()>=3)
                try(var in=Files.newInputStream(root.resolve("ecology.bin.gz"))){byte[] b=new byte[65536];for(int n;(n=in.read(b))>0;)digest.update(b,0,n);}
            for(int y=0;y<(manifest.height()+127)/128;y++)for(int x=0;x<(manifest.width()+127)/128;x++)
                try(var in=new GZIPInputStream(Files.newInputStream(root.resolve("tiles").resolve(x+"_"+y+".tdbp.gz")))){
                    byte[] b=new byte[65536];for(int n;(n=in.read(b))>0;)digest.update(b,0,n);
                }
            if(!HexFormat.of().formatHex(digest.digest()).equals(manifest.dataSha256()))throw new IOException("Blueprint data failed SHA-256 verification");
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    public BlueprintManifest manifest(){return manifest;}
    public float value(int ch,int x,int y)throws IOException{
        if(ch<0||ch>=manifest.effectiveChannelCount()||x<0||x>=manifest.width()||y<0||y>=manifest.height())throw new IndexOutOfBoundsException();
        int tx=x/manifest.tileSize(),ty=y/manifest.tileSize();Tile t=load(tx,ty);return t.channels[ch][(y%manifest.tileSize())*t.w+x%manifest.tileSize()];
    }
    public float valueOrDefault(int ch,int x,int y,float fallback)throws IOException{
        if(ch<0||ch>=manifest.effectiveChannelCount())return fallback;
        return value(ch,x,y);
    }
    private Tile load(int tx,int ty)throws IOException{long key=((long)ty<<32)|(tx&0xffffffffL);Tile hit=cache.get(key);if(hit!=null)return hit;Path p=root.resolve("tiles").resolve(tx+"_"+ty+".tdbp.gz");try(DataInputStream d=new DataInputStream(new GZIPInputStream(Files.newInputStream(p)))){if(d.readInt()!=MAGIC||d.readInt()!=1)throw new IOException("Invalid blueprint tile: "+p);int w=d.readInt(),h=d.readInt(),c=d.readInt();if(w!=Math.min(128,manifest.width()-tx*128)||h!=Math.min(128,manifest.height()-ty*128)||c!=manifest.effectiveChannelCount())throw new IOException("Invalid blueprint tile dimensions: "+p);float[][]a=new float[c][w*h];for(int ch=0;ch<c;ch++)for(int i=0;i<w*h;i++)a[ch][i]=Float.intBitsToFloat(Integer.reverseBytes(d.readInt()));Tile t=new Tile(w,h,a);cache.put(key,t);return t;}}
}
