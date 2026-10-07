package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.BlockUtil;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class DynamicIsland extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int BOX_HEIGHT = 20;
    private static final float PAD_LEFT = 11.0F;
    private static final float PAD_RIGHT = 12.0F;
    private static final float STATUS_RADIUS = 2.5F;
    private static final float STATUS_SPACE = STATUS_RADIUS * 2.0F + 7.0F;
    private static final float DOT_SPACE = 11.0F;
    private static final float ICON_SPACE = 20.0F;
    private static final int SCAFFOLD_STACK_SIZE = 64;
    private static final int DIM_TEXT = 0xFF9BA0AD;

    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"HUD Theme", "Custom"});
    public final ColorProperty textColor = new ColorProperty("accent-color", new Color(60, 162, 253).getRGB() & 0xFFFFFF,
            () -> this.colorMode.getValue() == 1);
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 72);
    public final IntProperty curve = new IntProperty("curve", 10, 0, 10);
    public final IntProperty offsetY = new IntProperty("offset-y", 6, 0, 80);
    public final BooleanProperty glow = new BooleanProperty("glow", true);
    public final BooleanProperty outline = new BooleanProperty("outline", true);
    public final BooleanProperty textShadow = new BooleanProperty("shadow", true);
    public final BooleanProperty showUsername = new BooleanProperty("username", true);
    public final BooleanProperty showServer = new BooleanProperty("server", true);
    public final BooleanProperty showPing = new BooleanProperty("ping", true);
    public final BooleanProperty showFps = new BooleanProperty("fps", true);
    public final BooleanProperty scaffoldMode = new BooleanProperty("scaffold-mode", true);

    private float animatedWidth = -1.0F;
    private long lastFrame;
    private float scaffoldAnim;

    public DynamicIsland() {
        super("DynamicIsland", true, false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        ScaledResolution resolution = new ScaledResolution(mc);
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0.0F : Math.min((now - lastFrame) / 1000.0F, 0.1F);
        lastFrame = now;

        boolean scaffoldActive = this.scaffoldMode.getValue() && isScaffoldActive();
        scaffoldAnim = scaffoldActive
                ? Math.min(1.0F, scaffoldAnim + 6.0F * dt)
                : Math.max(0.0F, scaffoldAnim - 6.0F * dt);

        List<Part> defaultParts = buildParts();
        float defaultContent = STATUS_SPACE + getPartsWidth(defaultParts);
        float scaffoldContent = STATUS_SPACE + ICON_SPACE + getPartsWidth(buildScaffoldParts(accentAt(0.0D).getRGB()));
        float targetWidth = Math.min(defaultContent + (scaffoldContent - defaultContent) * scaffoldAnim
                + PAD_LEFT + PAD_RIGHT, resolution.getScaledWidth() - 8.0F);

        if (animatedWidth <= 0.0F) {
            animatedWidth = targetWidth;
        } else {
            animatedWidth += (targetWidth - animatedWidth) * Math.min(1.0F, dt * 12.0F);
        }

        float width = animatedWidth;
        float x = Math.round(resolution.getScaledWidth() / 2.0F - width / 2.0F);
        float y = this.offsetY.getValue();
        float radius = Math.min(this.curve.getValue(), BOX_HEIGHT / 2.0F);
        float centerY = y + BOX_HEIGHT / 2.0F;
        Color accent = accentAt(0.0D);
        int accentRGB = accent.getRGB();
        int alpha = Math.max(0, Math.min(255, Math.round(255.0F * this.backgroundAlpha.getValue() / 100.0F)));

        RenderUtil.enableRenderState();
        if (this.glow.getValue()) {
            for (int i = 4; i >= 1; i--) {
                int glowAlpha = 4 + (4 - i) * 5;
                drawRoundedRect(x - i, y - i, x + width + i, y + BOX_HEIGHT + i, radius + i,
                        withAlpha(accentRGB, glowAlpha));
            }
        }
        drawRoundedGradient(x, y, x + width, y + BOX_HEIGHT, radius,
                argb(alpha, 30, 31, 38), argb(Math.min(255, alpha + 20), 11, 11, 15));
        if (this.outline.getValue()) {
            drawRoundedOutline(x, y, x + width, y + BOX_HEIGHT, radius, withAlpha(accentRGB, 165), 1.0F);
            drawRoundedOutline(x + 1.0F, y + 1.0F, x + width - 1.0F, y + BOX_HEIGHT - 1.0F,
                    Math.max(0.0F, radius - 1.0F), 0x14FFFFFF, 1.0F);
        }

        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 380.0);
        float statusX = x + PAD_LEFT + STATUS_RADIUS;
        drawCircle(statusX, centerY, STATUS_RADIUS + 1.8F, withAlpha(accentRGB, (int) (55 * pulse)));
        drawCircle(statusX, centerY, STATUS_RADIUS, withAlpha(accentRGB, 235));

        float defaultAlpha = Math.max(0.0F, 1.0F - scaffoldAnim * 2.0F);
        float scaffoldAlpha = Math.max(0.0F, (scaffoldAnim - 0.5F) * 2.0F);
        GlStateManager.enableTexture2D();
        if (defaultAlpha > 0.01F) {
            drawParts(defaultParts, x + PAD_LEFT + STATUS_SPACE, y, centerY, accentRGB, defaultAlpha);
        }
        if (scaffoldAlpha > 0.01F) {
            float cursor = x + PAD_LEFT + STATUS_SPACE;
            ItemStack heldBlock = getHeldBlock();
            if (heldBlock != null) {
                RenderUtil.disableRenderState();
                try {
                    renderBlockIcon(heldBlock, cursor, y, scaffoldAlpha);
                } finally {
                    RenderUtil.enableRenderState();
                }
                GlStateManager.enableTexture2D();
                GlStateManager.enableAlpha();
            }
            cursor += ICON_SPACE;
            List<Part> scaffoldParts = buildScaffoldParts(accentRGB);
            for (Part part : scaffoldParts) {
                int color = withAlpha(part.color, (int) (((part.color >>> 24) & 0xFF) * scaffoldAlpha));
                drawText(part.text, cursor, textY(y), color);
                cursor += part.width;
            }
            drawScaffoldProgress(x, y, width, getTotalBlocks(), scaffoldAlpha, accentRGB);
        }

        RenderUtil.disableRenderState();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawParts(List<Part> parts, float cursor, float y, float centerY, int accentRGB, float alpha) {
        for (Part part : parts) {
            if (part.dot) {
                drawCircle(cursor + DOT_SPACE / 2.0F, centerY, 1.3F, withAlpha(accentRGB, (int) (130 * alpha)));
            } else if (part.logo) {
                drawLogo(part.text, cursor, textY(y), alpha);
            } else {
                int color = withAlpha(part.color, (int) (((part.color >>> 24) & 0xFF) * alpha));
                drawText(part.text, cursor, textY(y), color);
            }
            cursor += part.width;
        }
    }

    private void renderBlockIcon(ItemStack stack, float x, float y, float alpha) {
        ItemStack iconStack = stack.copy();
        iconStack.stackSize = 1;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + (BOX_HEIGHT - 16.0F) / 2.0F, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(iconStack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private void drawScaffoldProgress(float x, float y, float width, int totalBlocks, float alpha, int accent) {
        float left = x + PAD_LEFT;
        float right = x + width - PAD_RIGHT;
        float barWidth = Math.max(0.0F, right - left);
        float barY = y + BOX_HEIGHT - 2.5F;
        float progress = Math.min(1.0F, Math.max(0.0F, totalBlocks / (float) SCAFFOLD_STACK_SIZE));

        drawRoundedRect(left, barY, right, barY + 1.5F, 0.75F, withAlpha(0xFF9BA0AD, (int) (55 * alpha)));
        if (progress > 0.0F && barWidth > 0.0F) {
            drawRoundedRect(left, barY, left + barWidth * progress, barY + 1.5F, 0.75F,
                    withAlpha(accent, (int) (220 * alpha)));
        }
    }

    private boolean isScaffoldActive() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private ItemStack getHeldBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (isUsableBlock(held)) return held;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isUsableBlock(stack)) return stack;
        }
        return null;
    }

    private int getTotalBlocks() {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isUsableBlock(stack)) total += stack.stackSize;
        }
        return total;
    }

    private List<Part> buildScaffoldParts(int accentRGB) {
        List<Part> parts = new ArrayList<>();
        parts.add(Part.text(String.valueOf(getTotalBlocks()), accentRGB));
        parts.add(Part.text(" blocks", DIM_TEXT));
        return parts;
    }

    private boolean isUsableBlock(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return !BlockUtil.isInteractable(block) && BlockUtil.isSolid(block);
    }

    private List<Part> buildParts() {
        List<Part> parts = new ArrayList<>();
        int accentRGB = accentAt(0.0D).getRGB();
        parts.add(Part.logo("MyAuPro", accentRGB));
        if (this.showUsername.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(mc.thePlayer.getName(), 0xFFFFFFFF));
        }
        if (this.showServer.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(getServerIP(), 0xFFE2E4EA));
        }
        if (this.showPing.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(String.valueOf(getPing()), accentRGB));
            parts.add(Part.text("ms", DIM_TEXT));
        }
        if (this.showFps.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(String.valueOf(Minecraft.getDebugFPS()), accentRGB));
            parts.add(Part.text("fps", DIM_TEXT));
        }
        return parts;
    }

    private float getPartsWidth(List<Part> parts) {
        float width = 0.0F;
        for (Part part : parts) width += part.width;
        return width;
    }

    private float textY(float y) {
        return y + (BOX_HEIGHT - mc.fontRendererObj.FONT_HEIGHT) / 2.0F;
    }

    private Color accentAt(double offset) {
        if (this.colorMode.getValue() == 0) {
            HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
            if (hud != null) return hud.getColor(System.currentTimeMillis(), (long) (offset * 1000.0D));
        }
        return new Color(this.textColor.getValue() & 0xFFFFFF);
    }

    private void drawLogo(String text, float x, float y, float alpha) {
        float cursor = x;
        int alphaValue = (int) (255 * alpha);
        for (int i = 0; i < text.length(); i++) {
            String character = String.valueOf(text.charAt(i));
            int color = withAlpha(accentAt(i * 0.45D).getRGB(), alphaValue);
            drawText(character, cursor, y, color);
            cursor += mc.fontRendererObj.getStringWidth(character);
        }
    }

    private void drawText(String text, float x, float y, int color) {
        if (this.textShadow.getValue()) mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
        else mc.fontRendererObj.drawString(text, (int) x, (int) y, color, false);
    }

    private int getPing() {
        try {
            if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getName());
            return info == null ? 0 : info.getResponseTime();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String getServerIP() {
        try {
            if (mc.theWorld != null) {
                if (mc.isIntegratedServerRunning()) return "SinglePlayer";
                if (mc.getCurrentServerData() != null) return mc.getCurrentServerData().serverIP;
            }
        } catch (Exception ignored) {
        }
        return "SinglePlayer";
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | (color & 0xFFFFFF);
    }

    private static int mixColor(int first, int second, float progress) {
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        int a1 = first >>> 24 & 0xFF, r1 = first >> 16 & 0xFF, g1 = first >> 8 & 0xFF, b1 = first & 0xFF;
        int a2 = second >>> 24 & 0xFF, r2 = second >> 16 & 0xFF, g2 = second >> 8 & 0xFF, b2 = second & 0xFF;
        return (int) (a1 + (a2 - a1) * progress) << 24 | (int) (r1 + (r2 - r1) * progress) << 16
                | (int) (g1 + (g2 - g1) * progress) << 8 | (int) (b1 + (b2 - b1) * progress);
    }

    private static void drawRoundedRect(float x1, float y1, float x2, float y2, float radius, int color) {
        drawRoundedGradient(x1, y1, x2, y2, radius, color, color);
    }

    private static void drawRoundedGradient(float x1, float y1, float x2, float y2, float radius, int top, int bottom) {
        radius = Math.max(0.0F, Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F)));
        float height = Math.max(1.0F, y2 - y1);
        int steps = Math.max(4, (int) radius + 3);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        RenderUtil.setColor(mixColor(top, bottom, 0.5F));
        GL11.glVertex2f((x1 + x2) / 2.0F, (y1 + y2) / 2.0F);
        arc(x1 + radius, y1 + radius, radius, Math.PI, steps, y1, height, top, bottom);
        arc(x2 - radius, y1 + radius, radius, -Math.PI / 2.0, steps, y1, height, top, bottom);
        arc(x2 - radius, y2 - radius, radius, 0.0, steps, y1, height, top, bottom);
        arc(x1 + radius, y2 - radius, radius, Math.PI / 2.0, steps, y1, height, top, bottom);
        RenderUtil.setColor(top);
        GL11.glVertex2f(x1, y1 + radius);
        GL11.glEnd();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.resetColor();
    }

    private static void arc(float centerX, float centerY, float radius, double start, int steps,
                            float topY, float height, int top, int bottom) {
        for (int i = 0; i <= steps; i++) {
            double angle = start + (Math.PI / 2.0) * ((double) i / steps);
            float vertexX = (float) (centerX + Math.cos(angle) * radius);
            float vertexY = (float) (centerY + Math.sin(angle) * radius);
            RenderUtil.setColor(mixColor(top, bottom, (vertexY - topY) / height));
            GL11.glVertex2f(vertexX, vertexY);
        }
    }

    private static void drawRoundedOutline(float x1, float y1, float x2, float y2, float radius, int color, float lineWidth) {
        radius = Math.max(0.0F, Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F)));
        int steps = Math.max(4, (int) radius + 3);
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        plainArc(x1 + radius, y1 + radius, radius, Math.PI, steps);
        plainArc(x2 - radius, y1 + radius, radius, -Math.PI / 2.0, steps);
        plainArc(x2 - radius, y2 - radius, radius, 0.0, steps);
        plainArc(x1 + radius, y2 - radius, radius, Math.PI / 2.0, steps);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GlStateManager.resetColor();
    }

    private static void plainArc(float centerX, float centerY, float radius, double start, int steps) {
        for (int i = 0; i <= steps; i++) {
            double angle = start + (Math.PI / 2.0) * ((double) i / steps);
            GL11.glVertex2f((float) (centerX + Math.cos(angle) * radius),
                    (float) (centerY + Math.sin(angle) * radius));
        }
    }

    private static void drawCircle(float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0F) return;
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        for (int i = 0; i <= 24; i++) {
            double angle = Math.PI * 2.0 * i / 24.0;
            GL11.glVertex2f((float) (centerX + Math.cos(angle) * radius),
                    (float) (centerY + Math.sin(angle) * radius));
        }
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    private static final class Part {
        final String text;
        final int color;
        final boolean dot;
        final boolean logo;
        final float width;

        private Part(String text, int color, boolean dot, boolean logo, float width) {
            this.text = text;
            this.color = color;
            this.dot = dot;
            this.logo = logo;
            this.width = width;
        }

        static Part text(String text, int color) {
            return new Part(text, color, false, false, mc.fontRendererObj.getStringWidth(text));
        }

        static Part logo(String text, int color) {
            return new Part(text, color, false, true, mc.fontRendererObj.getStringWidth(text));
        }

        static Part dot() {
            return new Part(null, 0, true, false, DOT_SPACE);
        }
    }
}