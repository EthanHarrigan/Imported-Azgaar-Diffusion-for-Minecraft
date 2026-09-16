# Blueprint.23 biome vertical slice

## What changed

- `BiomeClassifier` remains the only final classifier. Removed its high-frequency variant/edge grain, stopped incidental badlands conversion inside ordinary authored deserts, and retained authored Sand Sea cores as desert before explicit coverage reservations and final water ownership.
- Immutable `ColumnWorldContext` adapts final IDs, heights, saved water, ecology, slope, boundary distance, and artistic landform decisions. A 64-block halo supplies partition-independent bounded boundary distances. Edge widths use local patch thickness, capped at 16 blocks per side; single-block and small patches retain pure centers. Compatible neighbors share a curved material dividing line; interiors never use a material lottery.
- Modern desert and forest ground use `OwnedSurfaceDecorator`. Legacy dry/ecotone repaint paths and unbudgeted desert accent rocks are bypassed. Sand and red sand are coherent provinces. Dark height-relative strata use existing vanilla materials.
- `DesertLandforms` runs after existing water carving, before density sampling. It replaces the estimated legacy dune contribution only in protected dry interiors. Artistic relief is in final Minecraft blocks at every scale. No river routing input was changed for this art pass.
- Deterministic regional sites target 8% unprotected footprint coverage; all footprints fit a periodic radius-128 support at 512-block spacing. That support mathematically bounds every translated 512x512 window below 19.64%, hence below the 25% ceiling. Four original families: broken disc/table, twin massif, fin/eruption, hooked chain. Major sites every 4x4 cells; ordinary sites are smaller in height.
- Forest districts share clearing and path masks. Original oak/birch/spruce/jungle blueprints use world-coordinate sites, whole-footprint biome/clearance checks, and chunk-contained writes. Saved water remains the pond authority. Vanilla woodland trees are suppressed in the managed forest set to avoid competing placement.
- Complete validated drainage graphs, lake cuts and preview terrain are now saved atomically, restored without priority flood, and shared across identical input identities. Existing terrain checkpoint format and routing ownership remain intact. Corrupt solved graphs fail closed. Equal-discharge outlet ties are explicitly deterministic.
- Final height conversion clamps to the existing vertical profile. Experimental DH rough preview uses the same artistic completion/material functions and padded sampling; its underlying authored/prepared terrain is still approximate. Normal DH full chunks use the normal generator.
- Biome search cache suffix advanced to v3; hydrology cache identity was not changed by this task.

## Frozen inputs and evidence

See `build/biome23-audit/inputs.json`, `reference-samples.txt`, `validation.json`, and the test XML reports. The immutable manifest, ecology and tile files were copied from New World (12) into `build/biome23-audit/blueprint`; the running world and its hydrology were not modified.

Seed: unsigned 13744257987125675480 / signed -4702486086583876136. Scale 5. Profile v3_-2000_-1784_1967. Source JSON SHA-256: 6f980d70440ff5510eb0b48f8e1c0776cce811bdf93e6a8cacc120b1ff1f1e1e.

Validation command: `./gradlew.bat test build -PuseDml=true -PtestDhPreview --offline`.

New tests cover solved-routing byte identity and water/bed equality after reload; bad identity/truncated files; all-scale monotone vertical bounds; small-patch pure cores; boundary partition invariance; reverse-order artistic sampling; four archetypes; regional and translated-window budgets; water/coast/biome protection; height-relative strata; and frozen-coordinate rough-preview height/biome/material overlap equality. Existing Minecraft chunk and DH mixin tests remain in the suite.

Pre-existing source changes were preserved. `build/biome-overhaul-before.zip` snapshots source and build metadata from before this task. Nothing was committed or reset.

## Acceptance limits — do not describe these as completed

This is a testable vertical slice, not full visual acceptance of the reference image. No fresh Minecraft world, ground/aerial capture, shader traversal, or full ONNX chunk-order replay was run because the user's game remained open. The two coordinate audits use authored rough preview, not full generated chunks.

- The verified 7.47–7.54% regional rock coverage is for the unprotected artistic field over sampled 2x2 km regions. Water/slope/boundary protection can reduce actual eligible-region coverage; a complete authored Sand Sea census is still needed.
- No elevated-density apex was added. The conservative layout remains below the ordinary density target everywhere. A full one-apex regional hierarchy and larger 500-block silhouettes remain implementation work.
- Boundary widths are locally size-aware but capped at 16 blocks per side. Truly broad region-scale edges and exact Euclidean/global patch inradii are not implemented; the current distance metric is an eight-neighbor chamfer approximation.
- Legacy dunes are subtracted using their analytic estimate after carving; interpolation/residual underlying diffusion relief remains. Mostly-flat visual composition must be assessed in full chunks.
- New stacked ponds were not introduced, and no hydrology is recomputed for forest detail. Existing saved ponds/water masks are respected.
- Tree blueprints are deliberately small, original and chunk-contained. Full forest canopy composition and native feature interactions still need in-game inspection.
- Experimental DH rough preview keeps the same artistic field but cannot guarantee exact parity with full neural terrain or sub-LOD water/boundary masks. It remains opt-in.
- Old hydrology cache directories are not migrated across mismatched profile identities. Existing chunks are unchanged; use a fresh test world or fresh chunks to assess the new generation.

## GPT-5.6 Luna, medium: bounded visual tuning only

Do not change biome ownership, river preparation/identity, saved graph serialization, halo logic, protection masks, support radius, spacing, area-budget solver, scale conversion, or vertical profile. Do not add an apex, ponds, structures, features, assets, or a second classifier in this tuning pass.

After user-provided matching aerial/ground captures, adjust only:

1. `DesertLandforms`: primary relief coefficient 18 within 12–22; secondary coefficient 4 within 2–5; broad swell coefficient 3 within 2–5. Preserve maximum footprint support and corridor field scale.
2. `OwnedSurfaceDecorator.desertMaterial`: swap existing dark terracotta/stone band assignments; keep 3-block bands, world-coordinate coherence and height-relative Y.
3. `ForestDispatcher`: site acceptance .86 within .65–.90, tree height base 6 within 5–7 and extra range 6 within 4–6. Keep canopy radius <=3 and every preflight check.

Run the validation command after tuning, inspect both fixed coordinates, rebuild the DirectML JAR, verify packaged profile resources and SHA-256, and stage with `.jar.next-disabled` while Minecraft runs. The implementation gaps above require a separate correctness task, not constant tuning.

## Final artifact

SHA-256: `12bc286f83a5afe1bc687745b66f44ff4bcd307a8d772959a836951057fc29b9`

Staged disabled: `C:\Users\ethan\AppData\Roaming\ModrinthApp\profiles\Fabric 1.21.11\mods\terrain-diffusion-mc-2.2.0-blueprint.23-windows+1.21.11.jar.next-disabled`

Tests: {'tests': 98, 'failures': 0, 'errors': 0, 'skipped': 1}. Packaged seven dimension types and noise settings verified.
