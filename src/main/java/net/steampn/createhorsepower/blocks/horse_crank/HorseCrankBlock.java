package net.steampn.createhorsepower.blocks.horse_crank;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPShapes;
import net.steampn.createhorsepower.utils.CHPUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Stream;

public class HorseCrankBlock extends KineticBlock implements ICogWheel, IBE<HorseCrankTileEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BooleanProperty HAS_WORKER = BooleanProperty.create("has_worker");
    public static final BooleanProperty SMALL_WORKER_STATE = BooleanProperty.create("small_worker");
    public static final BooleanProperty MEDIUM_WORKER_STATE = BooleanProperty.create("medium_worker");
    public static final BooleanProperty LARGE_WORKER_STATE = BooleanProperty.create("large_worker");
    public HorseCrankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HAS_WORKER, false)
                .setValue(SMALL_WORKER_STATE, false)
                .setValue(MEDIUM_WORKER_STATE, false)
                .setValue(LARGE_WORKER_STATE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(HAS_WORKER,SMALL_WORKER_STATE, MEDIUM_WORKER_STATE, LARGE_WORKER_STATE));
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
        if (pState.getBlock() != pNewState.getBlock()) {
            withBlockEntityDo(pLevel, pPos, HorseCrankTileEntity::onCrankRemoved);
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }


    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<HorseCrankTileEntity> getBlockEntityClass() {
        return HorseCrankTileEntity.class;
    }

    @Override
    public BlockEntityType<? extends HorseCrankTileEntity> getBlockEntityType() {
        return TileEntityRegister.HORSE_CRANK.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return TileEntityRegister.HORSE_CRANK.create(pos,state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        boolean hasKnot = CHPUtils.getKnot(level, pos).isPresent();
        boolean hasWorker = state.getValue(HAS_WORKER) || hasKnot;

        List<Mob> mobsNearPlayer = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(7.0D), mob -> mob.isLeashed() && mob.getLeashHolder() == player);

        if (hasWorker) {
            if (!mobsNearPlayer.isEmpty()) {
                player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.alreadyHasWorker"), true);
                return InteractionResult.FAIL;
            }

            // Detach mob cleanly through BE on server
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof HorseCrankTileEntity crankBe) {
                    crankBe.detachWorker(true);
                } else {
                    level.setBlock(pos, state.setValue(HAS_WORKER, false).setValue(SMALL_WORKER_STATE, false).setValue(MEDIUM_WORKER_STATE, false).setValue(LARGE_WORKER_STATE, false), 3);
                    CHPUtils.cleanUpLeash(level, pos, true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (mobsNearPlayer.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (mobsNearPlayer.size() > 1) {
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.maximumMobs"), true);
            return InteractionResult.FAIL;
        }

        Mob mob = mobsNearPlayer.getFirst();
        CHPUtils.WorkerTier tier = CHPUtils.getWorkerTier(mob.getType());
        if (!tier.isValid()) {
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.notValidWorker"), true);
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            LeadItem.bindPlayerMobs(player, level, pos);
            if (level.getBlockEntity(pos) instanceof HorseCrankTileEntity crankBe) {
                crankBe.attachWorker(mob, tier);
            } else {
                boolean small = tier == CHPUtils.WorkerTier.SMALL;
                boolean medium = tier == CHPUtils.WorkerTier.MEDIUM;
                boolean large = tier == CHPUtils.WorkerTier.LARGE;
                level.setBlock(pos, state.setValue(HAS_WORKER, true).setValue(SMALL_WORKER_STATE, small).setValue(MEDIUM_WORKER_STATE, medium).setValue(LARGE_WORKER_STATE, large), 3);
            }
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.attached"), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
