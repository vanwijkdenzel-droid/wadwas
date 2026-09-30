package dev.cpvp.mixin;

import dev.cpvp.module.ModuleManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    /** Module keybinds. Observes only; vanilla handling is never cancelled. */
    @Inject(method = "onKey", at = @At("HEAD"))
    private void cpvp$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle())
            ModuleManager.INSTANCE.onKey(key, action);
    }
}
