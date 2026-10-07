package myau.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu {
    private static final ResourceLocation TITLE_BACKGROUND = new ResourceLocation("myau", "title.png");

    @Redirect(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiMainMenu;renderSkybox(IIF)V"
            )
    )
    private void myau$drawTitleBackground(GuiMainMenu menu, int mouseX, int mouseY, float partialTicks) {
        TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
        if (textureManager.getTexture(TITLE_BACKGROUND) == null) {
            try (InputStream stream = MixinGuiMainMenu.class.getResourceAsStream("/assets/myau/title.png")) {
                if (stream == null) {
                    throw new IOException("Missing /assets/myau/title.png in the mod jar");
                }
                BufferedImage image = ImageIO.read(stream);
                if (image == null) {
                    throw new IOException("Unable to decode /assets/myau/title.png");
                }
                textureManager.loadTexture(TITLE_BACKGROUND, new DynamicTexture(image));
            } catch (IOException exception) {
                throw new RuntimeException("Unable to load the custom main menu background", exception);
            }
        }
        textureManager.bindTexture(TITLE_BACKGROUND);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0.0F, 0.0F, menu.width, menu.height,
                menu.width, menu.height);
    }

        @Redirect(
            method = "drawScreen",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V"
            ),
            require = 2
        )
        private void myau$skipBackgroundGradient(GuiMainMenu menu, int left, int top, int right, int bottom,
                               int startColor, int endColor) {
        }
}