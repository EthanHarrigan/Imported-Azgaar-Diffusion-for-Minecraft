# Chunk generation and dune audit — 15 September 2026

## Scope and evidence
Audit only: no generation code, settings, mods or saves changed during this investigation. Running game: Blueprint.31; Blueprint.33 is staged, not active. The inspected source includes the staged palette work; the performance paths discussed here are present in the running version.

Captured 45 seconds while stationary, then 120 seconds while the user was asked to fly through unexplored terrain. JFR files are in Downloads (`terrain-generation-audit.jfr`, `terrain-generation-moving-audit.jfr`). Parsed summaries are in `build/audit-generation/`. These are sampling observations, not controlled chunks/second benchmarks. DH was active throughout, and therefore shared CPU, memory and the terrain preparation queue. Native wait samples must not be interpreted as CPU use. We did not disable or retune DH.

Moving capture: 4,214 nearby Worker-Main samples, 24,793 DH world-generation samples, 2,301 terrain-preparation-thread samples. In the nearby workers, 1,841 samples (43.7%) had AbstractChunkHolder.getUncheckedOrNull at the top; 1,840 followed ChunkRegion.getChunk -> getBiomeForNoiseGen -> BiomeAccess.getBiome, mostly through SurfaceBuilder. On the shared preparation thread, BiomeClassifier.classify appeared in 1,430 samples (62.1%) and applyCoverageReservation in 409 (17.8%). Inclusive stack counts overlap and are not additive.

The moving recording contains 54 garbage-collection events with a summed reported pause duration of 6.43 seconds; the largest event reports 183 ms. Event accounting can overlap, so this is evidence of material GC activity, not a precise lost-wall-time percentage. In the log snapshot, 188 of the last 200 completed terrain tiles reported zero newly computed neural windows. That does not mean the tiles were free: interpolation, drainage, biome classification and context construction still ran.

## Ranked speed opportunities

### 1. Reject underground blocks before testing their biome — highest-confidence first change
`src/main/resources/data/terrain-diffusion-mc/worldgen/noise_settings/terrain_diffusion.json`, surface_rule sequence entry 2, tests snowy biome before its nested stone_depth condition. Unlike the two later surface layers, it asks for biome information before rejecting deep stone. This matches the strongest nearby-worker stack in the moving profile.

Proposed change: exchange the two nested pure conditions (stone depth first, biome second), leaving bedrock/deepslate and material outcomes unchanged. This is a small optimization with a concrete equivalence argument. Validate block-for-block surface output across snowy/non-snowy terrain, water and tall cliffs, then repeat the same route/profile. Do not interpret the 43.7% sample share as a promised 43.7% speedup.

### 2. Index reserved biome regions and reuse column ecology — strong measured target
`pipeline/BiomeClassifier.java:482` loops over all biome reservations for every sampled column. It computes eligibility and can request ecology before applying a spatial rejection. `LocalTerrainProvider.handleModernRaw` performs classification over the padded tile.

Proposed change: build immutable spatial candidate lists from conservative warped reservation bounds, precompute reservation centers/aspects, preserve original order/tie behavior, and reuse ecology per column. Move cheap spatial rejection before expensive tests. Verify complete biome-ID grids against the old implementation, particularly reservation fringes, shorelines and negative coordinates. Incorrect bounds could silently remove reserved biomes, so this is not just an arbitrary smaller radius.

### 3. Reduce tall-world block and surface work — high potential, higher implementation risk
Height is 3,968 blocks: 248 chunk sections and 1,015,808 possible block positions per chunk. That is 3.875 times the former 1,024-block envelope, or 10.33 times vanilla's 384-block envelope. These are volume ratios, NOT measured generation slowdowns. Palette operations, array initialization, packed block writes and noise filling show up in the moving capture.

First optimize surface predicates. Later investigate uniform-section filling / skipping known empty vertical ranges for this heightfield generator. A direct heightfield population path could avoid generic density work, but is a separate architectural proposal: it must preserve structures, blending, heightmaps, fluids, lighting, section counts and feature statuses. Do not reduce world height or coarsen terrain merely to gain speed; do not bypass terrain needed by structures.

### 4. Separate CPU preparation from serialized inference, with nearby-work priority
`pipeline/LocalTerrainProvider.java:243-274` puts the entire modern tile pipeline on one inference executor. It includes CPU classification, hydrology and per-column data even when inference is already cached. All callers await the same queue.

Proposed staged change: keep ONNX/model state serialized, expose an immutable inference result, and use a small bounded CPU stage afterward. Add explicit nearby-generation priority/fairness so background refinement cannot monopolize the queue; keep DH throughput policy unchanged initially. Audit mutable static state, shared arrays, world switching, cancellation and memory limits before parallelizing. Simply adding threads is unsafe and can worsen allocation failures.

### 5. Reduce allocation and repeated hot-path lookups
- A 256-square tile creates 65,536 ColumnWorldContext entries, plus associated field/landform records; 64 cache entries can retain 4,194,304 column contexts before headroom. Compact primitive arrays or lazy optional data could lower retained memory. Measure allocations and memory before changing cache size.
- A 64-block halo expands raw tile work from 256 squared to 384 squared: 2.25 times as many raw columns. Share immutable overlap data or cache intermediate results without shrinking the halo or creating boundary seams.
- Density FillContext instances repeatedly read/validate tile_size and perform cache accesses. Cache immutable config values and preconverted column heights; profile benefit before committing complexity.
- Multiple surface, water, microfeature and finishing passes refresh heightmaps. Consolidate only where no intervening consumer requires the newer map; snow/features depend on correct timing.
- Rock site caches globally clear after 4,096 entries; consider bounded eviction if later profiles show replanning spikes. Not a top measured bottleneck here.

No numeric speedup is promised. Benchmark a fixed seed/route with a fresh test-save copy, cold and warm tile caches separately, fixed DH workload, and report nearby chunk completion latency (median/p95), server tick delay, GC and memory. Existing generated chunks are not a generation benchmark.

## Why dunes look regular and wide
`world/DesertLandforms.java:38-44` is the active final desert landform sampler. Primary ridges repeat at roughly 155 blocks, secondary ridges at 47 blocks, with the same approximately 20-degree direction. Warp bends these repeating waves but does not create independent widths, crest endings, forks or crescent populations. The common ridge profile uses the same 76% windward / 24% lee split everywhere. Envelope primarily changes height, not crest spacing. Both sides use smoothstep, so the ridge crest is rounded rather than a distinctly sharper slip-face break.

`hydrology/DesertTerrain.java:60-86` already has an older sand-supply-aware mix with crescents and secondary orientations; for modern generation it rescales its primary spacing to 500 blocks. `DesertLandforms.complete` then computes/subtracts a legacy offset and adds the newer simpler field. Near protection gates, after carving and rounding, this should not be assumed to be a perfect inverse. Tuning only the old crescent function will not reliably fix the final visible dunes. There should be one clear owner of dune height, without an add-then-approximate-subtract path.

## Prior art and direct applicability

1. [Terra's official Minecraft desert terrain expression](https://terra.polydev.org/config/development/terrain-list.html#desert): independently varied height, cellular regional rotation, nested domain warping, and a cell-edge mask around a sine-based dune field. Useful compositional precedent for warped regional dune fields. The documented example is symmetric and has a cellular mask; copying it unchanged would not produce our desired slip faces, and care is needed to avoid obvious cell outlines. Adapt the concepts, not the numbers.
2. [Paris et al., Desertscape Simulation, author page](https://aparis69.github.io/public_html/projects/paris2019_Deserts.html): terrain-sensitive surface wind and sand transport create barchan, longitudinal and anchored forms. [Reference C++ implementation](https://github.com/aparis69/Desertscapes-Simulation) is MIT-licensed; I inspected the simulation/flow sources. Its repeated transport and neighborhood operations are appropriate for an offline simulation/bake, not a drop-in cheap chunk sample. The author explicitly notes that the public reimplementation is not the exact original scene-generating code and timings can differ.
3. [Ali Hafez's implementation](https://www.alihafez.com/projects/desertsim.html): reports transverse and barchan dunes from sand quantity and wind simulation in Three.js. Useful independent implementation evidence; no unverified claim that its engine/code can be dropped into Minecraft.

## Options and recommendation — proposal, not implemented

### A. Modify the existing wave sampler
Vary crest spacing and amplitude using smooth warped coordinates, shorten the nominal scale, vary lee length, interrupt some ridges with correlated masks, and feed the existing sand-supply field into the final sampler. Lowest implementation cost and preserves existing integration. Expected result: less regular, narrower dune trains. Limitation: still fundamentally continuous waves; convincing isolated crescents and merging crests remain difficult. Directly dividing a global coordinate by a spatially varying wavelength can create unbounded phase distortion; use bounded coordinate warps or regional local coordinates instead.

### B. Recommended: replace only the final dune sampler with a bounded hybrid field
Keep rocks, biome ownership, drainage, landform protections, completion interface and DH plumbing. Replace the wave-only dune component; consolidate legacy dune ownership in the same change.

- Broad deterministic wind/sand-supply districts, independent of chunk edges.
- Sand-rich districts: asymmetric, curved transverse ridges with uneven spacing and occasional branches/terminations.
- Sand-poor districts: sparse analytic barchan mounds, varied footprints and downwind horns, with open interdune ground.
- Independent bounded site placement/local coordinates; coherent wind variation rather than arbitrary per-dune rotation.
- Initial art targets (proposed, not measured): common crest-normal spacing about 40–110 blocks; common heights 4–16 blocks; occasional larger dunes kept as landmarks. Calibrate height against lee-run length so narrower dunes do not become walls. Preserve gentle windward slopes and a sharper lee-side break.
- Subordinate smaller dunes in selected districts, not a second everywhere-periodic pattern.
- Use identical deterministic samples for detailed chunks and DH. Bound candidate queries and profile CPU cost; avoid simulation during chunk generation.

Why a partial replacement: the current final sampler lacks independently sized dunes and terminating crests; layering more noise onto fixed waves will only partly hide that structure. This changes dune shape without replacing the world generator.

Expected visual result: shorter, less uniform dune trains, distinct crescents in sparse sand, variable crest heights and genuine open areas. These are design expectations, not rendered results from an implementation.

Consequences: seed-stable output within the new version but different terrain from old versions; old/new chunk and DH-cache mismatches until regenerated; possible extra bounded CPU cost and candidate-cache memory; more tests for tile seams and dune/rock/water interactions. Preserve all existing river, coast, slope and biome-boundary gates. Version/cache identities must reflect the new geometry. Hydrology should not be silently recomputed or dune material placed across protected water corridors.

### C. Full wind/sand simulation
Potentially the most physically emergent dunes, but requires district-wide iteration, halo management and stored outputs to avoid chunk-order dependence. More generation/startup time, memory/storage and cache invalidation complexity. If pursued, bake it offline and sample stored results at runtime. Not recommended while nearby chunk generation is the performance concern.

## Acceptance checks before a dune release
Compare top-down and oblique previews across several seeds/scales; quantify width/height distributions, directional autocorrelation and open-ground fractions; verify tile-split and generation-order invariance, negative coordinates, dry/wet boundary protections and exact DH/detail sampler parity. Benchmark the dune kernel and full tile preparation separately. Use real Minecraft slope/height sampling to verify slip-face shape after height quantization. Keep the current version available for comparison.

## Next implementation order
1. Surface-rule predicate order, then measured A/B.
2. Reservation indexing and ecology reuse, with exact-output checks.
3. Memory/context/cache improvements, then cautious queue separation.
4. Dune option B only after agreement on this proposal; compare against a smaller option-A prototype if useful.
