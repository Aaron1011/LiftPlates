package com.zachsthings.liftplates.specialblock;

import com.zachsthings.liftplates.Lift;
import com.zachsthings.liftplates.LiftContents;
import com.zachsthings.liftplates.MoveResult;
import com.zachsthings.liftplates.util.Point;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.HashSet;
import java.util.Set;

/**
 * This special block stops the lift util its associated pressure plate is re-triggered
 */
public class StationSpecialBlock extends SpecialBlock {
    private final Set<Block> activeBlocks = new HashSet<Block>();
    public StationSpecialBlock() {
        super("Station", Material.GOLD_BLOCK);
    }

    protected StationSpecialBlock(String name, Material type) {
        super(name, type);
    }

    @Override
    public MoveResult liftActed(Lift lift, LiftContents contents) {
        return new MoveResult(MoveResult.Type.STOP);
    }

    @Override
    public void plateTriggered(Lift lift, Block block) {
        if (!activeBlocks.contains(block)) {
            activeBlocks.add(block);
            CallLift call = new CallLift(block, lift);
            call.taskId = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(
                    Bukkit.getServer().getPluginManager().getPlugin("LiftPlates"),
                    call, 0, CallLift.RUN_FREQUENCY);
        }
    }

    private class CallLift implements Runnable {
        public static final long RUN_FREQUENCY = 2;
        private int taskId;
        private final BlockFace direction;
        private final Block target;
        private Point nearestLiftBlock;
        private LiftContents contents;

        public CallLift(Block target, Lift lift) {
            this.target = target;
            this.contents = lift.getContents();

            Point blockLoc = new Point(target.getLocation());
            final int requiredY = lift.getPosition().getY() - 1;
            Point nearestLoc = null;
            int distance = Integer.MAX_VALUE;

            for (Point loc : contents.getBlocks()) {
                if (loc.getY() == requiredY) {
                    if (loc.distanceSquared(blockLoc) < distance) {
                        nearestLoc = loc;
                    }
                }
            }

            if (nearestLoc == null) {
                throw new IllegalStateException("No nearest location found from the lift!");
            }
            this.nearestLiftBlock = nearestLoc;

            BlockFace liftDirection = lift.getDirection().getFace();

            // Calculate the distances to travel and restrict them to directions the lift can move in normal operation
            int dx = (blockLoc.getX() - nearestLoc.getX()) * Math.abs(liftDirection.getModX());
            int dy = (blockLoc.getY() - nearestLoc.getY()) * Math.abs(liftDirection.getModY());
            int dz = (blockLoc.getZ() - nearestLoc.getZ()) * Math.abs(liftDirection.getModZ());


            // Make the amounts -1, 0, or 1
            dx = dx == 0 ? 0 : dx / Math.abs(dx);
            dy = dy == 0 ? 0 : dy / Math.abs(dy);
            dz = dz == 0 ? 0 : dz / Math.abs(dz);

            BlockFace moveFace = null;
            for (BlockFace face : BlockFace.values()) {
                if (face.getModX() == dx
                        && face.getModY() == dy
                        && face.getModZ() == dz) {
                    moveFace = face;
                    break;
                }
            }

            if (moveFace == null) {
                throw new IllegalArgumentException("No BlockFace for direction that lift is supposed to move ("
                        + dx + ", " + dy + ", " + dz +  ")!");
            } else if (moveFace == BlockFace.SELF) {

            }
            direction = moveFace;
        }

        public void run() {
            nearestLiftBlock = nearestLiftBlock.modify(direction);
            contents.update();
            MoveResult result = contents.move(direction, true);
            final int dx = (nearestLiftBlock.getX() - target.getX()) * direction.getModX(),
                    dy = (nearestLiftBlock.getY() - target.getY()) * direction.getModY(),
                    dz = (nearestLiftBlock.getZ() - target.getZ()) * direction.getModZ();
            if (result.getType() == MoveResult.Type.STOP
                    || result.getType() == MoveResult.Type.BLOCK
                    || (dx == 0 && dy == 0 && dz == 0)) {
                Bukkit.getServer().getScheduler().cancelTask(taskId);
                activeBlocks.remove(target);
            }
        }
    }
}
