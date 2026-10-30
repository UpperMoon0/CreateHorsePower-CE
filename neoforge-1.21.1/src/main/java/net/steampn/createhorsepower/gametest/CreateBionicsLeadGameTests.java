package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.compat.CreateBionicsPartInteractions;

/**
 * Exercises the real 1.21.1 Player.interactOn item lifecycle used by the
 * optional Bionics part router. A vanilla leashable mob serves as the parent:
 * released Bionics classes are intentionally NOT a test/runtime dependency.
 * The part-identity filter has separate unit tests.
 */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class CreateBionicsLeadGameTests {
    private CreateBionicsLeadGameTests() {}

    public static final class DestroyTracker {
        private final Player player;
        private int calls;
        private ItemStack original = ItemStack.EMPTY;
        private InteractionHand hand;

        private DestroyTracker(Player player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onDestroy(PlayerDestroyItemEvent event) {
            if (event.getEntity() != player) return;
            calls++;
            original = event.getOriginal().copy();
            hand = event.getHand();
        }
    }

    @GameTest(template = "empty")
    public static void creativeLeadThroughParentDoesNotConsumeItem(GameTestHelper helper) {
        Horse parent = helper.spawn(EntityType.HORSE, new BlockPos(1, 2, 1));
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        // makeMockPlayer overrides isCreative(), but sets no gamemode abilities.
        creative.getAbilities().instabuild = true;
        creative.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.LEAD, 3));
        DestroyTracker tracker = new DestroyTracker(creative);
        NeoForge.EVENT_BUS.register(tracker);
        try {
            InteractionResult result = CreateBionicsPartInteractions.interactThroughParent(
                    creative, parent, InteractionHand.OFF_HAND);
            helper.assertTrue(result.consumesAction(), "Offhand lead must attach to parent");
            helper.assertTrue(parent.getLeashHolder() == creative,
                    "Part lead routing must attach the parent to the player");
            helper.assertTrue(creative.getOffhandItem().getCount() == 3,
                    "Creative lead count must be restored by Player.interactOn");
            helper.assertTrue(tracker.calls == 0,
                    "Creative lead must not dispatch PlayerDestroyItemEvent");
        } finally {
            NeoForge.EVENT_BUS.unregister(tracker);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalSurvivalLeadFiresDestroyItemEvent(GameTestHelper helper) {
        Horse parent = helper.spawn(EntityType.HORSE, new BlockPos(1, 2, 1));
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.getAbilities().instabuild = false;
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 1));
        DestroyTracker tracker = new DestroyTracker(survival);
        NeoForge.EVENT_BUS.register(tracker);
        try {
            InteractionResult result = CreateBionicsPartInteractions.interactThroughParent(
                    survival, parent, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "Survival lead must attach to parent");
            helper.assertTrue(parent.getLeashHolder() == survival,
                    "Final survival lead must leash the parent");
            helper.assertTrue(survival.getMainHandItem().isEmpty(),
                    "Final survival lead must be consumed");
            helper.assertTrue(tracker.calls == 1,
                    "Final survival lead must fire one PlayerDestroyItemEvent");
            helper.assertTrue(tracker.original.is(Items.LEAD) && tracker.original.getCount() == 1,
                    "Destroy-item hook must receive the original lead stack");
            helper.assertTrue(tracker.hand == InteractionHand.MAIN_HAND,
                    "Destroy-item hook must preserve the consumed hand");
        } finally {
            NeoForge.EVENT_BUS.unregister(tracker);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonFinalSurvivalLeadDoesNotFireDestroyEvent(GameTestHelper helper) {
        Horse parent = helper.spawn(EntityType.HORSE, new BlockPos(1, 2, 1));
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.getAbilities().instabuild = false;
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 3));
        DestroyTracker tracker = new DestroyTracker(survival);
        NeoForge.EVENT_BUS.register(tracker);
        try {
            InteractionResult result = CreateBionicsPartInteractions.interactThroughParent(
                    survival, parent, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "Survival lead must attach to parent");
            helper.assertTrue(parent.getLeashHolder() == survival, "Parent must be leashed");
            helper.assertTrue(survival.getMainHandItem().getCount() == 2,
                    "Normal survival lead must decrement exactly once");
            helper.assertTrue(tracker.calls == 0,
                    "A nonempty stack must not fire PlayerDestroyItemEvent");
        } finally {
            NeoForge.EVENT_BUS.unregister(tracker);
        }
        helper.succeed();
    }
}
