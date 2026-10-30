package net.steampn.createhorsepower.compat.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Forge Bionics Oxhauler calls Create's client-only SoundScapes.play()
 * from its unconditional entity tick. On a dedicated server that reads an
 * uninitialized client config and crashes as soon as a fueled robot ticks.
 *
 * NeoForge Bionics already has the correct logical-side guard. Preserve
 * upstream client audio and every server-side movement/fuel/tick behavior:
 * only the client-only ambient audio helper is skipped on logical servers.
 * Target and signature are validated by the optional bytecode mixin plugin.
 */
@Pseudo
@Mixin(targets = "net.dshbwlto.createbionics.entity.custom.OxhaulerEntity", remap = false)
public abstract class BionicsOxhaulerSoundscapeMixin {
    @Inject(method = "playSoundScape", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void chp$skipClientAudioOnLogicalServer(int radius, int height, CallbackInfo ci) {
        if (!((Entity) (Object) this).level().isClientSide()) {
            ci.cancel();
        }
    }
}
