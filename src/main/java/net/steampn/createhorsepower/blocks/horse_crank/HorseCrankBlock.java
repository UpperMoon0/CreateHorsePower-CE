package net.steampn.createhorsepower.blocks.horse_crank;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
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
import net.steampn.createhorsepower.compat.OptionalIntegrations;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPShapes;
import net.steampn.createhorsepower.utils.CHPUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class HorseCrankBlock extends KineticBlock implements ICogWheel, IBE<HorseCrankTileEntity>, IWrenchable {
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
        return TileEntityRegister.HORSE_CRANK.create(pos, state);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockEntity(pos) instanceof HorseCrankTileEntity crankBe) {
            RedstoneMode newMode = crankBe.cycleRedstoneMode();
            if (player != null) {
                player.displayClientMessage(Component.translatable("tooltip.createhorsepower.redstone_mode.changed", newMode.getDisplayName()), true);
            }
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(net.steampn.createhorsepower.utils.CHPTags.Items.ATTACHMENT_ITEMS)) {

            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }

            boolean hasKnot = CHPUtils.getKnot(level, pos).isPresent();
            boolean hasWorker = state.getValue(HAS_WORKER) || hasKnot;

            if (hasWorker) {
                player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.alreadyHasWorker"), true);
                return ItemInteractionResult.SUCCESS;
            }

            List<Mob> mobsNearPlayer = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(7.0D), mob -> mob.isLeashed() && mob.getLeashHolder() == player);

            if (mobsNearPlayer.isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            if (mobsNearPlayer.size() > 1) {
                player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.maximumMobs"), true);
                return ItemInteractionResult.SUCCESS;
            }

            Mob mob = mobsNearPlayer.getFirst();
            WorkerResolver.ResolvedWorker profile = WorkerResolver.resolve(mob);
            if (!profile.isValid()) {
                player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.notValidWorker"), true);
                return ItemInteractionResult.SUCCESS;
            }

            if (!OptionalIntegrations.fireBeforeAttach(player, mob, pos, level, profile)) {
                return ItemInteractionResult.FAIL;
            }

            LeadItem.bindPlayerMobs(player, level, pos);
            if (level.getBlockEntity(pos) instanceof HorseCrankTileEntity crankBe) {
                crankBe.attachWorker(mob, profile);
            } else {
                level.setBlock(pos, state.setValue(HAS_WORKER, true), 3);
            }
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.attached"), true);

            return ItemInteractionResult.SUCCESS;
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        boolean hasKnot = CHPUtils.getKnot(level, pos).isPresent();
        boolean hasWorker = state.getValue(HAS_WORKER) || hasKnot;

        if (hasWorker) {
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

        return InteractionResult.PASS;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HorseCrankTileEntity crankBe) {
            if (crankBe.isStoppedByRedstone() || !crankBe.hasValidWorkingBlocks || crankBe.getGeneratedSpeed() == 0) {
                return 0;
            }
            float efficiency = crankBe.getEfficiencyPercent();
            return Math.min(15, Math.max(1, Math.round((efficiency / 100.0f) * 10.0f)));
        }
        return 0;
    }
}
