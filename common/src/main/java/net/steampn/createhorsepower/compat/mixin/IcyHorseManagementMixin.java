package net.steampn.createhorsepower.compat.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.compat.IcyHorseDeparture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
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
        if (chp$succeeded(result.getReturnValue())) IcyHorseDeparture.release(horse);
    }

    // Revival loads death-record NBT through a separate path. Resolve only
    // the body upstream successfully registered; never materialize a snapshot.
    @Inject(method = "revive", at = @At("RETURN"), require = 1, remap = false)
    private static void chp$releaseSuccessfulRevival(net.minecraft.server.level.ServerPlayer player,
            java.util.UUID horseId, CallbackInfoReturnable<?> result) {
        if (!chp$succeeded(result.getReturnValue())) return;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(horseId) instanceof AbstractHorse horse) {
                IcyHorseDeparture.release(horse);
                return;
            }
        }
    }

    @Unique
    private static boolean chp$succeeded(Object outcome) {
        try {
            return outcome != null && Boolean.TRUE.equals(outcome.getClass().getMethod("ok").invoke(outcome));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Icy horse command outcome API", e);
        }
    }

}
