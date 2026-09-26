package net.steampn.createhorsepower.content.machine;

import net.steampn.createhorsepower.blocks.crank.AnimalPowerEngine;

/** Loader-neutral access point used by ownership/recovery code for any animal-power machine. */
public interface AnimalPowerAccess {
    AnimalPowerEngine animalPowerEngine();
}
