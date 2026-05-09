package remoteio.common.tile;

import java.util.EnumMap;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.GridConnection;
import cofh.api.energy.IEnergyHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModAPIManager;
import cpw.mods.fml.common.Optional;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.tile.IEnergyStorage;
import ic2.api.tile.IWrenchable;
import remoteio.common.core.TransferType;
import remoteio.common.core.UpgradeType;
import remoteio.common.core.helper.RotationHelper;
import remoteio.common.core.helper.mod.IC2Helper;
import remoteio.common.lib.DependencyInfo;
import remoteio.common.lib.DimensionalCoords;
import remoteio.common.lib.ModItems;
import remoteio.common.lib.VisualState;
import remoteio.common.tile.core.TileIOCore;
import remoteio.common.tracker.BlockTracker;
import remoteio.common.tracker.RedstoneTracker;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.wands.IWandable;

/**
 * @author dmillerw
 */
@Optional.InterfaceList({
        @Optional.Interface(
                iface = DependencyInfo.Paths.Thaumcraft.IASPECTCONTAINER,
                modid = DependencyInfo.ModIds.THAUMCRAFT),
        @Optional.Interface(
                iface = DependencyInfo.Paths.Thaumcraft.IASPECTSOURCE,
                modid = DependencyInfo.ModIds.THAUMCRAFT),
        @Optional.Interface(
                iface = DependencyInfo.Paths.Thaumcraft.IESSENTIATRANSPORT,
                modid = DependencyInfo.ModIds.THAUMCRAFT),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IENERGYSOURCE, modid = DependencyInfo.ModIds.IC2),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IENERGYEMITTER, modid = DependencyInfo.ModIds.IC2),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IENERGYSINK, modid = DependencyInfo.ModIds.IC2),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IENERGYACCEPTOR, modid = DependencyInfo.ModIds.IC2),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IENERGYTILE, modid = DependencyInfo.ModIds.IC2),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IENERGYSTORAGE, modid = DependencyInfo.ModIds.IC2),
        @Optional.Interface(iface = DependencyInfo.Paths.COFH.IENERGYHANDLER, modid = DependencyInfo.ModIds.COFH_API),

        @Optional.Interface(
                iface = DependencyInfo.Paths.Thaumcraft.IWANDABLE,
                modid = DependencyInfo.ModIds.THAUMCRAFT),
        @Optional.Interface(iface = DependencyInfo.Paths.IC2.IWRENCHABLE, modid = DependencyInfo.ModIds.IC2) })
public class TileRemoteInterface extends TileIOCore
        implements BlockTracker.ITrackerCallback, IInventory, ISidedInventory, IFluidHandler, IAspectSource, // THAUMCRAFT
        IEssentiaTransport, // THAUMCRAFT
        IEnergySource, // IC2
        IEnergySink, // IC2
        IEnergyStorage, // IC2
        IEnergyHandler, // COFH
        IWandable, // THAUMCRAFT
        IWrenchable // IC2
{

    @Override
    public void callback(IBlockAccess world, int x, int y, int z) {
        // The remote block changed – any cached implementations and the missing-upgrade state are stale.
        invalidateRemoteCache();
        missingUpgrade = false;
        updateVisualState();
        updateNeighbors();
    }

    @Override
    public void callback(IInventory inventory) {
        if (!hasWorldObj() || getWorldObj().isRemote) {
            return;
        }

        // I think IC2 caches tile state...
        if (registeredWithIC2) {
            IC2Helper.unloadEnergyTile(this);
            registeredWithIC2 = false;
        }

        if (hasTransferChip(TransferType.ENERGY_IC2) && remotePosition != null
                && remotePosition.getTileEntity() != null) {
            IC2Helper.loadEnergyTile(this);
            registeredWithIC2 = true;
        }

        if (trackingRedstone) {
            RedstoneTracker.unregister(this);
            trackingRedstone = false;
        }

        RedstoneTracker.register(this);

        // Update AE connection when chips are added or removed.
        if (Loader.isModLoaded(DependencyInfo.ModIds.AE2)) updateAEConnection();

        // Clear missing upgrade flag
        missingUpgrade = false;

        updateVisualState();
        updateNeighbors();
    }

    public DimensionalCoords remotePosition;

    // THIS IS NOT AN ANGLE, BUT THE NUMBER OF LEFT-HAND ROTATIONS!
    public int rotationY = 0;

    public boolean locked = false;

    private int lastRedstoneLevel = 0;

    private boolean registeredWithIC2 = false;
    private boolean trackingRedstone = false;
    private boolean missingUpgrade = false;

    /**
     * Sentinel value placed in {@link #remoteImplCache} to indicate the remote does not implement an interface,
     * distinguishing a cached negative from an absent cache entry.
     */
    private static final Object NO_IMPL = new Object();

    /**
     * Cache of resolved remote implementations keyed by the interface class. Populated lazily on first lookup and
     * invalidated whenever the remote block state changes.
     */
    private final HashMap<Class<?>, Object> remoteImplCache = new HashMap<>();

    /** Whether {@link #remoteImplCache} holds valid data for the current remote block state. */
    private boolean remoteImplCacheValid = false;
    /** The world tick for which {@link #remoteImplCache} is valid. */
    private long remoteImplCacheTick = Long.MIN_VALUE;

    /**
     * Set to {@code true} after chunk-load so {@link #updateEntity} performs the initial AE connection on the first
     * tick when the world is available, without needing a periodic poll.
     */
    private boolean pendingAEConnect = false;
    private boolean pendingRemoteTracking = false;
    /**
     * Fallback reconnect timer. Counts down every server tick while the AE chip is installed. When it reaches zero (or
     * goes negative from the initial default of 0), {@link #connectAE()} is called unconditionally (it is idempotent –
     * it skips directions that already have live connections) and the timer is reset to {@link #AE_RECONNECT_INTERVAL}.
     * This handles races where {@link #onNeighborUpdated()} fires before a newly-placed cable's grid node is ready,
     * leaving the connection unestablished with no subsequent event to trigger a retry. Intentionally starts at
     * {@code 0} so that the first server tick always attempts a connection for newly-placed tiles.
     */
    private int aeReconnectTimer = 0;
    private static final int AE_RECONNECT_INTERVAL = 40;

    @Override
    public void writeCustomNBT(NBTTagCompound nbt) {
        if (remotePosition != null) {
            NBTTagCompound tag = new NBTTagCompound();
            remotePosition.writeToNBT(tag);
            nbt.setTag("position", tag);
        }

        nbt.setInteger("axisY", this.rotationY);
        nbt.setBoolean("locked", locked);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("position")) {
            remotePosition = DimensionalCoords.fromNBT(nbt.getCompoundTag("position"));
            invalidateRemoteCache();
            if (!hasWorldObj()) {
                pendingRemoteTracking = true;
            } else {
                pendingRemoteTracking = false;
                if (!worldObj.isRemote) {
                    BlockTracker.INSTANCE.startTracking(remotePosition, this);
                }
            }
            if (Loader.isModLoaded(DependencyInfo.ModIds.AE2)) {
                pendingAEConnect = true;
            }
        }

        rotationY = nbt.getInteger("axisY");
        locked = nbt.getBoolean("locked");
    }

    @Override
    public void onClientUpdate(NBTTagCompound nbt) {
        super.onClientUpdate(nbt);

        if (nbt.hasKey("position")) {
            remotePosition = DimensionalCoords.fromNBT(nbt.getCompoundTag("position"));
            invalidateRemoteCache();
        } else if (nbt.hasKey("position_null")) {
            remotePosition = null;
            invalidateRemoteCache();
        }

        if (nbt.hasKey("axisY")) {
            rotationY = nbt.getInteger("axisY");
        }
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (pendingRemoteTracking && remotePosition != null) {
                BlockTracker.INSTANCE.startTracking(remotePosition, this);
                pendingRemoteTracking = false;
            }
            // Perform deferred AE connection: on first tick after chunk-load, or immediately after a
            // connection was auto-destroyed by the AE2 network (e.g. neighbouring cable removed).
            if (pendingAEConnect || justDestroyed) {
                pendingAEConnect = false;
                justDestroyed = false;
                aeReconnectTimer = AE_RECONNECT_INTERVAL;
                updateAEConnection();
            }
            // Fallback periodic reconnect: handles the race where onNeighborUpdated() fires while the
            // neighbouring cable's grid node is not yet ready, so the first connection attempt silently
            // fails. connectAE() is idempotent – it skips directions that already have live connections.
            if (Loader.isModLoaded(DependencyInfo.ModIds.AE2) && hasTransferChip(TransferType.NETWORK_AE)
                    && --aeReconnectTimer <= 0) {
                aeReconnectTimer = AE_RECONNECT_INTERVAL;
                connectAE();
            }
            if (!trackingRedstone) {
                RedstoneTracker.register(this);
                trackingRedstone = true;
            }

            if (remotePosition != null && hasTransferChip(TransferType.REDSTONE)) {
                int redstone = worldObj.getStrongestIndirectPower(xCoord, yCoord, zCoord);
                if (redstone != lastRedstoneLevel) {
                    remotePosition.markForUpdate();
                    lastRedstoneLevel = redstone;
                }
            }

            if (ModAPIManager.INSTANCE.hasAPI(DependencyInfo.ModIds.COFH_API)) {
                ItemStack rfTransferChip = null;
                for (int i = 0; i < transferChips.getSizeInventory(); i++) {
                    ItemStack itemStack = transferChips.getStackInSlot(i);
                    if (itemStack != null && itemStack.hasTagCompound()
                            && itemStack.getItem() == ModItems.transferChip
                            && itemStack.getItemDamage() == TransferType.ENERGY_RF) {
                        rfTransferChip = itemStack;
                        break;
                    }
                }

                if (rfTransferChip != null) {
                    int maxPushPower = rfTransferChip.getTagCompound().getInteger("maxPushRate");

                    if (maxPushPower > 0) {
                        int count = 0;
                        IEnergyHandler[] energyHandlers = new IEnergyHandler[ForgeDirection.VALID_DIRECTIONS.length];
                        for (ForgeDirection forgeDirection : ForgeDirection.VALID_DIRECTIONS) {
                            TileEntity tileEntity = worldObj.getTileEntity(
                                    xCoord + forgeDirection.offsetX,
                                    yCoord + forgeDirection.offsetY,
                                    zCoord + forgeDirection.offsetZ);
                            if (tileEntity instanceof IEnergyHandler
                                    && getEnergyStored(forgeDirection.getOpposite()) > 0) {
                                count++;
                                energyHandlers[forgeDirection.ordinal()] = (IEnergyHandler) tileEntity;
                            }
                        }

                        if (count > 0) {
                            int perBlock = maxPushPower / count;
                            for (int i = 0; i < energyHandlers.length; i++) {
                                IEnergyHandler energyHandler = energyHandlers[i];
                                if (energyHandler != null) {
                                    ForgeDirection to = ForgeDirection.getOrientation(i);
                                    ForgeDirection from = to.getOpposite();
                                    energyHandler.receiveEnergy(from, extractEnergy(to, perBlock, false), false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onChunkUnload() {
        IC2Helper.unloadEnergyTile(this);
        if (Loader.isModLoaded(DependencyInfo.ModIds.AE2)) disconnectAE();
        RedstoneTracker.unregister(this);
        pendingRemoteTracking = false;
        BlockTracker.INSTANCE.stopTracking(remotePosition);
    }

    @Override
    public void invalidate() {
        IC2Helper.unloadEnergyTile(this);
        if (Loader.isModLoaded(DependencyInfo.ModIds.AE2)) disconnectAE();
        RedstoneTracker.unregister(this);
        pendingRemoteTracking = false;
        BlockTracker.INSTANCE.stopTracking(remotePosition);
        super.invalidate();
    }

    @Override
    public void onNeighborUpdated() {
        if (!worldObj.isRemote && Loader.isModLoaded(DependencyInfo.ModIds.AE2)) {
            updateAEConnection();
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    /*
     * BEGIN CLIENT UPDATE METHODS 'update' methods are used to calculate what should be sent to the client 'send'
     * methods actually send the data to the client, and take a single parameter that is the data to be sent Methods
     * pertaining to the same data are lumped together
     */

    /**
     * Sends the remote position to the client
     */
    public void updateRemotePosition() {
        sendRemotePosition(this.remotePosition);
    }

    public void sendRemotePosition(DimensionalCoords coords) {
        NBTTagCompound nbt = new NBTTagCompound();
        if (coords != null) {
            NBTTagCompound tag = new NBTTagCompound();
            coords.writeToNBT(tag);
            nbt.setTag("position", tag);
        } else {
            nbt.setBoolean("position_null", true);
        }
        sendClientUpdate(nbt);
    }

    /**
     * Sends the passed in theta modifer to the client Different than normal update methods due to how it's calculated
     */
    public void updateRotation(int modification) {
        this.rotationY += modification;
        if (rotationY > 3) {
            rotationY = 0;
        } else if (rotationY < 0) {
            rotationY = 3;
        }

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("axisY", this.rotationY);
        sendClientUpdate(nbt);
    }

    public VisualState calculateVisualState() {
        if (remotePosition == null) {
            return VisualState.INACTIVE;
        } else {
            if (!remotePosition.blockExists()) {
                return VisualState.INACTIVE_BLINK;
            }

            boolean simple = hasUpgradeChip(UpgradeType.SIMPLE_CAMO);
            boolean remote = hasUpgradeChip(UpgradeType.REMOTE_CAMO);

            if (simple && !remote) {
                return VisualState.CAMOUFLAGE_SIMPLE;
            } else if (!simple && remote) {
                return VisualState.CAMOUFLAGE_REMOTE;
            } else if (simple && remote) {
                return VisualState.CAMOUFLAGE_BOTH;
            }

            return missingUpgrade ? VisualState.ACTIVE_BLINK : VisualState.ACTIVE;
        }
    }

    @Override
    public void sendVisualState(VisualState visualState) {
        super.sendVisualState(visualState);

        if (visualState == VisualState.CAMOUFLAGE_REMOTE) {
            updateRemotePosition();
        }
    }

    /**
     * Sets the server-side remote position to the passed in position, and resets the tracker
     */
    public void setRemotePosition(DimensionalCoords coords) {
        IC2Helper.unloadEnergyTile(this);
        RedstoneTracker.unregister(this);
        BlockTracker.INSTANCE.stopTracking(remotePosition);
        remotePosition = coords;
        pendingRemoteTracking = false;
        invalidateRemoteCache();
        missingUpgrade = false;
        RedstoneTracker.register(this);
        BlockTracker.INSTANCE.startTracking(remotePosition, this);
        IC2Helper.loadEnergyTile(this);
        worldObj.notifyBlockOfNeighborChange(xCoord, yCoord, zCoord, this.getBlockType());
        if (Loader.isModLoaded(DependencyInfo.ModIds.AE2)) {
            disconnectAE();
            connectAE();
        }
        markForUpdate();
    }

    /* END CLIENT UPDATE METHODS */

    // -------------------------------------------------------------------------
    // Remote implementation resolution and caching
    // -------------------------------------------------------------------------

    /**
     * Invalidates the cached remote-implementation map. Must be called whenever the remote position or the remote block
     * itself changes.
     */
    private void invalidateRemoteCache() {
        remoteImplCacheValid = false;
        remoteImplCacheTick = Long.MIN_VALUE;
        remoteImplCache.clear();
    }

    /**
     * Returns the object at {@link #remotePosition} that implements {@code cls}, or {@code null} if the remote is
     * absent, the block does not exist, or neither its tile entity nor the block itself implements the interface.
     *
     * <p>
     * Results are cached per class for the current tick and invalidated when the remote changes (see
     * {@link #invalidateRemoteCache()}). This avoids repeated world lookups for repeated same-tick interface queries
     * while preventing stale tile-entity references from persisting across ticks.
     *
     * <p>
     * Also fixes a pre-existing bug where, when the remote has no tile entity, the lookup would always return
     * {@code null} even if the {@link Block} itself implemented the interface.
     */
    private Object resolveRemoteImpl(Class<?> cls) {
        if (remotePosition == null) return null;

        final World hostWorld = worldObj;
        final boolean canCache = hostWorld != null;
        final long currentTick = canCache ? hostWorld.getTotalWorldTime() : Long.MIN_VALUE;

        if (!canCache || (remoteImplCacheValid && remoteImplCacheTick != currentTick)) {
            invalidateRemoteCache();
        }

        if (remoteImplCacheValid && remoteImplCache.containsKey(cls)) {
            Object cached = remoteImplCache.get(cls);
            return cached == NO_IMPL ? null : cached;
        }

        // Perform a single-pass world lookup: one getWorld(), one getBlock(), one getTileEntity().
        World remoteWorld = remotePosition.getWorld();
        if (remoteWorld == null) return null;

        int rx = remotePosition.x, ry = remotePosition.y, rz = remotePosition.z;
        Block block = remoteWorld.getBlock(rx, ry, rz);
        if (block == null || block.isAir(remoteWorld, rx, ry, rz)) {
            // Block is absent; cache the negative so we don't keep looking up an empty position.
            if (canCache) {
                remoteImplCache.put(cls, NO_IMPL);
                remoteImplCacheValid = true;
                remoteImplCacheTick = currentTick;
            }
            return null;
        }

        TileEntity remote = remoteWorld.getTileEntity(rx, ry, rz);
        Object impl;
        boolean cacheNegative = true;
        if (remote != null) {
            impl = cls.isInstance(remote) ? remote : null;
        } else {
            // Fall back to the Block itself (e.g. a block that directly implements IEnergyHandler).
            impl = cls.isInstance(block) ? block : null;

            // If this block should have a tile entity but it is temporarily unavailable (chunk not fully loaded yet,
            // delayed TE creation, etc.), avoid caching a negative result so a later lookup can succeed.
            if (impl == null && block.hasTileEntity(remoteWorld.getBlockMetadata(rx, ry, rz))) {
                cacheNegative = false;
            }
        }

        if (impl != null) {
            if (canCache) {
                remoteImplCache.put(cls, impl);
                remoteImplCacheValid = true;
                remoteImplCacheTick = currentTick;
            }
        } else if (cacheNegative) {
            if (canCache) {
                remoteImplCache.put(cls, NO_IMPL);
                remoteImplCacheValid = true;
                remoteImplCacheTick = currentTick;
            }
        }
        return impl;
    }

    public Object getUpgradeImplementation(Class<?> cls) {
        return getUpgradeImplementation(cls, -1);
    }

    public Object getUpgradeImplementation(Class<?> cls, int upgradeType) {
        Object impl = resolveRemoteImpl(cls);
        if (impl == null) return null;

        if (upgradeType != -1 && !hasUpgradeChip(upgradeType)) {
            return null;
        }

        return impl;
    }

    public Object getTransferImplementation(Class<?> cls) {
        return getTransferImplementation(cls, true);
    }

    public Object getTransferImplementation(Class<?> cls, boolean requiresChip) {
        Object impl = resolveRemoteImpl(cls);
        if (impl == null) return null;

        if (requiresChip && !hasTransferChip(TransferType.getTypeForInterface(cls))) {
            if (!missingUpgrade) {
                missingUpgrade = true;
                updateVisualState();
            }
            return null;
        }

        return impl;
    }

    public ForgeDirection getAdjustedSide(ForgeDirection side) {
        return side;
        // return ForgeDirection.getOrientation(RotationHelper.getRotatedSide(0, rotationY, 0, side.ordinal()));
    }

    public int getAdjustedSide(int side) {
        return RotationHelper.getRotatedSide(0, rotationY, 0, side);
    }

    /* START IMPLEMENTATIONS */

    /* IINVENTORY */
    @Override
    public int getSizeInventory() {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null ? inventory.getSizeInventory() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null ? inventory.getStackInSlot(slot) : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amt) {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null ? inventory.decrStackSize(slot, amt) : null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null ? inventory.getStackInSlotOnClosing(slot) : null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        if (inventory != null) inventory.setInventorySlotContents(slot, stack);
    }

    @Override
    public String getInventoryName() {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null ? inventory.getInventoryName() : null;
    }

    @Override
    public boolean hasCustomInventoryName() {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null && inventory.hasCustomInventoryName();
    }

    @Override
    public int getInventoryStackLimit() {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null ? inventory.getInventoryStackLimit() : 0;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null && inventory.isUseableByPlayer(player);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
        return inventory != null && inventory.isItemValidForSlot(slot, stack);
    }

    /* ISIDEDINVENTORY */
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        ISidedInventory sidedInventory = (ISidedInventory) getTransferImplementation(ISidedInventory.class);
        if (sidedInventory != null) {
            return sidedInventory.getAccessibleSlotsFromSide(getAdjustedSide(side));
        } else {
            IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);
            if (inventory != null) {
                int[] array = new int[inventory.getSizeInventory()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = i;
                }
                return array;
            }
        }
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        ISidedInventory sidedInventory = (ISidedInventory) getTransferImplementation(ISidedInventory.class);
        if (sidedInventory != null) {
            return sidedInventory.canInsertItem(slot, stack, getAdjustedSide(side));
        } else {
            IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);

            if (inventory != null) {
                return inventory.isItemValidForSlot(slot, stack);
            }
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        ISidedInventory sidedInventory = (ISidedInventory) getTransferImplementation(ISidedInventory.class);
        if (sidedInventory != null) {
            return sidedInventory.canExtractItem(slot, stack, getAdjustedSide(side));
        } else {
            IInventory inventory = (IInventory) getTransferImplementation(IInventory.class);

            if (inventory != null) {
                return true;
            }
        }
        return false;
    }

    /* IFLUIDHANDLER */
    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        IFluidHandler fluidHandler = (IFluidHandler) getTransferImplementation(IFluidHandler.class);
        return fluidHandler != null ? fluidHandler.fill(getAdjustedSide(from), resource, doFill) : 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        IFluidHandler fluidHandler = (IFluidHandler) getTransferImplementation(IFluidHandler.class);
        return fluidHandler != null ? fluidHandler.drain(getAdjustedSide(from), resource, doDrain) : null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        IFluidHandler fluidHandler = (IFluidHandler) getTransferImplementation(IFluidHandler.class);
        return fluidHandler != null ? fluidHandler.drain(getAdjustedSide(from), maxDrain, doDrain) : null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        IFluidHandler fluidHandler = (IFluidHandler) getTransferImplementation(IFluidHandler.class);
        return fluidHandler != null && fluidHandler.canFill(getAdjustedSide(from), fluid);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        IFluidHandler fluidHandler = (IFluidHandler) getTransferImplementation(IFluidHandler.class);
        return fluidHandler != null && fluidHandler.canDrain(getAdjustedSide(from), fluid);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        IFluidHandler fluidHandler = (IFluidHandler) getTransferImplementation(IFluidHandler.class);
        return fluidHandler != null ? fluidHandler.getTankInfo(getAdjustedSide(from)) : new FluidTankInfo[0];
    }

    /* IASPECTSOURCE */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public AspectList getAspects() {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null ? aspectContainer.getAspects() : new AspectList();
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public void setAspects(AspectList aspects) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        if (aspectContainer != null) aspectContainer.setAspects(aspects);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean doesContainerAccept(Aspect tag) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null && aspectContainer.doesContainerAccept(tag);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int addToContainer(Aspect tag, int amount) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null ? aspectContainer.addToContainer(tag, amount) : amount;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean takeFromContainer(Aspect tag, int amount) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null && aspectContainer.takeFromContainer(tag, amount);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean takeFromContainer(AspectList ot) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null && aspectContainer.takeFromContainer(ot);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean doesContainerContainAmount(Aspect tag, int amount) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null && aspectContainer.doesContainerContainAmount(tag, amount);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean doesContainerContain(AspectList ot) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null && aspectContainer.doesContainerContain(ot);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int containerContains(Aspect tag) {
        IAspectSource aspectContainer = (IAspectSource) getTransferImplementation(IAspectSource.class);
        return aspectContainer != null ? aspectContainer.containerContains(tag) : 0;
    }

    /* IESSENTIATRANSPORT */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean isConnectable(ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null && essentiaTransport.isConnectable(getAdjustedSide(face));
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean canInputFrom(ForgeDirection face) {
        return true;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean canOutputTo(ForgeDirection face) {
        return true;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public void setSuction(Aspect aspect, int amount) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        if (essentiaTransport != null) essentiaTransport.setSuction(aspect, amount);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public Aspect getSuctionType(ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.getSuctionType(getAdjustedSide(face)) : null;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int getSuctionAmount(ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.getSuctionAmount(getAdjustedSide(face)) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int takeEssentia(Aspect aspect, int amount, ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.takeEssentia(aspect, amount, getAdjustedSide(face)) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int addEssentia(Aspect aspect, int amount, ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.addEssentia(aspect, amount, getAdjustedSide(face)) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public Aspect getEssentiaType(ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.getEssentiaType(getAdjustedSide(face)) : null;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int getEssentiaAmount(ForgeDirection face) {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.getEssentiaAmount(getAdjustedSide(face)) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int getMinimumSuction() {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null ? essentiaTransport.getMinimumSuction() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public boolean renderExtendedTube() {
        IEssentiaTransport essentiaTransport = (IEssentiaTransport) getTransferImplementation(IEssentiaTransport.class);
        return essentiaTransport != null && essentiaTransport.renderExtendedTube();
    }

    /* IENERGYSOURCE */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public double getOfferedEnergy() {
        IEnergySource energySource = (IEnergySource) getTransferImplementation(IEnergySource.class);
        return energySource != null ? energySource.getOfferedEnergy() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public void drawEnergy(double amount) {
        IEnergySource energySource = (IEnergySource) getTransferImplementation(IEnergySource.class);
        if (energySource != null) energySource.drawEnergy(amount);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection direction) {
        IEnergySource energySource = (IEnergySource) getTransferImplementation(IEnergySource.class);
        return energySource != null && energySource.emitsEnergyTo(receiver, getAdjustedSide(direction));
    }

    /* IENERGYSINK */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public double getDemandedEnergy() {
        IEnergySink energySink = (IEnergySink) getTransferImplementation(IEnergySink.class);
        return energySink != null ? energySink.getDemandedEnergy() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public int getSinkTier() {
        IEnergySink energySink = (IEnergySink) getTransferImplementation(IEnergySink.class);
        return energySink != null ? energySink.getSinkTier() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public double injectEnergy(ForgeDirection directionFrom, double amount, double voltage) {
        IEnergySink energySink = (IEnergySink) getTransferImplementation(IEnergySink.class);
        return energySink != null ? energySink.injectEnergy(getAdjustedSide(directionFrom), amount, voltage) : 0;
    }

    /* IENERGYACCEPTOR */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection direction) {
        IEnergyAcceptor energyAcceptor = (IEnergyAcceptor) getTransferImplementation(IEnergyAcceptor.class);
        return energyAcceptor != null && energyAcceptor.acceptsEnergyFrom(emitter, getAdjustedSide(direction));
    }

    /* IENERGYSOURCE */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public int getSourceTier() {
        IEnergySource energySource = (IEnergySource) getTransferImplementation(IEnergySource.class);
        return energySource != null ? energySource.getSourceTier() : 0;
    }

    /* IENERGYSTORAGE */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public int getStored() {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        return energyStorage != null ? energyStorage.getStored() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public void setStored(int energy) {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        if (energyStorage != null) energyStorage.setStored(energy);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public int addEnergy(int amount) {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        return energyStorage != null ? energyStorage.addEnergy(amount) : getStored();
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public int getCapacity() {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        return energyStorage != null ? energyStorage.getCapacity() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public int getOutput() {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        return energyStorage != null ? energyStorage.getOutput() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public double getOutputEnergyUnitsPerTick() {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        return energyStorage != null ? energyStorage.getOutputEnergyUnitsPerTick() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public boolean isTeleporterCompatible(ForgeDirection side) {
        IEnergyStorage energyStorage = (IEnergyStorage) getTransferImplementation(IEnergyStorage.class);
        return energyStorage != null && energyStorage.isTeleporterCompatible(getAdjustedSide(side));
    }

    /* IENERGYHANDLER - COFH */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.COFH_API)
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        IEnergyHandler energyHandler = (IEnergyHandler) getTransferImplementation(IEnergyHandler.class);
        return energyHandler != null ? energyHandler.receiveEnergy(getAdjustedSide(from), maxReceive, simulate) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.COFH_API)
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        IEnergyHandler energyHandler = (IEnergyHandler) getTransferImplementation(IEnergyHandler.class);
        return energyHandler != null ? energyHandler.extractEnergy(getAdjustedSide(from), maxExtract, simulate) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.COFH_API)
    public int getEnergyStored(ForgeDirection from) {
        IEnergyHandler energyHandler = (IEnergyHandler) getTransferImplementation(IEnergyHandler.class);
        return energyHandler != null ? energyHandler.getEnergyStored(getAdjustedSide(from)) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.COFH_API)
    public int getMaxEnergyStored(ForgeDirection from) {
        IEnergyHandler energyHandler = (IEnergyHandler) getTransferImplementation(IEnergyHandler.class);
        return energyHandler != null ? energyHandler.getMaxEnergyStored(getAdjustedSide(from)) : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.COFH_API)
    public boolean canConnectEnergy(ForgeDirection from) {
        IEnergyHandler energyHandler = (IEnergyHandler) getTransferImplementation(IEnergyHandler.class);
        return energyHandler != null && energyHandler.canConnectEnergy(getAdjustedSide(from));
    }

    /* IWANDABLE */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public int onWandRightClick(World world, ItemStack wandstack, EntityPlayer player, int x, int y, int z, int side,
            int md) {
        IWandable wandable = (IWandable) getUpgradeImplementation(IWandable.class, UpgradeType.REMOTE_ACCESS);
        return wandable != null
                ? wandable.onWandRightClick(world, wandstack, player, x, y, z, getAdjustedSide(side), md)
                : -1;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public ItemStack onWandRightClick(World world, ItemStack wandstack, EntityPlayer player) {
        IWandable wandable = (IWandable) getUpgradeImplementation(IWandable.class, UpgradeType.REMOTE_ACCESS);
        return wandable != null ? wandable.onWandRightClick(world, wandstack, player) : wandstack;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public void onUsingWandTick(ItemStack wandstack, EntityPlayer player, int count) {
        IWandable wandable = (IWandable) getUpgradeImplementation(IWandable.class, UpgradeType.REMOTE_ACCESS);
        if (wandable != null) wandable.onUsingWandTick(wandstack, player, count);
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.THAUMCRAFT)
    public void onWandStoppedUsing(ItemStack wandstack, World world, EntityPlayer player, int count) {
        IWandable wandable = (IWandable) getUpgradeImplementation(IWandable.class, UpgradeType.REMOTE_ACCESS);
        if (wandable != null) wandable.onWandStoppedUsing(wandstack, world, player, count);
    }

    /* IWRENCHABLE */
    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public boolean wrenchCanSetFacing(EntityPlayer entityPlayer, int side) {
        IWrenchable wrenchable = (IWrenchable) getUpgradeImplementation(IWrenchable.class, UpgradeType.REMOTE_ACCESS);
        return wrenchable != null && wrenchable.wrenchCanSetFacing(entityPlayer, getAdjustedSide(side));
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public short getFacing() {
        IWrenchable wrenchable = (IWrenchable) getUpgradeImplementation(IWrenchable.class, UpgradeType.REMOTE_ACCESS);
        return wrenchable != null ? wrenchable.getFacing() : 0;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public void setFacing(short facing) {
        IWrenchable wrenchable = (IWrenchable) getUpgradeImplementation(IWrenchable.class, UpgradeType.REMOTE_ACCESS);
        if (wrenchable != null) wrenchable.setFacing((short) getAdjustedSide(facing));
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public boolean wrenchCanRemove(EntityPlayer entityPlayer) {
        return false;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public float getWrenchDropRate() {
        return 0F;
    }

    @Override
    @Optional.Method(modid = DependencyInfo.ModIds.IC2)
    public ItemStack getWrenchDrop(EntityPlayer entityPlayer) {
        return null;
    }

    @Optional.Method(modid = DependencyInfo.ModIds.AE2)
    public IGridNode getGridNode(ForgeDirection dir) {

        IGridHost gridNode = (IGridHost) getTransferImplementation(IGridHost.class);
        return gridNode != null ? gridNode.getGridNode(getAdjustedSide(dir)) : null;
    }

    private boolean connected;

    public void updateAEConnection() {
        if (!Loader.isModLoaded(DependencyInfo.ModIds.AE2)) return;
        if (hasTransferChip(TransferType.NETWORK_AE)) {
            // if (!connected)
            connectAE();
        } else {
            if (connected) disconnectAE();
        }
    }

    @Optional.Method(modid = DependencyInfo.ModIds.AE2)
    public void disconnectAE() {
        connected = false;
        // Destroy all tracked connections directly instead of scanning neighbours.
        for (RIOGridConnection connection : connections.values()) {
            if (!connection.isDestroyed()) {
                connection.destroy();
            }
        }
        connections.clear();
    }

    private EnumMap<ForgeDirection, RIOGridConnection> connections = new EnumMap<>(ForgeDirection.class);

    @Optional.Method(modid = DependencyInfo.ModIds.AE2)
    public void connectAE() {
        connected = true;
        connections.entrySet().removeIf(e -> e.getValue().isDestroyed());
        for (ForgeDirection forgeDirection : ForgeDirection.VALID_DIRECTIONS) {
            if (connections.containsKey(forgeDirection)) {
                continue;
            }
            TileEntity tileEntity = getWorldObj().getTileEntity(
                    xCoord + forgeDirection.offsetX,
                    yCoord + forgeDirection.offsetY,
                    zCoord + forgeDirection.offsetZ);
            if (tileEntity != null && tileEntity instanceof IGridHost) {
                IGridNode neighbourNode = ((IGridHost) tileEntity).getGridNode(forgeDirection.getOpposite());
                if (neighbourNode != null) {
                    IGridNode myNode = getGridNode(forgeDirection);
                    if (myNode != null) {
                        try {
                            connections.put(
                                    forgeDirection,
                                    new RIOGridConnection(neighbourNode, myNode, forgeDirection.getOpposite()));
                        } catch (FailedConnection e) {
                            // already connected or permission denied
                        }
                    }
                }
            }
        }
    }

    private boolean justDestroyed;

    public class RIOGridConnection extends GridConnection {

        public RIOGridConnection(IGridNode aNode, IGridNode bNode, ForgeDirection fromAtoB) throws FailedConnection {
            super(aNode, bNode, fromAtoB);

        }

        private boolean destroyed;

        @Override
        public void destroy() {
            destroyed = true;
            justDestroyed = true;
            super.destroy();
        }

        public boolean isDestroyed() {
            return destroyed;
        }
    }
    /* END IMPLEMENTATIONS */
}
