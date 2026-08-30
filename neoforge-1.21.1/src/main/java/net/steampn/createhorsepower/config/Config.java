package net.steampn.createhorsepower.config;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.path.PathEvaluationMode;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.platform.CHPConfig;
import net.steampn.createhorsepower.registry.CHPDataMaps;

public class Config implements CHPConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==========================================
    // LEGACY ROOT KEYS (Preserved for 1.1 backward compatibility)
    // ==========================================
    public static final ModConfigSpec.IntValue BASE_CREATURE_RPM;
    public static final ModConfigSpec.IntValue SMALL_CREATURE_STRESS;
    public static final ModConfigSpec.IntValue MEDIUM_CREATURE_STRESS;
    public static final ModConfigSpec.IntValue LARGE_CREATURE_STRESS;
    public static final ModConfigSpec.DoubleValue POOR_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NORMAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GREAT_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> POOR_PATH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NORMAL_PATH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GREAT_PATH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SMALL_CREATURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MEDIUM_CREATURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LARGE_CREATURES;

    // ==========================================
    // BALANCE (1.2+)
    // ==========================================
    public static final ModConfigSpec.DoubleValue GLOBAL_RPM_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GLOBAL_STRESS_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_INDIVIDUAL_ANIMAL_STATS;
    public static final ModConfigSpec.DoubleValue MIN_SPEED_SCALING_CLAMP;
    public static final ModConfigSpec.DoubleValue MAX_SPEED_SCALING_CLAMP;
    public static final ModConfigSpec.DoubleValue MIN_HEALTH_SCALING_CLAMP;
    public static final ModConfigSpec.DoubleValue MAX_HEALTH_SCALING_CLAMP;

    // ==========================================
    // WORKERS (1.2+)
    // ==========================================
    public static final ModConfigSpec.BooleanValue ALLOW_BABIES;
    public static final ModConfigSpec.BooleanValue REQUIRE_TAMED_HORSE;
    public static final ModConfigSpec.BooleanValue ALLOW_UNDEAD_WORKERS;

    // ==========================================
    // PATH (1.2+)
    // ==========================================
    public static final ModConfigSpec.EnumValue<PathEvaluationMode> PATH_EVALUATION_MODE;
    public static final ModConfigSpec.DoubleValue MINIMUM_PATH_COVERAGE;
    public static final ModConfigSpec.IntValue CHECK_INTERVAL_TICKS;

    // ==========================================
    // AUTOMATION (1.2+)
    // ==========================================
    public static final ModConfigSpec.EnumValue<RedstoneMode> DEFAULT_REDSTONE_MODE;

    static {
        // --- 1.1 Legacy root keys ---
        BASE_CREATURE_RPM = BUILDER
                .comment("Base rpm creatures can spin the horse crank (fallback when not defined by Data Maps).")
                .defineInRange("creatureRPMRange", 4, 1, Integer.MAX_VALUE);

        SMALL_CREATURE_STRESS = BUILDER
                .comment("How much stress small creatures can produce for the horse crank (fallback when not defined by Data Maps).")
                .defineInRange("smallCreatureStressRange", 128, 1, Integer.MAX_VALUE);

        MEDIUM_CREATURE_STRESS = BUILDER
                .comment("How much stress medium creatures can produce for the horse crank (fallback when not defined by Data Maps).")
                .defineInRange("mediumCreatureStressRange", 256, 1, Integer.MAX_VALUE);

        LARGE_CREATURE_STRESS = BUILDER
                .comment("How much stress large creatures can produce for the horse crank (fallback when not defined by Data Maps).")
                .defineInRange("largeCreatureStressRange", 512, 1, Integer.MAX_VALUE);

        POOR_MULTIPLIER = BUILDER
                .comment("The multiplier for \"Poor\" paths.")
                .defineInRange("poorMultiplier", 0.5, 0.0, Double.MAX_VALUE);

        NORMAL_MULTIPLIER = BUILDER
                .comment("The multiplier for \"Normal\" paths.")
                .defineInRange("normalMultiplier", 1.0, 0.0, Double.MAX_VALUE);

        GREAT_MULTIPLIER = BUILDER
                .comment("The multiplier for \"Great\" paths.")
                .defineInRange("greatMultiplier", 2.0, 0.0, Double.MAX_VALUE);

        POOR_PATH = BUILDER
                .comment("Types of blocks valid as \"Poor\" quality (legacy fallback list).")
                .defineListAllowEmpty("poorPathBlock", List.of("minecraft:dirt", "minecraft:grass_block"), () -> "", Config::validateBlockName);

        NORMAL_PATH = BUILDER
                .comment("Types of blocks valid as \"Normal\" quality (legacy fallback list).")
                .defineListAllowEmpty("normalPathBlock", List.of("minecraft:dirt_path", "minecraft:gravel"), () -> "", Config::validateBlockName);

        GREAT_PATH = BUILDER
                .comment("Types of blocks valid as \"Great\" quality (legacy fallback list).")
                .defineListAllowEmpty("greatPathBlock", List.of("minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"), () -> "", Config::validateBlockName);

        SMALL_CREATURES = BUILDER
                .comment("Valid \"Small\" creatures (legacy fallback list).")
                .defineListAllowEmpty("smallCreatures", List.of("minecraft:wolf"), () -> "", Config::validateMobName);

        MEDIUM_CREATURES = BUILDER
                .comment("Valid \"Medium\" creatures (legacy fallback list).")
                .defineListAllowEmpty("mediumCreatures", List.of("minecraft:cow"), () -> "", Config::validateMobName);

        LARGE_CREATURES = BUILDER
                .comment("Valid \"Large\" creatures (legacy fallback list).")
                .defineListAllowEmpty("largeCreatures", List.of("minecraft:horse"), () -> "", Config::validateMobName);

        // --- 1.2+ Sections ---
        BUILDER.push("balance");
        GLOBAL_RPM_MULTIPLIER = BUILDER
                .comment("Global multiplier applied to all crank RPM output.")
                .defineInRange("globalRpmMultiplier", 1.0, 0.0, 100.0);
        GLOBAL_STRESS_MULTIPLIER = BUILDER
                .comment("Global multiplier applied to all crank stress capacity.")
                .defineInRange("globalStressMultiplier", 1.0, 0.0, 100.0);
        ENABLE_INDIVIDUAL_ANIMAL_STATS = BUILDER
                .comment("Whether animal attributes (movement speed -> RPM, max health -> Stress) scale power output.")
                .define("enableIndividualAnimalStats", true);
        MIN_SPEED_SCALING_CLAMP = BUILDER
                .comment("Minimum speed scaling multiplier clamp.")
                .defineInRange("minSpeedScalingClamp", 0.5, 0.01, 10.0);
        MAX_SPEED_SCALING_CLAMP = BUILDER
                .comment("Maximum speed scaling multiplier clamp.")
                .defineInRange("maxSpeedScalingClamp", 2.5, 0.1, 100.0);
        MIN_HEALTH_SCALING_CLAMP = BUILDER
                .comment("Minimum health scaling multiplier clamp.")
                .defineInRange("minHealthScalingClamp", 0.5, 0.01, 10.0);
        MAX_HEALTH_SCALING_CLAMP = BUILDER
                .comment("Maximum health scaling multiplier clamp.")
                .defineInRange("maxHealthScalingClamp", 3.0, 0.1, 100.0);
        BUILDER.pop();

        BUILDER.push("workers");
        ALLOW_BABIES = BUILDER
                .comment("Allow baby animals to be attached to the horse crank.")
                .define("allowBabies", false);
        REQUIRE_TAMED_HORSE = BUILDER
                .comment("Require horses/tamable mobs to be tamed before attaching.")
                .define("requireTamedHorse", false);
        ALLOW_UNDEAD_WORKERS = BUILDER
                .comment("Allow undead/skeleton horses and mobs as workers.")
                .define("allowUndeadWorkers", true);
        BUILDER.pop();

        BUILDER.push("path");
        PATH_EVALUATION_MODE = BUILDER
                .comment("Path calculation mode: WEIGHTED_AVERAGE (averages all track blocks), WORST_BLOCK (limited by slowest block), LEGACY (all great = great, any poor = poor).")
                .defineEnum("evaluationMode", PathEvaluationMode.WEIGHTED_AVERAGE);
        MINIMUM_PATH_COVERAGE = BUILDER
                .comment("Required fraction of valid path blocks required (0.0 to 1.0). Default 1.0 means full circle required.")
                .defineInRange("minimumCoverage", 1.0, 0.0, 1.0);
        CHECK_INTERVAL_TICKS = BUILDER
                .comment("Interval in ticks between path condition checks.")
                .defineInRange("checkIntervalTicks", 40, 1, 1200);
        BUILDER.pop();

        BUILDER.push("automation");
        DEFAULT_REDSTONE_MODE = BUILDER
                .comment("Default redstone mode for new Horse Cranks: IGNORE, HIGH_STOPS (redstone signal stops crank), HIGH_RUNS (redstone signal required to run).")
                .defineEnum("defaultRedstoneMode", RedstoneMode.HIGH_STOPS);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateMobName(final Object obj) {
        return obj instanceof final String mobName && BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(mobName));
    }

    private static boolean validateBlockName(final Object obj) {
        return obj instanceof final String blockName && BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockName));
    }

    // ==========================================
    // CHPConfig (loader-agnostic view for common logic)
    // ==========================================
    @Override public int baseCreatureRpm() { return BASE_CREATURE_RPM.getAsInt(); }
    @Override public int smallCreatureStress() { return SMALL_CREATURE_STRESS.getAsInt(); }
    @Override public int mediumCreatureStress() { return MEDIUM_CREATURE_STRESS.getAsInt(); }
    @Override public int largeCreatureStress() { return LARGE_CREATURE_STRESS.getAsInt(); }
    @Override public double poorMultiplier() { return POOR_MULTIPLIER.get(); }
    @Override public double normalMultiplier() { return NORMAL_MULTIPLIER.get(); }
    @Override public double greatMultiplier() { return GREAT_MULTIPLIER.get(); }
    @Override public List<? extends String> poorPath() { return POOR_PATH.get(); }
    @Override public List<? extends String> normalPath() { return NORMAL_PATH.get(); }
    @Override public List<? extends String> greatPath() { return GREAT_PATH.get(); }
    @Override public List<? extends String> smallCreatures() { return SMALL_CREATURES.get(); }
    @Override public List<? extends String> mediumCreatures() { return MEDIUM_CREATURES.get(); }
    @Override public List<? extends String> largeCreatures() { return LARGE_CREATURES.get(); }
    @Override public double globalRpmMultiplier() { return GLOBAL_RPM_MULTIPLIER.get(); }
    @Override public double globalStressMultiplier() { return GLOBAL_STRESS_MULTIPLIER.get(); }
    @Override public boolean enableIndividualAnimalStats() { return ENABLE_INDIVIDUAL_ANIMAL_STATS.get(); }
    @Override public double minSpeedScalingClamp() { return MIN_SPEED_SCALING_CLAMP.get(); }
    @Override public double maxSpeedScalingClamp() { return MAX_SPEED_SCALING_CLAMP.get(); }
    @Override public double minHealthScalingClamp() { return MIN_HEALTH_SCALING_CLAMP.get(); }
    @Override public double maxHealthScalingClamp() { return MAX_HEALTH_SCALING_CLAMP.get(); }
    @Override public boolean allowBabies() { return ALLOW_BABIES.get(); }
    @Override public boolean requireTamedHorse() { return REQUIRE_TAMED_HORSE.get(); }
    @Override public boolean allowUndeadWorkers() { return ALLOW_UNDEAD_WORKERS.get(); }
    @Override public PathEvaluationMode pathEvaluationMode() { return PATH_EVALUATION_MODE.get(); }
    @Override public double minimumPathCoverage() { return MINIMUM_PATH_COVERAGE.get(); }
    @Override public int checkIntervalTicks() { return CHECK_INTERVAL_TICKS.get(); }
    @Override public RedstoneMode defaultRedstoneMode() { return DEFAULT_REDSTONE_MODE.get(); }

    @Override
    public Optional<WorkerStats> lookupWorkerStats(EntityType<?> type) {
        return Optional.ofNullable(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).getData(CHPDataMaps.WORKER_STATS));
    }

    @Override
    public Optional<PathStats> lookupPathStats(Block block) {
        return Optional.ofNullable(BuiltInRegistries.BLOCK.wrapAsHolder(block).getData(CHPDataMaps.PATH_STATS));
    }
}
