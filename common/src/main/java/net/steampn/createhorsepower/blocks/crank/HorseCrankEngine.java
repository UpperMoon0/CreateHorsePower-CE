package net.steampn.createhorsepower.blocks.crank;

import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.machine.AnimalPowerMachinePolicy;

/** Backward-compatible Horse Crank facade over the reusable animal-power runtime. */
public final class HorseCrankEngine extends AnimalPowerEngine {
    public interface Host extends AnimalPowerEngine.Host {}

    public HorseCrankEngine(Host host, RedstoneMode defaultMode) {
        super(host, defaultMode, AnimalPowerMachinePolicy.horseCrank());
    }
}
