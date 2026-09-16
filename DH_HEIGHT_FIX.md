# Blueprint.27: DH distant-preview height overflow

## Confirmed cause
The installed Distant Horizons 3.2.1-b-dev JAR exactly matches the build/test dependency (SHA256 5A4CBF252916028079EDD61508111AF496C929B5C27DD5D35937D4C0363E8CDD).

Its ServerLevelWrapper.getMaxHeight() calls Minecraft getHeight(), which returns the total vertical span. DhRoughSurfaceGenerator.generateSurface() then subtracts getMinHeight(), incorrectly treating that span as an absolute top coordinate. For this world, 3968 - (-2000) becomes 5968. A surface at relative Y=240 then produces an air column of height 5728, exactly matching latest.log. DH's packed height field cannot represent it, and distant generation jobs repeatedly fail.

## Correction
A small optional mixin changes only the world-height argument passed to populateApiDataPoints. It supplies Minecraft's actual HeightLimitView.getHeight(), as required by the bottom-relative API columns. Ground and water heights remain unchanged. The correction also handles vanilla negative-bottom worlds, zero-bottom worlds, and positive-bottom worlds. It is independent of the experimental Terrain Diffusion preview toggle.

No DH quality, render-distance, thread, or generation-mode settings are changed. No extra sampling, inference, or chunk generation is added. The experimental replacement remains opt-in. This build also contains the snow-cap and underground-structure corrections from .25 and .26.

## Validation
The regression loads the transformed installed DH class, executes its injected height hook with representative height-limit views, and passes corrected columns through DH's actual packed-data encoder. It checks the exact 5728-height overflow from the log and correct encoding for low and high terrain. The constructor is bypassed only in the test to avoid starting worker pools or a server.

Build: `.\gradlew.bat test build -PuseDml=true -PtestDhPreview --offline`.

The correction requires a game restart. It does not remove saved terrain or DH caches. New-world generation still takes time after the overflow is fixed. Visual recovery in a running game remains to be verified.

Final validation: 110 passed, one skipped; zero failures/errors. Remapped JAR and seven dimension resources verified. Installed as the only active Terrain Diffusion JAR while Minecraft was closed; .26 retained as a disabled backup.

SHA256: d716d74781109a81f32f4de6488abfb8495fd9cb5d185d9dd0e0a9cb121cd0aa
