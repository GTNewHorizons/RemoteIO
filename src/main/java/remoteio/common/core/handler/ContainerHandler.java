package remoteio.common.core.handler;

import java.util.Map;

import net.minecraft.inventory.Container;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;

import com.google.common.collect.Maps;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * @author dmillerw
 */
public class ContainerHandler {

    public static final ContainerHandler INSTANCE = new ContainerHandler();

    public Map<String, Container> containerWhitelist = Maps.newHashMap();

    @SubscribeEvent
    public void onContainerOpen(PlayerOpenContainerEvent event) {
        if (event.entityPlayer.openContainer != null
                && event.entityPlayer.openContainer != event.entityPlayer.inventoryContainer) {
            Container whitelisted = containerWhitelist.get(event.entityPlayer.getCommandSenderName());
            if (whitelisted != null && whitelisted == event.entityPlayer.openContainer)
                event.setResult(Event.Result.ALLOW);
        }
    }
}
