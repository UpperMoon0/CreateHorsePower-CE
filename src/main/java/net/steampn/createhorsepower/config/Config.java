package net.steampn.createhorsepower.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BASE_CREATURE_RPM = BUILDER
            .comment("Base rpm creatures can spin the horse crank.")
            .defineInRange("creatureRPMRange", 4, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SMALL_CREATURE_STRESS = BUILDER
            .comment("How much stress small creatures can produce for the horse crank.")
            .defineInRange("smallCreatureStressRange", 128, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MEDIUM_CREATURE_STRESS = BUILDER
            .comment("How much stress medium creatures can produce for the horse crank.")
            .defineInRange("mediumCreatureStressRange", 256, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue LARGE_CREATURE_STRESS = BUILDER
            .comment("How much stress large creatures can produce for the horse crank.")
            .defineInRange("largeCreatureStressRange", 512, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue POOR_MULTIPLIER = BUILDER
            .comment("The multiplier for \"Poor\" paths")
            .defineInRange("poorMultiplier", 0.5, 0, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue NORMAL_MULTIPLIER = BUILDER
            .comment("The multiplier for \"Normal\" paths")
            .defineInRange("normalMultiplier", 1.0, 0, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue GREAT_MULTIPLIER = BUILDER
            .comment("The multiplier for \"Great\" paths")
            .defineInRange("greatMultiplier", 2.0, 0, Double.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> POOR_PATH = BUILDER
            .comment("Types of blocks valid as \"Poor\" quality")
            .defineListAllowEmpty("poorPathBlock", List.of("minecraft:dirt", "minecraft:grass_block"), () -> "", Config::validateBlockName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> NORMAL_PATH = BUILDER
            .comment("Types of blocks valid as \"Normal\" quality")
            .defineListAllowEmpty("normalPathBlock", List.of("minecraft:dirt_path", "minecraft:gravel"), () -> "", Config::validateBlockName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GREAT_PATH = BUILDER
            .comment("Types of blocks valid as \"Great\" quality")
            .defineListAllowEmpty("greatPathBlock", List.of("minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"), () -> "", Config::validateBlockName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SMALL_CREATURES = BUILDER
            .comment("Valid \"Small\" creatures")
            .defineListAllowEmpty("smallCreatures", List.of("minecraft:wolf"), () -> "", Config::validateMobName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MEDIUM_CREATURES = BUILDER
            .comment("Valid \"Medium\" creatures")
            .defineListAllowEmpty("mediumCreatures", List.of("minecraft:cow"), () -> "", Config::validateMobName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> LARGE_CREATURES = BUILDER
            .comment("Valid \"Large\" creatures")
            .defineListAllowEmpty("largeCreatures", List.of("minecraft:horse"), () -> "", Config::validateMobName);

    public static final ModConfigSpec SPEC = BUILDER.build();


    private static boolean validateMobName(final Object obj) {
        return obj instanceof final String mobName && BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(mobName));
    }

    private static boolean validateBlockName(final Object obj) {
        return obj instanceof final String blockName && BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockName));
    }
}
