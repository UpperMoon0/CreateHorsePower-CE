package net.steampn.createhorsepower.blocks.horse_crank;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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
 * Forge 1.20.1 shell: maps the shared interaction semantics onto the single
 * 1.20.1 {@code use} entry point and owns IBE registration.
 */
public class HorseCrankBlock extends AbstractHorseCrankBlock implements IBE<HorseCrankTileEntity> {

    public HorseCrankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter getter, BlockPos pos, PathComputationType pathComputationType) {
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
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);

        if (AllItems.WRENCH.isIn(stack)) {
            return InteractionResult.PASS;
        }

        if (HorseCrankInteractions.hasWorker(level, pos, state)) {
            HorseCrankInteractions.detachAt(level, pos, state);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (stack.is(CHPTags.Items.ATTACHMENT_ITEMS)) {
            return mapUse(HorseCrankInteractions.attachAt(level, pos, state, player, stack));
        }

        return InteractionResult.PASS;
    }

    private static InteractionResult mapUse(HorseCrankInteractions.Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case FAIL -> InteractionResult.FAIL;
            default -> InteractionResult.PASS;
        };
    }
}
