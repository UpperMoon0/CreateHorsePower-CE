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

    @Override
    public @Nullable java.util.UUID hostUuid() {
        // GeneratingKineticBlockEntity ultimately extends BlockEntity, which carries
        // a UUID since 1.20.5. Forge 1.20.1 still exposes the BE id via getBlockPos()
        // only, so we hash the position to obtain a stable per-crank identifier
        // good enough for suppression marker scoping.
        return new java.util.UUID(this.worldPosition.asLong(), 0L);
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
