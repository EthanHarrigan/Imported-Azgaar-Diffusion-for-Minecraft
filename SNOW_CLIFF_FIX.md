# Blueprint.25: exposed mountain snow caps

## Diagnosis and fix
The mountain finisher used a central difference: the east-minus-west and north-minus-south heights. Opposite sides of a narrow ridge can have equal heights, reporting zero slope even when both sides drop sharply. Snow eligibility could therefore leave a snow layer or snow block on a stone face.

The finisher now checks one-sided height differences in eight directions at one and two blocks, using converted Minecraft heights at the current scale. Differences steeper than 1.25 blocks per horizontal block force exposed geology. Gentle one-block stairs remain eligible for snow. This uses at most 16 cached height samples per mountain column, with no extra terrain inference or neighboring tile requests. Samples outside the cached tile are skipped.

The post-feature pass refreshes its surface heightmap before locating snow, and only removes a snow layer when its supporting block is recognized as terrain. Trees and non-terrain feature blocks retain their snow. Existing geological materials and sheltered snow selection remain in use.

Distant Horizons quality and sampling settings are unchanged. The normal chunk-derived LOD path will receive the exposed rock surface from newly generated chunks. The opt-in experimental DH preview is unchanged.

## Scope
This changes generation of new chunks. Already-generated terrain and existing DH LOD caches are not rewritten. Weather can subsequently place snow; this patch addresses generation, not weather. In-game visual acceptance is still required on an affected mountain.

## Validation
`./gradlew.bat test build -PuseDml=true -PtestDhPreview --offline`

Regression coverage includes snow removal on a symmetric narrow ridge in actual Minecraft ProtoChunks, sheltered snow retention, preserving snow on trees and built blocks, cliff lips, diagonal ledges, available tile-edge samples, and all six world scales. See build results for final counts and packaging verification.

Validated: 107 passed, one skipped, zero failures/errors. All seven dimension resources and the bundled DirectML dependency verified. SHA256: 933927352d8a4897041b24be1cfc5017af3558b00cd2ba543aaed06de908d4c9
