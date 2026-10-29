package net.steampn.createhorsepower.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * NeoForge Bionics' Oxhauler tool parts directly invoke mobInteract() rather
 * than Entity.interact(), so vanilla's lead processing never gets a chance.
 * Intercept only LEAD interactions with Oxhauler parts and delegate to the
 * parent's normal Mob.interact pipeline. No Bionics types are linked.
 */
public final class CreateBionicsPartInteractions {
    private CreateBionicsPartInteractions() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PartEntity<?> part)) return;
        if (!(part.getParent() instanceof Mob parent)) return;

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(parent.getType()).toString();
        if (!CreateBionicsCompat.shouldRedirectPartLead(entityId, true,
                event.getEntity().getItemInHand(event.getHand()).is(Items.LEAD))) return;

        // Use Player.interactOn rather than calling Mob.interact directly:
        // NeoForge intercepts this event *before* Player.interactOn snapshots
        // the held stack. A direct Mob.interact call would bypass the creative
        // lead-count restoration and onPlayerDestroyItem for the last survival
        // lead. Re-enter the normal player pipeline targeting the *parent*,
        // where this listener's PartEntity filter prevents recursive routing.
        // This also preserves other mods' standard parent-entity event hooks.
        InteractionResult result = event.getEntity().interactOn(parent, event.getHand());
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
