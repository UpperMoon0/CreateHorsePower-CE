package net.steampn.createhorsepower.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreateBionicsCompatTest {
    @Test
    void recognizesOnlyTheSupportedOxhauler() {
        assertTrue(CreateBionicsCompat.isOxhauler("createbionics:oxhauler"));
        assertFalse(CreateBionicsCompat.isOxhauler("createbionics:anole"));
        assertFalse(CreateBionicsCompat.isOxhauler("createbionics:replete"));
        assertFalse(CreateBionicsCompat.isOxhauler("minecraft:horse"));
        assertFalse(CreateBionicsCompat.isOxhauler("othermod:oxhauler"));
        assertFalse(CreateBionicsCompat.isOxhauler(null));
    }

    @Test
    void requiresFullAssemblyFuelAndFreeMovement() {
        assertFalse(CreateBionicsCompat.operational(2, 100, false, false, false));
        assertFalse(CreateBionicsCompat.operational(3, 0, false, false, false));
        assertFalse(CreateBionicsCompat.operational(3, 100, true, false, false));
        assertFalse(CreateBionicsCompat.operational(3, 100, false, true, false));
        assertTrue(CreateBionicsCompat.operational(3, 1, false, false, false));
        assertFalse(CreateBionicsCompat.operational(3, -1, false, false, false),
                "Forge 1.20.1 treats -1 as unfueled");
        assertTrue(CreateBionicsCompat.operational(3, -1, false, false, true),
                "NeoForge 1.21.1 treats -1 as infinite fuel");
        assertFalse(CreateBionicsCompat.operational(3, -2, false, false, true));
    }

    @Test
    void assembledForgeAndNeoForgeRobotsBypassAnimalTamingRequirement() {
        // The same registered robot is an AbstractHorse declared tamed in Forge
        // and an untamed TamableAnimal in NeoForge with no taming interaction.
        String forgeEntityId = "createbionics:oxhauler";
        String neoForgeEntityId = "createbionics:oxhauler";
        // requireTamedHorse=true: Forge's declared tame=true and NeoForge's
        // never-tamed TamableAnimal must both qualify, with unchanged rules for
        // actual livestock and unrelated robot entities.
        assertTrue(CreateBionicsCompat.meetsTamingPolicy(forgeEntityId, true, true));
        assertTrue(CreateBionicsCompat.meetsTamingPolicy(neoForgeEntityId, true, false));
        assertTrue(CreateBionicsCompat.meetsTamingPolicy(neoForgeEntityId, false, false));
        assertFalse(CreateBionicsCompat.meetsTamingPolicy("minecraft:horse", true, false));
        assertTrue(CreateBionicsCompat.meetsTamingPolicy("minecraft:horse", true, true));
        assertFalse(CreateBionicsCompat.meetsTamingPolicy("createbionics:anole", true, false));
        // The robot still needs fuel/assembly regardless of the exemption.
        assertFalse(CreateBionicsCompat.operational(2, 20, false, false, true));
        assertFalse(CreateBionicsCompat.operational(3, 0, false, false, true));
        assertTrue(CreateBionicsCompat.operational(3, 20, false, false, true));
    }

    @Test
    void onlyLeadsOnOxhaulerMultipartHitboxesAreForwarded() {
        assertTrue(CreateBionicsCompat.shouldRedirectPartLead("createbionics:oxhauler", true, true));
        assertFalse(CreateBionicsCompat.shouldRedirectPartLead("createbionics:oxhauler", false, true));
        assertFalse(CreateBionicsCompat.shouldRedirectPartLead("createbionics:oxhauler", true, false));
        assertFalse(CreateBionicsCompat.shouldRedirectPartLead("minecraft:horse", true, true));
        assertFalse(CreateBionicsCompat.shouldRedirectPartLead("createbionics:anole", true, true));
        assertFalse(CreateBionicsCompat.shouldRedirectPartLead(null, true, true));
    }

    @Test
    void burnsOneFuelOnlyForFiniteNonCreativeWork() {
        assertEquals(99, CreateBionicsCompat.fuelAfterWorkTick(100, false));
        assertEquals(0, CreateBionicsCompat.fuelAfterWorkTick(1, false));
        assertEquals(0, CreateBionicsCompat.fuelAfterWorkTick(0, false));
        assertEquals(-1, CreateBionicsCompat.fuelAfterWorkTick(-1, false));
        assertEquals(100, CreateBionicsCompat.fuelAfterWorkTick(100, true));
    }

    @Test
    void forgeDoesNotAcceptMalformedInfiniteSentinel() {
        ForgeFuel fake = new ForgeFuel(-1);
        assertFalse(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(-1, fake.getFuel());
    }

    @Test
    void neoforgeRecognizesItsInheritedFuelSchemeAndNeverBurnsInfiniteFuel() {
        NeoForgeFuel fake = new NeoForgeFuel(-1);
        assertTrue(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertTrue(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(-1, fake.getFuel());
        assertEquals(0, fake.writes);
    }

    @Test
    void rejectsUnknownBionicsApiInsteadOfGrantingInfiniteFuel() {
        UnknownFuel fake = new UnknownFuel(-1);
        assertFalse(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
    }

    @Test
    void successfulFiniteWritesAreVerifiedAndFuelIsDepleted() {
        ForgeFuel fake = new ForgeFuel(2);
        assertTrue(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertTrue(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(1, fake.getFuel());
        assertTrue(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(0, fake.getFuel());
        assertFalse(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(2, fake.writes);
    }

    @Test
    void creativeForgeCakeDoesNotSpendFuel() {
        ForgeFuel fake = new ForgeFuel(100);
        fake.creativeCake = true;
        assertTrue(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertTrue(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(100, fake.getFuel());
        assertEquals(0, fake.writes);
    }

    @Test
    void throwingSetterLatchesWorkerIneligibleEvenWhenReadsStillWork() {
        ThrowingFuel fake = new ThrowingFuel(100);
        assertTrue(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(100, fake.getFuel());
        assertFalse(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
    }

    @Test
    void silentNoopSetterAlsoLatchesWorkerIneligible() {
        NoOpFuel fake = new NoOpFuel(100);
        assertTrue(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
        assertEquals(100, fake.getFuel());
        assertFalse(CreateBionicsCompat.canUseFuel(fake, false, false));
        assertFalse(CreateBionicsCompat.consumeFuel(fake));
    }

    public static class ForgeFuel {
        protected int fuel;
        public int writes;
        public boolean creativeCake;

        public ForgeFuel(int fuel) {
            this.fuel = fuel;
        }

        public int getFuel() {
            return fuel;
        }

        public void setFuel(int fuel) {
            writes++;
            this.fuel = fuel;
        }

        public int getAssembly() {
            return 3;
        }

        public boolean hasBlazeCake() {
            return creativeCake;
        }
    }

    public static class NeoForgeFuel {
        protected int fuel;
        public int writes;

        public NeoForgeFuel(int fuel) {
            this.fuel = fuel;
        }

        public int getFuel() {
            return fuel;
        }

        public void setFuel(int fuel) {
            writes++;
            this.fuel = fuel;
        }

        public int getAssembly() {
            return 3;
        }

        public boolean isFueled() {
            return fuel > 0 || fuel == -1;
        }
    }

    public static class UnknownFuel {
        private int fuel;

        public UnknownFuel(int fuel) {
            this.fuel = fuel;
        }

        public int getFuel() {
            return fuel;
        }

        public void setFuel(int fuel) {
            this.fuel = fuel;
        }

        public int getAssembly() {
            return 3;
        }
    }

    public static class ThrowingFuel extends ForgeFuel {
        public ThrowingFuel(int fuel) {
            super(fuel);
        }

        @Override
        public void setFuel(int fuel) {
            throw new IllegalStateException("simulated Bionics setter failure");
        }
    }

    public static class NoOpFuel extends ForgeFuel {
        public NoOpFuel(int fuel) {
            super(fuel);
        }

        @Override
        public void setFuel(int fuel) {
            // An updated Bionics implementation silently ignores the call.
        }
    }
}
