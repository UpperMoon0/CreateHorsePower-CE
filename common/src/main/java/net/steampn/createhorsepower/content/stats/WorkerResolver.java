package net.steampn.createhorsepower.content.stats;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.tags.EntityTypeTags;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import net.steampn.createhorsepower.platform.CHPApi;
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

    private static WorkerStats createLegacyProfile(float stressCapacity) {
        float rpm = (float) CHPApi.config().baseCreatureRpm();
        return new WorkerStats(
                rpm,
                stressCapacity,
                2.5f,
                0.5f,
                WorkerStats.DEFAULT_SPEED_REF,
                0.2f,
                WorkerStats.DEFAULT_HEALTH_REF,
                false,
                false
        );
    }

    public static Optional<WorkerStats> getBaseStats(EntityType<?> type) {
        Optional<WorkerStats> kjsStats = KubeJSProfileRegistry.getWorker(type);
        if (kjsStats.isPresent()) {
            return kjsStats;
        }

        Optional<WorkerStats> platformStats = CHPApi.config().lookupWorkerStats(type);
        if (platformStats.isPresent()) {
            return platformStats;
        }

        Optional<WorkerStats> builtinStats = BuiltinProfiles.worker(type);
        if (builtinStats.isPresent()) {
            return builtinStats;
        }

        // Fallback to legacy worker tags with live config values
        if (type.is(CHPTags.Entities.WORKERS_LARGE) || type.is(CHPTags.Entities.LARGE_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(CHPApi.config().largeCreatureStress()));
        }
        if (type.is(CHPTags.Entities.WORKERS_MEDIUM) || type.is(CHPTags.Entities.MEDIUM_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(CHPApi.config().mediumCreatureStress()));
        }
        if (type.is(CHPTags.Entities.WORKERS_SMALL) || type.is(CHPTags.Entities.SMALL_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(CHPApi.config().smallCreatureStress()));
        }

        // Fallback to legacy config lists
        String entityKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (CHPApi.config().largeCreatures().contains(entityKey)) {
            return Optional.of(createLegacyProfile(CHPApi.config().largeCreatureStress()));
        }
        if (CHPApi.config().mediumCreatures().contains(entityKey)) {
            return Optional.of(createLegacyProfile(CHPApi.config().mediumCreatureStress()));
        }
        if (CHPApi.config().smallCreatures().contains(entityKey)) {
            return Optional.of(createLegacyProfile(CHPApi.config().smallCreatureStress()));
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
