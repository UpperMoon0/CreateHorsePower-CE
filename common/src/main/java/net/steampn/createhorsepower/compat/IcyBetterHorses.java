package net.steampn.createhorsepower.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/** Optional, server-safe adapter for the released 2.1.7 equine API on both loaders. */
public final class IcyBetterHorses {
    public static final String NAMESPACE = "icys-better-horses";
    public static final Set<String> BREEDS = Set.of(
            "icelandic_horse", "friesian_horse", "haflinger_horse", "percheron_horse",
            "shire_horse", "belgian_horse", "clydesdale_horse", "appaloosa_horse",
            "thoroughbred_horse", "american_paint_horse", "andalusian_horse", "mustang_horse",
            "quarter_horse", "arabian_horse", "morgan_horse");

    private IcyBetterHorses() {}

    public static boolean isBreed(String id) {
        return id != null && id.startsWith(NAMESPACE + ":")
                && BREEDS.contains(id.substring(NAMESPACE.length() + 1));
    }

    private record Api(Class<?> type, Method mayHandle, Method hasCart, Method managed) {}

    // Resolve the public interface rather than injected implementation methods.
    // ClassValue avoids repeated lookup and does not retain a stopped world's entities.
    private static final ClassValue<Api> API = new ClassValue<>() {
        @Override
        protected Api computeValue(Class<?> type) {
            try {
                Class<?> api = Class.forName("icy.betterhorses.net.IHorseData", false, type.getClassLoader());
                if (!api.isAssignableFrom(type)) return new Api(null, null, null, null);
                return new Api(api, api.getMethod("bh_mayHandle", UUID.class), api.getMethod("bh_hasCartGear"),
                        Class.forName("icy.betterhorses.net.BhHorseKind", false, type.getClassLoader())
                                .getMethod("managed", net.minecraft.world.entity.Entity.class));
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
                return new Api(null, null, null, null);
            }
        }
    };

    private static boolean managed(Mob mob, Api api) {
        if (isBreed(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString())) return true;
        if (api.type() == null) return false;
        try {
            return Boolean.TRUE.equals(api.managed().invoke(null, mob));
        } catch (ReflectiveOperationException | LinkageError e) {
            return true; // Known Icy API, but uninspectable policy: fail closed in callers.
        }
    }

    /** Respect Icy's owner/trusted-player policy without changing ownership or taming. */
    public static boolean mayAttach(Mob mob, UUID player) {
        Api api = API.get(mob.getClass());
        if (!managed(mob, api)) return true;
        if (api.type() == null) return false;
        try {
            return Boolean.TRUE.equals(api.mayHandle().invoke(mob, player));
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /** Two movement controllers cannot own a horse at once. Rechecked every crank tick. */
    public static boolean canWork(Mob mob) {
        Api api = API.get(mob.getClass());
        if (!managed(mob, api)) return true;
        if (api.type() == null || mob.isPassenger() || mob.isVehicle()
                || mob.getTags().contains(NAMESPACE + ":wagon_hitched")) return false;
        try {
            return Boolean.FALSE.equals(api.hasCart().invoke(mob));
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }
}
