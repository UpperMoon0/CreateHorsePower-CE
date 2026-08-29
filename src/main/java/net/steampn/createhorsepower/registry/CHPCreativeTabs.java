package net.steampn.createhorsepower.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.steampn.createhorsepower.CreateHorsePower;

public final class CHPCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateHorsePower.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createhorsepower"))
                    .icon(() -> new ItemStack(BlockRegister.HORSE_CRANK.get()))
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private CHPCreativeTabs() {}
}
