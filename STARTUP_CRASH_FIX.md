# Blueprint.24 startup repair

## Confirmed failure chain

The first failed creation of New World (13) used seed 330971835197786486 and scale 5, with blueprint fingerprint de2bf1ca4a20f3005b2e1c44d2f5eda4f37e952a67a465f165d082751ba2c415. At 18:50:28 on September 14, DirectML initialization failed with HRESULT 8007000E (insufficient memory). The session log records 129 river-preparation attempts, each stopping at 848/6962 checkpoints (12%). The GPU session claim was outside the existing CPU recovery handler, and preparation failures were not latched. Spawn selection swallowed inference exceptions and continued into a fallback spawn.

On relaunch, the profile guard rejected four zero-byte .mca placeholders because it checked filenames rather than saved chunk entries. No profile, scale or blueprint persistent-state files had reached disk.

## Changes

- Close every ONNX session-options object, including optimization, CPU, GPU and failed-provider paths; close CUDA provider options on failure as well as success. These were native-resource leaks. The logs prove memory exhaustion but do not isolate RAM versus VRAM or prove the leak was its only contributor.
- Use sequential DirectML execution and disable memory-pattern optimization. Extend the existing per-model CPU recovery to GPU session creation, not only inference. Preserve both causes if CPU recovery also fails.
- Latch one failed hydrology preparation per world-load attempt. Queued requests get the same failure, with no checkpoint recount or new preparation. A deliberate reload gets a new attempt.
- Propagate spawn inference failures; never claim successful spawn preparation using a fallback height after inference fails.
- Flush the profile before further initialization and flush scale/blueprint state before model preparation. Use a non-null opaque saved-data fixer category so primitive metadata also reads correctly without relying on Fabric's nullable-fixer handling.
- Distinguish empty region placeholders from saved chunk entries. Populated worlds still require a matching saved profile; truncated nonempty headers fail closed.

## Offline save recovery

Backed up the entire failed world to:
C:/Users/ethan/AppData/Roaming/ModrinthApp/profiles/Fabric 1.21.11/startup-recovery-backups/New World (13)-before-blueprint24.zip

Reconstructed exactly three missing files with Minecraft's own persistent-state codecs: vertical profile, world scale (5, as recorded during creation), and blueprint settings. Seed and manifest fingerprint were checked before repair. A fresh state manager read all three back successfully. Every one of the original 1728 files retains its original SHA-256, including level.dat, all checkpoints and the four empty region files. No chunks or rivers were regenerated.

## Validation

- Real DirectML smoke: created and evicted coarse, base and decoder sessions over three rounds (nine GPU session creations) using the user's actual local model files. All passed on this machine. This is session-lifecycle validation, not a Minecraft world join or neural terrain generation.
- Regression coverage: 129 fatal requests invoke preparation once; concurrent successful requests prepare once; GPU creation failure cleans up before CPU retry; GPU/CPU failures retain both causes; metadata is readable by a fresh manager before normal shutdown; empty placeholders are allowed and populated/truncated regions stay protected.
- Full DirectML build/test with DH integration enabled; final results and hash are appended after packaging.

No graphics settings, memory allocation, shader settings, seed, scale or terrain algorithms were changed. In-game world joining under the user's complete shader/resource-pack load remains to be confirmed.

## Final result

{
  "tests": {
    "tests": 106,
    "failures": 0,
    "errors": 0,
    "skipped": 1
  },
  "jar": "C:\\Users\\ethan\\AppData\\Roaming\\ModrinthApp\\profiles\\Fabric 1.21.11\\mods\\terrain-diffusion-mc-2.2.0-blueprint.24-windows+1.21.11.jar",
  "sha256": "0bb27b9fb408b1a35772ae4b6a11e173cf8d0ae7bc0b9f2a98537c08b7fcf498",
  "previousJar": "C:\\Users\\ethan\\AppData\\Roaming\\ModrinthApp\\profiles\\Fabric 1.21.11\\mods\\terrain-diffusion-mc-2.2.0-blueprint.23-windows+1.21.11.jar.pre-blueprint24-disabled",
  "profileVerified": true,
  "DirectMLRealSessionSwaps": 9,
  "worldRepair": "New World (13): three missing metadata files restored; all 1728 original files unchanged"
}
