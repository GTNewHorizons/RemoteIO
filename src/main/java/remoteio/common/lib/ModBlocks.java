package remoteio.common.lib;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;
import remoteio.common.block.BlockIntelligentWorkbench;
import remoteio.common.block.BlockMachine;
import remoteio.common.block.BlockRemoteInterface;
import remoteio.common.block.BlockRemoteInventory;
import remoteio.common.block.BlockSkylight;
import remoteio.common.block.item.ItemBlockRemoteInventory;
import remoteio.common.item.block.ItemBlockMulti;
import remoteio.common.tile.TileIntelligentWorkbench;
import remoteio.common.tile.TileMachineHeater;
import remoteio.common.tile.TileMachineReservoir;
import remoteio.common.tile.TileRemoteInterface;
import remoteio.common.tile.TileRemoteInventory;

/**
 * @author dmillerw
 */
public class ModBlocks {

    public static Block remoteInterface;
    public static Block remoteInventory;
    public static Block machine;
    public static Block skylight;
    public static Block intelligentWorkbench;
    public static Block transceiver;

    public static void initialize() {
        remoteInterface = new BlockRemoteInterface().setBlockName("remote_interface");
        GameRegistry.registerBlock(remoteInterface, remoteInterface.getUnlocalizedName());
        GameRegistry.registerTileEntity(TileRemoteInterface.class, remoteInterface.getUnlocalizedName());

        remoteInventory = new BlockRemoteInventory().setBlockName("remote_inventory");
        GameRegistry
                .registerBlock(remoteInventory, ItemBlockRemoteInventory.class, remoteInventory.getUnlocalizedName());
        GameRegistry.registerTileEntity(TileRemoteInventory.class, remoteInventory.getUnlocalizedName());

        machine = new BlockMachine().setBlockName("machine");
        GameRegistry.registerBlock(
                machine,
                ItemBlockMulti.class,
                machine.getUnlocalizedName(),
                new Object[] { new String[] { "reservoir", "heater" } });
        GameRegistry.registerTileEntity(TileMachineReservoir.class, "remoteio:machine_reservoir");
        GameRegistry.registerTileEntity(TileMachineHeater.class, "remoteio:machine_heater");

        skylight = new BlockSkylight().setBlockName("skylight");
        GameRegistry.registerBlock(skylight, "skylight");

        intelligentWorkbench = new BlockIntelligentWorkbench().setBlockName("intelligentWorkbench");
        GameRegistry.registerBlock(intelligentWorkbench, "intelligentWorkbench");
        GameRegistry.registerTileEntity(TileIntelligentWorkbench.class, "remoteio:intelligentWorkbench");

        /*
         * transceiver = new BlockTransceiver().setBlockName("transceiver"); GameRegistry.registerBlock(transceiver,
         * ItemBlockTransceiver.class, "transceiver"); GameRegistry.registerTileEntity(TileTransceiver.class,
         * "remoteio:transceiver");
         */
    }
}
