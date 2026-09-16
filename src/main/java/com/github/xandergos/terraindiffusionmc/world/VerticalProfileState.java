package com.github.xandergos.terraindiffusionmc.world;

import com.mojang.serialization.Codec;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/** Rejects accidental reuse of an incompatible generation profile. */
public final class VerticalProfileState extends PersistentState {
    private final String identity;
    private VerticalProfileState(String identity) { this.identity=identity; }
    public static final PersistentStateType<VerticalProfileState> TYPE = new PersistentStateType<>(
        "terrain_diffusion_vertical_profile", () -> new VerticalProfileState(VerticalProfile.identity()),
        Codec.STRING.fieldOf("identity").xmap(VerticalProfileState::new, s -> s.identity).codec(), net.minecraft.datafixer.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    public static void initialize(ServerWorld world) {
        if (!(world.getChunkManager().getChunkGenerator().getBiomeSource() instanceof TerrainDiffusionBiomeSource)) return;
        if (world.getBottomY()!=VerticalProfile.BOTTOM_Y || world.getHeight()!=VerticalProfile.HEIGHT
            || world.getChunkManager().getChunkGenerator().getSeaLevel()!=VerticalProfile.SEA_LEVEL)
            throw new IllegalStateException("This build requires a NEW lowered Terrain Diffusion world; existing worlds cannot be migrated in place.");
        var previous=world.getPersistentStateManager().get(TYPE);
        if(previous==null) {
            var region=world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("region");
            try {
                if(hasSavedChunks(region))throw new IllegalStateException("Existing chunks have no saved vertical profile. Create a new world; this build does not migrate old saves.");
            } catch(java.io.IOException e) { throw new IllegalStateException("Cannot verify existing world profile",e); }
        }
        var state=world.getPersistentStateManager().getOrCreate(TYPE);
        if (!state.identity.equals(VerticalProfile.identity()))
            throw new IllegalStateException("Saved Terrain Diffusion vertical profile differs from this build.");
        state.markDirty();
        world.getPersistentStateManager().save(); // Durable before the first chunk or GPU request.
    }
    /** Empty region placeholders are not evidence of an older generated world. */
    static boolean hasSavedChunks(java.nio.file.Path region)throws java.io.IOException {
        if(!java.nio.file.Files.isDirectory(region))return false;
        try(var files=java.nio.file.Files.list(region)){
            for(var path:files.filter(p->p.getFileName().toString().endsWith(".mca")).toList()){
                long size=java.nio.file.Files.size(path);if(size==0)continue;
                if(size<8192)throw new java.io.IOException("Truncated region header: "+path);
                try(var input=new java.io.DataInputStream(java.nio.file.Files.newInputStream(path))){
                    for(int i=0;i<1024;i++)if(input.readInt()!=0)return true;
                }
            }
        }
        return false;
    }

}
