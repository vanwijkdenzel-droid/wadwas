package dev.cpvp.module.impl;

import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.setting.BooleanSetting;
import dev.cpvp.setting.NumberSetting;
import dev.cpvp.util.SlotUtil;
import dev.cpvp.util.TargetUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Place anchor -> charge with glowstone -> detonate, all through hotbar packet swaps.
 * With Step Delay = 0 the three steps are issued in the same tick, in order.
 * Does nothing in dimensions where anchors work as spawn points (Nether).
 */
public final class AutoAnchorModule extends Module {
    private final NumberSetting range     = add(new NumberSetting("Target Range", 2, 10, 0.5, 6));
    private final NumberSetting stepDelay = add(new NumberSetting("Step Delay (ticks)", 0, 5, 1, 0));
    private final NumberSetting cooldown  = add(new NumberSetting("Cooldown (ticks)", 0, 20, 1, 4));
    private final NumberSetting minHealth = add(new NumberSetting("Min Health", 1, 20, 1, 8));
    private final BooleanSetting swapBack = add(new BooleanSetting("Swap Back", true));
    private final BooleanSetting auto     = add(new BooleanSetting("Auto Trigger", true));

    private enum Stage { IDLE, PLACE, CHARGE, EXPLODE }
    private record Placement(BlockPos pos, BlockHitResult hit) {}

    private Stage stage = Stage.IDLE;
    private Placement placement;
    private int timer, cooldownLeft, prevSlot = -1;

    public AutoAnchorModule() {
        super("Auto Anchor", "Place, charge and explode an anchor on a target", Category.COMBAT);
    }

    @Override public void onDisable() { abort(); }

    /** Used by SwapStun's anchor follow-up. */
    public void queue(PlayerEntity target) {
        if (stage == Stage.IDLE && cooldownLeft == 0 && canRun()) begin(target);
    }

    @Override public void onTick() {
        if (!canRun()) { abort(); return; }
        if (cooldownLeft > 0) cooldownLeft--;

        if (stage == Stage.IDLE) {
            if (!auto.get() || cooldownLeft > 0) return;
            PlayerEntity t = TargetUtil.nearest(range.get());
            if (t != null) begin(t);
        }
        if (stage != Stage.IDLE) run();
    }

    private boolean canRun() {
        return mc.player != null && mc.world != null && mc.interactionManager != null
                && mc.currentScreen == null
                && !mc.world.getDimension().respawnAnchorWorks()
                && mc.player.getHealth() + mc.player.getAbsorptionAmount() >= minHealth.get();
    }

    private void begin(PlayerEntity target) {
        placement = findPlacement(target);
        if (placement == null || SlotUtil.find(s -> s.isOf(Items.RESPAWN_ANCHOR)) < 0
                || SlotUtil.find(s -> s.isOf(Items.GLOWSTONE)) < 0) { placement = null; return; }
        prevSlot = SlotUtil.selected();
        stage = Stage.PLACE;
        timer = 0;
    }

    private void run() {
        for (int guard = 0; guard < 3 && stage != Stage.IDLE; guard++) {
            if (timer > 0) { timer--; return; }
            switch (stage) {
                case PLACE   -> { if (!place())  { finish(); return; } stage = Stage.CHARGE; }
                case CHARGE  -> { if (!charge()) { finish(); return; } stage = Stage.EXPLODE; }
                case EXPLODE -> { explode(); finish(); return; }
                default -> { return; }
            }
            timer = stepDelay.getInt();
            if (timer > 0) return;
        }
    }

    private boolean place() {
        int slot = SlotUtil.find(s -> s.isOf(Items.RESPAWN_ANCHOR));
        if (slot < 0) return false;
        SlotUtil.swap(slot);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placement.hit());
        if (r.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        return r.isAccepted();
    }

    private BlockHitResult anchorHit() {
        return new BlockHitResult(Vec3d.ofCenter(placement.pos()), Direction.UP, placement.pos(), false);
    }

    private boolean charge() {
        int slot = SlotUtil.find(s -> s.isOf(Items.GLOWSTONE));
        if (slot < 0) return false;
        SlotUtil.swap(slot);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, anchorHit());
        return r.isAccepted();
    }

    private void explode() {
        int slot = -1;
        if (prevSlot >= 0 && !isGlow(prevSlot)) slot = prevSlot;
        for (int i = 0; slot < 0 && i < 9; i++) if (!isGlow(i)) slot = i;
        if (slot < 0) return;
        SlotUtil.swap(slot); // any non-glowstone item in hand => the charged anchor explodes
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, anchorHit());
    }

    private boolean isGlow(int slot) {
        return mc.player.getInventory().getStack(slot).isOf(Items.GLOWSTONE);
    }

    private void finish() {
        if (swapBack.get() && prevSlot >= 0) SlotUtil.swap(prevSlot);
        prevSlot = -1; stage = Stage.IDLE; placement = null;
        cooldownLeft = cooldown.getInt();
    }

    private void abort() {
        if (stage != Stage.IDLE) finish();
    }

    /** Head-height blocks around the target that are free and have a solid neighbour to click against. */
    private Placement findPlacement(PlayerEntity t) {
        BlockPos feet = t.getBlockPos();
        BlockPos[] candidates = new BlockPos[5];
        candidates[0] = feet.up(2);
        int i = 1;
        for (Direction d : Direction.Type.HORIZONTAL) candidates[i++] = feet.up(1).offset(d);

        Vec3d eye = mc.player.getEyePos();
        for (BlockPos c : candidates) {
            if (!mc.world.getBlockState(c).isReplaceable()) continue;
            if (eye.squaredDistanceTo(Vec3d.ofCenter(c)) > 4.5 * 4.5) continue;
            if (!mc.world.getOtherEntities(null, new Box(c), e -> !e.isSpectator()).isEmpty()) continue;

            for (Direction d : Direction.values()) {
                BlockPos n = c.offset(d);
                if (mc.world.getBlockState(n).isReplaceable() || !mc.world.getBlockState(n).isSolidBlock(mc.world, n)) continue;
                Direction face = d.getOpposite();
                Vec3d hitPos = Vec3d.ofCenter(n).add(Vec3d.of(face.getVector()).multiply(0.5));
                return new Placement(c, new BlockHitResult(hitPos, face, n, false));
            }
        }
        return null;
    }
}
