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

        // Calling Mob.interact directly does not re-fire this event; the
        // player interaction event is emitted in Player.interactOn instead.
        // The parent handles vanilla lead attach/drop with the same behavior
        // as clicking the Oxhauler's central body, rather than tool-part
        // mobInteract() mounting the player while a lead is held.
        InteractionResult result = parent.interact(event.getEntity(), event.getHand());
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
