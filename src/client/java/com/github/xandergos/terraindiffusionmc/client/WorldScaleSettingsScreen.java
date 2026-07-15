package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleSelectionState;
import com.github.xandergos.terraindiffusionmc.blueprint.*;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.dimension.DimensionType;

import java.util.HashMap;
import java.util.Map;
import java.nio.file.*;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * World creation settings screen for selecting the initial terrain scale of a world.
 */
public final class WorldScaleSettingsScreen extends Screen {
    private static final String MOD_ID = "terrain-diffusion-mc";
    private static final int TEXT_FIELD_WIDTH = 80;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private static final Text LABEL_TEXT = Text.literal("World Scale");
    private static final Text DESCRIPTION_TEXT = Text.literal("Enter an integer value (1-6)");
    private static final Text ERROR_TEXT = Text.literal("Scale must be an integer between 1 and 6")
            .formatted(Formatting.RED);

    private final Screen parentScreen;
    private TextFieldWidget scaleTextField;
    private TextFieldWidget jsonTextField, widthTextField, elevationTextField, climateTextField, blendTextField,
            southLatitudeTextField, southRainTextField;
    private TextWidget validationTextWidget;
    private ButtonWidget doneButton;
    private CompiledBlueprint warningReadyBlueprint;
    private Integer warningReadyScale;

    public WorldScaleSettingsScreen(Screen parentScreen) {
        super(Text.translatable("terrain-diffusion-mc.world_settings.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = 36;

        addCenteredTextWidget(this.title, centerX, 20, 0xFFFFFF);

        addCenteredTextWidget(Text.literal("Azgaar Full JSON (leave blank for stock procedural terrain)"), centerX, centerY, 0xAAAAAA);
        jsonTextField = field(centerX-170,centerY+12,300,"Azgaar Full JSON","");
        // Minecraft's TextFieldWidget defaults to a short 32-character limit;
        // absolute Windows paths commonly exceed that by a wide margin.
        jsonTextField.setMaxLength(32767);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Browse"),b->browse()).dimensions(centerX+135,centerY+12,65,20).build());

        addCenteredTextWidget(Text.literal("Scale    Physical width (km)    Elevation noise    Climate noise    Edge blend (km)"),centerX,centerY+40,0xFFFFFF);

        scaleTextField = new TextFieldWidget(this.textRenderer,
                centerX - 195, centerY + 52,
                55, TEXT_FIELD_HEIGHT,
                LABEL_TEXT);
        scaleTextField.setText(String.valueOf(WorldScaleSelectionState.getPendingScaleOrDefault()));
        scaleTextField.setChangedListener(value -> validationTextWidget.setMessage(Text.empty()));
        this.addDrawableChild(scaleTextField);
        widthTextField=field(centerX-130,centerY+52,100,"Physical width","40075");
        elevationTextField=field(centerX-20,centerY+52,90,"Elevation noise","0.5");
        climateTextField=field(centerX+80,centerY+52,90,"Climate noise","0.2");
        blendTextField=field(centerX+180,centerY+52,80,"Blend km","250");
        addCenteredTextWidget(Text.literal("South climate latitude (−90 disables)       South rain multiplier"),centerX,centerY+78,0xFFFFFF);
        southLatitudeTextField=field(centerX-130,centerY+90,120,"South climate latitude","-20");
        southRainTextField=field(centerX+20,centerY+90,120,"South rain x","2.0");
        this.setInitialFocus(jsonTextField);

        doneButton=ButtonWidget.builder(Text.literal("Compile & Done"), b -> onDonePressed())
                .dimensions(centerX - 105, centerY + 124, 100, BUTTON_HEIGHT).build();
        this.addDrawableChild(doneButton);
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(centerX + 5, centerY + 124, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        validationTextWidget = new TextWidget(0, centerY + 152, this.width, 24, Text.empty(), this.textRenderer);
        this.addDrawableChild(validationTextWidget);
    }

    private TextFieldWidget field(int x,int y,int w,String label,String value){TextFieldWidget f=new TextFieldWidget(textRenderer,x,y,w,20,Text.literal(label));f.setText(value);f.setChangedListener(v->updatePreview());addDrawableChild(f);return f;}
    private void browse(){try(MemoryStack stack=MemoryStack.stackPush()){PointerBuffer filters=stack.mallocPointer(1);filters.put(stack.UTF8("*.json")).flip();String selected=TinyFileDialogs.tinyfd_openFileDialog("Select Azgaar Full JSON",null,filters,"JSON files",false);if(selected!=null)jsonTextField.setText(selected);}}
    private void updatePreview(){if(validationTextWidget==null)return;try{double km=Double.parseDouble(widthTextField.getText());int scale=Integer.parseInt(scaleTextField.getText());long coarse=Math.round(km/7.68);long blocks=coarse*256L*scale;validationTextWidget.setMessage(Text.literal(String.format(java.util.Locale.ROOT,"Map: %,d × %,d coarse pixels; approximately %,d × %,d Minecraft blocks",coarse,coarse/2,blocks,blocks/2)).formatted(Formatting.GRAY));}catch(Exception ignored){validationTextWidget.setMessage(Text.empty());}}

    /**
     * Adds a centered TextWidget at the given screen-center x and y position.
     */
    private void addCenteredTextWidget(Text text, int centerX, int y, int color) {
        int textWidth = this.textRenderer.getWidth(text);
        MutableText coloredText = text.copy().styled(style -> style.withColor(color));
        TextWidget widget = new TextWidget(centerX - textWidth / 2, y, textWidth, 9, coloredText, this.textRenderer);
        this.addDrawableChild(widget);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    /**
     * Parses and validates the chosen scale, then stores it as a pending world-creation value.
     */
    private void onDonePressed() {
        if (warningReadyBlueprint != null && warningReadyScale != null) {
            finishSelection(warningReadyBlueprint, warningReadyScale);
            return;
        }
        String rawScaleValue = scaleTextField.getText().trim();
        if (rawScaleValue.isEmpty()) {
            validationTextWidget.setMessage(ERROR_TEXT);
            return;
        }
        try {
            int selectedScale = Integer.parseInt(rawScaleValue);
            if (selectedScale < 1 || selectedScale > WorldScaleManager.MAX_SCALE) {
                validationTextWidget.setMessage(ERROR_TEXT);
                return;
            }
            String source=jsonTextField.getText().trim();
            if(source.isEmpty()){BlueprintSelectionState.clear();applyWorldHeightForScale(selectedScale);WorldScaleSelectionState.setPendingScale(selectedScale);close();return;}
            BlueprintCompileOptions options=new BlueprintCompileOptions(
                    Double.parseDouble(widthTextField.getText().trim()),
                    Float.parseFloat(elevationTextField.getText().trim()),
                    Float.parseFloat(climateTextField.getText().trim()),
                    Double.parseDouble(blendTextField.getText().trim()),
                    Double.parseDouble(southLatitudeTextField.getText().trim()),
                    Float.parseFloat(southRainTextField.getText().trim()));
            doneButton.active=false;validationTextWidget.setMessage(Text.literal("Validating and compiling blueprint…").formatted(Formatting.YELLOW));
            Thread.startVirtualThread(()->{try{Path temp=Files.createTempDirectory("terrain-diffusion-blueprint-");CompiledBlueprint compiled=AzgaarBlueprintCompiler.compile(Path.of(source),temp,options);this.client.execute(()->{if(compiled.warnings().isEmpty()){finishSelection(compiled,selectedScale);}else{warningReadyBlueprint=compiled;warningReadyScale=selectedScale;doneButton.active=true;doneButton.setMessage(Text.literal("Accept & Done"));validationTextWidget.setMessage(Text.literal("Warning: "+compiled.warnings().getFirst()).formatted(Formatting.YELLOW));}});}catch(Exception e){this.client.execute(()->{doneButton.active=true;validationTextWidget.setMessage(Text.literal("Import failed: "+e.getMessage()).formatted(Formatting.RED));});}});
        } catch (NumberFormatException exception) {
            validationTextWidget.setMessage(Text.literal("All numeric settings must contain valid numbers.").formatted(Formatting.RED));
        } catch (IllegalArgumentException exception) {
            validationTextWidget.setMessage(Text.literal(exception.getMessage()).formatted(Formatting.RED));
        }
    }

    private void finishSelection(CompiledBlueprint compiled,int selectedScale){BlueprintSelectionState.set(compiled);applyWorldHeightForScale(selectedScale);WorldScaleSelectionState.setPendingScale(selectedScale);close();}

    /**
     * Applies a pre-registered dimension type variant for the chosen scale.
     */
    private void applyWorldHeightForScale(int selectedScale) {
        if (!(parentScreen instanceof CreateWorldScreen createWorldScreen)) {
            return;
        }

        createWorldScreen.getWorldCreator().applyModifier((registryManager, selectedDimensions) -> {
            DimensionOptionsRegistryHolder updatedDimensions =
                    updateOverworldDimensionType(registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE),
                            selectedDimensions, selectedScale);
            return updatedDimensions == null ? selectedDimensions : updatedDimensions;
        });
    }

    /**
     * Replaces only the overworld dimension type entry with the scale-specific pre-registered one.
     */
    private DimensionOptionsRegistryHolder updateOverworldDimensionType(
            Registry<DimensionType> dimensionTypeRegistry,
            DimensionOptionsRegistryHolder selectedDimensions,
            int selectedScale
    ) {
        DimensionOptions overworldOptions = selectedDimensions.getOrEmpty(DimensionOptions.OVERWORLD).orElse(null);
        if (overworldOptions == null) {
            return null;
        }

        Identifier dimensionTypeId = Identifier.of(MOD_ID, "terrain_diffusion_scale_" + selectedScale);
        RegistryEntry.Reference<DimensionType> selectedDimensionTypeEntry = dimensionTypeRegistry.getEntry(dimensionTypeId).orElse(null);
        if (selectedDimensionTypeEntry == null) {
            return null;
        }

        DimensionOptions updatedOverworldOptions = new DimensionOptions(
                selectedDimensionTypeEntry,
                overworldOptions.chunkGenerator()
        );

        Map<net.minecraft.registry.RegistryKey<DimensionOptions>, DimensionOptions> updatedDimensionMap =
                new HashMap<>(selectedDimensions.dimensions());
        updatedDimensionMap.put(DimensionOptions.OVERWORLD, updatedOverworldOptions);
        return new DimensionOptionsRegistryHolder(updatedDimensionMap);
    }
}
