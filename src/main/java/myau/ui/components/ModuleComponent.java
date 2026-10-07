package myau.ui.components;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.HUD;
import myau.property.Property;
import myau.property.properties.*;
import myau.ui.ClickGui;
import myau.ui.Component;
import myau.ui.dataset.impl.FloatSlider;
import myau.ui.dataset.impl.IntSlider;
import myau.ui.dataset.impl.PercentageSlider;
import net.minecraft.client.gui.FontRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ModuleComponent implements Component {
    private static final long EXPANSION_DURATION = 300L;
    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    private final ArrayList<Component> settings;
    public boolean panelExpand;
    private float expansionProgress;
    private long lastAnimationUpdate;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.panelExpand = false;
        this.expansionProgress = 0.0F;
        this.lastAnimationUpdate = System.currentTimeMillis();
        int y = offsetY + 12;
        if (!Myau.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> baseProperty : Myau.propertyManager.properties.get(mod.getClass())) {
                if (baseProperty instanceof BooleanProperty) {
                    BooleanProperty property = (BooleanProperty) baseProperty;
                    CheckBoxComponent c = new CheckBoxComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof FloatProperty) {
                    FloatProperty property = (FloatProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new FloatSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof IntProperty) {
                    IntProperty property = (IntProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new IntSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof PercentProperty) {
                    PercentProperty property = (PercentProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new PercentageSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ModeProperty) {
                    ModeProperty property = (ModeProperty) baseProperty;
                    ModeComponent c = new ModeComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ColorProperty) {
                    ColorProperty property = (ColorProperty) baseProperty;
                    ColorSliderComponent c = new ColorSliderComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof TextProperty) {
                    TextProperty property = (TextProperty) baseProperty;
                    TextComponent c = new TextComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                }
            }
        }

        this.settings.add(new BindComponent(this, y));
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
        int y = this.offsetY + 16;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    public void draw(AtomicInteger offset) {
        this.updateAnimation();
        int textColor;
        if (this.mod.isEnabled()) {
            textColor = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB();
        } else {
            textColor = new Color(102, 102, 102).getRGB();
        }
        FontRenderer fr = ClickGui.getInstance().getCurrentRenderer();
        fr.drawStringWithShadow(this.mod.getName(), (float) (this.category.getX() + this.category.getWidth() / 2 - fr.getStringWidth(this.mod.getName()) / 2), (float) (this.category.getY() + this.offsetY + 4), textColor);
        int visibleSettingsHeight = this.getVisibleSettingsHeight();
        if (visibleSettingsHeight > 0 && !this.settings.isEmpty()) {
            int drawnHeight = 0;
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    if (drawnHeight + c.getHeight() > visibleSettingsHeight) {
                        break;
                    }
                    c.draw(offset);
                    offset.incrementAndGet();
                    drawnHeight += c.getHeight();
                }
            }
        }
    }

    public int getHeight() {
        this.updateAnimation();
        return 16 + this.getVisibleSettingsHeight();
    }

    private int getVisibleSettingsHeight() {
        int settingsHeight = 0;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                settingsHeight += c.getHeight();
            }
        }
        return Math.round(settingsHeight * this.expansionProgress);
    }

    public void update(int mousePosX, int mousePosY) {
        this.updateAnimation();
        if (this.expansionProgress <= 0.0F) return;
        if (!this.settings.isEmpty()) {
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    c.update(mousePosX, mousePosY);
                }
            }
        }
    }

    public void mouseDown(int x, int y, int button) {
        boolean onTitle = x > this.category.getX() && x < this.category.getX() + this.category.getWidth()
                && y > this.category.getY() + this.offsetY && y < this.category.getY() + this.offsetY + 16;
        if (onTitle) {
            if (button == 0) {
                this.mod.toggle();
            } else if (button == 1) {
                this.panelExpand = !this.panelExpand;
            }
            return;
        }

        this.updateAnimation();
        if (this.expansionProgress <= 0.0F) return;
        int contentBottom = this.category.getY() + this.offsetY + 16 + this.getVisibleSettingsHeight();
        if (y >= contentBottom) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseDown(x, y, button);
            }
        }
    }

    public void mouseReleased(int x, int y, int button) {
        this.updateAnimation();
        if (this.expansionProgress <= 0.0F) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseReleased(x, y, button);
            }
        }
    }

    public void keyTyped(char chatTyped, int keyCode) {
        if (this.expansionProgress <= 0.0F) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.keyTyped(chatTyped, keyCode);
            }
        }
    }

    public boolean isHovered(int x, int y) {
        return x > this.category.getX() && x < this.category.getX() + this.category.getWidth() && y > this.category.getY() + this.offsetY && y < this.category.getY() + 16 + this.offsetY;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - this.lastAnimationUpdate);
        this.lastAnimationUpdate = now;
        if (elapsed == 0L) {
            return;
        }

        float step = (float) elapsed / EXPANSION_DURATION;
        if (this.panelExpand) {
            this.expansionProgress = Math.min(1.0F, this.expansionProgress + step);
        } else {
            this.expansionProgress = Math.max(0.0F, this.expansionProgress - step);
        }
    }
}