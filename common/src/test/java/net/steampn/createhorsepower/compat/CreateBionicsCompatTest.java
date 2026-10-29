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
    void requiresFullAssemblyAndFuelAndFreeMovementControl() {
        assertFalse(CreateBionicsCompat.operational(2, 100, false, false));
        assertFalse(CreateBionicsCompat.operational(3, 0, false, false));
        assertFalse(CreateBionicsCompat.operational(3, 100, true, false));
        assertFalse(CreateBionicsCompat.operational(3, 100, false, true));
        assertTrue(CreateBionicsCompat.operational(3, 1, false, false));
        assertTrue(CreateBionicsCompat.operational(3, -1, false, false));
    }

    @Test
    void burnsOneFuelOnlyForFiniteNonCreativeWork() {
        assertEquals(99, CreateBionicsCompat.fuelAfterWorkTick(100, false));
        assertEquals(0, CreateBionicsCompat.fuelAfterWorkTick(1, false));
        assertEquals(0, CreateBionicsCompat.fuelAfterWorkTick(0, false));
        assertEquals(-1, CreateBionicsCompat.fuelAfterWorkTick(-1, false));
        assertEquals(100, CreateBionicsCompat.fuelAfterWorkTick(100, true));
    }
}
