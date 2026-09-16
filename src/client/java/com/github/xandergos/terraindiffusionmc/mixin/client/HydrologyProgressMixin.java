package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class HydrologyProgressMixin {
    @Shadow public int width;
    @Shadow public int height;
    @Shadow @Final protected TextRenderer textRenderer;
    @Unique private String terrainDiffusion$lastNotice="";
    @Unique private int terrainDiffusion$noticeWidth=-1;
    @Unique private java.util.List<net.minecraft.text.OrderedText> terrainDiffusion$noticeLines=java.util.List.of();
    @Inject(method="renderWithTooltip",at=@At("TAIL"))
    private void terrainDiffusion$progress(DrawContext context,int mouseX,int mouseY,float delta,CallbackInfo ci){
        String status=WorldHydrology.progress();
        boolean rivers=!status.isEmpty();
        if(!rivers)status=com.github.xandergos.terraindiffusionmc.world.BiomeTileCache.progress();
        if(status.isEmpty())return;
        String notice=rivers?WorldHydrology.cacheNotice():"";
        if(!notice.isEmpty()){
            // Text layout is cached until the message or window width changes. Rendering
            // does no filesystem access, cache inspection, animation or background work.
            if(!notice.equals(terrainDiffusion$lastNotice)||width!=terrainDiffusion$noticeWidth){
                terrainDiffusion$lastNotice=notice;terrainDiffusion$noticeWidth=width;
                terrainDiffusion$noticeLines=textRenderer.wrapLines(net.minecraft.text.Text.literal(notice),Math.max(1,width-32));
            }
            int bottom=12+terrainDiffusion$noticeLines.size()*(textRenderer.fontHeight+2);
            context.fill(8,6,width-8,bottom,0xdd101820);
            int y=10;
            for(var line:terrainDiffusion$noticeLines){
                context.drawCenteredTextWithShadow(textRenderer,line,width/2,y,0xfff0cf78);
                y+=textRenderer.fontHeight+2;
            }
        }
        context.fill(4,height-48,width-4,height-4,0xdd101820);
        context.drawCenteredTextWithShadow(textRenderer,status,width/2,height-38,0xfff0cf78);
        context.drawCenteredTextWithShadow(textRenderer,rivers?"Loading saved drainage or preparing missing river checkpoints.":"Exact biome samples are saved for faster future world joins.",width/2,height-23,0xffffffff);
    }
}
