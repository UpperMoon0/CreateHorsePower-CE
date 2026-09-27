package net.steampn.createhorsepower.blocks.crank;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.platform.CHPApi;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared Horse Crank block entity. All gameplay state/logic lives in the
 * shared {@link HorseCrankEngine}; platform subclasses only bridge Create's
 * BE lifecycle (their NBT write/read signatures differ between 1.20.1 and
 * 1.21.1 and are therefore kept in the version layer).
 */
public abstract class AbstractHorseCrankBlockEntity extends GeneratingKineticBlockEntity implements HorseCrankEngine.Host, HorseCrankAccess {

    private final HorseCrankEngine engine = new HorseCrankEngine(this, CHPApi.config().defaultRedstoneMode());

    public AbstractHorseCrankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
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
        // BlockEntity#getBlockState() can lag behind a same-block property
        // update performed through Level#setBlock(). Worker attachment toggles
        // HAS_WORKER that way, and Create's kinetic rebuilds can widen the
        // window where the BE cache is stale. Gameplay reconciliation must use
        // the authoritative world state or it can erase a perfectly valid
        // worker assignment on the very next tick.
        return this.level != null ? this.level.getBlockState(this.worldPosition) : this.getBlockState();
    }

    @Override
    public boolean hasWorkerProperty() {
        BlockState state = blockState();
        return state.hasProperty(CrankProperties.HAS_WORKER) && state.getValue(CrankProperties.HAS_WORKER);
    }

    @Override
    public void setWorkerPresent(boolean present) {
        BlockState state = blockState();
        if (state.hasProperty(CrankProperties.HAS_WORKER) && state.getValue(CrankProperties.HAS_WORKER) != present) {
            setBlockState(state.setValue(CrankProperties.HAS_WORKER, present));
        }
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
    public void markDirty() {
        this.setChanged();
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
            engine.beforeHostTick();
        }
        super.tick();
        if (this.level != null && !this.level.isClientSide()) {
            engine.afterHostTick();
        }
    }
}
