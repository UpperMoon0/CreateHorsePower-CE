package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;

/**
 * Real Create: Bionics entities in an isolated Minecraft GameTest server.
 * No Bionics classes are linked; missing/mismatched released runtime APIs
 * fail the tests rather than counting as skipped compatibility coverage.
 */
public final class BionicsCompatibilityScenarios {
    private static final BlockPos CRANK = new BlockPos(4, 2, 4);
    private static final BlockPos SPAWN = new BlockPos(7, 2, 4);

    private BionicsCompatibilityScenarios() {}

    private static void required(GameTestHelper h) {
        h.assertTrue(Boolean.getBoolean("createhorsepower.bionicsExpected"),
                "Bionics namespace must only execute in explicitly requested real-mod CI");
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id("createbionics", "oxhauler")),
                "Required Create: Bionics Oxhauler registry entry missing");
    }

    private static int get(Mob mob, String method) {
        try {
            return ((Number) mob.getClass().getMethod(method).invoke(mob)).intValue();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Required released Bionics API missing: " + method, e);
        }
    }

    private static void set(Mob mob, String method, int value) {
        try {
            mob.getClass().getMethod(method, int.class).invoke(mob, value);
            if (get(mob, method.equals("setFuel") ? "getFuel" : "getAssembly") != value)
                throw new AssertionError("Bionics API did not persist " + method + "(" + value + ")");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Required released Bionics API missing: " + method, e);
        }
    }

    private static Mob oxhauler(GameTestHelper h, int assembly, int fuel) {
        required(h);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id("createbionics", "oxhauler"));
        h.assertTrue(type != EntityType.PIG, "Unknown Bionics entity type");
        h.assertTrue(type.create(h.getLevel()) instanceof Mob, "Real Bionics entity is not a Mob");
        Mob mob = (Mob) type.create(h.getLevel());
        h.assertTrue(mob != null, "Cannot create real Oxhauler entity");
        mob.moveTo(h.absolutePos(SPAWN), 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(mob), "Real Oxhauler failed to join world");
        // 1.21.1 AbstractRobot returns a special assembly value until joined.
        set(mob, "setAssembly", assembly);
        set(mob, "setFuel", fuel);
        h.assertTrue(CreateBionicsCompat.canWork(mob) ==
                        (assembly == 3 && (fuel > 0 || (fuel == -1 && neoForge(mob)))),
                "Real Bionics work state contradicts assembly/fuel API");
        return mob;
    }

    private static boolean neoForge(Mob mob) {
        try {
            return mob.getClass().getMethod("isFueled").getReturnType() == boolean.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static AbstractHorseCrankBlockEntity crank(GameTestHelper h) {
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(3.0f)) {
            h.setBlock(CRANK.offset(offset).below(), Blocks.BEDROCK);
            h.setBlock(CRANK.offset(offset), Blocks.COBBLESTONE);
        }
        h.setBlock(CRANK, BlockRegister.HORSE_CRANK.get());
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(CRANK))
                        instanceof AbstractHorseCrankBlockEntity,
                "Real Horse Crank block entity missing");
        return (AbstractHorseCrankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CRANK));
    }

    private static void attach(GameTestHelper h, AbstractHorseCrankBlockEntity crank, Mob mob) {
        var profile = WorkerResolver.resolve(mob);
        h.assertTrue(profile.isValid(), "Real assembled/fueled Bionics worker was rejected");
        mob.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().setRedstoneMode(RedstoneMode.IGNORE);
        crank.engine().attachWorker(mob, profile);
        h.assertTrue(crank.engine().isWorkerAttachmentValid(mob), "Real Oxhauler could not attach");
    }

    public static void realRegistryAndFuelPolicy(GameTestHelper h) {
        Mob robot = oxhauler(h, 3, 500);
        h.assertTrue(WorkerResolver.resolve(robot).isValid(), "Real robot profile not eligible");
        set(robot, "setAssembly", 2);
        h.assertTrue(!WorkerResolver.resolve(robot).isValid(), "Incomplete real robot must be rejected");
        set(robot, "setAssembly", 3);
        set(robot, "setFuel", 0);
        h.assertTrue(!WorkerResolver.resolve(robot).isValid(), "Empty real robot must be rejected");
        set(robot, "setFuel", -1);
        h.assertTrue(WorkerResolver.resolve(robot).isValid() == neoForge(robot),
                "Only NeoForge Bionics may accept creative -1 sentinel");
        robot.discard();
        h.succeed();
    }

    public static void realRiderWaterAndTamingPolicy(GameTestHelper h, Player rider) {
        Mob robot = oxhauler(h, 3, 500);
        h.assertTrue(WorkerResolver.resolve(robot).isValid(),
                "Assembled Oxhauler must work even with configured requireTamedHorse=true");
        h.assertTrue(rider.startRiding(robot, true), "Test player failed to mount real Oxhauler");
        h.assertTrue(robot.isVehicle() && !CreateBionicsCompat.canWork(robot),
                "Ridden real Bionics Oxhauler must be ineligible");
        rider.stopRiding();
        h.assertTrue(CreateBionicsCompat.canWork(robot),
                "Real Oxhauler must become eligible after dismount");
        h.setBlock(SPAWN, Blocks.WATER);
        h.runAfterDelay(8, () -> {
            h.assertTrue(robot.isInWater(), "Real robot did not enter a water block");
            h.assertTrue(!WorkerResolver.resolve(robot).isValid(),
                    "Submerged real Oxhauler must not generate power");
            h.succeed();
        });
    }

    public static void invalidTrackStopsRealFuelBurn(GameTestHelper h) {
        AbstractHorseCrankBlockEntity crank = crank(h);
        Mob robot = oxhauler(h, 3, 800);
        attach(h, crank, robot);
        h.runAfterDelay(40, () -> {
            h.assertTrue(crank.engine().generatedSpeed() != 0, "Real crank must start before track removal");
            for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(3.0f)) {
                h.setBlock(CRANK.offset(offset), Blocks.AIR);
            }
            h.runAfterDelay(100, () -> {
                h.assertTrue(crank.engine().generatedSpeed() == 0,
                        "Invalid path must stop real Bionics crank output");
                int stoppedFuel = get(robot, "getFuel");
                h.runAfterDelay(35, () -> {
                    h.assertTrue(get(robot, "getFuel") == stoppedFuel,
                            "Invalid-track shutdown must not consume Bionics fuel");
                    h.succeed();
                });
            });
        });
    }

    public static void actualOrbitAndFuelDebit(GameTestHelper h) {
        AbstractHorseCrankBlockEntity crank = crank(h);
        Mob robot = oxhauler(h, 3, 2000);
        attach(h, crank, robot);
        final double[] lastAngle = {Double.NaN};
        final double[] signedAngle = {0.0};
        final double[] totalAngle = {0.0};
        final int[] direction = {0};
        final int[] samples = {0};
        final int[] idleSamples = {0};
        final int startFuel = get(robot, "getFuel");
        final int maxSamples = 350; // 1,750 server ticks, bounded by GameTest timeout
        // Wait for TWO measured orbits, not a fixed number of samples.
        // New callbacks are essential: GameTestInfo keys tasks by Runnable identity.
        class Sampler implements Runnable {
            @Override public void run() {
                int sample = ++samples[0];
                double angle = Math.atan2(robot.getZ() - crank.getBlockPos().getZ() - 0.5,
                        robot.getX() - crank.getBlockPos().getX() - 0.5);
                if (!Double.isNaN(lastAngle[0])) {
                    double delta = angle - lastAngle[0];
                    while (delta > Math.PI) delta -= 2.0 * Math.PI;
                    while (delta < -Math.PI) delta += 2.0 * Math.PI;
                    // Allow a brief spin-up, then demand consistent direction
                    // and continued actual movement, rather than just ticking.
                    if (Math.abs(delta) > 0.005) {
                        int currentDirection = delta > 0 ? 1 : -1;
                        if (direction[0] == 0) direction[0] = currentDirection;
                        h.assertTrue(currentDirection == direction[0],
                                "Real Oxhauler reversed its orbit during power generation");
                        idleSamples[0] = 0;
                    } else if (sample > 12) {
                        ++idleSamples[0];
                        h.assertTrue(idleSamples[0] <= 16,
                                "Real Oxhauler stalled before completing two revolutions");
                    }
                    signedAngle[0] += delta;
                    totalAngle[0] += Math.abs(delta);
                }
                lastAngle[0] = angle;
                if (totalAngle[0] >= 4.0 * Math.PI) {
                    int used = startFuel - get(robot, "getFuel");
                    h.assertTrue(direction[0] != 0
                                    && Math.abs(signedAngle[0]) >= 4.0 * Math.PI - 0.01,
                            "Oxhauler did not travel two revolutions consistently");
                    h.assertTrue(used > 100, "Real crank did not debit finite fuel: " + used);
                    h.assertTrue(robot.tickCount > 500, "Real Oxhauler did not receive server ticks");
                    h.assertTrue(Math.abs(crank.engine().generatedSpeed()) > 0.01f,
                            "Real Create crank must generate speed from Bionics worker");
                    h.succeed();
                } else if (sample >= maxSamples) {
                    h.assertTrue(false, "Real Oxhauler did not complete two revolutions within "
                            + (maxSamples * 5) + " ticks: " + totalAngle[0] + " rad");
                } else {
                    h.runAfterDelay(5, () -> new Sampler().run());
                }
            }
        }
        h.runAfterDelay(5, new Sampler());
    }

    public static void stopResumeAndDetach(GameTestHelper h) {
        AbstractHorseCrankBlockEntity crank = crank(h);
        Mob robot = oxhauler(h, 3, 1000);
        attach(h, crank, robot);
        h.runAfterDelay(45, () -> {
            h.assertTrue(crank.engine().generatedSpeed() != 0, "Attached real Oxhauler must generate speed");
            crank.engine().setRedstoneMode(RedstoneMode.HIGH_RUNS);
            h.runAfterDelay(8, () -> {
                int before = get(robot, "getFuel");
                h.assertTrue(crank.engine().generatedSpeed() == 0, "Redstone must stop real robot crank");
                h.runAfterDelay(35, () -> {
                    h.assertTrue(get(robot, "getFuel") == before, "Redstone-stopped crank consumed Bionics fuel");
                    crank.engine().setRedstoneMode(RedstoneMode.IGNORE);
                    h.runAfterDelay(35, () -> {
                        h.assertTrue(crank.engine().generatedSpeed() != 0, "Reenabled crank must resume generation");
                        h.assertTrue(get(robot, "getFuel") < before, "Resumed crank must debit Bionics fuel");
                        crank.detachWorker(false);
                        h.assertTrue(crank.engine().generatedSpeed() == 0, "Detached robot must stop power");
                        h.assertTrue(!crank.engine().isWorkerAttachmentValid(robot),
                                "Detached robot must no longer own this crank");
                        attach(h, crank, robot);
                        h.runAfterDelay(20, () -> {
                            h.assertTrue(crank.engine().generatedSpeed() != 0,
                                    "Reattached real Oxhauler must generate power again");
                            h.succeed();
                        });
                    });
                });
            });
        });
    }
}
