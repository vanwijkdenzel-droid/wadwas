package dev.cpvp.gui;

import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.module.ModuleManager;
import dev.cpvp.module.impl.InterfaceModule;
import dev.cpvp.setting.BooleanSetting;
import dev.cpvp.setting.NumberSetting;
import dev.cpvp.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.IntConsumer;

/**
 * Vape-V4-style ClickGUI: icon sidebar on the left, smooth-scrolling module cards on the right.
 * LMB on a card toggles the module, RMB expands its settings (sliders, checkboxes, keybind).
 * Hit-testing is rebuilt every frame from the exact rectangles that were drawn.
 */
public final class ClickGuiScreen extends Screen {
    private static final int W = 480, H = 312, SIDEBAR = 66, ROW_H = 30;

    private static Category selected = Category.COMBAT;
    private static float scrollTarget, scrollCurrent;
    private static final Map<Module, Float> EXPAND = new HashMap<>();
    private static final Set<Module> OPEN = new HashSet<>();

    private record Hit(int x, int y, int w, int h, boolean clip, IntConsumer click) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Module listening;
    private NumberSetting dragging;
    private int dragX, dragW, viewTop, viewBottom;
    private double clickX;
    private float contentHeight;
    private long lastFrame = System.nanoTime();

    public ClickGuiScreen() { super(Text.literal("CPVP ClickGUI")); }

    @Override public boolean shouldPause() { return false; }

    private InterfaceModule ui() { return ModuleManager.INSTANCE.get(InterfaceModule.class); }

    @Override
    public void render(DrawContext c, int mx, int my, float tickDelta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float kScroll = 1f - (float) Math.exp(-dt * 16f);
        float kAnim   = 1f - (float) Math.exp(-dt * ui().animSpeed.get());
        hits.clear();

        c.fill(0, 0, width, height, 0x77000000);
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        RenderUtil.roundRect(c, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 9, RenderUtil.BORDER);
        RenderUtil.roundRect(c, x0, y0, x0 + W, y0 + H, 8, RenderUtil.BG);

        // ---------- sidebar ----------
        RenderUtil.roundRect(c, x0, y0, x0 + SIDEBAR, y0 + H, 8, RenderUtil.SIDEBAR);
        c.fill(x0 + SIDEBAR - 8, y0, x0 + SIDEBAR, y0 + H, RenderUtil.SIDEBAR);
        RenderUtil.centerText(c, "CPVP", x0 + SIDEBAR / 2, y0 + 14, RenderUtil.ACCENT, 1.4f);

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            final Category cat = cats[i];
            int ty = y0 + 46 + i * 54;
            boolean sel = cat == selected;
            boolean hov = mx >= x0 && mx < x0 + SIDEBAR && my >= ty && my < ty + 50;
            if (sel || hov) c.fill(x0, ty, x0 + SIDEBAR, ty + 50, sel ? 0x22FFFFFF : 0x0EFFFFFF);
            if (sel) c.fill(x0, ty + 6, x0 + 2, ty + 44, RenderUtil.ACCENT);
            int col = sel ? RenderUtil.ACCENT : 0xFF8A8A8A;
            RenderUtil.icon(c, cat.ordinal(), x0 + SIDEBAR / 2 - 8, ty + 8, col);
            RenderUtil.centerText(c, cat.label, x0 + SIDEBAR / 2, ty + 30, sel ? 0xFFFFFFFF : 0xFF8A8A8A, 0.8f);
            hits.add(new Hit(x0, ty, SIDEBAR, 50, false, b -> {
                selected = cat; scrollTarget = 0; scrollCurrent = 0; listening = null;
            }));
        }

        // ---------- panel ----------
        int px = x0 + SIDEBAR, pw = W - SIDEBAR;
        RenderUtil.text(c, selected.label, px + 14, y0 + 12, 0xFFFFFFFF, 1.3f);
        String hint = "LMB toggle  |  RMB settings";
        RenderUtil.text(c, hint, px + pw - 14 - RenderUtil.width(hint, 0.75f), y0 + 15, 0xFF5E5E5E, 0.75f);
        viewTop = y0 + 34;
        viewBottom = y0 + H - 8;

        float maxScroll = Math.max(0, contentHeight - (viewBottom - viewTop) + 8);
        scrollTarget = MathHelper.clamp(scrollTarget, 0, maxScroll);
        scrollCurrent += (scrollTarget - scrollCurrent) * kScroll;
        if (Math.abs(scrollTarget - scrollCurrent) < 0.05f) scrollCurrent = scrollTarget;

        c.enableScissor(px, viewTop, x0 + W - 2, viewBottom);
        float y = viewTop + 4 - scrollCurrent;
        for (Module m : ModuleManager.INSTANCE.byCategory(selected)) {
            float cur = EXPAND.getOrDefault(m, 0f);
            float goal = OPEN.contains(m) ? 1f : 0f;
            cur += (goal - cur) * kAnim;
            if (Math.abs(goal - cur) < 0.01f) cur = goal;
            EXPAND.put(m, cur);

            final int cx = px + 10, cw = pw - 20;
            final int top = Math.round(y);
            int setH = Math.round(settingsHeight(m) * cur);
            int h = ROW_H + setH;

            if (top + h > viewTop && top < viewBottom) {
                boolean hovHead = mx >= cx && mx < cx + cw && my >= top && my < top + ROW_H
                        && my >= viewTop && my <= viewBottom;
                RenderUtil.roundRect(c, cx, top, cx + cw, top + h, 5, hovHead ? RenderUtil.CARD_HOVER : RenderUtil.CARD);
                if (m.isEnabled()) c.fill(cx, top + 7, cx + 2, top + ROW_H - 7, RenderUtil.ACCENT);

                RenderUtil.text(c, m.getName(), cx + 10, top + 6, m.isEnabled() ? 0xFFFFFFFF : 0xFFCFCFCF, 1f);
                RenderUtil.text(c, m.getDescription(), cx + 10, top + 18, 0xFF6E6E6E, 0.75f);

                int chk = cx + cw - 24;
                RenderUtil.checkbox(c, chk, top + ROW_H / 2 - 7, 14, m.isEnabled());
                if (m.getKey() >= 0) {
                    String kn = keyName(m.getKey());
                    RenderUtil.text(c, kn, chk - 8 - RenderUtil.width(kn, 0.8f), top + 11, 0xFF7A7A7A, 0.8f);
                }
                hits.add(new Hit(cx, top, cw, ROW_H, true, b -> {
                    if (b == 0) m.toggle();
                    else if (b == 1 && !OPEN.remove(m)) OPEN.add(m);
                }));

                if (setH > 1) drawSettings(c, m, cx, cw, top + ROW_H, setH, cur > 0.95f);
            }
            y += h + 6;
        }
        c.disableScissor();
        contentHeight = (y + scrollCurrent) - viewTop;

        // scrollbar
        if (maxScroll > 0) {
            int trackH = viewBottom - viewTop;
            int barH = Math.max(18, (int) (trackH * (trackH / (contentHeight + 8))));
            int barY = viewTop + (int) ((trackH - barH) * (scrollCurrent / maxScroll));
            RenderUtil.roundRect(c, x0 + W - 5, barY, x0 + W - 2, barY + barH, 1, 0xFF3A3A3A);
        }
    }

    private void drawSettings(DrawContext c, Module m, int cx, int cw, int startY, int setH, boolean interact) {
        c.enableScissor(cx, startY, cx + cw, startY + setH);
        int sy = startY;

        // keybind binder
        RenderUtil.text(c, "Keybind", cx + 10, sy + 5, 0xFFB0B0B0, 0.9f);
        String kt = listening == m ? "press a key..." : (m.getKey() < 0 ? "NONE" : keyName(m.getKey()));
        int kw = RenderUtil.width(kt, 0.85f) + 12;
        int kx = cx + cw - 10 - kw;
        RenderUtil.roundRect(c, kx, sy + 2, kx + kw, sy + 16, 3, 0xFF262626);
        RenderUtil.text(c, kt, kx + 6, sy + 6, listening == m ? RenderUtil.ACCENT : 0xFFD0D0D0, 0.85f);
        if (interact) hits.add(new Hit(kx, sy + 2, kw, 14, true, b -> listening = (listening == m) ? null : m));
        sy += 20;

        for (Setting s : m.getSettings()) {
            RenderUtil.text(c, s.getName(), cx + 10, sy + 5, 0xFFB0B0B0, 0.9f);
            if (s instanceof BooleanSetting bs) {
                RenderUtil.checkbox(c, cx + cw - 24, sy + 2, 14, bs.get());
                if (interact) hits.add(new Hit(cx + 10, sy, cw - 20, 20, true, b -> bs.toggle()));
                sy += 20;
            } else if (s instanceof NumberSetting ns) {
                String v = ns.display();
                RenderUtil.text(c, v, cx + cw - 10 - RenderUtil.width(v, 0.9f), sy + 5, RenderUtil.ACCENT, 0.9f);
                final int tx = cx + 10, tw = cw - 20, ty = sy + 18;
                double f = (ns.get() - ns.min()) / (ns.max() - ns.min());
                int fw = (int) (tw * f);
                RenderUtil.roundRect(c, tx, ty, tx + tw, ty + 4, 2, 0xFF2A2A2A);
                if (fw > 0) RenderUtil.roundRect(c, tx, ty, tx + Math.max(fw, 4), ty + 4, 2, RenderUtil.ACCENT);
                RenderUtil.roundRect(c, tx + fw - 3, ty - 2, tx + fw + 3, ty + 6, 3, 0xFFFFFFFF);
                if (interact) hits.add(new Hit(tx - 3, ty - 6, tw + 6, 16, true, b -> {
                    dragging = ns; dragX = tx; dragW = tw; updateSlider(clickX);
                }));
                sy += 28;
            }
        }
        c.disableScissor();
    }

    private int settingsHeight(Module m) {
        int h = 20;
        for (Setting s : m.getSettings()) h += (s instanceof NumberSetting) ? 28 : 20;
        return h + 4;
    }

    private void updateSlider(double mouseX) {
        if (dragging == null) return;
        double f = MathHelper.clamp((mouseX - dragX) / (double) dragW, 0, 1);
        dragging.set(dragging.min() + f * (dragging.max() - dragging.min()));
    }

    private static String keyName(int key) {
        return InputUtil.fromKeyCode(key, -1).getLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        clickX = mx;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.clip() && (my < viewTop || my > viewBottom)) continue;
            if (h.contains(mx, my)) { h.click().accept(button); return true; }
        }
        listening = null;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { updateSlider(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) (vertical * ui().scrollSpeed.get());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (listening != null) {
            boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
            listening.setKey(clear ? -1 : key);
            listening = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(key, scancode, mods);
    }
}
