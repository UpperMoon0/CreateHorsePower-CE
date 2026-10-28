package net.steampn.createhorsepower.compat.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inspect optional targets as bytecode, never by loading Icy classes at bootstrap.
 * Loader metadata enforces the exact supported 2.1.7 version when Icy is present.
 * This guard also keeps missing methods from masking the loader's version error.
 */
public final class IcyMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("CreateHorsePower/IcyCompat");
    private static final Map<String, Set<String>> TARGET_METHODS = Map.of(
            "icy.betterhorses.net.HorsePlacement", Set.of("teleport"),
            "icy.betterhorses.net.HorseManagement", Set.of("respawnFromSnapshot", "summonToPlayer", "revive"),
            "icy.betterhorses.net.BhWagonHitch", Set.of("mark"));

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Set<String> required = TARGET_METHODS.get(targetClassName);
        if (required == null) return false;
        try {
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName, false);
            Set<String> actual = target.methods.stream().map(method -> method.name)
                    .collect(java.util.stream.Collectors.toSet());
            if (actual.containsAll(required)) return true;
            LOGGER.warn("Skipping {}: optional Icy target {} lacks supported 2.1.7 methods {}. "
                    + "Only Icy 2.1.7 is supported; see the loader dependency diagnostic.",
                    mixinClassName, targetClassName, required);
        } catch (ClassNotFoundException | IOException e) {
            // No Icy dependency is loaded or initialized when the mod is absent.
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
