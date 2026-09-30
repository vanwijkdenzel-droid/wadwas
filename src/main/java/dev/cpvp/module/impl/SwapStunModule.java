package dev.cpvp.module.impl;

import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.module.ModuleManager;
import dev.cpvp.setting.BooleanSetting;
import dev.cpvp.setting.NumberSetting;
import dev.cpvp.util.PacketUtil;
import dev.cpvp.util.SlotUtil;
import dev.cpvp.util.TargetUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Watches the nearest target and fires a burst the moment it is vulnerable:
 *  - Shield Stun : target is blocking -> swap to axe, hit, swap back
 *  - Mace Slam   : you are falling onto the target -> swap to mace, hit, swap back
 *  - Anchor Slam : target is launched / knocked back -> hand over to Auto Anchor
 *
 * NOTE: servers reset your attack-strength when the held item changes, so "Swap Delay" lets the
 * cooldown recover a little before the hit if you want more than the minimum damage.
 */
public final class SwapStunModule extends Module {
    private final NumberSetting reach      = add(new NumberSetting("Attack Reach", 2, 6, 0.1, 3.0));
    private final NumberSetting swapDelay  = add(new NumberSetting("Swap Delay (ticks)", 0, 12, 1, 1));
    private final NumberSetting cooldown   = add(new NumberSetting("Cooldown (ticks)", 0, 40, 1, 10));
    private final BooleanSetting shieldStun = add(new BooleanSetting("Shield Stun", true));
    private final BooleanSetting maceSlam   = add(new BooleanSetting("Mace Slam", true));
    private final NumberSetting minFall     = add(new NumberSetting("Min Fall (blocks)", 1.5, 10, 0.5, 2.5));
    private final BooleanSetting anchorSlam = add(new BooleanSetting("Anchor Follow-up", true));
    private final NumberSetting displace    = add(new NumberSetting("Displace Threshold", 0.2, 1.5, 0.05, 0.5));
    private final BooleanSetting swapBack   = add(new BooleanSetting("Swap Back", true));

    private final Map<Integer, Vec3d> lastPos = new HashMap<>();
    private PlayerEntity victim;
    private int prevSlot = -1, timer, cooldownLeft;

    public SwapStunModule() {
        super("Swap Stun / Slam", "Burst-damage the instant a target becomes vulnerable", Category.COMBAT);
    }

    @Override public void onDisable() { finish(false); lastPos.clear(); }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) { finish(false); return; }

        // Movement tracking (client-side entity velocity is unreliable for other players; use position deltas).
        PlayerEntity target = TargetUtil.nearest(Math.max(reach.get() + 3.0, 6.0));
        Vec3d motion = Vec3d.ZERO;
        if (target != null) {
            Vec3d now = target.getPos();
            motion = now.subtract(lastPos.getOrDefault(target.getId(), now));
            lastPos.put(target.getId(), now);
        }

        // Executing a queued swap-hit.
        if (victim != null) {
            if (timer-- > 0) return;
            if (victim.isAlive() && mc.player.distanceTo(victim) <= reach.get()) {
                PacketUtil.attack(victim);
                PacketUtil.swing();
            }
            finish(true);
            return;
        }

        if (cooldownLeft > 0) { cooldownLeft--; return; }
        if (target == null || mc.player.distanceTo(target) > reach.get()) {
            // Anchor follow-up is allowed at longer range since Auto Anchor has its own range.
            if (target != null) tryAnchor(target, motion);
            return;
        }

        if (shieldStun.get() && target.isBlocking()) {
            int axe = SlotUtil.find(s -> s.getItem() instanceof AxeItem);
            if (axe >= 0) { begin(target, axe); return; }
        }
        if (maceSlam.get() && !mc.player.isOnGround() && mc.player.fallDistance >= minFall.get()) {
            int mace = SlotUtil.find(s -> s.isOf(Items.MACE));
            if (mace >= 0) { begin(target, mace); return; }
        }
        tryAnchor(target, motion);
    }

    private void tryAnchor(PlayerEntity target, Vec3d motion) {
        if (!anchorSlam.get()) return;
        boolean launched = motion.y > 0.3 || motion.horizontalLength() > displace.get();
        if (!launched) return;
        AutoAnchorModule anchor = ModuleManager.INSTANCE.get(AutoAnchorModule.class);
        if (anchor != null) {
            anchor.queue(target);
            cooldownLeft = cooldown.getInt();
        }
    }

    private void begin(PlayerEntity target, int weaponSlot) {
        victim = target;
        prevSlot = SlotUtil.selected();
        SlotUtil.swap(weaponSlot);
        timer = swapDelay.getInt();
    }

    private void finish(boolean applyCooldown) {
        if (swapBack.get() && prevSlot >= 0 && mc.player != null) SlotUtil.swap(prevSlot);
        victim = null; prevSlot = -1;
        if (applyCooldown) cooldownLeft = cooldown.getInt();
    }
}
