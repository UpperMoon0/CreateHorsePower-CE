package net.steampn.createhorsepower.blocks.horse_crank;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlock;
import net.steampn.createhorsepower.blocks.crank.HorseCrankInteractions;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPTags;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge 1.21.1 shell: maps the shared interaction semantics onto the
 * 1.21.1 {@code useItemOn}/{@code useWithoutItem} split and owns IBE registration.
 */
public class HorseCrankBlock extends AbstractHorseCrankBlock implements IBE<HorseCrankTileEntity> {

    public HorseCrankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(CHPTags.Items.ATTACHMENT_ITEMS)) {
            return mapUse(HorseCrankInteractions.attachAt(level, pos, state, player));
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (HorseCrankInteractions.hasWorker(level, pos, state)) {
            HorseCrankInteractions.detachAt(level, pos, state);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    private static ItemInteractionResult mapUse(HorseCrankInteractions.Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            case FAIL -> ItemInteractionResult.FAIL;
            case SKIP_DEFAULT -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }
}
