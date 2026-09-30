package dev.cpvp.module.impl;

import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.setting.BooleanSetting;
import dev.cpvp.setting.NumberSetting;
import dev.cpvp.util.PacketUtil;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Manual hold-to-explode. Never places anything: it only detonates an End Crystal that is
 * already under the crosshair while RMB is clicked / held.
 *  - click path: MouseMixin -> onRightClick() (fires on the raw GLFW press)
 *  - hold path : onFrame() every rendered frame while useKey is down
 */
public final class HoldExplodeModule extends Module {
    private final NumberSetting range   = add(new NumberSetting("Explode Range", 1.0, 6.0, 0.1, 4.5));
    private final NumberSetting delay   = add(new NumberSetting("Attack Delay (ticks)", 0, 10, 1, 0));
    private final NumberSetting packets = add(new NumberSetting("Packets / Burst", 1, 5, 1, 2));
    private final BooleanSetting swing  = add(new BooleanSetting("Swing Hand", true));
    private final BooleanSetting walls  = add(new BooleanSetting("Wall Check", true));

    private long lastAttackMs;

    public HoldExplodeModule() {
        super("Hold Explode", "Detonates the crystal you look at while RMB is held", Category.MACROS);
    }

    @Override public void onRightClick() { fire(true); }

    @Override public void onFrame() {
        if (mc.options.useKey.isPressed()) fire(false);
    }

    private void fire(boolean fromClick) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        // delay 0 => every frame; otherwise N ticks (50ms each). A fresh click always bypasses the gate.
        if (!fromClick && now - lastAttackMs < delay.getInt() * 50L) return;

        EndCrystalEntity crystal = findCrystal(range.get());
        if (crystal == null) return;

        lastAttackMs = now;
        for (int i = 0; i < packets.getInt(); i++) PacketUtil.attack(crystal);
        if (swing.get()) PacketUtil.swing();
    }

    private EndCrystalEntity findCrystal(double reach) {
        Vec3d eye  = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d end  = eye.add(look.multiply(reach));
        Box box    = mc.player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(
                mc.player, eye, end, box,
                e -> e instanceof EndCrystalEntity && e.isAlive(),
                reach * reach); // squared distance
        if (hit == null) return null;

        if (walls.get()) {
            BlockHitResult block = mc.world.raycast(new RaycastContext(
                    eye, hit.getPos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (block.getType() != HitResult.Type.MISS) return null;
        }
        return (EndCrystalEntity) hit.getEntity();
    }
}
