package dev.cpvp.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Small 2D helpers on top of DrawContext: rounded rects, scaled text, pixel icons, checkbox. */
public final class RenderUtil {
    public static final int ACCENT     = 0xFF1FD07A;
    public static final int BG         = 0xF0121212;
    public static final int SIDEBAR    = 0xF00C0C0C;
    public static final int BORDER     = 0xFF262626;
    public static final int CARD       = 0xFF1A1A1A;
    public static final int CARD_HOVER = 0xFF202020;

    private RenderUtil() {}

    private static int withAlpha(int argb, float mul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * mul);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Filled rounded rectangle; corner rows get a half-alpha fringe pixel as cheap anti-aliasing. */
    public static void roundRect(DrawContext c, int x, int y, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x) / 2, (y2 - y) / 2)));
        if (r == 0) { c.fill(x, y, x2, y2, color); return; }
        c.fill(x, y + r, x2, y2 - r, color);
        int soft = withAlpha(color, 0.5f);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double edge = r - Math.sqrt(r * r - dy * dy);
            int inset = (int) Math.floor(edge);
            c.fill(x + inset + 1, y + i, x2 - inset - 1, y + i + 1, color);
            c.fill(x + inset + 1, y2 - i - 1, x2 - inset - 1, y2 - i, color);
            // fringe
            c.fill(x + inset, y + i, x + inset + 1, y + i + 1, soft);
            c.fill(x2 - inset - 1, y + i, x2 - inset, y + i + 1, soft);
            c.fill(x + inset, y2 - i - 1, x + inset + 1, y2 - i, soft);
            c.fill(x2 - inset - 1, y2 - i - 1, x2 - inset, y2 - i, soft);
        }
    }

    // ---- text ----
    public static void text(DrawContext c, String s, int x, int y, int color, float scale) {
        var tr = MinecraftClient.getInstance().textRenderer;
        var m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(tr, s, 0, 0, color, false);
        m.pop();
    }

    public static int width(String s, float scale) {
        return Math.round(MinecraftClient.getInstance().textRenderer.getWidth(s) * scale);
    }

    public static void centerText(DrawContext c, String s, int cx, int y, int color, float scale) {
        text(c, s, cx - width(s, scale) / 2, y, color, scale);
    }

    // ---- icons (8x8 bitmaps, drawn at 2x) ----
    private static final String[][] ICONS = {
        { // combat: sword
            "......##", ".....###", "....###.", "#..###..",
            "##.##...", ".###....", ".##.....", "#..#...." },
        { // macros: cursor
            "#.......", "##......", "###.....", "####....",
            "#####...", "###.....", "#.##....", "..##...." },
        { // render: eye
            "........", "..####..", ".######.", "###..###",
            "###..###", ".######.", "..####..", "........" },
        { // settings: gear
            "...##...", ".#.##.#.", "..####..", "###..###",
            "###..###", "..####..", ".#.##.#.", "...##..." }
    };

    public static void icon(DrawContext c, int id, int x, int y, int color) {
        drawBitmap(c, ICONS[id], x, y, 2, color);
    }

    private static void drawBitmap(DrawContext c, String[] rows, int x, int y, int px, int color) {
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++)
                if (rows[j].charAt(i) == '#')
                    c.fill(x + i * px, y + j * px, x + (i + 1) * px, y + (j + 1) * px, color);
    }

    private static final String[] CHECK = {
        "........", "......#.", ".....##.", "#...##..",
        "##.##...", ".###....", "..#.....", "........" };

    public static void checkbox(DrawContext c, int x, int y, int size, boolean checked) {
        roundRect(c, x, y, x + size, y + size, 3, checked ? ACCENT : 0xFF2C2C2C);
        if (checked) {
            int o = (size - 8) / 2;
            drawBitmap(c, CHECK, x + o, y + o, 1, 0xFF0A0A0A);
        }
    }
}
