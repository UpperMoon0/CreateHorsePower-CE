package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;

import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class HorsePowerLifecycleGameTests {
    private HorsePowerLifecycleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void durableNoDropDetachSurvivesCrankReplacement(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();
        ServerLevel level = helper.getLevel();

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        horse.setLeashedTo(knot, true);
        engine.setWorkerUuidForTesting(horse.getUUID());
        engine.setCachedWorkerMobForTesting(horse);
        WorkerAttachmentControl.markAttached(horse, crank.getBlockPos(), engine.crankInstanceUuid());
        engine.controlWorkerAiForTesting(horse);

        CompoundTag savedWorker = new CompoundTag();
        horse.saveWithoutId(savedWorker);
        UUID workerUuid = horse.getUUID();
        UUID oldCrankUuid = engine.crankInstanceUuid();

        horse.discard();
        engine.setCachedWorkerMobForTesting(null);
        engine.detachWorker(false);

        var durable = CHPApi.deferredDetaches().get(level, workerUuid);
        helper.assertTrue(durable != null && !durable.dropLead(),
                "detach(false) must persist a no-drop policy at level scope while worker is unloaded");
        helper.assertTrue(durable != null && durable.matches(crank.getBlockPos(), oldCrankUuid),
                "durable detach record must retain crank position and instance UUID");

        level.destroyBlock(crank.getBlockPos(), false);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity replacement = requireCrank(helper, localCrankPos);
        helper.assertFalse(oldCrankUuid.equals(replacement.engine().crankInstanceUuid()),
                "replacement crank must not inherit the old instance UUID");

        Horse reloaded = EntityType.HORSE.create(level);
        helper.assertTrue(reloaded != null, "horse must be creatable for reload regression");
        reloaded.load(savedWorker);
        level.addFreshEntity(reloaded);
        WorkerRecoveryQueue.enqueue(reloaded, level);

        helper.runAfterDelay(3, () -> {
            helper.assertFalse(WorkerAttachmentControl.hasMarker(reloaded),
                    "durable detach recovery must clear the attachment marker");
            helper.assertFalse(WorkerActivityControl.hasMarker(reloaded),
                    "durable detach recovery must clear the CHP activity marker");
            helper.assertFalse(reloaded.isNoAi(),
                    "durable detach recovery must restore CHP-owned NoAI suppression");
            helper.assertTrue(CHPApi.deferredDetaches().get(level, workerUuid) == null,
                    "durable detach policy must be consumed after worker recovery");
            long droppedLeads = level.getEntitiesOfClass(ItemEntity.class,
                            new AABB(reloaded.blockPosition()).inflate(6.0D),
                            item -> item.getItem().is(Items.LEAD))
                    .size();
            helper.assertTrue(droppedLeads == 0,
                    "detach(false) must remain no-drop after old crank destruction/replacement");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recoveryTimeoutRestoresOwnedNoAiWithoutLoadingOldCrank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        BlockPos oldCrankPos = horse.blockPosition().offset(1024, 0, 1024);
        UUID oldCrankUuid = UUID.randomUUID();

        helper.assertFalse(level.hasChunkAt(oldCrankPos), "old crank chunk must begin unloaded");
        boolean changedNoAi = WorkerActivityControl.acquire(horse, oldCrankPos, oldCrankUuid);
        helper.assertTrue(changedNoAi && horse.isNoAi(),
                "fixture must create CHP-owned NoAI false->true state");
        WorkerRecoveryQueue.enqueue(horse, level);

        helper.runAfterDelay(2, () -> {
            WorkerRecoveryQueue.expireForTesting(horse.getUUID());
            WorkerRecoveryQueue.process(level);

            helper.assertFalse(level.hasChunkAt(oldCrankPos),
                    "bounded recovery must not force-load the old crank chunk");
            helper.assertFalse(WorkerActivityControl.hasMarker(horse),
                    "timed-out recovery must clear the CHP activity marker");
            helper.assertFalse(horse.isNoAi(),
                    "timed-out recovery must restore CHP-owned NoAI false->true state");
            helper.assertFalse(WorkerRecoveryQueue.isPendingForTesting(horse.getUUID()),
                    "timed-out recovery must terminate instead of remaining pending forever");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void tfcHorseAndTerrainHaveBuiltinCompatibility(GameTestHelper helper) {
        EntityType<?> tfcHorse = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id("tfc", "horse"));
        helper.assertTrue(tfcHorse != null && WorkerResolver.getBaseStats(tfcHorse).isPresent(),
                "TFC horse must resolve through the built-in optional worker profile");

        Block tfcGround = BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).toString().startsWith("tfc:grass/"))
                .findFirst()
                .orElse(null);
        helper.assertTrue(tfcGround != null, "TFC dev runtime must expose generated grass blocks");
        helper.assertTrue(PathEvaluator.getPathStats(tfcGround).isPresent(),
                "TFC grass must be valid crank footing without manual server config");
        helper.succeed();
    }

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos localPos) {
        BlockEntity be = helper.getBlockEntity(localPos);
        helper.assertTrue(be instanceof AbstractHorseCrankBlockEntity,
                "expected a horse crank block entity at " + localPos);
        return (AbstractHorseCrankBlockEntity) be;
    }
}
