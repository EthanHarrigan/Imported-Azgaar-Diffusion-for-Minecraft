# Blueprint.26: underground structure height correction

## Cause
Packaged Minecraft 1.21.11 structure definitions specify trial chamber starts with a uniform absolute range of -40 to -20 and ancient city anchors at absolute -27. The Terrain Diffusion profile moved sea level to -1784, but those anchors still used vanilla coordinates. These structures could therefore start far above ordinary terrain.

## Fix
A scoped JigsawStructure hook rebases constant/uniform absolute anchors in the vanilla coordinate range by the difference between the generator sea level and vanilla sea level (63). Trial chamber starts become -1887 to -1867; ancient city anchors become -1874. The shift happens before pool generation, biome acceptance, piece bounds, terrain adaptation, and serialization, so all generated pieces use the correct coordinates from the start.

The hook applies only to Terrain Diffusion biome sources and the vanilla trial chamber/ancient city starting pools, with no surface-heightmap projection. Relative anchors and already rebased custom heights are preserved. Shared registry providers are not mutated. Other provider distributions are left unchanged.

Audit: normal mineshafts and strongholds already call StructurePiecesCollector.shiftInto with the generator's sea level and minimum Y. Mesa mineshafts use the generator's sea level and surface height. Villages and trail ruins project to terrain. The existing monument correction remains in place. No blanket structure translation is performed.

## Scope and validation
Includes Blueprint.25's snow cliff fix. Existing generated structures are not moved or deleted. New structures receive the correction; this is not a saved-world repair. In-game visual verification remains pending.

Regression tests read the actual packaged vanilla structure definitions and assert their corrected coordinates, unchanged shared providers, transformation of the actual Minecraft jigsaw class, and preservation of other dimensions, surface projection, unrelated pools, custom namespaces, relative anchors, and already lowered anchors.

Build command: `.\gradlew.bat test build -PuseDml=true -PtestDhPreview --offline`.

Validation: 109 tests passed, one skipped, zero failures/errors. Seven vertical-profile dimension resources, DirectML dependency, and both new mixin registrations verified in the remapped JAR.

Installed and SHA256-verified in the Fabric 1.21.11 Modrinth profile while Minecraft was closed. Blueprint.24 retained as a disabled backup; Blueprint.25 installer now recognizes the newer installed build.

SHA256: 6a80804e7038c5777e2b616cd98f81aaf3e1fcd78a0fbf440a0089ae68fa820a
