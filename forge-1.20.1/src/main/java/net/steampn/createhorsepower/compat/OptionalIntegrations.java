package net.steampn.createhorsepower.compat;

import net.minecraftforge.fml.ModList;
import net.steampn.createhorsepower.platform.ScriptHooks;

/**
 * Forge script-integration facade. KubeJS event glue for 1.20.1 is not ported yet;
 * the no-op hooks keep shared engine logic working without it.
 */
public final class OptionalIntegrations implements ScriptHooks {
    public static final OptionalIntegrations INSTANCE = new OptionalIntegrations();

    private static final boolean KUBE_JS_LOADED = isKubeJsPresent();

    private static boolean isKubeJsPresent() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private OptionalIntegrations() {}
}
