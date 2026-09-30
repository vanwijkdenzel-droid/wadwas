package dev.cpvp.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public final class TargetUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private TargetUtil() {}

    public static PlayerEntity nearest(double range) {
        PlayerEntity best = null;
        double bestSq = range * range;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        return best;
    }
}
