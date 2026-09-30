package dev.cpvp.module.impl;

import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.setting.NumberSetting;

/** Holds ClickGUI options (shown in the Settings tab). */
public final class InterfaceModule extends Module {
    public final NumberSetting scrollSpeed = add(new NumberSetting("Scroll Speed", 10, 60, 2, 28));
    public final NumberSetting animSpeed   = add(new NumberSetting("Animation Speed", 6, 30, 1, 18));

    public InterfaceModule() {
        super("Interface", "ClickGUI behaviour", Category.SETTINGS);
        setEnabled(true);
    }
}
