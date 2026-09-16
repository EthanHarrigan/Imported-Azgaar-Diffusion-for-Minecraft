# Blueprint .37 — water planes and September 15 map

Installed into the Modrinth Fabric 1.21.11 profile. Includes the previously staged .36 cache-version notice.

## Water

- Lake rasterization previously bilinearly averaged wet spill levels with dry mountain elevations. A shore next to a high dry node therefore gained a sloping water surface. Membership/depth still interpolate, but the water height now comes exclusively from a real wet spill plane. Different adjacent spill planes are not averaged together; the lower basin owns the overlap and supplies its own membership.
- Ordinary river nodes were lowered six metres while lake nodes retained their spill height. An upstream valley node could consequently sit below a downstream lake. Channel levels now receive a downstream-first monotonic pass, sharing the existing outlet traversal and path scratch buffer. Channels can stay level or fall downstream; true lake nodes retain their exact plane.
- The derived channel array adds four bytes per routing node (about 26.8 MiB for this map), with no extra per-column graph traversal. Saved routing is still readable and serializes identically.

## Desert and mountain contacts

- Replaced the 12-block stochastic terracotta coverage at desert/mountain contacts with one broad, smoothly varying contact threshold. The weak outer portion of the ecological feather remains ordinary mountain rock. Core clay palette/banding is unchanged.
- Detailed rock and DH fallback use the same contact predicate. The final biome classifier limits desert and all three badlands variants to authored warm-arid provinces, after climate classification and coverage reservations.
- Cold-arid source cells alone no longer authorize warm terracotta/desert surfaces. A separate warm mask preserves the original pre-drainage terrain mask and its expensive terrain checkpoints.
- Biome-search cache revision is now v5; routing cache revision stays v4. Changing the map JSON correctly creates a different blueprint/cache identity.

## Updated map

Source: `C:\Users\ethan\Downloads\Ceteviles Full 2026-09-15-23-05.json` (unaltered).

Ready import: `C:\Users\ethan\Downloads\Ceteviles Full 2026-09-15-23-05 Sand-Sea-ready.json`.

- Two southern Sand Sea selections contain 27 and 42 arid land cells, respectively. Water, green enclaves, all source heights, vertices, river data and nonselected cells are preserved. The existing two exceptional summit anchors are retained.
- Screenshot coordinates were registered against nine ice landmasses before selecting cells. Maximum landmark residual: 0.479 source pixels. Direct screenshot scaling would have misplaced the regions because the viewport was zoomed and panned.
- `scripts/prepare_september15_map.py` reproduces the derivative, validates unchanged data and writes a standalone source map in Downloads. Selection metadata records polygons and calibration.
- Import compiled at physical width 1800 km: native 59904 × 29952; at scale 5, 299520 × 149760 blocks.

## Validation and installation

- Full Gradle test/build passed: **145 tests, zero failures/errors/skips**, including the existing DH preview checks against New World (15)'s blueprint.
- Seven new regression tests cover dry mountain shores, adjacent lake planes, lake entry, rugged drainage graphs, cold-arid permission and coherent terracotta contacts.
- A separate audit classified **360,000** deliberately hot/dry samples across the new map with two seeds. All **355,984** samples outside authored warm deserts remained non-arid; Sand Sea interiors survived. **28,952** northeastern land samples had neither arid biome IDs nor terracotta permission. This is a synthetic terrain/classifier check, not an in-game screenshot validation.
- A subsequent auxiliary audit initially hit an external model-manifest HTTP 429 and then a missing test game-directory setting. It passed using the already verified local manifest and the normal offline test game directory; no model binaries were changed.
- JAR SHA-256: `6a6465aa62ae55f9587d3ade9566fd9970e9caadd5656071007118dc07da0c6f`.
- ZIP integrity/version, active JAR count, Modrinth inventory hashes and content-store linkage verified. Previous .35 JAR retained as `.jar.disabled`; prior launcher row backed up in `mod-backups/blueprint37-launcher-backup.json`.

## What existing worlds retain

No saved chunks, DH databases or save-local blueprints were rewritten. Already generated water/terrain remains as saved. The new map must be selected when creating a new world; reusing the same Minecraft seed alone does not update an old world's embedded map. Existing-map river checkpoints are reusable; the newly imported map has a new fingerprint and needs its own preparation.
