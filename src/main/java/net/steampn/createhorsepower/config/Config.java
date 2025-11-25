package net.steampn.createhorsepower.config;

import static net.steampn.createhorsepower.CreateHorsePower.MODID;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue BASE_CREATURE_RPM = BUILDER
            .comment("Base rpm creatures can spin the horse crank.")
            .defineInRange("creatureRPMRange", 4, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue SMALL_CREATURE_STRESS = BUILDER
            .comment("How much stress small creatures can produce for the horse crank.")
            .defineInRange("smallCreatureStressRange", 128, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue MEDIUM_CREATURE_STRESS = BUILDER
            .comment("How much stress medium creatures can produce for the horse crank.")
            .defineInRange("mediumCreatureStressRange", 256, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue LARGE_CREATURE_STRESS = BUILDER
            .comment("How much stress large creatures can produce for the horse crank.")
            .defineInRange("largeCreatureStressRange", 512, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue POOR_MULTIPLIER = BUILDER
            .comment("The multiplier for \"Poor\" paths")
            .defineInRange("poorMultiplier", 0.5, 0, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue NORMAL_MULTIPLIER = BUILDER
            .comment("The multiplier for \"Normal\" paths")
            .defineInRange("normalMultiplier", 1.0, 0, Double.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue GREAT_MULTIPLIER = BUILDER
            .comment("The multiplier for \"Great\" paths")
            .defineInRange("greatMultiplier", 2.0, 0, Double.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> POOR_PATH = BUILDER
            .comment("Types of blocks valid as \"Poor\" quality")
            .defineListAllowEmpty("poorPathBlock", List.of("minecraft:dirt", "minecraft:grass_block"), Config::validateBlockName);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NORMAL_PATH = BUILDER
            .comment("Types of blocks valid as \"Normal\" quality")
            .defineListAllowEmpty("normalPathBlock", List.of("minecraft:dirt_path", "minecraft:gravel"), Config::validateBlockName);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GREAT_PATH = BUILDER
            .comment("Types of blocks valid as \"Great\" quality")
            .defineListAllowEmpty("greatPathBlock", List.of("minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"), Config::validateBlockName);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SMALL_CREATURES = BUILDER
            .comment("Valid \"Small\" creatures")
            .defineListAllowEmpty("smallCreatures", List.of("minecraft:wolf"), Config::validateMobName);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MEDIUM_CREATURES = BUILDER
            .comment("Valid \"Medium\" creatures")
            .defineListAllowEmpty("mediumCreatures", List.of("minecraft:cow"), Config::validateMobName);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LARGE_CREATURES = BUILDER
            .comment("Valid \"Large\" creatures")
            .defineListAllowEmpty("largeCreatures", List.of("minecraft:horse"), Config::validateMobName);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean validateMobName(final Object obj){
        return obj instanceof final String mobName && ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.parse(mobName));
    }

    private static boolean validateBlockName(final Object obj){
        return obj instanceof final String blockName && ForgeRegistries.BLOCKS.containsKey(ResourceLocation.parse(blockName));
    }
}
