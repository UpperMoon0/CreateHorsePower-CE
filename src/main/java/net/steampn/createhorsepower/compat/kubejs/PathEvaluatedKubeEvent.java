package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.content.path.PathEvaluator;

public class PathEvaluatedKubeEvent implements KubeEvent {
    private final BlockPos crankPos;
    private final Level level;
    private final PathEvaluator.Result result;
    private float speedMultiplier;
    private float stressMultiplier;

    public PathEvaluatedKubeEvent(BlockPos crankPos, Level level, PathEvaluator.Result result) {
        this.crankPos = crankPos;
        this.level = level;
        this.result = result;
        this.speedMultiplier = result.speedMultiplier();
        this.stressMultiplier = result.stressMultiplier();
    }

    public BlockPos getCrankPos() {
        return crankPos;
    }

    public Level getLevel() {
        return level;
    }

    public PathEvaluator.Result getResult() {
        return result;
    }

    public int getEfficiencyPercent() {
        return result.efficiencyPercent();
    }

    public int getValidBlocks() {
        return result.validBlocks();
    }

    public int getInvalidBlocks() {
        return result.invalidBlocks();
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public float getStressMultiplier() {
        return stressMultiplier;
    }

    public void setStressMultiplier(float stressMultiplier) {
        this.stressMultiplier = stressMultiplier;
    }
}
