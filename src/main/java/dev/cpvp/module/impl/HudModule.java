package dev.cpvp.module.impl;

import dev.cpvp.gui.RenderUtil;
import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.module.ModuleManager;
import dev.cpvp.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;

public final class HudModule extends Module {
    private final BooleanSetting watermark = add(new BooleanSetting("Watermark", true));
    private final BooleanSetting arrayList = add(new BooleanSetting("Module List", true));

    public HudModule() {
        super("HUD", "Watermark and enabled-module list", Category.RENDER);
        setEnabled(true);
    }

    @Override public void onHudRender(DrawContext c) {
        if (mc.options.hudHidden) return;
        if (watermark.get()) RenderUtil.text(c, "CPVP", 6, 6, RenderUtil.ACCENT, 1.4f);
        if (!arrayList.get()) return;

        List<Module> on = ModuleManager.INSTANCE.all().stream()
                .filter(m -> m.isEnabled() && m != this && m.getCategory() != Category.SETTINGS)
                .sorted(Comparator.comparingInt((Module m) -> mc.textRenderer.getWidth(m.getName())).reversed())
                .toList();
        int y = 6, right = c.getScaledWindowWidth() - 6;
        for (Module m : on) {
            int w = mc.textRenderer.getWidth(m.getName());
            c.fill(right - w - 6, y - 1, right + 2, y + 10, 0x90101010);
            c.fill(right + 1, y - 1, right + 2, y + 10, RenderUtil.ACCENT);
            c.drawText(mc.textRenderer, m.getName(), right - w - 2, y, RenderUtil.ACCENT, false);
            y += 11;
        }
    }
}
