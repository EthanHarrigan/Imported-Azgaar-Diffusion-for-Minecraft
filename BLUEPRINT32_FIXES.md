# Blueprint.32: arid mountain clay gradient

The reported coordinate (49878, -1461, 24575) in New World (15), seed 330971835197786486, scale 5, is stony_peaks. Its saved surface contains sandstone and terracotta. Blueprint.31 changed the BADLANDS palette, but these mountains use SurfaceGeology -> DesertSurface.rock, which still made most of every 29-block band sandstone.

DesertSurface.rock now uses the irregular depositional beds shared with the badlands, with a separate mountain color gradient: dark grey/brown lower rock, warmer plain/red terracotta above, and orange upper exposures. Broad noise bends the color transitions across the range. Only some pale upper seams use sandstone; it is no longer the base material. No per-block random color mixing was added. Mountain finishing recognizes the complete terracotta palette and sandstone as natural surface materials. DH invokes this same mountain palette at the actual top block Y (previously one block too high).

Validation samples the saved blueprint at the reported coordinates and compares the SurfaceGeology result against the DH palette path. Additional samples check darker lower slopes and limited sandstone coverage, and emit a cross-section for visual inspection. The reference uses shaders, so exact screenshot lighting cannot be reproduced by block palette alone.

This changes new generation and newly computed previews. Already saved chunks and DH caches are not rewritten. No saves were edited.
