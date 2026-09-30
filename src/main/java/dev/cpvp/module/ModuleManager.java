package dev.cpvp.module;

import dev.cpvp.module.impl.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {}

    public void init() {
        modules.add(new AutoAnchorModule());
        modules.add(new SwapStunModule());
        modules.add(new HoldExplodeModule());
        modules.add(new AutoBreachModule());
        modules.add(new HudModule());
        modules.add(new InterfaceModule());
    }

    public List<Module> all() { return modules; }

    public List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.getCategory() == c) out.add(m);
        return out;
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) if (type.isInstance(m)) return type.cast(m);
        return null;
    }

    private static boolean inWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null;
    }

    public void onTick()  { if (inWorld()) for (Module m : modules) if (m.isEnabled()) m.onTick(); }
    public void onFrame() { if (inWorld()) for (Module m : modules) if (m.isEnabled()) m.onFrame(); }

    public void onHud(DrawContext ctx) {
        for (Module m : modules) if (m.isEnabled()) m.onHudRender(ctx);
    }

    /** Called from KeyboardMixin. */
    public void onKey(int key, int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || action != GLFW.GLFW_PRESS || key < 0) return;
        for (Module m : modules) if (m.getKey() == key) m.toggle();
    }

    /** Called from MouseMixin on every mouse button press. */
    public void onMouseClick(int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!inWorld() || mc.currentScreen != null) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            for (Module m : modules) if (m.isEnabled()) m.onRightClick();
    }
}
