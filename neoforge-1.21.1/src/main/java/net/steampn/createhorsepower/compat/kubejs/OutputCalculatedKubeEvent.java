package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.content.stats.WorkerStats;

public class OutputCalculatedKubeEvent implements KubeEvent {
    private final Mob worker;
    private final BlockPos crankPos;
    private final Level level;
    private final float baseRpm;
    private final float baseStress;
    private float rpmMultiplier = 1.0f;
    private float stressMultiplier = 1.0f;

    public OutputCalculatedKubeEvent(Mob worker, BlockPos crankPos, Level level, float baseRpm, float baseStress) {
        this.worker = worker;
        this.crankPos = crankPos;
        this.level = level;
        this.baseRpm = baseRpm;
        this.baseStress = baseStress;
    }

    public Mob getWorker() {
        return worker;
    }

    public BlockPos getCrankPos() {
        return crankPos;
    }

    public Level getLevel() {
        return level;
    }

    public float getBaseRpm() {
        return baseRpm;
    }

    public float getBaseStress() {
        return baseStress;
    }

    public float getRpmMultiplier() {
        return rpmMultiplier;
    }

    public void setRpmMultiplier(float rpmMultiplier) {
        this.rpmMultiplier = WorkerStats.validateNonNegativeFinite(rpmMultiplier, "rpmMultiplier");
    }

    public float getStressMultiplier() {
        return stressMultiplier;
    }

    public void setStressMultiplier(float stressMultiplier) {
        this.stressMultiplier = WorkerStats.validateNonNegativeFinite(stressMultiplier, "stressMultiplier");
    }
}
