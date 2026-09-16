# Blueprint.35 â€” staged terrain generation and validation audit

## Implementation
- Two daemon terrain workers perform interpolation, coast/relief processing, drainage carving, biome classification, boundary fields and final landforms. Model calls and mutable tensor caches remain on one inference thread.
- At most two active jobs and sixteen queued jobs. Admission blocks without allocating terrain arrays or running work on chunk callers. Duplicate requests share one computation; interrupted callers do not cancel it.
- Chunk requests take priority over queued explorer operations. A waiting explorer operation gets a turn after at most three foreground dequeues. Fast DH preview bypasses this model queue and its settings are unchanged. No distance-based priority or DH thread-name guessing is used.
- World transitions stop admission, cancel queued requests, and drain active work before replacing world globals. Active native inference is not forcibly interrupted. A fresh provider binds to each save-local blueprint even for the same seed/fingerprint. Provider identity is part of both pending and completed cache keys.
- Failures release pending work, allowing retry. Successful preparation is still shared; fatal preparation errors still stop loading through PreparationGate.
- Removed a redundant intermediate HeightmapData construction that converted the same raw tile heights twice.
- Region logs now report queue and work milliseconds. They are diagnostic measurements, not an end-to-end speed benchmark.

## Assumptions checked one layer deeper

| Assumption | Validation / finding |
| --- | --- |
| Model output can safely leave the model thread | Inspected fresh allocations in WorldPipeline.computeElev/computeClimate, then mutated actual returned DirectML output to NaN and fetched again. Cached results remained intact. Ownership transfers to one CPU job; the arrays are not falsely described as immutable. |
| Parallel CPU processing preserves terrain | Replayed the actual modern CPU stage against deterministic native inputs and the saved blueprint at six locations, including reported problem coordinates and negative coordinates. Heights, biomes, water, cached block heights and full surface contexts match serial results. Subdividing a tile also matches. |
| Production executor integration works with native models | Explicit terrainStageSmoke uses local model weights and the real LocalTerrainProvider. Concurrent stock tiles match serial tiles; same-seed reload creates a distinct provider and rejects the retired provider. No Minecraft save is opened or modified. |
| Interrupting a waiting caller should cancel its tile | False for shared requests. Dedicated test proves another caller receives the original computation without duplication. |
| Same seed and fingerprint identify the same provider | Insufficient: the blueprint store is save-local. Replaced cross-load reuse with fresh provider binding and lifecycle draining. |
| Surface-condition reordering is equivalent | Decoded both old and new rules through Minecraft's codec and ran its actual surface-rule evaluator for snowy/plain biomes, depth values and several heights. Block results match; rejected depths do not call the biome supplier. Also inspected the actual predicate bytecode. |
| Reservation bounds remain exact at other scales/seeds | 122,436 original-versus-indexed comparisons, using scales 1, 3, 5 and 6; seeds 0, the user's seed and Long.MIN_VALUE; reservation fringes and random positive/negative coordinates. All match. |
| Cached height values fit and preserve conversion | All 65,536 short elevation values at every supported scale 1â€“6: 393,216 exact comparisons. |
| Dune checks at cell intersections prove edge continuity | Expanded to full cell edges, four seeds, four sand supplies, and cache eviction/recomputation. Geometry remains bounded, deterministic and continuous at tested edges. |
| Dune height quantization loses at most one block | Too strong: metre flooring precedes block flooring. Validated the bound of less than 1 + scale/30 blocks, and water protection at every supported scale. Terrain arithmetic did not need changing. |
| Fast DH and detailed terrain are identical | They share the landform/material functions, but DH uses approximate base elevation, biome and boundary samples. Pixel/block identity of the rough preview is not claimed. Existing integration tests verify the DH hooks and height encoding. |

## Validation
Full offline DirectML Gradle test/build: **135 tests, zero failures/errors/skips**, with installed DH integration and the saved New World (15) blueprint. Final combined test/build/native-smoke run passed in 1m 9s. Native smoke is an additional explicit run, not a mocked inference test. Existing ocean-fluid, stray-pillar, underground-structure, DH-lock, pond, rock, biome, palette, routing-persistence and snow checks remain in the suite.

## Practical limits
- No measured claim about actual nearby chunks/second. A fixed-route in-game A/B run is still needed; the native smoke and CPU replay are correctness checks.
- Two CPU jobs may use more temporary memory than one, but inferred arrays are not accumulated in an unbounded queue. Existing completed-tile cache limits remain unchanged.
- Initial whole-world hydrology preparation remains serialized and checkpointed. This change chiefly removes per-tile CPU work from the inference queue; it does not make first-time preparation parallel.
- World shutdown/switch can wait for the two active jobs, including a native model call, to finish safely. It will not drain all queued work.
- Blueprint.35 does not intentionally change Blueprint.34 terrain geometry or cache identities. Existing worlds need no regeneration for this scheduling update. Earlier palette/dune changes still require ungenerated terrain or deliberate regeneration to appear.
