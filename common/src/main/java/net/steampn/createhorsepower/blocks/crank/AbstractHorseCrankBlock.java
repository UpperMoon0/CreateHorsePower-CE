package net.steampn.createhorsepower.blocks.crank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.steampn.createhorsepower.utils.CHPShapes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared Horse Crank block. Interaction entry points stay in the platform
 * blocks (1.20.1 has one {@code use} method, 1.21.1 splits
 * {@code useItemOn}/{@code useWithoutItem}); everything else is version-stable.
 */
public abstract class AbstractHorseCrankBlock extends KineticBlock implements ICogWheel, IWrenchable {
    public static final BooleanProperty HAS_WORKER = CrankProperties.HAS_WORKER;
    public static final BooleanProperty SMALL_WORKER_STATE = CrankProperties.SMALL_WORKER_STATE;
    public static final BooleanProperty MEDIUM_WORKER_STATE = CrankProperties.MEDIUM_WORKER_STATE;
    public static final BooleanProperty LARGE_WORKER_STATE = CrankProperties.LARGE_WORKER_STATE;

    public AbstractHorseCrankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HAS_WORKER, false)
                .setValue(SMALL_WORKER_STATE, false)
                .setValue(MEDIUM_WORKER_STATE, false)
                .setValue(LARGE_WORKER_STATE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(HAS_WORKER, SMALL_WORKER_STATE, MEDIUM_WORKER_STATE, LARGE_WORKER_STATE));
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter getter, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return CHPShapes.HORSE_CRANK;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.getBlock() != pNewState.getBlock() && pLevel.getBlockEntity(pPos) instanceof AbstractHorseCrankBlockEntity be) {
            be.onCrankRemoved();
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return switch (HorseCrankInteractions.wrench(state, context)) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case FAIL -> InteractionResult.FAIL;
            default -> InteractionResult.PASS;
        };
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return HorseCrankInteractions.comparatorOutput(level, pos);
    }
}
