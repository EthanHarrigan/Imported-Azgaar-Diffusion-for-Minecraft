# Blueprint.28: restore the prepared-terrain DH preview

The Terrain Diffusion DH preview is enabled by default again, matching Blueprint.21. It reads the existing prepared hydrology/terrain relief rather than invoking neural inference for the rough pass. Before prepared data exists, the same path can show approximate imported elevation. Normal detailed DH generation may subsequently replace the SURFACE-priority preview.

The integration remains limited to Terrain Diffusion worlds with an active blueprint. The explicit terrainDiffusion.disableDhPreview property remains available as an opt-out. No launcher property is needed to activate it. The Blueprint.27 height correction remains for the fallback DH path; the custom preview already supplies bottom-relative column endpoints using the world height of 3968.

All prior snow, structure, and startup fixes are retained. DH quality, thread count, FEATURES mode, and render radius remain unchanged. This accelerates the initial distant preview; full nearby chunks and subsequent detailed generation still have their existing costs. Existing DH cache entries are not deleted or rewritten.

Validation: test/build with the installed DH version, including prepared elevation sampling, actual mixin application, and actual DH height encoding across world profiles. In-game activation is logged as Fast DH preview active: prepared diffusion relief, no neural inference in rough pass. Visual confirmation requires relaunching the game.

Validated: 110 tests passed, one skipped. Packaged default activation, fallback height fix, seven dimension resources, and DirectML dependency verified. Installed .28 as the only active Terrain Diffusion JAR, retaining .27 as a disabled backup. SHA256: ea49699933eb6d25283893b6819899f1e9883364b9c8d7d5773edb6f4931e410
