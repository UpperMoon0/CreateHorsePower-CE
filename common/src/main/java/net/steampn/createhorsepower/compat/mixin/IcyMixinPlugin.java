package net.steampn.createhorsepower.compat.mixin;

import org.objectweb.asm.tree.ClassNode;
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
 * Missing optional classes are skipped; present targets must expose the supported methods.
 */
public final class IcyMixinPlugin implements IMixinConfigPlugin {
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
            // ModLauncher's provider supports transformed ClassNodes only.
            // This reads bytes through the transformer pipeline; it does not
            // define the target Class or execute Icy static initialization.
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            Set<String> actual = target.methods.stream().map(method -> method.name)
                    .collect(java.util.stream.Collectors.toSet());
            if (actual.containsAll(required)) return true;
            throw new IllegalStateException("Unsupported Icy target " + targetClassName
                    + ": required 2.1.7 methods " + required + " are missing for " + mixinClassName);
        } catch (ClassNotFoundException absent) {
            // Absence is expected for this optional integration.
            return false;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect optional Icy target " + targetClassName, failure);
        }
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
