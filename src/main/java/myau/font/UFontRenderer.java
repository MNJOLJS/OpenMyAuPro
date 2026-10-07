package myau.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import java.awt.*;

public class UFontRenderer extends FontRenderer {
    private StringCache stringCache;
    private final int size;

    public UFontRenderer(int size) {
        super(Minecraft.getMinecraft().gameSettings, new ResourceLocation("textures/font/ascii.png"),
                Minecraft.getMinecraft().getTextureManager(), false);
        this.size = size;
        boolean antiAlias = true;
        Font font = new Font("Arial", Font.PLAIN, size);

        int[] colorCode = new int[32];
        for (int i = 0; i <= 31; i++) {
            int j = (i >> 3 & 1) * 85;
            int k = (i >> 2 & 1) * 170 + j;
            int l = (i >> 1 & 1) * 170 + j;
            int i1 = (i & 1) * 170 + j;
            if (i == 6) k += 85;
            if (Minecraft.getMinecraft().gameSettings.anaglyph) {
                int j1 = (k * 30 + l * 59 + i1 * 11) / 100;
                int k1 = (k * 30 + l * 70) / 100;
                int l1 = (k * 30 + i1 * 70) / 100;
                k = j1; l = k1; i1 = l1;
            }
            if (i >= 16) { k /= 4; l /= 4; i1 /= 4; }
            colorCode[i] = (k & 255) << 16 | (l & 255) << 8 | (i1 & 255);
        }

        stringCache = new StringCache(colorCode);
        stringCache.setDefaultFont(font, size, antiAlias);
        stringCache.getStringWidth(" ");
    }

    @Override
    public int drawStringWithShadow(String text, float x, float y, int color) {
        stringCache.renderString(text, x + 1.0f, y + 1.0f, 0xBF000000, false);
        return stringCache.renderString(text, x, y, color, false);
    }

    public int drawString(String text, float x, float y, int color) {
        return drawString(text, x, y, color, false);
    }

    @Override
    public int drawString(String text, float x, float y, int color, boolean dropShadow) {
        if (dropShadow) {
            stringCache.renderString(text, x + 0.5f, y + 0.5f, 0xBF000000, false);
        }
        return stringCache.renderString(text, x, y, color, false);
    }

    @Override
    public int getStringWidth(String text) {
        return stringCache.getStringWidth(text);
    }

    public void drawCenteredString(String text, float x, float y, int color) {
        drawString(text, x - getStringWidth(text) / 2f, y, color, false);
    }

    public int getHeight() {
        return stringCache.height / 2;
    }
}