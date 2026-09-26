package net.steampn.createhorsepower.content.machine;

import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.platform.CHPApi;

/** Runtime policy that identifies an animal-power machine independently of its block implementation. */
public record AnimalPowerMachinePolicy(ResourceLocation id, int maxWorkers) {
    public AnimalPowerMachinePolicy {
        if (id == null) throw new IllegalArgumentException("machine id cannot be null");
        if (maxWorkers < 1) throw new IllegalArgumentException("maxWorkers must be >= 1");
    }

    public static AnimalPowerMachinePolicy horseCrank() {
        return new AnimalPowerMachinePolicy(CHPApi.modId("horse_crank"), 1);
    }
}
