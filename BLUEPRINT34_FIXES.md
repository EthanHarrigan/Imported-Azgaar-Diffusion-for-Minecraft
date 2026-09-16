# Blueprint.34: nearby generation overhead and dune fields

## Implemented speed work
1. The snow surface rule now tests stone depth before biome membership. The same two conditions and block result remain; deep blocks avoid the expensive biome lookup.
2. Reserved-biome candidates are indexed in bounded 512-block spatial buckets. Conservative bounds retain the original 1.7-radius cutoff and original tie order. Centers/aspects are precomputed, and coast ecology is read once only when a remaining shore candidate needs it. Index identity tracks active store, reservation list, scale and classifier seed. It refreshes after preparation/world changes.
4. Immutable tile_size is parsed/validated once. Heightmap tiles hold preconverted short block heights for density evaluation. Unused per-column ecology objects/sampling were removed from ColumnWorldContext. Retained memory falls by avoiding an ecology record per column, partially offset by a two-byte height per column. No cache-size increase, tall-world quality reduction, or wholesale heightmap-refresh removal.

The inference/CPU queue separation requested as item 3 is deliberately not included. Inference remains serialized exactly as before.

## Dunes
DuneField supplies static, seed-stable wind-shaped forms from bounded neighboring cells. Cells index jittered candidates; they are not mandatory dune placements or crest boundaries. Dunes have unequal height/width, gentle windward slopes, a sharper lee face, curved crests and tapering downwind horns. Broad density variation creates open interdunes. Sand supply blends shorter sparse crescents into longer forms, and progressively introduces extra neighboring dunes in rich sand. Overlap uses maximum height instead of additive stacking. Regional wind varies smoothly, with no hard cell-boundary orientation changes. Rock geometry and wet/slope/biome protections are retained.

The older pre-drainage dune offset is disabled for modern worlds; final landform completion now adds the dune field once rather than subtracting an estimated older offset. Pre-modern behavior stays intact. Shared and world-local hydrology use cache revision 4, and biome-search cache identity is bumped. First load needs preparation for the new geometry; old routing/preview checkpoints cannot be reused as if identical. Existing chunk blocks and DH databases are not rewritten. New chunks differ; regenerate chunks/LOD data or use a new world for a clean comparison.

## Azgaar wind
The compiler reads the source JSON but does not retain a wind field in the compiled blueprint/manifest used by saved worlds. Adding it would require importer/schema/migration work. Per the requested easy-only scope, this release uses deterministic regional wind instead. No claim is made that its wind directions come from Azgaar.

## Validation and practical limits
- Full Gradle test/build with actual DH integration and New World (15) blueprint audit.
- Original-vs-indexed reservation comparison covers 10,203 cases including cutoff fringes and negative coordinates. All results agree. Isolated timings are recorded in test output; not a claim of end-to-end chunk-rate improvement.
- Cached density heights checked across all 65,536 short elevations.
- Dune bounds, open ground, rich/sparse sand differences, cell-edge continuity and reverse query order checked. Actual heightfield shaded at build/dune34-review/dunes.png for review; it is not an in-game screenshot.
- Earlier ocean, pillar, structure-lock, terrain and palette tests retained. Blueprint.33 warmer mountain palette is included.

Further work: live A/B profiling after installation is still needed to measure actual nearby-chunk speed. Queue redesign remains a separate task; whole-section filling and broader heightmap consolidation remain higher-risk future optimizations.
