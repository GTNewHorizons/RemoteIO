package remoteio.common.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import remoteio.common.lib.ModInfo;
import remoteio.common.network.packet.PacketClientAllowContainer;
import remoteio.common.network.packet.PacketClientForceSlot;
import remoteio.common.network.packet.PacketServerApplyRFConfig;

/**
 * @author dmillerw
 */
public class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(ModInfo.ID);

    public static void initialize() {
        INSTANCE.registerMessage(PacketClientAllowContainer.class, PacketClientAllowContainer.class, 0, Side.CLIENT);
        INSTANCE.registerMessage(PacketClientForceSlot.class, PacketClientForceSlot.class, 1, Side.CLIENT);
        // Discriminator 2 is retired: the unused remote GUI packet allowed arbitrary block activation.
        INSTANCE.registerMessage(PacketServerApplyRFConfig.class, PacketServerApplyRFConfig.class, 3, Side.SERVER);
    }
}
