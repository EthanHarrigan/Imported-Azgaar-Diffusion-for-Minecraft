# Blueprint.29: desert rock fields, foothills and ponds

## Rock fields
Replaced one mandatory rock per 512-block cell and periodic major rocks with independent randomly positioned candidates, including empty cells and multiple sites. Cells index candidates only; footprints cross their boundaries. Broad warped density fields form uneven clusters and large clearings. Width varies continuously from 0.25 to 1 of the template, biased toward smaller rocks; heights, orientation, and four silhouette families vary independently. Overlapping heights use the maximum rather than accumulating into enormous spikes. Bounded neighboring-cell lookup and a site cache require no neural inference or chunk reads.

The screenshot-area rock-field sample covers X=52000..56096, Z=19000..23096 with the saved-world seed. Before terrain/water protection, rocks cover about 2.2% of this area. Other tested seeds give approximately 1.0% and 3.4%. The plotted layout has irregular clusters and broad interior gaps. Individual rock beds remain relative to their base height.

## Terracotta and sand ownership
Added a coherent badlands belt where desert terrain lies beside elevated mountain source terrain, using nearby height samples and a smooth threshold. Low desert interiors remain sand seas. Explicit biome reservations and water ownership still win. Cream sand is the dominant baseline; only the strongest red-sand provinces remain red, with one material per location rather than block-scale mixing.

Restored continuous arid rock strata by removing the per-block color lottery. The new badlands/rock palette uses broad orange terracotta beds, pale and neutral seams, narrow brown seams, and coherent stone exposures. This follows the .20 screenshot's layer structure, rather than trying to duplicate its shader lighting. Surface beds and relief appear in both the chunk and fast-preview paths.

## Desert ponds
Builds one immutable whole-basin suppression mask from prepared drainage. About 90% of eligible small, isolated desert basins are suppressed. Authored excavated lakes, basins with river nodes, non-desert basins, and basins of at least 64 drainage nodes are retained. This is a selection rule, not an exact count quota per biome. Rivers and the underlying routing graph are not changed. Both lake bed shaping and preview/chunk water read the same mask.

Read-only audit of New World (13), within 5000 blocks of approximately X=54021, Z=21052: 300 of 1840 lake-grid nodes suppressed. These are sampled area nodes, not individual pond counts. A separate 4096-square prepared preview found 3894 badlands columns and 1982 desert columns among 16384 samples, alongside the surrounding mountain and other biomes.

## Verification and scope
Full offline DirectML build with DH integration and the saved blueprint/routing data. Regression tests cover deterministic layouts, cell-border continuity, density clearings and clusters, water/coast protection, whole-basin decisions, protected water classes, relative rock strata, preview partition consistency and height encoding.

New generation only: existing chunk blocks and DH cache entries are not rewritten. Existing region boundaries may show old/new terrain transitions. The one-time basin scan adds bounded work when prepared hydrology is first used. No new model calls are added. In-game visual acceptance is still required.

{
  "sha256": "462cefe60649e81172ae98766fdb41f732b5cab8634f1f2fa9a38185a5e1ed6a",
  "testResults": {
    "tests": 115,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "savedWorldAudit": "Screenshot +/-5000 blocks: lake nodes=1840, suppressed=300; preview biome counts={5=1982, 15=810, 21=68, 22=173, 26=3894, 29=335, 31=12, 32=185, 33=77, 34=20, 35=8745, 108=66, 115=17}"
}

Installed as the only active Terrain Diffusion JAR in the Fabric 1.21.11 Modrinth profile. Blueprint.28 retained as a disabled backup.
