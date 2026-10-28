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
        Object ok = placement.getMethod("teleport", net.minecraft.world.entity.animal.horse.AbstractHorse.class, BlockPos.class)
                .invoke(null, horse, h.absolutePos(new BlockPos(11, 2, 4)));
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

}
