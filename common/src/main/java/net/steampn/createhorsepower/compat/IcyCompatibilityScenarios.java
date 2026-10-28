package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.blocks.crank.*;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;

import java.util.UUID;

/** Shared acceptance scenarios; wrappers supply only loader GameTest annotations. */
public final class IcyCompatibilityScenarios {
    private IcyCompatibilityScenarios() {}

    private static boolean present(GameTestHelper h) {
        boolean present = BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        h.assertTrue(present == Boolean.getBoolean("createhorsepower.icyExpected"), "Real Icy runtime must match requested test mode");
        return present;
    }

    private static Horse breed(GameTestHelper h, String path) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id(IcyBetterHorses.NAMESPACE, path));
        h.assertTrue(type != EntityType.PIG, "Icy breed must actually be registered");
        Horse horse = (Horse) type.create(h.getLevel());
        h.assertTrue(horse != null, "Real released breed factory must succeed");
        horse.moveTo(h.absolutePos(new BlockPos(6, 2, 4)), 0, 0);
        horse.setTamed(true);
        h.getLevel().addFreshEntity(horse);
        return horse;
    }

    public static void recognitionAndPolicy(GameTestHelper h) throws Exception {
        if (!present(h)) {
            Horse vanilla = h.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
            h.assertTrue(IcyBetterHorses.canWork(vanilla) && IcyBetterHorses.mayAttach(vanilla, UUID.randomUUID()), "No-mod path must preserve vanilla behavior");
            h.succeed();
            return;
        }
        for (String name : IcyBetterHorses.BREEDS) {
            Horse horse = breed(h, name);
            h.assertTrue(WorkerResolver.resolve(horse).isValid(), "Every released breed must be eligible: " + name);
            h.assertTrue(net.steampn.createhorsepower.content.stats.BuiltinProfiles.worker(horse.getType()).orElseThrow()
                    .equals(net.steampn.createhorsepower.content.stats.BuiltinProfiles.HORSE), "Released breeds must use horse profile");
            h.assertTrue(net.steampn.createhorsepower.content.stats.BuiltinProfiles.workerTier(horse.getType()).orElseThrow()
                    == net.steampn.createhorsepower.content.stats.BuiltinProfiles.WorkerTier.LARGE, "Legacy large tier must apply");
            UUID owner = UUID.randomUUID();
            horse.getClass().getMethod("bh_setOwner", UUID.class).invoke(horse, owner);
            h.assertTrue(IcyBetterHorses.mayAttach(horse, owner), "Owner may attach");
            h.assertTrue(!IcyBetterHorses.mayAttach(horse, UUID.randomUUID()), "Untrusted stranger must be rejected");
            var gear = (net.minecraft.world.SimpleContainer) horse.getClass().getMethod("bh_getGearContainer").invoke(horse);
            var cart = BuiltInRegistries.ITEM.get(CHPApi.id(IcyBetterHorses.NAMESPACE, "horse_cart_gear"));
            gear.setItem(3, new net.minecraft.world.item.ItemStack(cart));
            h.assertTrue(!IcyBetterHorses.canWork(horse), "Real cart equipment must exclude competing movement");
            gear.setItem(3, net.minecraft.world.item.ItemStack.EMPTY);
            h.assertTrue(IcyBetterHorses.canWork(horse), "Removing cart must restore eligibility");
            horse.addTag(IcyBetterHorses.NAMESPACE + ":wagon_hitched");
            h.assertTrue(!IcyBetterHorses.canWork(horse), "Wagon must not share control");
            horse.discard();
        }
        h.succeed();
    }

    private static AbstractHorseCrankBlockEntity crank(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(2.5f)) h.setBlock(pos.offset(offset), Blocks.DIRT_PATH);
        h.setBlock(pos, BlockRegister.HORSE_CRANK.get());
        return (AbstractHorseCrankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    public static void replacementAndSavedWorker(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = breed(h, "arabian_horse");
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crank.engine().crankInstanceUuid());
        CompoundTag body = horse.saveWithoutId(new CompoundTag());
        CompoundTag savedCrank = new CompoundTag();
        crank.engine().write(savedCrank, false);
        UUID id = horse.getUUID();
        var type = horse.getType();
        horse.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getLevel().getEntity(id) == null, "CHP must not recreate an unloaded horse");
            Horse restored = (Horse) type.create(h.getLevel());
            restored.load(body);
            h.getLevel().addFreshEntity(restored);
            crank.engine().read(savedCrank, false);
            h.runAfterDelay(5, () -> {
                h.assertTrue(h.getLevel().getEntity(id) == restored, "Only the real replacement may own the UUID");
                h.assertTrue(crank.engine().isWorkerAttachmentValid(restored), "Saved custom breed must restore its delayed leash");
                crank.detachWorker(true);
                h.assertTrue(!WorkerAttachmentControl.hasMarker(restored) && !WorkerActivityControl.hasMarker(restored), "Detach must clear owned markers");
                h.assertTrue(!restored.isNoAi(), "Original AI baseline must survive body and crank reload");
                h.succeed();
            });
        });
    }

    public static void successfulRecallReleasesControl(GameTestHelper h) throws Exception {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = breed(h, "morgan_horse");
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crank.engine().crankInstanceUuid());
        for (int x = 9; x <= 13; x++) for (int z = 2; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Class<?> placement = Class.forName("icy.betterhorses.net.HorsePlacement");
        var teleport = placement.getMethod("teleport", net.minecraft.world.entity.animal.horse.AbstractHorse.class, BlockPos.class);
        Object failed = teleport.invoke(null, horse, h.absolutePos(new BlockPos(11, 70, 4)));
        h.assertTrue(Boolean.FALSE.equals(failed) && horse.getUUID().equals(crank.engine().getWorkerUuid())
                && WorkerAttachmentControl.hasMarker(horse), "Failed unsafe teleport must keep original attachment");
        Object ok = teleport.invoke(null, horse, h.absolutePos(new BlockPos(11, 2, 4)));
        h.assertTrue(Boolean.TRUE.equals(ok), "Real Icy teleport must find isolated test floor");
        h.assertTrue(crank.engine().getWorkerUuid() == null, "Successful teleport mixin must release crank reservation");
        h.assertTrue(!horse.isLeashed() && !horse.isNoAi() && !WorkerAttachmentControl.hasMarker(horse), "Recall must restore AI and attachment");
        h.succeed();
    }
    public static void snapshotRecallReleasesVirtualWorker(GameTestHelper h) throws Exception {
        snapshotRecall(h, false);
    }

    public static void snapshotRecallReleasesLeashedWorker(GameTestHelper h) throws Exception {
        snapshotRecall(h, true);
    }

    private static void snapshotRecall(GameTestHelper h, boolean leashed) throws Exception {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = breed(h, "arabian_horse");
        UUID owner = UUID.randomUUID();
        horse.getClass().getMethod("bh_setOwner", UUID.class).invoke(horse, owner);
        horse.getClass().getMethod("bh_setBond", int.class).invoke(horse, 2);
        var profile = new net.steampn.createhorsepower.content.attachment.AttachmentProfile(
                CHPApi.modId("icy_test_virtual"), 1, java.util.Set.of(CHPApi.id("minecraft", "lead")),
                java.util.Set.of(), net.steampn.createhorsepower.content.attachment.AttachmentMode.VIRTUAL_TETHER,
                3, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false, true, 1);
        if (leashed) {
            horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
            crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        } else {
            crank.engine().attachWorker(horse, WorkerResolver.resolve(horse), profile, net.minecraft.world.item.ItemStack.EMPTY, false);
        }
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crank.engine().crankInstanceUuid());
        Class<?> tracker = Class.forName("icy.betterhorses.net.HorseTracker");
        tracker.getMethod("register", net.minecraft.world.entity.animal.horse.AbstractHorse.class).invoke(null, horse);
        UUID id = horse.getUUID();
        horse.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        for (int x = 9; x <= 13; x++) for (int z = 2; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(h.getLevel().getEntity(id) == null, "CHP must not invent an unloaded worker");
                Class<?> management = Class.forName("icy.betterhorses.net.HorseManagement");
                var respawn = management.getDeclaredMethod("respawnFromSnapshot", net.minecraft.server.MinecraftServer.class,
                        UUID.class, ServerLevel.class, double.class, double.class, double.class);
                respawn.setAccessible(true);
                BlockPos target = h.absolutePos(new BlockPos(11, 2, 4));
                Horse recalled = (Horse) respawn.invoke(null, h.getLevel().getServer(), id, h.getLevel(),
                        target.getX() + 0.5, (double) target.getY(), target.getZ() + 0.5);
                h.assertTrue(recalled != null && h.getLevel().getEntity(id) == recalled, "Real upstream snapshot path must register its own replacement");
                h.assertTrue(crank.engine().getWorkerUuid() == null, "Snapshot recall must relinquish the old reservation");
                h.assertTrue(!WorkerAttachmentControl.hasMarker(recalled) && !WorkerActivityControl.hasMarker(recalled)
                        && !recalled.isNoAi(), "Recalled virtual worker must retain no stale control or NoAI");
                h.runAfterDelay(3, () -> {
                    h.assertTrue(!recalled.isLeashed() && !WorkerAttachmentControl.hasMarker(recalled)
                            && !WorkerActivityControl.hasMarker(recalled), "Delayed saved leash must not reattach after recall");
                    h.succeed();
                });
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
    }

    public static void vanillaSwapRetainsWorker(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse original = h.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));
        original.setTamed(true);
        original.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(original, WorkerResolver.resolve(original));
        UUID id = original.getUUID();
        h.runAfterDelay(8, () -> {
            var current = h.getLevel().getEntity(id);
            h.assertTrue(current instanceof Horse && current != original, "Real Icy first-tick swap must replace vanilla body");
            Horse replacement = (Horse) current;
            h.assertTrue(IcyBetterHorses.isBreed(BuiltInRegistries.ENTITY_TYPE.getKey(replacement.getType()).toString()), "Replacement must be a real released breed");
            h.assertTrue(id.equals(crank.engine().getWorkerUuid()) && crank.engine().isWorkerAttachmentValid(replacement), "Exact UUID claim and delayed leash must survive real conversion");
            crank.detachWorker(true);
            h.assertTrue(!replacement.isNoAi() && !replacement.isLeashed(), "Conversion release must restore original AI and leash state");
            h.succeed();
        });
    }

    public static void permissionInteractionsAndTrust(GameTestHelper h) throws Exception {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = breed(h, "morgan_horse");
        var owner = IcyTestActors.survival(h);
        var stranger = IcyTestActors.survival(h);
        var trusted = IcyTestActors.survival(h);
        horse.getClass().getMethod("bh_setOwner", UUID.class).invoke(horse, owner.getUUID());
        var lead = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEAD);
        horse.setLeashedTo(stranger, false);
        HorseCrankInteractions.attachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), stranger, lead);
        h.assertTrue(crank.engine().getWorkerUuid() == null && horse.getLeashHolder() == stranger,
                "Unauthorized attach must not claim a horse or transfer its lead");
        Class<?> stateClass = Class.forName("icy.betterhorses.net.HorseTrackerState");
        Object state = stateClass.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, h.getLevel().getServer());
        stateClass.getMethod("trust", UUID.class, UUID.class, String.class).invoke(state, owner.getUUID(), trusted.getUUID(), "test-trusted");
        horse.setLeashedTo(trusted, false);
        HorseCrankInteractions.attachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), trusted, lead);
        h.assertTrue(horse.getUUID().equals(crank.engine().getWorkerUuid()), "Actual trusted-player interaction must attach");
        h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), stranger)
                == HorseCrankInteractions.Outcome.FAIL && horse.getUUID().equals(crank.engine().getWorkerUuid()),
                "Unauthorized detach must leave claim intact");
        h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), trusted)
                == HorseCrankInteractions.Outcome.SUCCESS && crank.engine().getWorkerUuid() == null,
                "Trusted player must be able to release through actual interaction");
        h.succeed();
    }

    public static void tickingOrbitAndWagonHandoff(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = breed(h, "arabian_horse");
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        double x = horse.getX(), z = horse.getZ();
        h.runAfterDelay(10, () -> {
            h.assertTrue(crank.engine().generatedSpeed() != 0 && horse.isNoAi() && WorkerActivityControl.hasMarker(horse),
                    "Real active engine must generate and own AI suppression");
            h.assertTrue(Math.hypot(horse.getX() - x, horse.getZ() - z) > 0.01, "Real worker must orbit across ticks");
            var wagon = h.spawn(EntityType.COW, new BlockPos(8, 2, 4));
            try {
                Class.forName("icy.betterhorses.net.BhWagonHitch").getMethod("mark", net.minecraft.world.entity.Mob.class,
                        net.minecraft.world.entity.Entity.class).invoke(null, horse, wagon);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            // The third-party wagon sets its own NoAI after Icy's mark boundary.
            horse.setNoAi(true);
            h.assertTrue(crank.engine().getWorkerUuid() == null && !WorkerActivityControl.hasMarker(horse)
                    && !WorkerAttachmentControl.hasMarker(horse), "Wagon hook must release old crank before handoff");
            crank.engine().afterHostTick();
            h.assertTrue(horse.isNoAi() && horse.getTags().contains(IcyBetterHorses.NAMESPACE + ":wagon_hitched"),
                    "Old engine must not undo incoming wagon ownership");
            h.succeed();
        });
    }

    public static void activityOnlyDepartureAndForeignRecovery(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse legacy = breed(h, "arabian_horse");
        legacy.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(legacy, WorkerResolver.resolve(legacy));
        WorkerActivityControl.acquire(legacy, crank.getBlockPos(), crank.engine().crankInstanceUuid());
        legacy.getPersistentData().remove(WorkerAttachmentControl.MARKER_KEY);
        IcyHorseDeparture.release(legacy);
        h.assertTrue(!legacy.isNoAi() && !WorkerActivityControl.hasMarker(legacy) && crank.engine().getWorkerUuid() == null,
                "Legacy activity-only command departure must restore baseline immediately");
        Horse foreign = breed(h, "morgan_horse");
        foreign.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(foreign, WorkerResolver.resolve(foreign));
        WorkerActivityControl.acquire(foreign, h.absolutePos(new BlockPos(12, 2, 10)), UUID.randomUUID());
        IcyHorseDeparture.release(foreign);
        h.assertTrue(WorkerActivityControl.hasMarker(foreign) && WorkerRecoveryQueue.isPendingForTesting(foreign.getUUID()),
                "Foreign activity marker must retain bounded recovery scheduling");
        h.runAfterDelay(4, () -> {
            h.assertTrue(!foreign.isNoAi() && !WorkerActivityControl.hasMarker(foreign),
                    "Foreign orphan activity must still recover after departure");
            h.succeed();
        });
    }

}
