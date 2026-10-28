package net.steampn.createhorsepower.compat;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/** Version adapters used only by the opt-in real-game acceptance scenarios. */
public final class IcyTestActors {
    private IcyTestActors() {}

    public static Player survival(GameTestHelper helper) {
        try {
            try {
                return (Player) GameTestHelper.class.getMethod("makeMockPlayer", GameType.class)
                        .invoke(helper, GameType.SURVIVAL);
            } catch (NoSuchMethodException oldVersion) {
                return (Player) GameTestHelper.class.getMethod("makeMockSurvivalPlayer").invoke(helper);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create isolated test actor", e);
        }
    }

    public static ServerPlayer serverPlayer(GameTestHelper helper) {
        try {
            for (var method : GameTestHelper.class.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == ServerPlayer.class) {
                    return (ServerPlayer) method.invoke(helper);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create isolated server test actor", e);
        }
        throw new IllegalStateException("No server test actor factory on this Minecraft version");
    }
}
