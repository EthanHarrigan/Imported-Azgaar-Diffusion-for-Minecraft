# Blueprint.31: lowered-world fluids and badlands

## Corrections
- Minecraft NoiseChunkGenerator selected lava below min(-54, sea level). With sea level -1784, ocean columns therefore became lava. Terrain Diffusion density settings now use a lava plane ten blocks above the dimension bottom (-1990), and water up to sea level. Vanilla settings retain their original fluids.
- SurfaceBuilder.placeBadlandsPillar used an absolute 64.0 baseline. In the lowered profile this could build columns hundreds of blocks above the intended land. Its baseline now follows sea level + 1 (-1783); standard worlds are unchanged.
- Saved New World (14), chunk 4048,558 has eroded_badlands, red sand at (64776,65,8934), terracotta immediately below and a stone column beneath. This matches the reported Y66 standing position and the pillar path.

## Palette
Broad clay packages have independently offset boundaries (24–104 blocks apart), gentle shared folds, optional thin pale/dark contact beds and occasional lenses that pinch out across the slope. Brown and plain terracotta dominate, with grey, red and orange beds. There is no repeating 37-block color sequence. The shared material function serves both detailed terrain and the fast DH preview.

## Validation
Actual transformed Minecraft fluid sampler tested throughout the lowered ocean interval, the bottom lava interval and unchanged vanilla water/lava levels. Actual SurfaceBuilder pillar generation exercised around the reported coordinates with vanilla as a positive reproduction control. Palette checks cover dominant darker colors, diverse band widths and lateral continuity. Generated palette cross-section reviewed at build/badlands-review/strata.png (schematic material colors; not an in-game screenshot).

## Existing terrain
These are generation changes. Already saved chunks and cached DH terrain are not rewritten. Existing lava oceans and erroneous pillars require regenerated chunks or a fresh world to disappear. No player blocks or saves are modified by this release.
