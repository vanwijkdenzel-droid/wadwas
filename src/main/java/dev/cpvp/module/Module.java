package dev.cpvp.module;

import dev.cpvp.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name, description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;
    private int key = -1; // GLFW_KEY_UNKNOWN

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    protected <T extends Setting> T add(T setting) { settings.add(setting); return setting; }

    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        if (v) onEnable(); else onDisable();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }
    public List<Setting> getSettings() { return Collections.unmodifiableList(settings); }

    public void onEnable() {}
    public void onDisable() {}
    /** 20 Hz. */
    public void onTick() {}
    /** Every rendered frame (start of world render) - used for near-instant reactions. */
    public void onFrame() {}
    /** Raw right-click press, fired from the GLFW mouse callback (before vanilla handles it). */
    public void onRightClick() {}
    public void onHudRender(DrawContext ctx) {}
}
