package dev.cpvp.module.impl;

import dev.cpvp.module.Category;
import dev.cpvp.module.Module;
import dev.cpvp.setting.BooleanSetting;
import dev.cpvp.setting.NumberSetting;
import dev.cpvp.util.PacketUtil;
import dev.cpvp.util.SlotUtil;
import dev.cpvp.util.TargetUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Finds a target standing in a hole (>= 3 solid horizontal sides), picks the nearest
 * Obsidian / Crying Obsidian wall block in reach, swaps to the best tool via packet and mines it.
 */
public final class AutoBreachModule extends Module {
    private final NumberSetting range    = add(new NumberSetting("Target Range", 2, 8, 0.5, 5));
    private final NumberSetting reach    = add(new NumberSetting("Break Reach", 2, 6, 0.1, 4.5));
    private final BooleanSetting crying  = add(new BooleanSetting("Crying Obsidian", true));
    private final BooleanSetting swapBack = add(new BooleanSetting("Swap Back", true));

    private BlockPos current;
    private int prevSlot = -1;

    public AutoBreachModule() {
        super("Auto Breach", "Mines through obsidian hole walls with the best tool", Category.MACROS);
    }

    @Override public void onDisable() { reset(); }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) { reset(); return; }

        if (current != null && !isHoleBlock(mc.world.getBlockState(current))) reset(); // broken (or replaced)

        if (current == null) {
            PlayerEntity t = TargetUtil.nearest(range.get());
            if (t == null) return;
            current = pickWall(t);
            if (current == null) return;
            prevSlot = SlotUtil.selected();
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(current);
        if (eye.squaredDistanceTo(c) > reach.get() * reach.get()) { reset(); return; }

        BlockState state = mc.world.getBlockState(current);
        SlotUtil.swap(SlotUtil.bestTool(state));

        Direction face = Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
        mc.interactionManager.updateBlockBreakingProgress(current, face); // sends START/STOP_DESTROY packets
        PacketUtil.swing();
    }

    private void reset() {
        if (current != null && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (swapBack.get() && prevSlot >= 0 && mc.player != null) SlotUtil.swap(prevSlot);
        current = null; prevSlot = -1;
    }

    private BlockPos pickWall(PlayerEntity t) {
        BlockPos feet = t.getBlockPos();
        int solid = 0;
        for (Direction d : Direction.Type.HORIZONTAL) if (isHoleWall(mc.world.getBlockState(feet.offset(d)))) solid++;
        if (solid < 3) return null; // target is not in a hole

        Vec3d eye = mc.player.getEyePos();
        BlockPos best = null;
        double bestSq = reach.get() * reach.get();
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos p = feet.offset(d);
            if (!isHoleBlock(mc.world.getBlockState(p))) continue;
            double sq = eye.squaredDistanceTo(Vec3d.ofCenter(p));
            if (sq <= bestSq) { bestSq = sq; best = p; }
        }
        return best;
    }

    private boolean isHoleBlock(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.OBSIDIAN || (crying.get() && b == Blocks.CRYING_OBSIDIAN);
    }

    private boolean isHoleWall(BlockState s) {
        return isHoleBlock(s) || s.isOf(Blocks.BEDROCK);
    }
}
