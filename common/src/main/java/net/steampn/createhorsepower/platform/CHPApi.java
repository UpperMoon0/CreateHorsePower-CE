package net.steampn.createhorsepower.platform;

import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.CHPConstants;

/**
 * Static access point for platform services. Platforms call {@link #init}
 * as the first step of mod construction, before any gameplay logic runs.
 */
public final class CHPApi {
    private static CHPConfig config;
    private static ScriptHooks scripts = ScriptHooks.NOOP;
    private static BiFunction<String, String, ResourceLocation> idFactory;
    private static DeferredDetachStore deferredDetaches;

    private CHPApi() {}

    public static void init(
            CHPConfig configImpl,
            ScriptHooks scriptHooks,
            BiFunction<String, String, ResourceLocation> idFactoryImpl,
            DeferredDetachStore deferredDetachStoreImpl
    ) {
        config = configImpl;
        scripts = scriptHooks == null ? ScriptHooks.NOOP : scriptHooks;
        idFactory = idFactoryImpl;
        deferredDetaches = deferredDetachStoreImpl;
    }

    public static CHPConfig config() {
        if (config == null) {
            throw new IllegalStateException("CHPApi not initialized: platform config missing");
        }
        return config;
    }

    public static ScriptHooks scripts() {
        return scripts;
    }

    public static DeferredDetachStore deferredDetaches() {
        if (deferredDetaches == null) {
            throw new IllegalStateException("CHPApi not initialized: deferred detach store missing");
        }
        return deferredDetaches;
    }

    /** Version-safe ResourceLocation factory (1.20.1 lacks parse/fromNamespaceAndPath parity). */
    public static ResourceLocation id(String namespace, String path) {
        if (idFactory == null) {
            throw new IllegalStateException("CHPApi not initialized: id factory missing");
        }
        return idFactory.apply(namespace, path);
    }

    public static ResourceLocation modId(String path) {
        return id(CHPConstants.MODID, path);
    }

    @Nullable
    public static CHPConfig configOrNull() {
        return config;
    }
}
