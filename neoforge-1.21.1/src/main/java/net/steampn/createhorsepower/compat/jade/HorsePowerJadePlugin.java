package net.steampn.createhorsepower.compat.jade;

import net.steampn.createhorsepower.blocks.horse_crank.HorseCrankBlock;
import net.steampn.createhorsepower.blocks.horse_crank.HorseCrankTileEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class HorsePowerJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HorseCrankJadeProvider.INSTANCE, HorseCrankTileEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(HorseCrankJadeProvider.INSTANCE, HorseCrankBlock.class);
    }
}
