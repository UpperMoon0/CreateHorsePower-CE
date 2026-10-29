package net.steampn.createhorsepower.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Method;

/**
 * Optional Create: Bionics Oxhauler contract for Forge 1.20.1 and NeoForge 1.21.1.
 *
 * Both releases expose getFuel(), setFuel(int), and getAssembly(), but have
 * different superclasses and infinite-fuel representations. Never link against
 * Bionics classes: CE must load and work unchanged without Bionics installed.
 */
public final class CreateBionicsCompat {
    public static final String OXHAULER_ID = "createbionics:oxhauler";

    private CreateBionicsCompat() {}

    public static boolean isOxhauler(String entityId) {
        return OXHAULER_ID.equals(entityId);
    }

    private static boolean isOxhauler(Mob mob) {
        return isOxhauler(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
    }

    private record Api(Method fuel, Method setFuel, Method assembly, Method creativeCake) {
        boolean available() {
            return fuel != null && setFuel != null && assembly != null;
        }
    }

    private static final ClassValue<Api> API = new ClassValue<>() {
        @Override
        protected Api computeValue(Class<?> type) {
            try {
                Method fuel = type.getMethod("getFuel");
                Method setFuel = type.getMethod("setFuel", int.class);
                Method assembly = type.getMethod("getAssembly");
                Method creativeCake;
                try {
                    // Forge 1.20.1 stores an independent creative-cake flag.
                    creativeCake = type.getMethod("hasBlazeCake");
                } catch (NoSuchMethodException ignored) {
                    // NeoForge 1.21.1 uses fuel == -1 for infinite fuel.
                    creativeCake = null;
                }
                return new Api(fuel, setFuel, assembly, creativeCake);
            } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
                return new Api(null, null, null, null);
            }
        }
    };

    static boolean operational(int assembly, int fuel, boolean riding, boolean inWater) {
        return assembly >= 3 && (fuel > 0 || fuel == -1) && !riding && !inWater;
    }

    static int fuelAfterWorkTick(int fuel, boolean creativeCake) {
        return fuel > 0 && !creativeCake ? fuel - 1 : fuel;
    }

    /**
     * Called on each crank tick, not just the 60-tick stats refresh.
     * A robot without fuel or full assembly cannot generate power, and a ridden
     * Oxhauler cannot be controlled by the crank simultaneously.
     */
    public static boolean canWork(Mob mob) {
        if (!isOxhauler(mob)) return true;
        Api api = API.get(mob.getClass());
        if (!api.available()) return false;
        try {
            int assembly = ((Number) api.assembly().invoke(mob)).intValue();
            int fuel = ((Number) api.fuel().invoke(mob)).intValue();
            return operational(assembly, fuel, mob.isVehicle() || mob.isPassenger(), mob.isInWater());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false; // Incompatible Bionics version: no free power.
        }
    }

    /**
     * Bionics charges fuel only while ridden. Charge exactly once per active
     * crank tick too; preserve both variants' creative/infinite fuel behavior.
     */
    public static void consumeWorkFuel(Mob mob) {
        if (!isOxhauler(mob)) return;
        Api api = API.get(mob.getClass());
        if (!api.available()) return;
        try {
            int fuel = ((Number) api.fuel().invoke(mob)).intValue();
            boolean creativeCake = api.creativeCake() != null
                    && Boolean.TRUE.equals(api.creativeCake().invoke(mob));
            int next = fuelAfterWorkTick(fuel, creativeCake);
            if (next != fuel) api.setFuel().invoke(mob, next);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // An API mismatch fails eligibility on the next tick if unreadable;
            // never rewrite Bionics data or deplete another entity type.
        }
    }
}
