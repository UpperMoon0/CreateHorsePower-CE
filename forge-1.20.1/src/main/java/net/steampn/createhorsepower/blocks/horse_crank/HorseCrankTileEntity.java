package net.steampn.createhorsepower.blocks.horse_crank;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.blocks.crank.CrankProperties;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Forge 1.20.1 shell. All gameplay state/logic lives in the shared
 * {@link HorseCrankEngine}; this class only bridges Create's BE lifecycle.
 */
public class HorseCrankTileEntity extends GeneratingKineticBlockEntity implements HorseCrankEngine.Host {

    private final HorseCrankEngine engine = new HorseCrankEngine(this, Config.DEFAULT_REDSTONE_MODE.get());

    public HorseCrankTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public HorseCrankEngine engine() {
        return engine;
    }

    // ==========================================
    // Host bridge
    // ==========================================

    @Override
    @Nullable
    public Level level() {
        return this.level;
    }

    @Override
    public BlockPos pos() {
        return this.worldPosition;
    }

    @Override
    public BlockState blockState() {
        return this.getBlockState();
    }

    @Override
    public boolean hasWorkerProperty() {
        BlockState state = getBlockState();
        return state.hasProperty(CrankProperties.HAS_WORKER) && state.getValue(CrankProperties.HAS_WORKER);
    }

    @Override
    public void setBlockState(BlockState state) {
        this.level.setBlock(worldPosition, state, 3);
    }

    @Override
    public float theoreticalSpeed() {
        return this.getTheoreticalSpeed();
    }

    @Override
    public void refreshKinetic() {
        this.updateGeneratedRotation();
    }

    @Override
    public void syncToClient() {
        this.notifyUpdate();
    }

    @Override
    public void clearKineticInfo() {
        this.clearKineticInformation();
    }

    @Override
    public void requestSpeedUpdate() {
        this.updateSpeed = true;
    }

    @Override
    public void setLastCapacityProvided(float capacity) {
        this.lastCapacityProvided = capacity;
    }

    // ==========================================
    // Create lifecycle
    // ==========================================

    @Override
    public float getGeneratedSpeed() {
        return engine.generatedSpeed();
    }

    @Override
    public float calculateAddedStressCapacity() {
        return engine.addedStressCapacity();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        engine.buildGoggleTooltip(tooltip);
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(this.getBlockPos()).inflate(engine.renderBoundingBoxInflate());
    }

    @Override
    public void tick() {
        if (this.level != null && !this.level.isClientSide()) {
            engine.serverTick();
        }
        super.tick();
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        engine.write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        engine.read(compound, clientPacket);
    }

    // ==========================================
    // Public API for commands / tests
    // ==========================================

    public boolean isStoppedByRedstone() {
        return engine.isStoppedByRedstone();
    }

    public RedstoneMode getRedstoneMode() {
        return engine.getRedstoneMode();
    }

    public void setRedstoneMode(RedstoneMode mode) {
        engine.setRedstoneMode(mode);
    }

    public RedstoneMode cycleRedstoneMode() {
        return engine.cycleRedstoneMode();
    }

    public boolean isWorkerResolved() {
        return engine.isWorkerResolved();
    }

    public boolean isWorkerEligible() {
        return engine.isWorkerEligible();
    }

    public boolean isScriptVetoed() {
        return engine.isScriptVetoed();
    }

    public boolean isWorking() {
        return engine.isWorking();
    }

    public boolean canPhysicallyWork() {
        return engine.canPhysicallyWork();
    }

    public boolean hasValidWorkingBlocks() {
        return engine.hasValidWorkingBlocks;
    }

    public float getEfficiencyPercent() {
        return engine.getEfficiencyPercent();
    }

    public int getInvalidBlockCount() {
        return engine.getInvalidBlockCount();
    }

    public float getSpeedBonusPercent() {
        return engine.getSpeedBonusPercent();
    }

    public float getHealthBonusPercent() {
        return engine.getHealthBonusPercent();
    }

    public String getCachedWorkerName() {
        return engine.getCachedWorkerName();
    }

    public float getEffectiveBaseRpm() {
        return engine.getEffectiveBaseRpm();
    }

    public float getEffectiveBaseStress() {
        return engine.getEffectiveBaseStress();
    }

    public void attachWorker(net.minecraft.world.entity.Mob worker, net.steampn.createhorsepower.content.stats.WorkerResolver.ResolvedWorker profile) {
        engine.attachWorker(worker, profile);
    }

    public void detachWorker(boolean dropLead) {
        engine.detachWorker(dropLead);
    }

    public void onCrankRemoved() {
        engine.onCrankRemoved();
    }
}
