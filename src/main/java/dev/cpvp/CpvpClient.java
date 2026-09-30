package dev.cpvp;

import dev.cpvp.gui.ClickGuiScreen;
import dev.cpvp.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class CpvpClient implements ClientModInitializer {
    private static KeyBinding openGui;

    @Override
    public void onInitializeClient() {
        ModuleManager.INSTANCE.init();

        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cpvpclient.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.cpvpclient"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.wasPressed()) mc.setScreen(new ClickGuiScreen());
            ModuleManager.INSTANCE.onTick();
        });

        // Per-frame hook for sub-tick reactions (Hold Explode while RMB is held).
        WorldRenderEvents.START.register(ctx -> ModuleManager.INSTANCE.onFrame());

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> ModuleManager.INSTANCE.onHud(ctx));
    }
}
