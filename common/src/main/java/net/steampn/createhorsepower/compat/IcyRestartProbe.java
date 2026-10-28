package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
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
 * Test-only, opt-in dedicated-server probe. Two JVM launches use the same isolated
 * world; the read phase never creates a worker or reconstructs one from NBT.
 */
public final class IcyRestartProbe {
    private static final String PHASE = System.getProperty("createhorsepower.restart.phase", "");
    private static final BlockPos[] CRANKS = {new BlockPos(8, 70, 8), new BlockPos(40, 70, 8)};
    private static final UUID[] WORKERS = new UUID[2];
    private static final UUID[] IDENTITIES = new UUID[2];
    private static final double[] SAMPLE_X = new double[2];
    private static final double[] SAMPLE_Z = new double[2];
    private static final int[] SAMPLE_TICK = new int[2];
    private static final Properties EVIDENCE = new Properties();
    private static int ticks;
    private static boolean initialized;
    private static boolean sampled;
    private static boolean done;

    private IcyRestartProbe() {}

    public static boolean enabled() {
        return !PHASE.isEmpty();
    }

    public static void tick(MinecraftServer server) {
        if (!enabled() || done) return;
        try {
            if (!PHASE.equals("write") && !PHASE.equals("read")) throw new IllegalStateException("Unknown restart phase " + PHASE);
            ServerLevel level = server.overworld();
            if (level == null) return;
            if (++ticks > 600) throw new IllegalStateException("Restart probe exceeded 600 actual server ticks");
            if (!initialized) initialize(level);
            if (ticks < 60) return;
            if (!sampled) {
                for (int i = 0; i < CRANKS.length; i++) {
                    Mob worker = requireWorker(level, i);
                    AbstractHorseCrankBlockEntity crank = requireCrank(level, i);
                    require(worker.getUUID().equals(crank.engine().getWorkerUuid()), "Loaded crank lost saved worker UUID " + i);
                    require(IDENTITIES[i].equals(crank.engine().crankInstanceUuid()), "Crank instance UUID changed across restart " + i);
                    require(crank.engine().isWorkerAttachmentValid(worker), "Attachment did not recover " + i);
                    require(worker.isNoAi(), "Working worker must have AI suppression " + i);
                    SAMPLE_X[i] = worker.getX();
                    SAMPLE_Z[i] = worker.getZ();
                    SAMPLE_TICK[i] = worker.tickCount;
                }
                sampled = true;
                return;
            }
            if (ticks < 90) return;
            for (int i = 0; i < CRANKS.length; i++) {
                Mob worker = requireWorker(level, i);
                AbstractHorseCrankBlockEntity crank = requireCrank(level, i);
                double dx = worker.getX() - SAMPLE_X[i];
                double dz = worker.getZ() - SAMPLE_Z[i];
                double displacement = Math.sqrt(dx * dx + dz * dz);
                require(worker.tickCount >= SAMPLE_TICK[i] + 20, "Indexed worker is not actually ticking " + i);
                require(displacement > 0.02D, "Worker did not actually orbit after restart " + i);
                require(Math.abs(crank.engine().generatedSpeed()) > 0.01F, "Crank has no live generated output " + i);
                UUID expectedUuid = WORKERS[i];
                long count = level.getEntitiesOfClass(Mob.class, new AABB(CRANKS[i]).inflate(12),
                        candidate -> expectedUuid.equals(candidate.getUUID())).size();
                require(count == 1, "Expected exactly one indexed body for persisted worker " + i + ", found " + count);
                EVIDENCE.setProperty("worker." + i + ".uuid", worker.getUUID().toString());
                EVIDENCE.setProperty("worker." + i + ".type", BuiltInRegistries.ENTITY_TYPE.getKey(worker.getType()).toString());
                EVIDENCE.setProperty("worker." + i + ".crank", crank.engine().crankInstanceUuid().toString());
                EVIDENCE.setProperty("worker." + i + ".displacement", Double.toString(displacement));
                EVIDENCE.setProperty("worker." + i + ".tickDelta", Integer.toString(worker.tickCount - SAMPLE_TICK[i]));
                EVIDENCE.setProperty("worker." + i + ".rpm", Float.toString(crank.engine().generatedSpeed()));
                if (PHASE.equals("read")) {
                    crank.detachWorker(true);
                    require(!worker.isLeashed(), "Detach left a leash after restart " + i);
                    require(!WorkerAttachmentControl.hasMarker(worker) && !WorkerActivityControl.hasMarker(worker), "Detach left ownership markers " + i);
                    require(worker.isNoAi() == (i == 1), "Original NoAI baseline was not restored " + i);
                    EVIDENCE.setProperty("worker." + i + ".restoredNoAi", Boolean.toString(worker.isNoAi()));
                }
            }
            server.saveEverything(true, true, true);
            finish(server, "PASS", "actual indexed worker ticks, orbit and power verified");
        } catch (Throwable failure) {
            finish(server, "FAIL", failure.toString());
        }
    }

    private static void initialize(ServerLevel level) throws Exception {
        require(Files.isRegularFile(directory().resolve(".chp-isolated-restart-probe")), "Missing isolated restart directory sentinel");
        require(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(directory().toAbsolutePath().normalize()),
                "Restart evidence directory must be the dedicated server working directory");
        boolean icy = BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        require(icy == Boolean.getBoolean("createhorsepower.icyExpected"), "Actual Icy dependency presence differs from requested mode");
        EVIDENCE.setProperty("icy", Boolean.toString(icy));
        EVIDENCE.setProperty("phase", PHASE);
        EVIDENCE.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        for (BlockPos pos : CRANKS) {
            // These tickets exist solely in the isolated probe world so workers
            // keep ticking on a playerless dedicated test server.
            for (int x = (pos.getX() >> 4) - 1; x <= (pos.getX() >> 4) + 1; x++) {
                for (int z = (pos.getZ() >> 4) - 1; z <= (pos.getZ() >> 4) + 1; z++) level.setChunkForced(x, z, true);
            }
            level.getChunkAt(pos);
        }
        if (PHASE.equals("read")) {
            Properties saved = new Properties();
            try (InputStream in = Files.newInputStream(directory().resolve("restart-write.properties"))) { saved.load(in); }
            require("PASS".equals(saved.getProperty("status")), "Write phase did not complete");
            require(Boolean.toString(icy).equals(saved.getProperty("icy")), "Restart mod mode differs from saved world");
            for (int i = 0; i < CRANKS.length; i++) {
                WORKERS[i] = UUID.fromString(saved.getProperty("worker." + i + ".uuid"));
                IDENTITIES[i] = UUID.fromString(saved.getProperty("worker." + i + ".crank"));
            }
        } else {
            require(!Files.exists(directory().resolve("restart-write.properties")), "Write probe refuses to overwrite previous evidence");
            for (int i = 0; i < CRANKS.length; i++) {
                BlockPos pos = CRANKS[i];
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                    level.setBlock(pos.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    for (int y = 0; y <= 4; y++) level.setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(pos, BlockRegister.HORSE_CRANK.get().defaultBlockState(), 3);
                AbstractHorseCrankBlockEntity crank = requireCrank(level, i);
                EntityType<?> type = icy ? BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse")) : EntityType.HORSE;
                Horse horse = (Horse) type.create(level);
                require(horse != null, "Released worker factory returned null");
                horse.moveTo(pos.getX() + 3.0D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
                horse.setTamed(true);
                horse.setNoAi(i == 1);
                require(level.addFreshEntity(horse), "Could not register real worker");
                horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, pos), false);
                crank.engine().setRedstoneMode(RedstoneMode.IGNORE);
                crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
                WORKERS[i] = horse.getUUID();
                IDENTITIES[i] = crank.engine().crankInstanceUuid();
            }
        }
        initialized = true;
    }

    private static AbstractHorseCrankBlockEntity requireCrank(ServerLevel level, int i) {
        require(level.getBlockEntity(CRANKS[i]) instanceof AbstractHorseCrankBlockEntity, "Saved crank missing " + i);
        return (AbstractHorseCrankBlockEntity) level.getBlockEntity(CRANKS[i]);
    }

    private static Mob requireWorker(ServerLevel level, int i) {
        Entity entity = level.getEntity(WORKERS[i]);
        require(entity instanceof Mob && entity.isAlive() && !entity.isRemoved(), "Saved indexed worker missing " + i);
        return (Mob) entity;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Path directory() {
        String path = System.getProperty("createhorsepower.restart.dir");
        if (path == null || path.isBlank()) throw new IllegalStateException("Restart probe requires explicit isolated directory");
        return Path.of(path);
    }

    private static void finish(MinecraftServer server, String status, String message) {
        if (done) return;
        done = true;
        EVIDENCE.setProperty("status", status);
        EVIDENCE.setProperty("message", message);
        try {
            Files.createDirectories(directory());
            try (OutputStream out = Files.newOutputStream(directory().resolve("restart-" + PHASE + ".properties"))) {
                EVIDENCE.store(out, "CHP isolated separate-process restart acceptance");
            }
        } catch (Exception e) {
            System.err.println("CHP_RESTART_PROBE could not write evidence: " + e);
        }
        System.out.println("CHP_RESTART_PROBE " + PHASE + " " + status + " " + message);
        server.halt(false);
    }
}
