package remoteio.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import remoteio.client.documentation.Documentation;
import remoteio.client.handler.SoundHandler;
import remoteio.client.handler.TooltipEventHandler;
import remoteio.client.render.RenderBlockRemoteInterface;
import remoteio.client.render.RenderTileIntelligentWorkbench;
import remoteio.client.render.RenderTileMachine;
import remoteio.client.render.RenderTileRemoteInterface;
import remoteio.client.render.RenderTileRemoteInventory;
import remoteio.client.render.RenderTileTransceiver;
import remoteio.common.CommonProxy;
import remoteio.common.RemoteIO;
import remoteio.common.core.helper.EventHelper;
import remoteio.common.network.ClientProxyPlayer;
import remoteio.common.tile.TileIntelligentWorkbench;
import remoteio.common.tile.TileMachineHeater;
import remoteio.common.tile.TileMachineReservoir;
import remoteio.common.tile.TileRemoteInterface;
import remoteio.common.tile.TileRemoteInventory;
import remoteio.common.tile.TileTransceiver;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * @author dmillerw
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        RenderingRegistry.registerBlockHandler(new RenderBlockRemoteInterface());

        ClientRegistry.bindTileEntitySpecialRenderer(TileRemoteInterface.class, new RenderTileRemoteInterface());
        ClientRegistry.bindTileEntitySpecialRenderer(TileRemoteInventory.class, new RenderTileRemoteInventory());
        ClientRegistry.bindTileEntitySpecialRenderer(TileMachineReservoir.class, new RenderTileMachine());
        ClientRegistry.bindTileEntitySpecialRenderer(TileMachineHeater.class, new RenderTileMachine());
        ClientRegistry
                .bindTileEntitySpecialRenderer(TileIntelligentWorkbench.class, new RenderTileIntelligentWorkbench());
        ClientRegistry.bindTileEntitySpecialRenderer(TileTransceiver.class, new RenderTileTransceiver());

        MinecraftForge.EVENT_BUS.register(SoundHandler.INSTANCE);
        EventHelper.register(new TooltipEventHandler());

        RemoteIO.localizationUpdater.registerListener();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        Documentation.initialize();
    }

    @Override
    public void setClientPlayerSlot(int slot, ItemStack itemStack) {
        Minecraft.getMinecraft().thePlayer.openContainer.getSlot(slot).putStack(itemStack);
    }

    @Override
    public World getWorld(int dimension) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            return super.getWorld(dimension);
        } else {
            return Minecraft.getMinecraft().theWorld;
        }
    }

    @Override
    public void activateBlock(World world, int x, int y, int z, EntityPlayer entityPlayer, int side, float fx, float fy,
            float fz) {
        if (entityPlayer instanceof EntityPlayerMP) {
            super.activateBlock(world, x, y, z, entityPlayer, side, fx, fy, fz);
        } else {
            EntityClientPlayerMP entityClientPlayerMP = (EntityClientPlayerMP) entityPlayer;
            ClientProxyPlayer proxyPlayer = new ClientProxyPlayer(entityClientPlayerMP);
            proxyPlayer.inventory = entityClientPlayerMP.inventory;
            proxyPlayer.inventoryContainer = entityClientPlayerMP.inventoryContainer;
            proxyPlayer.openContainer = entityClientPlayerMP.openContainer;
            proxyPlayer.movementInput = entityClientPlayerMP.movementInput;

            Block block = entityClientPlayerMP.worldObj.getBlock(x, y, z);
            if (block != null) {
                SoundHandler.INSTANCE.translateNextSound(x, y, z);

                if (proxyPlayer.getHeldItem() != null) {
                    if (proxyPlayer.getHeldItem().getItem().onItemUseFirst(
                            proxyPlayer.getHeldItem(),
                            proxyPlayer,
                            proxyPlayer.worldObj,
                            x,
                            y,
                            z,
                            side,
                            fx,
                            fy,
                            fz))
                        return;
                }
                block.onBlockActivated(entityClientPlayerMP.worldObj, x, y, z, proxyPlayer, side, fx, fy, fz);
            }

            if (entityClientPlayerMP.openContainer != proxyPlayer.openContainer) {
                entityClientPlayerMP.openContainer = proxyPlayer.openContainer;
            }
        }
    }
}
