package net.steampn.createhorsepower.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.UUID;

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

    /**
     * Construct an unregistered command actor. Public Icy management methods
     * need a server level, identity and position, but no client connection.
     * Logging in GameTestHelper's mock would start unrelated mod handshakes
     * against a connection that has no real client.
     */
    public static ServerPlayer serverPlayer(GameTestHelper helper) {
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "chp-icy-test");
            for (var constructor : ServerPlayer.class.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length < 3 || parameters[0] != MinecraftServer.class
                        || parameters[1] != ServerLevel.class || parameters[2] != GameProfile.class) continue;
                if (parameters.length == 3) {
                    return (ServerPlayer) constructor.newInstance(helper.getLevel().getServer(), helper.getLevel(), profile);
                }
                if (parameters.length == 4) {
                    // 1.21.1 adds ClientInformation; resolve that version-only
                    // type from the real constructor rather than linking it on 1.20.1.
                    Object clientInformation = parameters[3].getMethod("createDefault").invoke(null);
                    return (ServerPlayer) constructor.newInstance(helper.getLevel().getServer(), helper.getLevel(),
                            profile, clientInformation);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create isolated server test actor", e);
        }
        throw new IllegalStateException("No supported ServerPlayer constructor on this Minecraft version");
    }
}
