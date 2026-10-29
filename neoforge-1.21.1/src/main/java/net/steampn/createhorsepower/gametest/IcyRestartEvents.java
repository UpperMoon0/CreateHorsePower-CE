package net.steampn.createhorsepower.gametest;

import net.steampn.createhorsepower.compat.IcyRestartProbe;
import net.steampn.createhorsepower.compat.BionicsRestartProbe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "createhorsepower")
public final class IcyRestartEvents {
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (BionicsRestartProbe.enabled()) BionicsRestartProbe.tick(event.getServer());
        else IcyRestartProbe.tick(event.getServer());
    }
}
