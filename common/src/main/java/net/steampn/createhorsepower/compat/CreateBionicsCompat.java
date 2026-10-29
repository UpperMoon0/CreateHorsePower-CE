package net.steampn.createhorsepower.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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

    private record Api(Method fuel, Method setFuel, Method assembly, Method creativeCake,
                       boolean neoForgeInfiniteFuel) {
        boolean available() {
            // Refuse unknown API variants rather than infer infinite fuel from a missing method.
            return fuel != null && setFuel != null && assembly != null
                    && (creativeCake != null || neoForgeInfiniteFuel);
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
                    creativeCake = null;
                }

                // NeoForge 1.21.1 inherits isFueled() from AbstractRobot and
                // uses fuel == -1. Forge 1.20.1 does neither.
                boolean neoForgeInfiniteFuel = false;
                if (creativeCake == null) {
                    try {
                        neoForgeInfiniteFuel = type.getMethod("isFueled").getReturnType() == boolean.class;
                    } catch (NoSuchMethodException ignored) {
                        // Unknown API, not an infinite-fuel grant.
                    }
                }
                return new Api(fuel, setFuel, assembly, creativeCake, neoForgeInfiniteFuel);
            } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
                return new Api(null, null, null, null, false);
            }
        }
    };

    // Per-entity, fail-closed latch. A setter which throws or silently ignores a
    // write must never allow a readable (but unchanged) fuel value to power ticks.
    // Weak keys avoid retaining unloaded/discarded entities indefinitely.
    private static final Map<Object, Boolean> FAILED_FUEL_WRITES =
            Collections.synchronizedMap(new WeakHashMap<>());

    static boolean operational(int assembly, int fuel, boolean riding, boolean inWater,
                               boolean neoForgeInfiniteFuel) {
        return assembly >= 3 && (fuel > 0 || (neoForgeInfiniteFuel && fuel == -1))
                && !riding && !inWater;
    }

    static int fuelAfterWorkTick(int fuel, boolean creativeCake) {
        return fuel > 0 && !creativeCake ? fuel - 1 : fuel;
    }

    /** Shared by the live worker gate and reflection-backed regression fixtures. */
    static boolean canUseFuel(Object entity, boolean riding, boolean inWater) {
        if (FAILED_FUEL_WRITES.containsKey(entity)) return false;
        Api api = API.get(entity.getClass());
        if (!api.available()) return false;
        try {
            int assembly = ((Number) api.assembly().invoke(entity)).intValue();
            int fuel = ((Number) api.fuel().invoke(entity)).intValue();
            return operational(assembly, fuel, riding, inWater, api.neoForgeInfiniteFuel());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Called on each crank tick, not just the 60-tick stats refresh.
     * A robot without fuel or full assembly cannot generate power, and a ridden
     * Oxhauler cannot be controlled by the crank simultaneously.
     */
    public static boolean canWork(Mob mob) {
        return !isOxhauler(mob) || canUseFuel(mob, mob.isVehicle() || mob.isPassenger(), mob.isInWater());
    }

    /**
     * Consume a finite unit and confirm that Bionics actually stored the new
     * value. A failed/no-op write latches the entity ineligible for work.
     */
    static boolean consumeFuel(Object entity) {
        if (FAILED_FUEL_WRITES.containsKey(entity)) return false;
        Api api = API.get(entity.getClass());
        if (!api.available()) return false;
        try {
            int fuel = ((Number) api.fuel().invoke(entity)).intValue();
            if (fuel <= 0 && !(api.neoForgeInfiniteFuel() && fuel == -1)) return false;

            boolean creativeCake = api.creativeCake() != null
                    && Boolean.TRUE.equals(api.creativeCake().invoke(entity));
            int next = fuelAfterWorkTick(fuel, creativeCake);
            if (next == fuel) return true;

            api.setFuel().invoke(entity, next);
            if (((Number) api.fuel().invoke(entity)).intValue() != next) {
                FAILED_FUEL_WRITES.put(entity, true);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            FAILED_FUEL_WRITES.put(entity, true);
            return false;
        }
    }

    /**
     * Bionics charges fuel only while ridden. Charge once per active crank tick;
     * return false immediately on any inability to pay so the engine can stop
     * generation and refresh Create's kinetic network in the same tick.
     */
    public static boolean consumeWorkFuel(Mob mob) {
        return !isOxhauler(mob) || consumeFuel(mob);
    }
}
