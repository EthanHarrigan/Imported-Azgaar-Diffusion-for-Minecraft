"""Create a separate, reproducible Complementary r5.9 altitude compatibility copy."""
import sys, zipfile, re
from pathlib import Path

source, destination = map(Path, sys.argv[1:3])
if destination.exists():
    raise SystemExit("Destination already exists; preserve the existing pack.")
changes = []
with zipfile.ZipFile(source) as src, zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as dst:
    for entry in src.infolist():
        data = src.read(entry)
        if entry.filename.endswith((".glsl", ".properties")):
            text = data.decode("utf-8")
            original = text
            # Only atmospheric height references. Geometry, shadows, water and matrices retain true positions.
            if entry.filename.endswith("caveFactor.glsl"):
                text = text.replace("cameraPosition.y / oceanAltitude", "(cameraPosition.y + tdAltitudeOffset) / oceanAltitude")
            if entry.filename.endswith("common.glsl"):
                text = text.replace("const float oceanAltitude = 61.9;",
                    "const float oceanAltitude = 61.9;\n    float tdAltitudeOffset = (!isnan(cloudHeight) && cloudHeight < -1000.0) ? 1847.0 : 0.0;")
            if entry.filename.endswith("mainFog.glsl"):
                text = text.replace("GetAtmFogAltitudeFactor(playerPos.y + cameraPosition.y)",
                    "GetAtmFogAltitudeFactor(playerPos.y + cameraPosition.y + tdAltitudeOffset)")
                text = text.replace("GetAtmFogAltitudeFactor(cameraPosition.y + 0.25 * atmFogCRFTM)",
                    "GetAtmFogAltitudeFactor(cameraPosition.y + tdAltitudeOffset + 0.25 * atmFogCRFTM)")
            if entry.filename.endswith("shaders.properties"):
                text = text.replace("eyeAltitude < 5.0", "(eyeAltitude + if(cloudHeight < -1000.0, 1847.0, 0.0)) < 5.0")
            if text != original:
                changes.append(entry.filename)
                data = text.encode("utf-8")
        dst.writestr(entry, data)
assert len(changes) == 4, changes
print("Patched:", *changes, sep="\n")
