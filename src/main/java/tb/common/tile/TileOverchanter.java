package tb.common.tile;

import static tb.core.TBCore.isAutomagyLoaded;
import static tb.core.TBCore.isEioLoaded;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import DummyCore.Utils.MathUtils;
import DummyCore.Utils.MiscUtils;
import crazypants.enderio.machine.obelisk.xp.TileExperienceObelisk;
import crazypants.enderio.xp.ExperienceContainer;
import crazypants.enderio.xp.XpUtil;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.wands.IWandable;
import thaumcraft.common.lib.events.EssentiaHandler;
import tuhljin.automagy.tiles.TileEntityJarXP;

public class TileOverchanter extends TileEntity implements ISidedInventory, IWandable {

    public ItemStack inventory;

    public int enchantingTicks;
    public int xpToAbsorb;
    public boolean isEnchantingStarted;
    public int syncTimer;

    public static final int xp30lv = 825;

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            return;
        }
        if (syncTimer <= 0) {
            syncTimer = 100;
            NBTTagCompound tg = new NBTTagCompound();
            tg.setInteger("0", enchantingTicks);
            tg.setInteger("1", xpToAbsorb);
            tg.setBoolean("2", isEnchantingStarted);
            tg.setInteger("x", xCoord);
            tg.setInteger("y", yCoord);
            tg.setInteger("z", zCoord);
            MiscUtils.syncTileEntity(tg, 0);
        } else {
            --syncTimer;
        }

        if (this.inventory == null) {
            isEnchantingStarted = false;
            xpToAbsorb = xp30lv; // 30 levels
            enchantingTicks = 0;
            return;
        }
        if (this.isEnchantingStarted) {
            if (enchantingTicks % 20 == 0) {
                this.worldObj
                    .playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "thaumcraft:infuserstart", 1F, 1.0F);
                if (EssentiaHandler.drainEssentia(this, Aspect.MAGIC, ForgeDirection.UNKNOWN, 8, false)) {
                    // +1 is needed to exactly mimic original timings and not use extra 1 essentia (should be 32 in
                    // perfect conditions)
                    int enchantingSeconds = enchantingTicks / 20 + 1;
                    // the values being checked against are the second milestones in the enchanting process
                    if (enchantingSeconds >= 16) {
                        if (xpToAbsorb != 0) absorbXP();
                        if (enchantingSeconds >= 32 && xpToAbsorb == 0) {
                            int enchId = this.findEnchantment(inventory);
                            NBTTagList nbttaglist = this.inventory.getEnchantmentTagList();
                            for (int i = 0; i < nbttaglist.tagCount(); ++i) {
                                NBTTagCompound tag = nbttaglist.getCompoundTagAt(i);
                                if (tag != null && tag.getShort("id") == enchId) {
                                    tag.setShort(
                                        "lvl",
                                        (short) Math.max(1, Math.min(tag.getShort("lvl") + 1, Short.MAX_VALUE)));
                                    NBTTagCompound stackTag = MiscUtils.getStackTag(inventory);
                                    if (!stackTag.hasKey("overchants")) {
                                        stackTag.setIntArray("overchants", new int[] { enchId });
                                    } else {
                                        int[] arrayInt = stackTag.getIntArray("overchants");
                                        int[] newArrayInt = new int[arrayInt.length + 1];
                                        System.arraycopy(arrayInt, 0, newArrayInt, 0, arrayInt.length);
                                        newArrayInt[newArrayInt.length - 1] = enchId;

                                        stackTag.setIntArray("overchants", newArrayInt);
                                    }
                                    break;
                                }
                            }
                            isEnchantingStarted = false;
                            xpToAbsorb = xp30lv;
                            enchantingTicks = 0;
                            this.worldObj
                                .playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "thaumcraft:wand", 1F, 1F);
                            return;
                        }
                    }

                } else {
                    enchantingTicks -= 20;
                }
            }
            enchantingTicks += 1;
        }
    }

    public boolean canStartEnchanting() {
        if (!this.isEnchantingStarted) if (this.inventory != null) {
            if (this.inventory.getEnchantmentTagList() != null && this.inventory.getEnchantmentTagList()
                .tagCount() > 0) {
                if (findEnchantment(inventory) != -1) {
                    return true;
                }
            }
        }
        return false;
    }

    public int findEnchantment(ItemStack enchanted) {
        NBTTagCompound stackTag = MiscUtils.getStackTag(enchanted);
        LinkedHashMap<Integer, Integer> ench = (LinkedHashMap<Integer, Integer>) EnchantmentHelper
            .getEnchantments(enchanted);
        Set<Integer> keys = ench.keySet();
        Iterator<Integer> $i = keys.iterator();

        while ($i.hasNext()) {
            int i = $i.next();
            if (!stackTag.hasKey("overchants")) {
                return i;
            } else {
                int[] overchants = stackTag.getIntArray("overchants");
                if (MathUtils.arrayContains(overchants, i)) continue;

                return i;
            }
        }

        return -1;
    }

    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        enchantingTicks = pkt.func_148857_g()
            .getInteger("0");
        xpToAbsorb = pkt.func_148857_g()
            .getInteger("1");
        isEnchantingStarted = pkt.func_148857_g()
            .getBoolean("2");
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (!worldObj.isRemote && !isEnchantingStarted) {

            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory;
    }

    @Override
    public ItemStack decrStackSize(int slot, int num) {
        if (this.inventory != null) {
            ItemStack itemstack;

            if (this.inventory.stackSize <= num) {
                itemstack = this.inventory;
                this.inventory = null;
                this.markDirty();
                return itemstack;
            } else {
                itemstack = this.inventory.splitStack(num);

                if (this.inventory.stackSize == 0) {
                    this.inventory = null;
                }

                this.markDirty();
                return itemstack;
            }
        } else {
            return null;
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return inventory;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stk) {
        inventory = stk;
    }

    @Override
    public String getInventoryName() {
        return "tb.overchanter";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return player.dimension == this.worldObj.provider.dimensionId
            && this.worldObj.blockExists(xCoord, yCoord, zCoord);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stk) {
        return stk.hasTagCompound() && stk.getEnchantmentTagList() != null
            && stk.getEnchantmentTagList()
                .tagCount() > 0
            && findEnchantment(stk) != -1;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, int side) {
        return !isItemValidForSlot(0, item);
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side) {
        return isItemValidForSlot(0, item);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[] { 0 };
    }

    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        enchantingTicks = tag.getInteger("enchTime");
        xpToAbsorb = tag.hasKey("xpToAbsorb") ? tag.getInteger("xpToAbsorb") : xp30lv;
        isEnchantingStarted = tag.getBoolean("enchStarted");

        if (tag.hasKey("itm")) inventory = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("itm"));
    }

    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        tag.setInteger("enchTime", enchantingTicks);
        tag.setInteger("xpToAbsorb", xpToAbsorb);
        tag.setBoolean("enchStarted", isEnchantingStarted);

        if (inventory != null) {
            NBTTagCompound t = new NBTTagCompound();
            inventory.writeToNBT(t);
            tag.setTag("itm", t);
        }
    }

    @Override
    public int onWandRightClick(World world, ItemStack wandstack, EntityPlayer player, int x, int y, int z, int side,
        int md) {
        if (!world.isRemote && canStartEnchanting()) {
            isEnchantingStarted = true;
            player.swingItem();
            syncTimer = 0;
            this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "thaumcraft:craftstart", 0.5F, 1.0F);
            return 1;
        }
        return -1;
    }

    @Override
    public ItemStack onWandRightClick(World world, ItemStack wandstack, EntityPlayer player) {
        return wandstack;
    }

    @Override
    public void onUsingWandTick(ItemStack wandstack, EntityPlayer player, int count) {}

    @Override
    public void onWandStoppedUsing(ItemStack wandstack, World world, EntityPlayer player, int count) {}

    public void absorbXP() {
        // Note that the drain functions shouldn't be in a non-remote test because of player damage fallback
        if (isAutomagyLoaded) {
            // This scans a 17x17x17 cube centered around the TE, prioritizing the closest sources
            this.xpToAbsorb = this.drainXPJarsInRange(this.xpToAbsorb, 8);
            if (xpToAbsorb == 0) return;
        }
        if (isEioLoaded) {
            this.xpToAbsorb = this.drainEIOObelisksInRange(this.xpToAbsorb, 8);
            if (xpToAbsorb == 0) return;
        }
        List<EntityPlayer> players = this.worldObj.getEntitiesWithinAABB(
            EntityPlayer.class,
            AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1)
                .expand(6, 3, 6));
        if (!players.isEmpty()) {
            // See https://minecraft.wiki/w/Experience#Values_from_Java_Edition_1.3.1_to_1.8_(14w02a) for XP math
            // Uses a double in the second branch so that float division is used
            int lvlsleft = (int) Math
                .round(xpToAbsorb > 255 ? (59 + Math.sqrt(24 * xpToAbsorb - 5159)) / 6 : xpToAbsorb / 17d);
            for (EntityPlayer p : players) {
                if (p.experienceLevel >= lvlsleft) {
                    p.attackEntityFrom(DamageSource.magic, 8);
                    this.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "thaumcraft:zap", 1F, 1.0F);
                    p.addExperienceLevel(-lvlsleft);
                    this.xpToAbsorb = 0;
                    // If anyone else wants to implement the exact formula for experience draining, you can
                    return;
                }
            }
        }
    }

    private int drainXPJarsInRange(int xp, int range) {
        if (xp <= 0) return xp;
        AtomicInteger requiredXp = new AtomicInteger(xp);
        iterateTileEntities(this.xCoord, this.yCoord, this.zCoord, range, TileEntityJarXP.class, jar -> {
            int jarxp = jar.getXP();
            if (jarxp < requiredXp.get()) {
                jar.setXP(0);
                requiredXp.addAndGet(-jarxp);
            } else {
                jar.setXP(jarxp - requiredXp.getAndSet(0));
            }
            return requiredXp.get() <= 0;
        });
        return Math.max(0, requiredXp.get());
        // This algorithm drains each jar it comes across sequentially without regard to how full they are. If you would
        // rather it prioritize emptying barely filled jars within a certain radius, and then emptying non-full jars,
        // then instead have a counter sinceLastPrioJar that starts at 0 and increments each iteration, and cache the
        // most recent "priority" jar (lowest non-full jar with jarxp > xp argument); reset that counter per each jar
        // with jarxp under xp argument, and drain each jar with jarxp under xp argument, stopping once the counter
        // reaches an arbitrary count of blocks searched without draining a jar, and then drain the cached lowest-filled
        // jar. The TC EssentiaHandler would be so much better if it worked that way as well.
    }

    // EIO version (separated so that it prioritizes the magic jars, feel free to switch this to being in sync instead)
    private int drainEIOObelisksInRange(int xp, int range) {
        if (xp <= 0) return xp;
        AtomicInteger requiredXp = new AtomicInteger(xp);
        iterateTileEntities(this.xCoord, this.yCoord, this.zCoord, range, TileExperienceObelisk.class, obelisk -> {
            ExperienceContainer cont = obelisk.getContainer();
            int required = XpUtil.experienceToLiquid(requiredXp.get());
            FluidStack drained = cont.drain(null, required, true);
            return requiredXp.addAndGet(-XpUtil.liquidToExperience(drained.amount)) <= 0;
        });
        return Math.max(0, requiredXp.get());
    }

    private <T extends TileEntity> void iterateTileEntities(int centerX, int centerY, int centerZ, int range,
        Class<T> tileEntityType, Function<T, Boolean> action) {
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    TileEntity tileEntity = this.worldObj.getTileEntity(x, y, z);
                    if (tileEntity != null && tileEntity.getClass()
                        .isAssignableFrom(tileEntityType)) {
                        // noinspection unchecked
                        if (action.apply((T) tileEntity)) {
                            return;
                        }
                    }
                }
            }
        }
    }

}
