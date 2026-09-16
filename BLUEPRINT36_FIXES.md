# Blueprint.36 — river cache version notice

When river preparation finds an older matching cache but cannot restore current-version routing, the loading overlay displays a small banner at the top:

> Terrain updated: older river cache can't be reused. Preparing updated rivers.

Detection checks prior hydrology cache revisions in the current save and the shared cache, preserving seed, blueprint/profile, dimensions and scale identity. This also explains rebuilding in a fresh save using the same seed. Empty directories, incomplete copy files and unrelated shared cache identities do not trigger the notice. A successfully restored current routing cache returns before this check and shows no version-change banner.

Detection runs once on the preparation thread. Drawing reads a cached string and wrapped lines; wrapping happens only when the message or screen width changes. There are no per-frame filesystem checks, timers, animation or inference calls. Drawing text has a small normal rendering cost, not literally zero cost.

The notice clears on preparation completion/failure and preview reset. The log records the old and current cache revisions. This release leaves hydrology cache revision 4 unchanged and does not itself require another river rebuild.

Validation: client compilation and full test/build with DH integration; temporary-directory tests cover prior local/shared caches, unrelated seed/scale/dimensions, empty directories and incomplete copies. The user's existing shared v3 cache was also confirmed complete while v4 is still in preparation. No running-world caches were changed.
