package net.steampn.createhorsepower.compat.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.compat.IcyHorseDeparture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "icy.betterhorses.net.HorsePlacement", remap = false)
public abstract class IcyHorsePlacementMixin {
    @Inject(method = "teleport", at = @At("RETURN"), require = 1, remap = false)
    private static void chp$releaseAfterSuccessfulTeleport(AbstractHorse horse, BlockPos target,
                                                         CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) IcyHorseDeparture.release(horse);
    }
}
