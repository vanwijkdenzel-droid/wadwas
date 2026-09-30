package dev.cpvp.module;

public enum Category {
    COMBAT("Combat"), MACROS("Macros"), RENDER("Render"), SETTINGS("Settings");
    public final String label;
    Category(String label) { this.label = label; }
}
