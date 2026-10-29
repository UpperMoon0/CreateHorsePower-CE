package net.steampn.createhorsepower.gametest;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.platform.CHPApi;

/**
 * Use ACTUAL optional Bionics 2.5.0 plough and harvester PartEntity hitboxes
 * rather than stand-ins. Player.interactOn fires the NeoForge event used by
 * multiplayer server packets, but this remains a SERVER-SIDE interaction test:
 * separate real client raycast/packet acceptance is still required.
 */
@GameTestHolder("createhorsepower_bionics")
@PrefixGameTestTemplate(false)
public final class BionicsPartGameTests {
    private BionicsPartGameTests() {}

    private static int read(Mob entity, String method) throws Exception {
        return ((Number) entity.getClass().getMethod(method).invoke(entity)).intValue();
    }

    private static boolean state(Mob entity, String method) throws Exception {
        return (boolean) entity.getClass().getMethod(method).invoke(entity);
    }

    private static Mob setup(GameTestHelper h, Player player, boolean plough) throws Exception {
        h.assertTrue(Boolean.getBoolean("createhorsepower.bionicsExpected"), "Required Bionics mode not enabled");
        var loaded = net.neoforged.fml.ModList.get().getModContainerById("createbionics");
        h.assertTrue(loaded.isPresent() && "2.5.0".equals(loaded.get().getModInfo().getVersion().toString()),
                "Must execute with actual released NeoForge Bionics 2.5.0");
        var key = CHPApi.id("createbionics", "oxhauler");
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key), "Oxhauler registry missing");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
        h.assertTrue(type.create(h.getLevel()) instanceof Mob, "Real robot factory did not return Mob");
        Mob mob = (Mob) type.create(h.getLevel());
        mob.moveTo(h.absolutePos(new BlockPos(5, 2, 5)), 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(mob), "Real Bionics robot not registered");
        mob.getClass().getMethod("setAssembly", int.class).invoke(mob, 3);
        mob.getClass().getMethod("setFuel", int.class).invoke(mob, 1000);
        h.assertTrue(read(mob, "getAssembly") == 3 && read(mob, "getFuel") == 1000,
                "Real Bionics assembly/fuel getter mismatch");
        ItemStack tool = new ItemStack(plough ? AllBlocks.MECHANICAL_PLOUGH.get()
                : AllBlocks.MECHANICAL_HARVESTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var interaction = player.interactOn(mob, InteractionHand.MAIN_HAND);
        h.assertTrue(interaction.consumesAction(), "Real Bionics tool install interaction failed");
        h.assertTrue(state(mob, plough ? "isPlough" : "isHarvester"),
                "Real Bionics tool failed to install");
        return mob;
    }

    private static PartEntity<?> part(Mob mob, String field) throws Exception {
        Object value = mob.getClass().getField(field).get(mob);
        if (!(value instanceof PartEntity<?> actual)) {
            throw new AssertionError("Real Bionics " + field + " is not a NeoForge PartEntity");
        }
        if (actual.getParent() != mob || actual.getBbWidth() < 0.1f)
            throw new AssertionError("Bionics tool part parent or pickable dimensions invalid");
        return actual;
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 80)
    public static void survivalLastLeadOnRealPloughPart(GameTestHelper h) throws Exception {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        Mob mob = setup(h, player, true);
        h.runAfterDelay(5, () -> {
            try {
                PartEntity<?> tool = part(mob, "plough");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 1));
                h.assertTrue(player.interactOn(tool, InteractionHand.MAIN_HAND).consumesAction(),
                        "Real plough interaction must consume action");
                h.assertTrue(mob.getLeashHolder() == player, "Plough lead must leash the actual Oxhauler");
                h.assertTrue(player.getMainHandItem().isEmpty(), "Last survival lead must be consumed exactly once");
                h.succeed();
            } catch (Exception e) {
                throw new AssertionError("Real Bionics plough lead acceptance failed", e);
            }
        });
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 80)
    public static void creativeLeadOnRealHarvesterPart(GameTestHelper h) throws Exception {
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        Mob mob = setup(h, player, false);
        h.runAfterDelay(5, () -> {
            try {
                PartEntity<?> tool = part(mob, "harvester");
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.LEAD, 3));
                h.assertTrue(player.interactOn(tool, InteractionHand.OFF_HAND).consumesAction(),
                        "Real harvester interaction must consume action");
                h.assertTrue(mob.getLeashHolder() == player, "Harvester lead must leash the real Oxhauler");
                h.assertTrue(player.getOffhandItem().getCount() == 3,
                        "Creative lead must not be consumed through real tool hitbox");
                h.assertTrue(player.interactOn(tool, InteractionHand.OFF_HAND).consumesAction(),
                        "Second lead click on real harvester must detach");
                h.assertTrue(!mob.isLeashed(), "Second lead click must remove leash");
                h.assertTrue(player.getOffhandItem().getCount() == 3,
                        "Creative lead count must still be unchanged after detach");
                h.succeed();
            } catch (Exception e) {
                throw new AssertionError("Real Bionics harvester lead acceptance failed", e);
            }
        });
    }
}
