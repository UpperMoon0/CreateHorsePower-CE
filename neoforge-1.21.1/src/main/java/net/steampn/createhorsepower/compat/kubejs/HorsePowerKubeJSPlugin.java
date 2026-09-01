package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptType;

public class HorsePowerKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(HorsePowerKubeEvents.GROUP);
    }

    @Override
    public void initStartup() {
        KubeJSProfileRegistry.clear();
        HorsePowerKubeEvents.WORKER_PROFILES.post(ScriptType.STARTUP, new WorkerProfilesKubeEvent());
        HorsePowerKubeEvents.PATH_PROFILES.post(ScriptType.STARTUP, new PathProfilesKubeEvent());
    }
}
