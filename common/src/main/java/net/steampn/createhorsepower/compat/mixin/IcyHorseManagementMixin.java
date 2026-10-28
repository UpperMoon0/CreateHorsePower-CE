package net.steampn.createhorsepower.compat.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.compat.IcyHorseDeparture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "icy.betterhorses.net.HorseManagement", remap = false)
public abstract class IcyHorseManagementMixin {
    // Upstream recreates recalled unloaded bodies with moveTo(), bypassing
    // HorsePlacement.teleport(). Only release a successfully registered body.
    @Inject(method = "respawnFromSnapshot", at = @At("RETURN"), require = 1, remap = false)
    private static void chp$releaseRecalledSnapshot(CallbackInfoReturnable<AbstractHorse> result) {
        AbstractHorse horse = result.getReturnValue();
        if (horse != null) IcyHorseDeparture.release(horse);
    }
    // Nearby whistles may only change FOLLOW without teleporting. They still
    // hand movement control back to Icy after the successful command.
    @Inject(method = "summonToPlayer", at = @At("RETURN"), require = 1, remap = false)
    private static void chp$releaseSuccessfulSummon(AbstractHorse horse,
            net.minecraft.server.level.ServerPlayer player, CallbackInfoReturnable<?> result) {
        Object outcome = result.getReturnValue();
        try {
            if (outcome != null && Boolean.TRUE.equals(outcome.getClass().getMethod("ok").invoke(outcome))) {
                IcyHorseDeparture.release(horse);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Icy horse command outcome API", e);
        }
    }

}
