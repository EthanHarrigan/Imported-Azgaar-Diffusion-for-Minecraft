package com.github.xandergos.terraindiffusionmc.blueprint;

import java.nio.file.Path;
import java.util.List;

public record CompiledBlueprint(Path directory, BlueprintManifest manifest, List<String> warnings) { }
