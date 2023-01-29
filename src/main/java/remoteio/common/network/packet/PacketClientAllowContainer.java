package remoteio.common.network.packet;

import net.minecraft.client.Minecraft;

import remoteio.common.core.handler.ContainerHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * @author dmillerw
 */
public class PacketClientAllowContainer implements IMessage, IMessageHandler<PacketClientAllowContainer, IMessage> {

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public IMessage onMessage(PacketClientAllowContainer message, MessageContext ctx) {
        ContainerHandler.INSTANCE.containerWhitelist.put(
                Minecraft.getMinecraft().thePlayer.getCommandSenderName(),
                Minecraft.getMinecraft().thePlayer.openContainer);
        return null;
    }
}
