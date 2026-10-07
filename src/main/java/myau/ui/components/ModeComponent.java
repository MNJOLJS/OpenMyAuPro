package myau.ui.components;

import myau.enums.ChatColors;
import myau.property.properties.ModeProperty;
import myau.ui.ClickGui;
import myau.ui.Component;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicInteger;

public class ModeComponent implements Component {
    private static final long EXPANSION_DURATION = 300L;
    private final ModeProperty property;
    private final ModuleComponent parentModule;
    private int x;
    private int y;
    private int offsetY;
    private boolean expanded;
    private float expansionProgress;
    private long lastAnimationUpdate;

    public ModeComponent(ModeProperty desc, ModuleComponent parentModule, int offsetY) {
        this.property = desc;
        this.parentModule = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
        this.expanded = false;
        this.expansionProgress = 0.0F;
        this.lastAnimationUpdate = System.currentTimeMillis();
    }

    public void draw(AtomicInteger offset) {
        this.updateAnimation();
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String mode = this.property.getModeString();
        mode = mode.replace("_", " ");
        if (mode.isEmpty()) {
            mode = "?";
        }
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        int bruhWidth = (int) (fr.getStringWidth(this.property.getName() + ": ") * 0.5);
        fr.drawString(this.property.getName() + ": ", (float) ((this.parentModule.category.getX() + 4) * 2), (float) ((this.parentModule.category.getY() + this.offsetY + 4) * 2), 0xffffffff, true);
        fr.drawString(ChatColors.formatColor("&9" + mode.substring(0, 1).toUpperCase() + mode.substring(1).toLowerCase()), (float) ((this.parentModule.category.getX() + 4 + bruhWidth) * 2), (float) ((this.parentModule.category.getY() + this.offsetY + 4) * 2), -1, true);
        fr.drawString(this.expanded ? "-" : "+", (float) ((this.parentModule.category.getX() + this.parentModule.category.getWidth() - 10) * 2), (float) ((this.parentModule.category.getY() + this.offsetY + 4) * 2), -1, true);

        int visibleOptionHeight = this.getVisibleOptionHeight();
        if (visibleOptionHeight > 0) {
            String[] modes = this.property.getModes();
            for (int i = 0; i < modes.length; i++) {
                if (i * 12 >= visibleOptionHeight) {
                    break;
                }
                int color = i == this.property.getValue() ? 0xff55aaff : 0xffffffff;
                String option = modes[i].replace("_", " ");
                fr.drawString(option.substring(0, 1).toUpperCase() + option.substring(1).toLowerCase(),
                        (float) ((this.parentModule.category.getX() + 10) * 2),
                        (float) ((this.parentModule.category.getY() + this.offsetY + 16 + i * 12) * 2), color, true);
            }
        }
        GL11.glPopMatrix();
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        this.updateAnimation();
        return 12 + this.getVisibleOptionHeight();
    }


    public void mouseDown(int x, int y, int button) {
        if (this.isExpandedOptionHovered(x, y) && button == 0) {
            int modeIndex = (y - this.y - 12) / 12;
            if (modeIndex >= 0 && modeIndex < this.property.getModes().length) {
                this.property.setValue(modeIndex);
                this.expanded = false;
            }
            return;
        }

        if (isHovered(x, y)) {
            if (button == 0) {
                this.expanded = !this.expanded;
            } else if (button == 1) {
                this.property.previousMode();
            }
        } else if (this.expanded && button == 0) {
            this.expanded = false;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    private boolean isHovered(int x, int y) {
        return x > this.x && x < this.x + this.parentModule.category.getWidth() && y > this.y && y < this.y + 11;
    }

    private boolean isExpandedOptionHovered(int x, int y) {
        return this.getVisibleOptionHeight() > 0
                && x > this.x
                && x < this.x + this.parentModule.category.getWidth()
                && y >= this.y + 12
                && y < this.y + 12 + this.getVisibleOptionHeight();
    }

    private int getVisibleOptionHeight() {
        return Math.round(this.property.getModes().length * 12.0F * this.expansionProgress);
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - this.lastAnimationUpdate);
        this.lastAnimationUpdate = now;
        if (elapsed == 0L) {
            return;
        }

        float step = (float) elapsed / EXPANSION_DURATION;
        if (this.expanded) {
            this.expansionProgress = Math.min(1.0F, this.expansionProgress + step);
        } else {
            this.expansionProgress = Math.max(0.0F, this.expansionProgress - step);
        }
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}