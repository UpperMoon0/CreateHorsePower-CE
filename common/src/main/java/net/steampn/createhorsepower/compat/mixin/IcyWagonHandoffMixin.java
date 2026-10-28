package net.steampn.createhorsepower.compat.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.compat.IcyHorseDeparture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Relinquish CHP's AI baseline before a wagon acquires the same horse. */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.BhWagonHitch", remap = false)
public abstract class IcyWagonHandoffMixin {
    @Inject(method = "mark", at = @At("HEAD"), require = 1, remap = false)
    private static void chp$releaseBeforeWagonHandoff(Mob horse, Entity wagon, CallbackInfo callback) {
        if (horse instanceof AbstractHorse equine) IcyHorseDeparture.release(equine);
    }
}
