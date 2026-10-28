package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.blocks.crank.*;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.platform.CHPApi;
import java.util.UUID;

/** Actual ticking integration checks; no copied/fake Icy entity classes. */
public final class IcyCleanupScenarios {
    private IcyCleanupScenarios() {}
    private record Fixture(AbstractHorseCrankBlockEntity crank, Horse horse) {}

    private static boolean present(GameTestHelper h) {
        boolean present = BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        h.assertTrue(present == Boolean.getBoolean("createhorsepower.icyExpected"), "Actual Icy release must match test mode");
        return present;
    }

    private static Fixture fixture(GameTestHelper h, boolean originalNoAi) {
        BlockPos pos = new BlockPos(4, 2, 4);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(2.5f)) h.setBlock(pos.offset(offset), Blocks.DIRT_PATH);
        h.setBlock(pos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = (AbstractHorseCrankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        var type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        Horse horse = (Horse) type.create(h.getLevel());
        horse.moveTo(h.absolutePos(new BlockPos(6, 2, 4)), 0, 0);
        horse.setTamed(true);
        horse.setNoAi(originalNoAi);
        h.assertTrue(h.getLevel().addFreshEntity(horse), "Released breed must register");
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        return new Fixture(crank, horse);
    }

    public static void activeCartEquipmentPausesAndResumesAfterTicks(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        Fixture f = fixture(h, false);
        h.runAfterDelay(8, () -> {
            h.assertTrue(f.crank.engine().isWorking() && f.horse.isNoAi(), "Fixture must be active before cart installation");
            try {
                SimpleContainer gear = (SimpleContainer) f.horse.getClass().getMethod("bh_getGearContainer").invoke(f.horse);
                gear.setItem(3, new ItemStack(BuiltInRegistries.ITEM.get(CHPApi.id(IcyBetterHorses.NAMESPACE, "horse_cart_gear"))));
                h.runAfterDelay(4, () -> {
                    h.assertTrue(f.crank.engine().generatedSpeed() == 0 && !f.crank.engine().isWorking()
                            && !WorkerActivityControl.hasMarker(f.horse) && !f.horse.isNoAi(),
                            "Real cart equipment must stop generation and restore CHP's original AI");
                    double stoppedX = f.horse.getX(), stoppedZ = f.horse.getZ();
                    h.runAfterDelay(4, () -> {
                        h.assertTrue(Math.hypot(f.horse.getX() - stoppedX, f.horse.getZ() - stoppedZ) < 0.01,
                                "Cart controller must remain free of crank orbit movement");
                        gear.setItem(3, ItemStack.EMPTY);
                        h.runAfterDelay(70, () -> {
                            h.assertTrue(f.crank.engine().isWorking() && f.crank.engine().generatedSpeed() != 0
                                    && f.horse.isNoAi() && WorkerActivityControl.hasMarker(f.horse),
                                    "Removing cart must resume real engine ticking after profile refresh");
                            h.assertTrue(Math.hypot(f.horse.getX() - stoppedX, f.horse.getZ() - stoppedZ) > 0.01,
                                    "Worker must actually orbit again, not merely retain saved Speed");
                            f.crank.detachWorker(true);
                            h.succeed();
                        });
                    });
                });
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
    }

    public static void crankBreakRestoresActiveAi(GameTestHelper h) { crankBreak(h, false); }
    public static void crankBreakPreservesOriginalNoAi(GameTestHelper h) { crankBreak(h, true); }

    private static void crankBreak(GameTestHelper h, boolean originalNoAi) {
        if (!present(h)) { h.succeed(); return; }
        Fixture f = fixture(h, originalNoAi);
        h.runAfterDelay(8, () -> {
            h.assertTrue(f.crank.engine().isWorking() && WorkerActivityControl.hasMarker(f.horse), "Crank must own active AI before break");
            h.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
            h.runAfterDelay(3, () -> {
                h.assertTrue(f.crank.engine().getWorkerUuid() == null && !f.horse.isLeashed()
                        && !WorkerAttachmentControl.hasMarker(f.horse) && !WorkerActivityControl.hasMarker(f.horse),
                        "Real block removal must clear assignment, attachment and AI markers");
                h.assertTrue(f.horse.isNoAi() == originalNoAi, "Block removal must restore exact original AI baseline");
                h.succeed();
            });
        });
    }

    public static void deadWorkerDoesNotResurrectOrKeepGeneration(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        Fixture f = fixture(h, false);
        h.runAfterDelay(8, () -> {
            h.assertTrue(f.crank.engine().isWorking(), "Fixture must be active before real horse death");
            UUID id = f.horse.getUUID();
            f.horse.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            h.runAfterDelay(50, () -> {
                h.assertTrue(f.crank.engine().getWorkerUuid() == null && f.crank.engine().generatedSpeed() == 0,
                        "Dead worker must release crank reservation and generation");
                h.assertTrue(h.getLevel().getEntity(id) == null, "CHP must not invent a live body after actual death/removal");
                h.succeed();
            });
        });
    }

    public static void duplicateUuidAndForeignClaimCannotTakeControl(GameTestHelper h) {
        if (!present(h)) { h.succeed(); return; }
        Fixture f = fixture(h, false);
        CompoundTag saved = f.horse.saveWithoutId(new CompoundTag());
        Horse duplicate = (Horse) f.horse.getType().create(h.getLevel());
        duplicate.load(saved);
        h.assertTrue(!h.getLevel().addFreshEntity(duplicate), "World must reject duplicate live UUID body");
        h.assertTrue(h.getLevel().getEntity(f.horse.getUUID()) == f.horse, "Original indexed instance must remain authoritative");
        h.setBlock(new BlockPos(11, 2, 4), BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity other = (AbstractHorseCrankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(11, 2, 4)));
        boolean rejected = false;
        try { other.engine().attachWorker(f.horse, WorkerResolver.resolve(f.horse)); }
        catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected && other.engine().getWorkerUuid() == null, "Another crank must reject foreign attachment identity");
        h.runAfterDelay(8, () -> {
            h.assertTrue(f.crank.engine().isWorking() && f.horse.getUUID().equals(f.crank.engine().getWorkerUuid()),
                    "Rejected UUID/claim attempts must leave original real worker ticking");
            f.crank.detachWorker(true);
            h.succeed();
        });
    }

    public static void ownerDisconnectDoesNotOrphanKnotAssignment(GameTestHelper h) throws Exception {
        if (!present(h)) { h.succeed(); return; }
        Fixture f = fixture(h, false);
        var owner = IcyTestActors.survival(h);
        var stranger = IcyTestActors.survival(h);
        f.horse.getClass().getMethod("bh_setOwner", UUID.class).invoke(f.horse, owner.getUUID());
        // Player-owned lead has already been transferred to the crank knot.
        // A mock removal exercises disappearance, not a real network disconnect.
        owner.remove(Entity.RemovalReason.DISCARDED);
        h.runAfterDelay(10, () -> {
            h.assertTrue(f.crank.engine().isWorking() && f.horse.isLeashed()
                    && f.horse.getUUID().equals(f.crank.engine().getWorkerUuid()),
                    "Owner disappearance must preserve independent knot assignment");
            h.assertTrue(HorseCrankInteractions.detachAt(h.getLevel(), f.crank.getBlockPos(), f.crank.getBlockState(), stranger)
                    == HorseCrankInteractions.Outcome.FAIL, "Owner disappearance must not authorize a stranger");
            f.crank.detachWorker(true);
            h.succeed();
        });
    }
}
