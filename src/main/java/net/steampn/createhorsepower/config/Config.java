package net.steampn.createhorsepower.config;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.path.PathEvaluationMode;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==========================================
    // BALANCE
    // ==========================================
    public static final ModConfigSpec.DoubleValue GLOBAL_RPM_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GLOBAL_STRESS_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_INDIVIDUAL_ANIMAL_STATS;
    public static final ModConfigSpec.DoubleValue MIN_SPEED_SCALING_CLAMP;
    public static final ModConfigSpec.DoubleValue MAX_SPEED_SCALING_CLAMP;
    public static final ModConfigSpec.DoubleValue MIN_HEALTH_SCALING_CLAMP;
    public static final ModConfigSpec.DoubleValue MAX_HEALTH_SCALING_CLAMP;

    // Legacy balance settings
    public static final ModConfigSpec.IntValue BASE_CREATURE_RPM;
    public static final ModConfigSpec.IntValue SMALL_CREATURE_STRESS;
    public static final ModConfigSpec.IntValue MEDIUM_CREATURE_STRESS;
    public static final ModConfigSpec.IntValue LARGE_CREATURE_STRESS;
    public static final ModConfigSpec.DoubleValue POOR_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NORMAL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GREAT_MULTIPLIER;

    // ==========================================
    // WORKERS
    // ==========================================
    public static final ModConfigSpec.BooleanValue ALLOW_BABIES;
    public static final ModConfigSpec.BooleanValue REQUIRE_TAMED_HORSE;
    public static final ModConfigSpec.BooleanValue ALLOW_UNDEAD_WORKERS;

    // Legacy worker lists
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SMALL_CREATURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MEDIUM_CREATURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LARGE_CREATURES;

    // ==========================================
    // PATH
    // ==========================================
    public static final ModConfigSpec.EnumValue<PathEvaluationMode> PATH_EVALUATION_MODE;
    public static final ModConfigSpec.DoubleValue MINIMUM_PATH_COVERAGE;
    public static final ModConfigSpec.IntValue CHECK_INTERVAL_TICKS;

    // Legacy path lists
    public static final ModConfigSpec.ConfigValue<List<? extends String>> POOR_PATH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NORMAL_PATH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GREAT_PATH;

    // ==========================================
    // AUTOMATION
    // ==========================================
    public static final ModConfigSpec.EnumValue<RedstoneMode> DEFAULT_REDSTONE_MODE;

    static {
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

        BASE_CREATURE_RPM = BUILDER
                .comment("Fallback base rpm when not defined by Data Maps.")
                .defineInRange("creatureRPMRange", 4, 1, Integer.MAX_VALUE);
        SMALL_CREATURE_STRESS = BUILDER
                .comment("Fallback stress for small creatures when not defined by Data Maps.")
                .defineInRange("smallCreatureStressRange", 128, 1, Integer.MAX_VALUE);
        MEDIUM_CREATURE_STRESS = BUILDER
                .comment("Fallback stress for medium creatures when not defined by Data Maps.")
                .defineInRange("mediumCreatureStressRange", 256, 1, Integer.MAX_VALUE);
        LARGE_CREATURE_STRESS = BUILDER
                .comment("Fallback stress for large creatures when not defined by Data Maps.")
                .defineInRange("largeCreatureStressRange", 512, 1, Integer.MAX_VALUE);
        POOR_MULTIPLIER = BUILDER
                .comment("Fallback multiplier for \"Poor\" paths.")
                .defineInRange("poorMultiplier", 0.5, 0.0, Double.MAX_VALUE);
        NORMAL_MULTIPLIER = BUILDER
                .comment("Fallback multiplier for \"Normal\" paths.")
                .defineInRange("normalMultiplier", 1.0, 0.0, Double.MAX_VALUE);
        GREAT_MULTIPLIER = BUILDER
                .comment("Fallback multiplier for \"Great\" paths.")
                .defineInRange("greatMultiplier", 2.0, 0.0, Double.MAX_VALUE);
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

        SMALL_CREATURES = BUILDER
                .comment("Legacy list of valid \"Small\" creatures.")
                .defineListAllowEmpty("smallCreatures", List.of("minecraft:wolf"), () -> "", Config::validateMobName);
        MEDIUM_CREATURES = BUILDER
                .comment("Legacy list of valid \"Medium\" creatures.")
                .defineListAllowEmpty("mediumCreatures", List.of("minecraft:cow"), () -> "", Config::validateMobName);
        LARGE_CREATURES = BUILDER
                .comment("Legacy list of valid \"Large\" creatures.")
                .defineListAllowEmpty("largeCreatures", List.of("minecraft:horse"), () -> "", Config::validateMobName);
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
                .defineInRange("checkIntervalTicks", 20, 1, 1200);

        POOR_PATH = BUILDER
                .comment("Legacy fallback list of \"Poor\" quality blocks.")
                .defineListAllowEmpty("poorPathBlock", List.of("minecraft:dirt", "minecraft:grass_block"), () -> "", Config::validateBlockName);
        NORMAL_PATH = BUILDER
                .comment("Legacy fallback list of \"Normal\" quality blocks.")
                .defineListAllowEmpty("normalPathBlock", List.of("minecraft:dirt_path", "minecraft:gravel"), () -> "", Config::validateBlockName);
        GREAT_PATH = BUILDER
                .comment("Legacy fallback list of \"Great\" quality blocks.")
                .defineListAllowEmpty("greatPathBlock", List.of("minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"), () -> "", Config::validateBlockName);
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
}
