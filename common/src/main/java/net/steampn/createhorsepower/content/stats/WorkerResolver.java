package net.steampn.createhorsepower.content.stats;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.platform.CHPConfig;
import net.steampn.createhorsepower.utils.CHPTags;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class WorkerResolver {

    // 1.20.1 EntityTypeTags has no UNDEAD constant; reference the vanilla tag id directly.
    private static net.minecraft.tags.TagKey<EntityType<?>> undeadTag() {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                CHPApi.id("minecraft", "undead"));
    }

    public record ResolvedWorker(
            WorkerStats baseStats,
            float effectiveRpm,
            float effectiveStressCapacity,
            float speedBonusPercent,
            float healthBonusPercent,
            boolean isValid
    ) {
        public static final ResolvedWorker INVALID = new ResolvedWorker(WorkerStats.DEFAULT, 0.0f, 0.0f, 0.0f, 0.0f, false);
    }

    private static Optional<BuiltinProfiles.WorkerTier> legacyTier(EntityType<?> type) {
        CHPConfig config = CHPApi.config();
        String entityKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();

        // Explicit legacy lists are packmaker intent and therefore outrank CE's
        // built-in tags/default classification. This matters for packs such as
        // TFG, which deliberately classifies TFC donkey/pig/sheep differently.
        if (config.smallCreatures().contains(entityKey)) {
            return Optional.of(BuiltinProfiles.WorkerTier.SMALL);
        }
        if (config.mediumCreatures().contains(entityKey)) {
            return Optional.of(BuiltinProfiles.WorkerTier.MEDIUM);
        }
        if (config.largeCreatures().contains(entityKey)) {
            return Optional.of(BuiltinProfiles.WorkerTier.LARGE);
        }

        if (type.is(CHPTags.Entities.WORKERS_SMALL) || type.is(CHPTags.Entities.SMALL_WORKER_TAG)) {
            return Optional.of(BuiltinProfiles.WorkerTier.SMALL);
        }
        if (type.is(CHPTags.Entities.WORKERS_MEDIUM) || type.is(CHPTags.Entities.MEDIUM_WORKER_TAG)) {
            return Optional.of(BuiltinProfiles.WorkerTier.MEDIUM);
        }
        if (type.is(CHPTags.Entities.WORKERS_LARGE) || type.is(CHPTags.Entities.LARGE_WORKER_TAG)) {
            return Optional.of(BuiltinProfiles.WorkerTier.LARGE);
        }

        return BuiltinProfiles.workerTier(type);
    }

    private static WorkerStats applyLegacyOutputOverrides(EntityType<?> type, WorkerStats profile) {
        Optional<BuiltinProfiles.WorkerTier> tier = legacyTier(type);
        if (tier.isEmpty()) {
            return profile;
        }
        return createLegacyAwareProfile(profile, tier.get());
    }

    private static WorkerStats createLegacyAwareProfile(WorkerStats profile, BuiltinProfiles.WorkerTier tier) {
        CHPConfig config = CHPApi.config();
        return LegacyOutputBalance.apply(
                profile,
                tier,
                config.baseCreatureRpm(),
                config.smallCreatureStress(),
                config.mediumCreatureStress(),
                config.largeCreatureStress()
        );
    }

    private static WorkerStats createLegacyProfile(BuiltinProfiles.WorkerTier tier) {
        CHPConfig config = CHPApi.config();
        return LegacyOutputBalance.legacyProfile(
                tier,
                config.baseCreatureRpm(),
                config.smallCreatureStress(),
                config.mediumCreatureStress(),
                config.largeCreatureStress()
        );
    }

    public static Optional<WorkerStats> getBaseStats(EntityType<?> type) {
        Optional<WorkerStats> kjsStats = KubeJSProfileRegistry.getWorker(type);
        if (kjsStats.isPresent()) {
            return kjsStats;
        }

        // Platform lookup is reserved for explicit pack-provided overrides
        // (NeoForge Data Maps). Bundled CE defaults are resolved below so the
        // legacy server balance knobs can still govern migrated packs.
        Optional<WorkerStats> platformStats = CHPApi.config().lookupWorkerStats(type);
        if (platformStats.isPresent()) {
            return platformStats;
        }

        Optional<WorkerStats> builtinStats = BuiltinProfiles.worker(type);
        if (builtinStats.isPresent()) {
            return Optional.of(applyLegacyOutputOverrides(type, builtinStats.get()));
        }

        // Explicit legacy lists also outrank generic CE tags, allowing a pack to
        // deliberately reclassify a tagged worker without needing a Data Map.
        String entityKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (CHPApi.config().smallCreatures().contains(entityKey)) {
            return Optional.of(createLegacyProfile(BuiltinProfiles.WorkerTier.SMALL));
        }
        if (CHPApi.config().mediumCreatures().contains(entityKey)) {
            return Optional.of(createLegacyProfile(BuiltinProfiles.WorkerTier.MEDIUM));
        }
        if (CHPApi.config().largeCreatures().contains(entityKey)) {
            return Optional.of(createLegacyProfile(BuiltinProfiles.WorkerTier.LARGE));
        }

        // Fallback to worker tags with the same legacy-aware output semantics.
        if (type.is(CHPTags.Entities.WORKERS_SMALL) || type.is(CHPTags.Entities.SMALL_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(BuiltinProfiles.WorkerTier.SMALL));
        }
        if (type.is(CHPTags.Entities.WORKERS_MEDIUM) || type.is(CHPTags.Entities.MEDIUM_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(BuiltinProfiles.WorkerTier.MEDIUM));
        }
        if (type.is(CHPTags.Entities.WORKERS_LARGE) || type.is(CHPTags.Entities.LARGE_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(BuiltinProfiles.WorkerTier.LARGE));
        }

        return Optional.empty();
    }

    public static ResolvedWorker resolve(@Nullable Mob mob) {
        if (mob == null || !mob.isAlive()) {
            return ResolvedWorker.INVALID;
        }

        Optional<WorkerStats> baseOpt = getBaseStats(mob.getType());
        if (baseOpt.isEmpty()) {
            return ResolvedWorker.INVALID;
        }

        WorkerStats stats = baseOpt.get();

        // Check baby constraint
        if (mob.isBaby() && !stats.allowBaby() && !CHPApi.config().allowBabies()) {
            return ResolvedWorker.INVALID;
        }

        // Check undead constraint
        if (!CHPApi.config().allowUndeadWorkers()) {
            if (mob.isInvertedHealAndHarm() || mob.getType().is(undeadTag())) {
                return ResolvedWorker.INVALID;
            }
        }

        // Check tamed constraint
        if ((stats.requiresTamed() || CHPApi.config().requireTamedHorse())) {
            if (mob instanceof AbstractHorse horse && !horse.isTamed()) {
                return ResolvedWorker.INVALID;
            }
            if (mob instanceof TamableAnimal tamable && !tamable.isTame()) {
                return ResolvedWorker.INVALID;
            }
        }

        float baseRpm = stats.baseRpm();
        float baseStress = stats.stressCapacity();

        float speedBonus = 0.0f;
        float healthBonus = 0.0f;

        if (CHPApi.config().enableIndividualAnimalStats()) {
            // Speed scaling -> RPM
            if (stats.speedScaling() > 0.0f && mob.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)) {
                double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double baseSpeedRef = stats.speedReference();
                double ratio = speed / baseSpeedRef;
                double clamped = Mth.clamp(ratio, CHPApi.config().minSpeedScalingClamp(), CHPApi.config().maxSpeedScalingClamp());
                speedBonus = (float) ((clamped - 1.0) * stats.speedScaling());
                baseRpm *= (1.0f + speedBonus);
            }

            // Health scaling -> Stress
            if (stats.healthScaling() > 0.0f && mob.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) {
                double maxHealth = mob.getAttributeValue(Attributes.MAX_HEALTH);
                double baseHealthRef = stats.healthReference();
                double ratio = maxHealth / baseHealthRef;
                double clamped = Mth.clamp(ratio, CHPApi.config().minHealthScalingClamp(), CHPApi.config().maxHealthScalingClamp());
                healthBonus = (float) ((clamped - 1.0) * stats.healthScaling());
                baseStress *= (1.0f + healthBonus);
            }
        }

        baseRpm *= (float) CHPApi.config().globalRpmMultiplier();
        baseStress *= (float) CHPApi.config().globalStressMultiplier();

        return new ResolvedWorker(
                stats,
                baseRpm,
                baseStress,
                speedBonus * 100.0f,
                healthBonus * 100.0f,
                true
        );
    }
}
