package net.steampn.createhorsepower.gametest;

import net.steampn.createhorsepower.compat.IcyRestartProbe;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "createhorsepower")
public final class IcyRestartEvents {
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) IcyRestartProbe.tick(event.getServer());
    }
}
