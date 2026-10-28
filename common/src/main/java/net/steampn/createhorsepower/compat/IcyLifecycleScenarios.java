package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.blocks.crank.*;
import net.steampn.createhorsepower.content.attachment.*;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;
import java.util.Set;
import java.util.UUID;

/** Real-world lifecycle regressions shared by loader annotation wrappers. */
public final class IcyLifecycleScenarios {
    private IcyLifecycleScenarios() {}

    private static Horse horse(GameTestHelper h) {
        var id = CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse");
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id), "Released Icy runtime is required for this scenario");
        Horse horse = (Horse) BuiltInRegistries.ENTITY_TYPE.get(id).create(h.getLevel());
        horse.moveTo(h.absolutePos(new BlockPos(6, 2, 4)), 0, 0);
        horse.setTamed(true);
        h.assertTrue(h.getLevel().addFreshEntity(horse), "Real custom entity must register");
        return horse;
    }

    private static AbstractHorseCrankBlockEntity crank(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(2.5f)) h.setBlock(pos.offset(offset), Blocks.DIRT_PATH);
        h.setBlock(pos, BlockRegister.HORSE_CRANK.get());
        return (AbstractHorseCrankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    public static void unauthorizedDetachCannotHandleOwnedOrUnavailableHorse(GameTestHelper h) throws Exception {
        if (!Boolean.getBoolean("createhorsepower.icyExpected")) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = horse(h);
        var owner = IcyTestActors.survival(h);
        var stranger = IcyTestActors.survival(h);
        h.assertTrue(!owner.getUUID().equals(stranger.getUUID()), "Distinct ownership test actors are required");
        horse.getClass().getMethod("bh_setOwner", UUID.class).invoke(horse, owner.getUUID());
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), stranger)
                == HorseCrankInteractions.Outcome.FAIL, "Untrusted player's real detach path must reject");
        h.assertTrue(horse.getUUID().equals(crank.engine().getWorkerUuid()) && horse.isLeashed(), "Unauthorized detach must not mutate claim or lead");
        Class<?> tracker = Class.forName("icy.betterhorses.net.HorseTrackerState");
        Object trustState = tracker.getMethod("get", net.minecraft.server.MinecraftServer.class)
                .invoke(null, h.getLevel().getServer());
        Object trusted = tracker.getMethod("trust", UUID.class, UUID.class, String.class)
                .invoke(trustState, owner.getUUID(), stranger.getUUID(), "Icy GameTest trusted player");
        h.assertTrue(Boolean.TRUE.equals(trusted), "Real Icy trust store must accept isolated test actor");
        h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), stranger)
                == HorseCrankInteractions.Outcome.SUCCESS, "Icy trusted player's actual detach must succeed");
        tracker.getMethod("untrust", UUID.class, UUID.class).invoke(trustState, owner.getUUID(), stranger.getUUID());
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        CompoundTag saved = horse.saveWithoutId(new CompoundTag());
        var type = horse.getType();
        horse.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAfterDelay(2, () -> {
            h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), owner)
                    == HorseCrankInteractions.Outcome.FAIL, "Unavailable Icy body cannot prove player ownership/trust");
            Horse restored = (Horse) type.create(h.getLevel());
            restored.load(saved);
            h.getLevel().addFreshEntity(restored);
            h.runAfterDelay(3, () -> {
                h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), crank.getBlockPos(), crank.getBlockState(), owner)
                        == HorseCrankInteractions.Outcome.SUCCESS, "Owner may detach the loaded real worker");
                h.assertTrue(crank.engine().getWorkerUuid() == null && !restored.isLeashed(), "Owner release must clear claim and lead");
                h.succeed();
            });
        });
    }

    private static int count(GameTestHelper h, Item item) {
        AABB bounds = new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(0, 0, 0))), net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(15, 8, 15))));
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, bounds, e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    public static void unloadedCachedBodyCannotReturnAttachmentTwice(GameTestHelper h) {
        if (!Boolean.getBoolean("createhorsepower.icyExpected")) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = horse(h);
        var profile = new AttachmentProfile(CHPApi.modId("icy_consuming_leash"), 1,
                Set.of(CHPApi.id("minecraft", "saddle")), Set.of(), AttachmentMode.VANILLA_LEASH,
                3, Set.of(), Set.of(), Set.of(), true, true, 1);
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse), profile, new ItemStack(Items.SADDLE), true);
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crank.engine().crankInstanceUuid());
        CompoundTag saved = horse.saveWithoutId(new CompoundTag());
        var type = horse.getType();
        int saddles = count(h, Items.SADDLE);
        int leads = count(h, Items.LEAD);
        horse.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getLevel().getEntity(horse.getUUID()) == null, "Removed body must no longer be indexed");
            crank.engine().detachWorker(true);
            h.assertTrue(count(h, Items.SADDLE) == saddles && count(h, Items.LEAD) == leads,
                    "System detach must not return items from stale unloaded cache");
            Horse restored = (Horse) type.create(h.getLevel());
            restored.load(saved);
            h.getLevel().addFreshEntity(restored);
            h.runAfterDelay(4, () -> {
                h.assertTrue(count(h, Items.SADDLE) == saddles + 1, "Actual loaded body returns its consumed attachment exactly once");
                h.assertTrue(count(h, Items.LEAD) == leads + 1, "Actual loaded body returns its old exact-knot lead exactly once");
                h.assertTrue(!restored.isLeashed() && !restored.isNoAi() && !WorkerAttachmentControl.hasMarker(restored),
                        "Deferred system detach restores real body's AI and attachment");
                h.succeed();
            });
        });
    }
    public static void noDropTombstoneSurvivesOldCrankReload(GameTestHelper h) {
        noDropTombstone(h, true, false);
    }

    public static void loadedNoDropTombstoneSurvivesDirectLeashCleanup(GameTestHelper h) {
        noDropTombstone(h, false, false);
    }

    public static void noDropTombstoneSurvivesCrankRemoval(GameTestHelper h) {
        noDropTombstone(h, false, true);
    }

    public static void snapshotRecallHonorsSettledNoDrop(GameTestHelper h) throws Exception {
        snapshotRecallDetachPolicy(h, false);
    }

    public static void snapshotRecallPreservesForeignDetachPolicy(GameTestHelper h) throws Exception {
        snapshotRecallDetachPolicy(h, true);
    }

    private static void snapshotRecallDetachPolicy(GameTestHelper h, boolean foreign) throws Exception {
        if (!Boolean.getBoolean("createhorsepower.icyExpected")) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = horse(h);
        horse.getClass().getMethod("bh_setOwner", UUID.class).invoke(horse, UUID.randomUUID());
        horse.getClass().getMethod("bh_setBond", int.class).invoke(horse, 2);
        var profile = new AttachmentProfile(CHPApi.modId("icy_snapshot_disposition"), 1,
                Set.of(CHPApi.id("minecraft", "saddle")), Set.of(), AttachmentMode.VANILLA_LEASH,
                3, Set.of(), Set.of(), Set.of(), true, true, 1);
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse), profile, new ItemStack(Items.SADDLE), true);
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crank.engine().crankInstanceUuid());
        Class.forName("icy.betterhorses.net.HorseTracker").getMethod("register",
                net.minecraft.world.entity.animal.horse.AbstractHorse.class).invoke(null, horse);
        UUID worker = horse.getUUID();
        horse.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(h.getLevel().getEntity(worker) == null, "Original snapshot body must be unavailable");
                crank.engine().detachWorker(false);
                // The original reservation no longer exists, so snapshot recall
                // must settle its marker without using a live source engine.
                h.assertTrue(crank.engine().getWorkerUuid() == null, "Original reservation must be released");
                var policy = new net.steampn.createhorsepower.platform.DeferredDetachStore.Entry(
                        crank.getBlockPos(), foreign ? UUID.randomUUID() : crank.engine().crankInstanceUuid(), false);
                CHPApi.deferredDetaches().put(h.getLevel(), worker, policy);
                int saddles = count(h, Items.SADDLE), leads = count(h, Items.LEAD);
                var respawn = Class.forName("icy.betterhorses.net.HorseManagement").getDeclaredMethod(
                        "respawnFromSnapshot", net.minecraft.server.MinecraftServer.class, UUID.class,
                        net.minecraft.server.level.ServerLevel.class, double.class, double.class, double.class);
                respawn.setAccessible(true);
                BlockPos target = h.absolutePos(new BlockPos(11, 2, 4));
                Horse recalled = (Horse) respawn.invoke(null, h.getLevel().getServer(), worker, h.getLevel(),
                        target.getX() + 0.5, (double) target.getY(), target.getZ() + 0.5);
                h.assertTrue(recalled != null && h.getLevel().getEntity(worker) == recalled,
                        "Actual Icy snapshot method must register the replacement");
                h.runAfterDelay(4, () -> {
                    int returned = foreign ? 1 : 0;
                    h.assertTrue(count(h, Items.SADDLE) == saddles + returned && count(h, Items.LEAD) == leads + returned,
                            "Recall fallback must honor only its exact owner's settled item disposition");
                    h.assertTrue(!recalled.isLeashed() && !recalled.isNoAi()
                            && !WorkerAttachmentControl.hasMarker(recalled) && !WorkerActivityControl.hasMarker(recalled),
                            "Recall must clear saved attachment/AI even when no items are returned");
                    h.assertTrue(foreign ? policy.equals(CHPApi.deferredDetaches().get(h.getLevel(), worker))
                                    : CHPApi.deferredDetaches().get(h.getLevel(), worker) == null,
                            "Only the matching settled policy may be consumed");
                    if (foreign) CHPApi.deferredDetaches().remove(h.getLevel(), worker);
                    h.succeed();
                });
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
    }

    private static void noDropTombstone(GameTestHelper h, boolean unload, boolean breakCrank) {
        if (!Boolean.getBoolean("createhorsepower.icyExpected")) { h.succeed(); return; }
        AbstractHorseCrankBlockEntity crank = crank(h);
        Horse horse = horse(h);
        var profile = new AttachmentProfile(CHPApi.modId("icy_no_drop_tombstone"), 1,
                Set.of(CHPApi.id("minecraft", "saddle")), Set.of(), AttachmentMode.VANILLA_LEASH,
                3, Set.of(), Set.of(), Set.of(), true, true, 1);
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse), profile, new ItemStack(Items.SADDLE), true);
        h.runAfterDelay(8, () -> {
            h.assertTrue(crank.engine().isWorking() && horse.isNoAi(), "Real engine must own active body before stale-source test");
            CompoundTag body = horse.saveWithoutId(new CompoundTag());
            CompoundTag oldCrank = new CompoundTag();
            crank.engine().write(oldCrank, false);
            int saddles = count(h, Items.SADDLE), leads = count(h, Items.LEAD);
            var type = horse.getType();
            UUID worker = horse.getUUID();
            if (unload) horse.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            CHPApi.deferredDetaches().put(h.getLevel(), worker,
                    new net.steampn.createhorsepower.platform.DeferredDetachStore.Entry(
                            crank.getBlockPos(), crank.engine().crankInstanceUuid(), false));
            h.runAfterDelay(2, () -> {
                if (!unload && !breakCrank) {
                    net.steampn.createhorsepower.utils.CHPUtils.cleanUpLeash(h.getLevel(), crank.getBlockPos(), worker, true);
                    var retained = CHPApi.deferredDetaches().get(h.getLevel(), worker);
                    h.assertTrue(retained != null && !retained.dropLead(),
                            "Physical-only direct cleanup must retain no-drop policy for profile item cleanup");
                }
                crank.engine().read(oldCrank, false);
                if (breakCrank) h.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
                else crank.engine().detachWorker(true);
                h.assertTrue(count(h, Items.SADDLE) == saddles && count(h, Items.LEAD) == leads,
                        "Old crank detach/break request must not upgrade settled no-drop attachment");
                if (unload) {
                    var preserved = CHPApi.deferredDetaches().get(h.getLevel(), worker);
                    h.assertTrue(preserved != null && !preserved.dropLead(), "Old BE reload/detach must preserve source tombstone");
                    Horse restored = (Horse) type.create(h.getLevel());
                    restored.load(body);
                    h.getLevel().addFreshEntity(restored);
                    h.runAfterDelay(4, () -> {
                        h.assertTrue(count(h, Items.SADDLE) == saddles && count(h, Items.LEAD) == leads,
                                "Late stale body must not duplicate an attachment already returned by recall");
                        h.assertTrue(!restored.isNoAi() && !restored.isLeashed()
                                && !WorkerAttachmentControl.hasMarker(restored) && !WorkerActivityControl.hasMarker(restored),
                                "No-drop recovery must still clear real stale-body AI and leash");
                        h.succeed();
                    });
                } else {
                    h.assertTrue(!horse.isNoAi() && !horse.isLeashed()
                            && !WorkerAttachmentControl.hasMarker(horse) && !WorkerActivityControl.hasMarker(horse),
                            "Loaded no-drop cleanup must restore AI and clear exact attachment");
                    h.succeed();
                }
            });
        });
    }

}
