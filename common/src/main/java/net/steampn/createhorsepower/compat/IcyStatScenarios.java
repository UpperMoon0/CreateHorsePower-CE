package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;

/** Released-Icy stat/offspring acceptance; optional APIs are linked only after the presence check. */
public final class IcyStatScenarios {
    private static final double EPSILON = 0.0001D;

    private IcyStatScenarios() {}

    private static boolean present(GameTestHelper helper) {
        boolean present = BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        helper.assertTrue(present == Boolean.getBoolean("createhorsepower.icyExpected"),
                "Stat acceptance must use the requested real Icy runtime");
        if (!present) helper.succeed();
        return present;
    }

    private static Horse horse(GameTestHelper helper, String breed) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id(IcyBetterHorses.NAMESPACE, breed));
        helper.assertTrue(type != EntityType.PIG, "Released breed must be registered: " + breed);
        Horse horse = (Horse) type.create(helper.getLevel());
        helper.assertTrue(horse != null, "Released breed factory must create a horse");
        horse.setAge(0);
        horse.setTamed(true);
        return horse;
    }

    private static void bond(Horse horse, int value) throws ReflectiveOperationException {
        Class.forName("icy.betterhorses.net.IHorseData").getMethod("bh_setBond", int.class).invoke(horse, value);
    }

    private static void referenceAttributes(Horse horse) {
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.225D);
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(22.0D);
        horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.6D);
        horse.setHealth(horse.getMaxHealth());
    }

    private static void near(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) <= EPSILON,
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static WorkerResolver.ResolvedWorker output(GameTestHelper helper, Horse horse) {
        var output = WorkerResolver.resolve(horse);
        helper.assertTrue(output.isValid(), "Adult released breed must remain a valid worker");
        return output;
    }

    public static void bondThresholdsScaleRpmExactlyOnce(GameTestHelper helper) throws ReflectiveOperationException {
        if (!present(helper)) return;
        // Boundary cases and repeated/reset commands catch rounding, stacked modifiers and a second CHP bond bonus.
        int[] bonds = {0, 19, 20, 20, 39, 40, 59, 60, 79, 80, 99, 100, 100, 0};
        double[] speedFactors = {1, 1, 1.15, 1.15, 1.15, 1.30, 1.30, 1.45, 1.45, 1.60, 1.60, 1.75, 1.75, 1};
        double[] rpmFactors = {1, 1, 1.1125, 1.1125, 1.1125, 1.225, 1.225, 1.3375, 1.3375, 1.45, 1.45, 1.5625, 1.5625, 1};
        for (String breed : IcyBetterHorses.BREEDS) {
            Horse horse = horse(helper, breed);
            referenceAttributes(horse);
            bond(horse, 0);
            var baseline = output(helper, horse);
            near(helper, baseline.baseStats().speedScaling(), 0.75D, "Fixture must use the default horse speed scaling");
            near(helper, baseline.baseStats().healthScaling(), 0.25D, "Fixture must use the default horse health scaling");
            for (int i = 0; i < bonds.length; i++) {
                bond(horse, bonds[i]);
                String context = breed + " bond=" + bonds[i];
                near(helper, horse.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.225D * speedFactors[i],
                        "Actual Icy bond must apply its own speed threshold: " + context);
                near(helper, horse.getAttributeValue(Attributes.JUMP_STRENGTH), 0.6D * speedFactors[i],
                        "Actual Icy bond must retain its jump bonus: " + context);
                var bonded = output(helper, horse);
                near(helper, bonded.effectiveRpm(), baseline.effectiveRpm() * rpmFactors[i],
                        "CHP must count the effective speed bonus exactly once: " + context);
                near(helper, bonded.effectiveStressCapacity(), baseline.effectiveStressCapacity(),
                        "Bond/jump changes must not change stress capacity: " + context);
            }
        }
        helper.succeed();
    }

    public static void inheritedStatsSurviveReloadAndDriveOutput(GameTestHelper helper) throws ReflectiveOperationException {
        if (!present(helper)) return;
        Horse first = horse(helper, "mustang_horse");
        Horse second = horse(helper, "mustang_horse");
        first.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.19D);
        second.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.19D);
        first.getAttribute(Attributes.MAX_HEALTH).setBaseValue(16.0D);
        second.getAttribute(Attributes.MAX_HEALTH).setBaseValue(16.0D);
        // Call the released breed's public offspring factory, not a fabricated saved "genetics" payload.
        Horse weaker = (Horse) first.getBreedOffspring(helper.getLevel(), second);
        first.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31D);
        second.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31D);
        first.getAttribute(Attributes.MAX_HEALTH).setBaseValue(28.0D);
        second.getAttribute(Attributes.MAX_HEALTH).setBaseValue(28.0D);
        Horse stronger = (Horse) first.getBreedOffspring(helper.getLevel(), second);
        helper.assertTrue(weaker != null && stronger != null && stronger.getType() == first.getType(),
                "Real Icy same-breed inheritance must return breed offspring");
        // Grow the offspring for work; natural mating, gender selection and growth timing are outside this scenario.
        weaker.setAge(0);
        stronger.setAge(0);
        weaker.setTamed(true);
        stronger.setTamed(true);
        bond(weaker, 0);
        bond(stronger, 0);
        double inheritedSpeed = stronger.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        double inheritedHealth = stronger.getAttributeBaseValue(Attributes.MAX_HEALTH);
        helper.assertTrue(inheritedSpeed > weaker.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
                        && inheritedHealth > weaker.getAttributeBaseValue(Attributes.MAX_HEALTH),
                "Separated parent ranges must yield distinct inherited attributes despite Icy's random variation");
        var weak = output(helper, weaker);
        var strong = output(helper, stronger);
        helper.assertTrue(strong.effectiveRpm() > weak.effectiveRpm()
                        && strong.effectiveStressCapacity() > weak.effectiveStressCapacity(),
                "CHP must honor individual inherited stats instead of flattening every horse to its breed profile");

        stronger.getAttribute(Attributes.MAX_HEALTH).setBaseValue(weaker.getAttributeBaseValue(Attributes.MAX_HEALTH));
        var healthOnly = output(helper, stronger);
        near(helper, healthOnly.effectiveRpm(), strong.effectiveRpm(), "Health changes must not affect RPM");
        near(helper, healthOnly.effectiveStressCapacity(), weak.effectiveStressCapacity(), "Only max health must drive stress scaling");
        stronger.getAttribute(Attributes.MAX_HEALTH).setBaseValue(inheritedHealth);
        stronger.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(weaker.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        var speedOnly = output(helper, stronger);
        near(helper, speedOnly.effectiveRpm(), weak.effectiveRpm(), "Only speed must drive RPM scaling");
        near(helper, speedOnly.effectiveStressCapacity(), strong.effectiveStressCapacity(), "Speed changes must not affect stress capacity");
        stronger.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(inheritedSpeed);
        stronger.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(1.0D);
        near(helper, output(helper, stronger).effectiveRpm(), strong.effectiveRpm(), "Jump alone must not increase crank RPM");
        near(helper, output(helper, stronger).effectiveStressCapacity(), strong.effectiveStressCapacity(),
                "Jump alone must not increase stress capacity");

        bond(stronger, 100);
        var beforeSave = output(helper, stronger);
        double bondedSpeed = stronger.getAttributeValue(Attributes.MOVEMENT_SPEED);
        CompoundTag saved = stronger.saveWithoutId(new CompoundTag());
        Horse restored = (Horse) stronger.getType().create(helper.getLevel());
        helper.assertTrue(restored != null, "Reload must use the actual released breed factory");
        for (int reload = 0; reload < 2; reload++) {
            restored.load(saved);
            near(helper, restored.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), inheritedSpeed, "Inherited base speed must survive reload");
            near(helper, restored.getAttributeBaseValue(Attributes.MAX_HEALTH), inheritedHealth, "Inherited max health must survive reload");
            near(helper, restored.getAttributeValue(Attributes.MOVEMENT_SPEED), bondedSpeed, "Reload must restore bond once without stacking it");
            var afterSave = output(helper, restored);
            near(helper, afterSave.effectiveRpm(), beforeSave.effectiveRpm(), "Inherited and bonded RPM must survive reload");
            near(helper, afterSave.effectiveStressCapacity(), beforeSave.effectiveStressCapacity(), "Inherited capacity must survive reload");
        }
        helper.succeed();
    }

    public static void workingCrankRefreshesBondAndHealth(GameTestHelper helper) throws ReflectiveOperationException {
        if (!present(helper)) return;
        BlockPos pos = new BlockPos(4, 2, 4);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(2.5F)) helper.setBlock(pos.offset(offset), Blocks.DIRT_PATH);
        helper.setBlock(pos, BlockRegister.HORSE_CRANK.get());
        var crank = (AbstractHorseCrankBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        Horse horse = horse(helper, "arabian_horse");
        referenceAttributes(horse);
        bond(horse, 0);
        horse.moveTo(helper.absolutePos(new BlockPos(6, 2, 4)), 0, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(horse), "Stat-refresh worker must be indexed in the real world");
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, output(helper, horse));
        helper.runAfterDelay(8, () -> {
            float rpm = crank.engine().generatedSpeed();
            double capacity = crank.engine().addedStressCapacity() * Math.abs(rpm);
            helper.assertTrue(crank.engine().isWorking() && rpm != 0 && capacity > 0, "Crank must have live baseline output");
            int ticks = horse.tickCount;
            double x = horse.getX(), z = horse.getZ();
            changeBond(horse, 100);
            helper.runAfterDelay(70, () -> {
                near(helper, crank.engine().generatedSpeed(), rpm * 1.5625D, "Attached crank must refresh Icy bond RPM exactly once");
                near(helper, crank.engine().addedStressCapacity() * Math.abs(crank.engine().generatedSpeed()), capacity,
                        "Bond must preserve total SU even though Create's per-RPM capacity changes");
                helper.assertTrue(horse.tickCount > ticks && Math.hypot(horse.getX() - x, horse.getZ() - z) > 0.01D
                                && horse.getUUID().equals(crank.engine().getWorkerUuid()),
                        "Bond refresh must preserve actual ticking, orbit movement and worker identity");
                horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(44.0D);
                helper.runAfterDelay(70, () -> {
                    near(helper, crank.engine().generatedSpeed(), rpm * 1.5625D, "Health refresh must leave bonded RPM unchanged");
                    near(helper, crank.engine().addedStressCapacity() * Math.abs(crank.engine().generatedSpeed()), capacity * 1.25D,
                            "Attached crank must refresh individual max-health capacity");
                    horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(22.0D);
                    changeBond(horse, 0);
                    helper.runAfterDelay(70, () -> {
                        near(helper, crank.engine().generatedSpeed(), rpm, "Removing bond must restore baseline live RPM");
                        near(helper, crank.engine().addedStressCapacity() * Math.abs(crank.engine().generatedSpeed()), capacity,
                                "Restoring health must restore baseline live capacity");
                        helper.assertTrue(crank.engine().isWorking() && horse.getUUID().equals(crank.engine().getWorkerUuid()),
                                "Stat refresh must not detach the worker");
                        crank.detachWorker(true);
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static void changeBond(Horse horse, int value) {
        try {
            bond(horse, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Released Icy bond API must remain available during live work", e);
        }
    }
}
