package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Isolated source/field audit: no save, model inference or Minecraft process is opened. */
public final class DesertImportAudit {
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[1]);
        var defaults=BlueprintCompileOptions.defaults();
        var options=args.length>2?new BlueprintCompileOptions(Double.parseDouble(args[2]),defaults.elevationNoiseRatio(),defaults.climateNoiseRatio(),defaults.edgeBlendKm(),defaults.southClimateLatitudeDeg(),defaults.southPrecipitationMultiplier()):defaults;
        var compiled=AzgaarBlueprintCompiler.compile(Path.of(args[0]),out,options);
        var m=compiled.manifest();var map=BlueprintDetailMap.read(out.resolve("ecology.bin.gz"));
        var sf=WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);sf.set(null,new BlueprintTileStore(out));
        var df=WorldBlueprintManager.class.getDeclaredField("detail");df.setAccessible(true);df.set(null,map);
        if(m.effectiveBiomeClassifierVersion()!=13||m.effectiveExceptionalSummits().size()!=2)throw new AssertionError("Missing new map configuration");
        int nw=m.width()*256,nh=m.height()*256,w=784,h=w*m.height()/m.width();
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        int dry=0;double maximum=0;long start=System.nanoTime();
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            double u=(x+.5)/w,v=(z+.5)/h,nx=(u-.5)*nw,nz=(v-.5)*nh;
            float e=map.sample(0,u,v);var f=DesertTerrain.at(map,nw,nh,nx,nz,42);
            double offset=DesertTerrain.duneOffset(nx,nz,42,f);
            if(!Double.isFinite(offset)||offset<0||offset>355)throw new AssertionError("Invalid dune displacement");
            maximum=Math.max(maximum,offset);
            int color=e<=0?0x244a70:e>4500?0xb9b8ad:0x638459;
            if(f.dry()>.15&&e>0){dry++;color=f.sand()>.6?0xe3ba72:f.sand()>.3?0xbd9769:0x95836a;}
            image.setRGB(x,z,color);
        }
        ImageIO.write(image,"png",out.resolve("desert-regions-source-approximation.png").toFile());
        // Close-up of the analytic field, NOT a screenshot of generated Minecraft terrain.
        var close=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);
        var sand=new DesertTerrain.Field(1,1,0,.7,0,1);
        for(int z=0;z<512;z++)for(int x=0;x<512;x++){
            double e=DesertTerrain.duneOffset(x*1.4,z*1.4,42,sand);
            double dx=DesertTerrain.duneOffset(x*1.4+1,z*1.4,42,sand)-e;
            double dz=DesertTerrain.duneOffset(x*1.4,z*1.4+1,42,sand)-e;
            double shade=Math.max(.45,Math.min(1.2,.9+(dx+dz)*.012));
            int red=Math.min(255,(int)(220*shade)),green=Math.min(255,(int)(174*shade)),blue=Math.min(255,(int)(110*shade));
            close.setRGB(x,z,(red<<16)|(green<<8)|blue);
        }
        ImageIO.write(close,"png",out.resolve("analytic-dunes-not-minecraft.png").toFile());
        String report="Source-field audit only; not an in-game or ONNX terrain validation.\nDimensions at scale 3: "+nw*3+" x "+nh*3+"\nSummits: "+m.effectiveExceptionalSummits()+"\nDry-mask pixels: "+dry+" / "+w*h+"\nMax unattenuated sampled dune offset at scale 3: "+maximum/10+" blocks\nAudit time including mask preparation and images: "+(System.nanoTime()-start)/1e9+" seconds\nWarnings: "+compiled.warnings();
        Files.writeString(out.resolve("audit.txt"),report);System.out.println(report);
        System.out.println("Dimensions at scale 5: "+nw*5+" x "+nh*5+"; drainage nodes: "+(m.width()*16L+1)*(m.height()*16L+1));
    }
}
