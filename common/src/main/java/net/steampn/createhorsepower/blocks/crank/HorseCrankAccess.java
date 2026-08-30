package net.steampn.createhorsepower.blocks.crank;

import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.stats.WorkerResolver;

/**
 * Loader-agnostic view of a Horse Crank block entity. Commands, Jade and
 * other integrations depend on this interface, not on a particular platform
 * block entity class.
 */
public interface HorseCrankAccess {
    HorseCrankEngine engine();

    default boolean isStoppedByRedstone() {
        return engine().isStoppedByRedstone();
    }

    default RedstoneMode getRedstoneMode() {
        return engine().getRedstoneMode();
    }

    default void setRedstoneMode(RedstoneMode mode) {
        engine().setRedstoneMode(mode);
    }

    default RedstoneMode cycleRedstoneMode() {
        return engine().cycleRedstoneMode();
    }

    default boolean isWorkerResolved() {
        return engine().isWorkerResolved();
    }

    default boolean isWorkerEligible() {
        return engine().isWorkerEligible();
    }

    default boolean isScriptVetoed() {
        return engine().isScriptVetoed();
    }

    default boolean isWorking() {
        return engine().isWorking();
    }

    default boolean canPhysicallyWork() {
        return engine().canPhysicallyWork();
    }

    default boolean hasValidWorkingBlocks() {
        return engine().hasValidWorkingBlocks;
    }

    default float getEfficiencyPercent() {
        return engine().getEfficiencyPercent();
    }

    default int getInvalidBlockCount() {
        return engine().getInvalidBlockCount();
    }

    default float getSpeedBonusPercent() {
        return engine().getSpeedBonusPercent();
    }

    default float getHealthBonusPercent() {
        return engine().getHealthBonusPercent();
    }

    default String getCachedWorkerName() {
        return engine().getCachedWorkerName();
    }

    default float getEffectiveBaseRpm() {
        return engine().getEffectiveBaseRpm();
    }

    default float getEffectiveBaseStress() {
        return engine().getEffectiveBaseStress();
    }

    default float getGeneratedSpeed() {
        return engine().generatedSpeed();
    }

    default void attachWorker(net.minecraft.world.entity.Mob worker, WorkerResolver.ResolvedWorker profile) {
        engine().attachWorker(worker, profile);
    }

    default void detachWorker(boolean dropLead) {
        engine().detachWorker(dropLead);
    }

    default void onCrankRemoved() {
        engine().onCrankRemoved();
    }
}
