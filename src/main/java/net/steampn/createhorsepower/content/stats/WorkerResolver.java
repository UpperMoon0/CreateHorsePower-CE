package net.steampn.createhorsepower.content.stats;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.registry.CHPDataMaps;
import net.steampn.createhorsepower.utils.CHPTags;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class WorkerResolver {

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
        float rpm = (float) Config.BASE_CREATURE_RPM.getAsInt();
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
        Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type);
        WorkerStats stats = holder.getData(CHPDataMaps.WORKER_STATS);
        if (stats != null) {
            return Optional.of(stats);
        }

        // Fallback to legacy worker tags with live config values
        if (holder.is(CHPTags.Entities.WORKERS_LARGE) || holder.is(CHPTags.Entities.LARGE_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(Config.LARGE_CREATURE_STRESS.getAsInt()));
        }
        if (holder.is(CHPTags.Entities.WORKERS_MEDIUM) || holder.is(CHPTags.Entities.MEDIUM_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(Config.MEDIUM_CREATURE_STRESS.getAsInt()));
        }
        if (holder.is(CHPTags.Entities.WORKERS_SMALL) || holder.is(CHPTags.Entities.SMALL_WORKER_TAG)) {
            return Optional.of(createLegacyProfile(Config.SMALL_CREATURE_STRESS.getAsInt()));
        }

        // Fallback to legacy config lists
        String entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (Config.LARGE_CREATURES.get().contains(entityKey)) {
            return Optional.of(createLegacyProfile(Config.LARGE_CREATURE_STRESS.getAsInt()));
        }
        if (Config.MEDIUM_CREATURES.get().contains(entityKey)) {
            return Optional.of(createLegacyProfile(Config.MEDIUM_CREATURE_STRESS.getAsInt()));
        }
        if (Config.SMALL_CREATURES.get().contains(entityKey)) {
            return Optional.of(createLegacyProfile(Config.SMALL_CREATURE_STRESS.getAsInt()));
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
        if (mob.isBaby() && !stats.allowBaby() && !Config.ALLOW_BABIES.get()) {
            return ResolvedWorker.INVALID;
        }

        // Check tamed constraint
        if ((stats.requiresTamed() || Config.REQUIRE_TAMED_HORSE.get())) {
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

        if (Config.ENABLE_INDIVIDUAL_ANIMAL_STATS.get()) {
            // Speed scaling -> RPM
            if (stats.speedScaling() > 0.0f && mob.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)) {
                double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double baseSpeedRef = stats.speedReference();
                double ratio = speed / baseSpeedRef;
                double clamped = Mth.clamp(ratio, Config.MIN_SPEED_SCALING_CLAMP.get(), Config.MAX_SPEED_SCALING_CLAMP.get());
                speedBonus = (float) ((clamped - 1.0) * stats.speedScaling());
                baseRpm *= (1.0f + speedBonus);
            }

            // Health scaling -> Stress
            if (stats.healthScaling() > 0.0f && mob.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) {
                double maxHealth = mob.getAttributeValue(Attributes.MAX_HEALTH);
                double baseHealthRef = stats.healthReference();
                double ratio = maxHealth / baseHealthRef;
                double clamped = Mth.clamp(ratio, Config.MIN_HEALTH_SCALING_CLAMP.get(), Config.MAX_HEALTH_SCALING_CLAMP.get());
                healthBonus = (float) ((clamped - 1.0) * stats.healthScaling());
                baseStress *= (1.0f + healthBonus);
            }
        }

        baseRpm *= Config.GLOBAL_RPM_MULTIPLIER.get().floatValue();
        baseStress *= Config.GLOBAL_STRESS_MULTIPLIER.get().floatValue();

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
