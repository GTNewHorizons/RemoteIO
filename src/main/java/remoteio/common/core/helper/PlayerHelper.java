package remoteio.common.core.helper;

import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

/**
 * @author dmillerw
 */
public class PlayerHelper {

    public static EntityPlayerMP getPlayerForUsername(String username) {
        ServerConfigurationManager serverConfigurationManager = MinecraftServer.getServer().getConfigurationManager();

        Iterator<EntityPlayerMP> iterator = serverConfigurationManager.playerEntityList.iterator();
        EntityPlayerMP entityplayermp;

        do {
            if (!iterator.hasNext()) {
                return null;
            }

            entityplayermp = iterator.next();
        } while (!entityplayermp.getCommandSenderName().equalsIgnoreCase(username));

        return entityplayermp;
    }
}
