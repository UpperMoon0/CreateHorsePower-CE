package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * Isolated, test-only, real-Bionics save and reload across two JVMs.
 * The read process MUST load the same indexed Bionics entity from the world;
 * it never constructs a replacement, or interprets a mocked serialization.
 */
public final class BionicsRestartProbe {
    private static final String PHASE = System.getProperty("createhorsepower.bionicsRestartPhase", "");
    private static final BlockPos CRANK_POS = new BlockPos(2056, 70, 2056);
    private static final Properties EVIDENCE = new Properties();
    private static UUID workerId;
    private static UUID crankId;
    private static double sampleX;
    private static double sampleZ;
    private static int sampleTick;
    private static int sampleFuel;
    private static int startTick;
    private static int ticks;
    private static boolean initialized;
    private static boolean done;

    private BionicsRestartProbe() {}

    public static boolean enabled() { return !PHASE.isEmpty(); }

    private static void need(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static Path directory() {
        return Path.of(System.getProperty("createhorsepower.restart.dir", ""));
    }

    private static int api(Mob entity, String method) throws Exception {
        return ((Number) entity.getClass().getMethod(method).invoke(entity)).intValue();
    }

    private static void set(Mob entity, String method, int value) throws Exception {
        entity.getClass().getMethod(method, int.class).invoke(entity, value);
    }

    private static void force(ServerLevel level) {
        for (int x = (CRANK_POS.getX() >> 4) - 1; x <= (CRANK_POS.getX() >> 4) + 1; x++)
            for (int z = (CRANK_POS.getZ() >> 4) - 1; z <= (CRANK_POS.getZ() >> 4) + 1; z++)
                level.setChunkForced(x, z, true);
        level.getChunkAt(CRANK_POS);
    }

    private static AbstractHorseCrankBlockEntity crank(ServerLevel level) {
        var be = level.getBlockEntity(CRANK_POS);
        need(be instanceof AbstractHorseCrankBlockEntity, "Persisted Horse Crank missing");
        return (AbstractHorseCrankBlockEntity) be;
    }

    private static Mob worker(ServerLevel level) {
        Entity entity = level.getEntity(workerId);
        need(entity instanceof Mob && entity.isAlive(), "Real persisted Oxhauler absent from entity index");
        Mob mob = (Mob) entity;
        need("createbionics:oxhauler".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()),
                "Restored entity has wrong type");
        return mob;
    }

    private static void initialize(ServerLevel level) throws Exception {
        need(Files.isRegularFile(directory().resolve(".chp-isolated-bionics-probe")),
                "Missing isolated Bionics restart sentinel");
        need(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(directory().toAbsolutePath().normalize()),
                "Refusing non-isolated Bionics restart directory");
        need(Boolean.getBoolean("createhorsepower.bionicsExpected"), "Bionics runtime flag missing");
        need(BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id("createbionics", "oxhauler")),
                "Required real Bionics jar not loaded");
        need(PHASE.equals("write") || PHASE.equals("read"), "Unknown Bionics restart phase");
        force(level);
        EVIDENCE.setProperty("phase", PHASE);
        EVIDENCE.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        if (PHASE.equals("write")) {
            need(!Files.exists(directory().resolve("bionics-write.properties")), "Refusing to overwrite saved evidence");
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                BlockPos pos = CRANK_POS.offset(x, 0, z);
                // PathEvaluator scans at crank Y-1, not at the robot's feet.
                // Bedrock here would make path coverage zero, despite a visually
                // present walking ring at the wrong elevation.
                level.setBlock(pos.below(2), Blocks.BEDROCK.defaultBlockState(), 3);
                level.setBlock(pos.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(CRANK_POS, BlockRegister.HORSE_CRANK.get().defaultBlockState(), 3);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id("createbionics", "oxhauler"));
            need(type.create(level) instanceof Mob, "Released Bionics type not a Mob");
            Mob mob = (Mob) type.create(level);
            mob.moveTo(CRANK_POS.getX() + 3.0, CRANK_POS.getY(), CRANK_POS.getZ() + 0.5, 0, 0);
            need(level.addFreshEntity(mob), "Oxhauler failed to join world");
            // NeoForge's pre-join getAssembly() reports a sentinel: set AFTER add.
            set(mob, "setAssembly", 3);
            set(mob, "setFuel", 1800);
            need(api(mob, "getAssembly") == 3 && api(mob, "getFuel") == 1800, "Upstream fuel/assembly API mismatch");
            mob.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, CRANK_POS), false);
            AbstractHorseCrankBlockEntity be = crank(level);
            be.engine().setRedstoneMode(RedstoneMode.IGNORE);
            var stats = WorkerResolver.resolve(mob);
            need(stats.isValid(), "Real Bionics work policy rejected configured worker");
            be.engine().attachWorker(mob, stats);
            need(be.engine().hasValidWorkingBlocks,
                    "Real Bionics restart fixture has an invalid crank path at its scanned height");
            need(be.engine().getInvalidBlockCount() == 0,
                    "Real Bionics restart fixture has unwalkable track blocks");
            workerId = mob.getUUID();
            crankId = be.engine().crankInstanceUuid();
        } else {
            Properties previous = new Properties();
            try (InputStream in = Files.newInputStream(directory().resolve("bionics-write.properties"))) {
                previous.load(in);
            }
            need("PASS".equals(previous.getProperty("status")), "Write phase not complete");
            workerId = UUID.fromString(previous.getProperty("uuid"));
            crankId = UUID.fromString(previous.getProperty("crank"));
            EVIDENCE.setProperty("savedFuel", previous.getProperty("fuel"));
        }
        initialized = true;
        startTick = ticks;
    }

    private static void sample(ServerLevel level) throws Exception {
        Mob mob = worker(level);
        AbstractHorseCrankBlockEntity be = crank(level);
        need(crankId.equals(be.engine().crankInstanceUuid()), "Crank changed identity on JVM restart");
        need(workerId.equals(be.engine().getWorkerUuid()), "Saved assignment changed on JVM restart");
        need(be.engine().isWorkerAttachmentValid(mob), "Saved leash/crank attachment was not recovered");
        need(be.engine().hasValidWorkingBlocks,
                "Persisted Bionics crank path became invalid after world reload");
        need(api(mob, "getAssembly") == 3, "Bionics assembly did not survive restart");
        need(CreateBionicsCompat.canWork(mob), "Restored real robot is ineligible");
        sampleX = mob.getX();
        sampleZ = mob.getZ();
        sampleTick = mob.tickCount;
        sampleFuel = api(mob, "getFuel");
        need(sampleFuel > 0, "Fuel was lost before sampling");
        EVIDENCE.setProperty("initialFuel", Integer.toString(sampleFuel));
    }

    private static void verify(ServerLevel level) throws Exception {
        Mob mob = worker(level);
        AbstractHorseCrankBlockEntity be = crank(level);
        int remaining = api(mob, "getFuel");
        double displacement = Math.hypot(mob.getX() - sampleX, mob.getZ() - sampleZ);
        need(mob.tickCount - sampleTick >= 40, "Restored entity was not actually ticking");
        need(displacement > 0.03, "Restored Oxhauler did not move around real crank");
        need(Math.abs(be.engine().generatedSpeed()) > 0.01f, "Restored crank was not generating speed");
        need(remaining < sampleFuel && sampleFuel - remaining >= 35,
                "Real Bionics fuel was not debited during running crank");
        if (PHASE.equals("read")) {
            int saved = Integer.parseInt(EVIDENCE.getProperty("savedFuel"));
            need(sampleFuel <= saved && sampleFuel > 0, "Bionics fuel was not persisted across JVM boundary");
        }
        EVIDENCE.setProperty("uuid", workerId.toString());
        EVIDENCE.setProperty("crank", crankId.toString());
        EVIDENCE.setProperty("assembly", Integer.toString(api(mob, "getAssembly")));
        EVIDENCE.setProperty("fuel", Integer.toString(remaining));
        EVIDENCE.setProperty("tickDelta", Integer.toString(mob.tickCount - sampleTick));
        EVIDENCE.setProperty("displacement", Double.toString(displacement));
        EVIDENCE.setProperty("rpm", Float.toString(be.engine().generatedSpeed()));
        level.getServer().saveEverything(true, true, true);
    }

    public static void tick(MinecraftServer server) {
        if (!enabled() || done) return;
        try {
            ServerLevel level = server.overworld();
            if (level == null) return;
            if (++ticks > 600) throw new IllegalStateException("Bionics restart phase timed out");
            if (!initialized) initialize(level);
            if (ticks == startTick + 85) sample(level);
            if (ticks == startTick + 150) {
                verify(level);
                finish(server, "PASS", "real Bionics entity, fuel, attachment and generated speed persisted");
            }
        } catch (Throwable e) {
            finish(server, "FAIL", e.toString());
        }
    }

    private static void finish(MinecraftServer server, String status, String message) {
        if (done) return;
        done = true;
        EVIDENCE.setProperty("status", status);
        EVIDENCE.setProperty("message", message);
        try (OutputStream out = Files.newOutputStream(directory().resolve("bionics-" + PHASE + ".properties"))) {
            EVIDENCE.store(out, "Real Bionics JVM restart acceptance");
        } catch (Exception e) {
            System.err.println("CHP_BIONICS_RESTART cannot write evidence: " + e);
        }
        System.out.println("CHP_BIONICS_RESTART " + PHASE + " " + status + " " + message);
        server.halt(false);
    }
}
