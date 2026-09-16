package com.github.xandergos.terraindiffusionmc.hydrology;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.*;
import java.util.HexFormat;
import java.security.MessageDigest;

/** Small, immutable cross-save cache for already validated river terrain checkpoints. */
final class SharedHydrologyCache {
    private static final String ALGORITHM = "river-terrain-v1";
    private final Path root;
    private final Path entry;
    private final boolean usable;

    private SharedHydrologyCache(String key) {
        Path game = FabricLoader.getInstance().getGameDir();
        root = game.resolve("terrain-diffusion-cache").resolve("hydrology");
        entry = root.resolve(key);
        boolean ok;
        try { Files.createDirectories(entry); ok = true; }
        catch (IOException | RuntimeException e) { ok = false; }
        usable = ok;
    }

    static SharedHydrologyCache open(String fingerprint, long seed, int scale, int nw, int nh, int version) {
        return new SharedHydrologyCache(cacheKey(fingerprint,seed,scale,nw,nh,version));
    }

    static String cacheKey(String fingerprint, long seed, int scale, int nw, int nh, int version) {
        String identity = ALGORITHM + "|" + fingerprint + "|" + Long.toUnsignedString(seed)
                + "|s=" + scale + "|" + nw + "x" + nh + "|v=" + version;
        return hash(identity);
    }

    /** One startup check only. Never opens old caches or changes their contents. */
    int olderVersion(Path worldRoot,String worldPrefix,String worldSuffix,String fingerprint,
                     long seed,int scale,int nw,int nh,int version) {
        return olderVersion(worldRoot,root,worldPrefix,worldSuffix,fingerprint,seed,scale,nw,nh,version);
    }

    static int olderVersion(Path worldRoot,Path sharedRoot,String worldPrefix,String worldSuffix,String fingerprint,
                            long seed,int scale,int nw,int nh,int version) {
        for(int old=version-1;old>=1;old--) {
            if(hasCheckpoint(worldRoot.resolve(worldPrefix+"-v"+old+worldSuffix))
                    ||hasCheckpoint(sharedRoot.resolve(cacheKey(fingerprint,seed,scale,nw,nh,old))))return old;
        }
        return 0;
    }

    private static boolean hasCheckpoint(Path directory) {
        // Stop on the first nonempty checkpoint. Empty directories and copy-in-progress
        // files are not evidence that this world had cached river data.
        try(var files=Files.newDirectoryStream(directory,"*.bin.gz")) {
            for(Path file:files)if(Files.isRegularFile(file)&&Files.size(file)>0)return true;
        } catch(IOException|RuntimeException ignored) { /* optional notice must never block loading */ }
        return false;
    }

    boolean copyToWorld(Path worldFile, int expectedCount) {
        if (!usable || Files.exists(worldFile)) return false;
        Path source = entry.resolve(worldFile.getFileName().toString());
        try {
            if (!Files.exists(source) || !WorldHydrology.validCheckpoint(source, expectedCount)) return false;
            Files.createDirectories(worldFile.getParent());
            Path tmp = worldFile.resolveSibling(worldFile.getFileName() + ".shared.tmp");
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(tmp, worldFile, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, worldFile, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | RuntimeException e) { return false; }
    }

    void publish(Path worldFile, int expectedCount) {
        if (!usable || !Files.exists(worldFile)) return;
        try {
            if (!WorldHydrology.validCheckpoint(worldFile, expectedCount)) return;
            Path target = entry.resolve(worldFile.getFileName().toString());
            if (Files.exists(target) && WorldHydrology.validCheckpoint(target, expectedCount)) return;
            Path tmp = entry.resolve(target.getFileName() + ".tmp-" + Long.toUnsignedString(System.nanoTime()));
            Files.copy(worldFile, tmp, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException | RuntimeException ignored) { /* shared cache is an optimization */ }
    }

    void restoreRouting(Path target)throws IOException{
        if(!usable||Files.exists(target))return;
        Path source=entry.resolve("prepared-routing-v1.bin.gz");if(!Files.exists(source))return;
        // The caller validates complete identity, graph invariants, dimensions and gzip CRC on load.
        transfer(source,target);
    }
    void publishRouting(Path source)throws IOException{
        if(!usable)return;
        Path target=entry.resolve("prepared-routing-v1.bin.gz");
        if(!Files.exists(target))transfer(source,target);
    }
    private static void transfer(Path source,Path target)throws IOException{
        Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"routing-copy-",".tmp");
        try{
            Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
