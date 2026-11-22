package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.boat.BoatWood;
import com.exiledradio.backportcore.boat.Boats;
import com.exiledradio.backportcore.boat.EntityWoodBoat;
import com.exiledradio.backportcore.boat.EntityWoodChestBoat;
import com.exiledradio.backportcore.boat.ItemWoodBoat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The family's one boat registry ({@code boat/}): woods by id, Core's two entities, and boats saved the way the
 * backports' own entities saved them (bare wood names, chest items, passengers). The rename of a saved entity id is
 * Forge's alias, made when a world with the old names loads; the release check loads worlds saved by each backport's
 * previous jar for that.
 */
final class BoatTests {

    private static final BlockPos AREA = new BlockPos(4620, 120, 4620);

    private static BoatWood teak;
    private static Item teakBoat;
    private static Item teakChestBoat;

    private BoatTests() {
    }

    private static List<EntityItem> drops(WorldServer world, Entity at) {
        return world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at.getPosition()).grow(4.0D));
    }

    private static int count(List<EntityItem> drops, Item item) {
        int n = 0;
        for (EntityItem e : drops) {
            if (e.getItem().getItem() == item) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static void clearDrops(WorldServer world) {
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(AREA).grow(16.0D))) {
            e.setDead();
        }
    }

    static void schedule() {
        then(1, "Boats: woods and entities", world -> {
            check("backportcore:boat is registered", ForgeRegistries.ENTITIES.containsKey(Boats.BOAT), "");
            check("backportcore:chest_boat is registered", ForgeRegistries.ENTITIES.containsKey(Boats.CHEST_BOAT), "");
            check("vanilla's six woods are first", BoatWood.all().size() >= 6 && BoatWood.all().get(0).id.equals("minecraft:oak")
                    && BoatWood.all().get(5).id.equals("minecraft:dark_oak"), BoatWood.all());
            check("spruce's boat is vanilla's", BoatWood.get("minecraft:spruce").boatItem() == Items.SPRUCE_BOAT, "");
            check("spruce carries vanilla's type", BoatWood.get("minecraft:spruce").vanilla == EntityBoat.Type.SPRUCE, "");
            check("an unknown wood is oak", BoatWood.get("nobody:gone").id.equals("minecraft:oak"), "");
            check("an unknown wood is not found", BoatWood.find("nobody:gone") == null, "");
            check("a bare vanilla name is not a wood (only the backports' bare names are)", BoatWood.find("oak") == null, "");
            if (teak == null) {
                teak = BoatWood.register(new ResourceLocation("backportcore_selftest", "teak"),
                        () -> new ItemStack(Blocks.PLANKS, 1, 3), false)
                        .setTextures(new ResourceLocation("backportcore_selftest", "textures/entity/boat/teak.png"),
                                new ResourceLocation("backportcore_selftest", "textures/entity/chest_boat/teak.png"));
                teakBoat = new ItemWoodBoat(teak, false).setRegistryName("backportcore_selftest", "teak_boat");
                teakChestBoat = new ItemWoodBoat(teak, true).setRegistryName("backportcore_selftest", "teak_chest_boat");
                teak.setBoatItem(teakBoat).setChestBoatItem(teakChestBoat);
            }
            check("registering twice gives the same wood",
                    BoatWood.register(new ResourceLocation("backportcore_selftest", "teak"), () -> ItemStack.EMPTY, true) == teak && !teak.raft, "");
            check("a bare name finds a backport's wood", BoatWood.find("teak") == teak, BoatWood.find("teak"));
            check("old names map to Core's", Boats.renamedTo(new ResourceLocation("nobody", "boat")) == null, "");
            Boats.renamed(new ResourceLocation("backportcore_selftest", "old_chest_boat"), true);
            check("a renamed chest boat loads as Core's chest boat",
                    Boats.CHEST_BOAT.equals(Boats.renamedTo(new ResourceLocation("backportcore_selftest", "old_chest_boat"))), "");
            check("boat items burn 1200 ticks", TileEntityFurnace.getItemBurnTime(new ItemStack(teakChestBoat)) == 1200,
                    TileEntityFurnace.getItemBurnTime(new ItemStack(teakChestBoat)));
            check("boat items stack to 1", teakBoat.getItemStackLimit(new ItemStack(teakBoat)) == 1, "");
        });

        then(1, "Boats: a boat saved as the backports saved theirs", world -> {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    world.setBlockState(AREA.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
                    world.setBlockState(AREA.add(x, 0, z), Blocks.AIR.getDefaultState(), 2);
                    world.setBlockState(AREA.add(x, 1, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
            // What Trails & Tales 1.0.2 wrote for a cherry chest boat with a pig in it, under Core's id.
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", Boats.CHEST_BOAT.toString());
            NBTTagList pos = new NBTTagList();
            pos.appendTag(new net.minecraft.nbt.NBTTagDouble(AREA.getX() + 0.5));
            pos.appendTag(new net.minecraft.nbt.NBTTagDouble(AREA.getY()));
            pos.appendTag(new net.minecraft.nbt.NBTTagDouble(AREA.getZ() + 0.5));
            tag.setTag("Pos", pos);
            tag.setString("Type", "oak");
            tag.setString("Wood", "teak");
            NBTTagList items = new NBTTagList();
            NBTTagCompound diamonds = new ItemStack(Items.DIAMOND, 5).writeToNBT(new NBTTagCompound());
            diamonds.setByte("Slot", (byte) 3);
            items.appendTag(diamonds);
            NBTTagCompound last = new ItemStack(Items.APPLE, 2).writeToNBT(new NBTTagCompound());
            last.setByte("Slot", (byte) 26);
            items.appendTag(last);
            tag.setTag("Items", items);
            NBTTagList passengers = new NBTTagList();
            NBTTagCompound pig = new NBTTagCompound();
            pig.setString("id", "minecraft:pig");
            passengers.appendTag(pig);
            tag.setTag("Passengers", passengers);
            Entity e = AnvilChunkLoader.readWorldEntityPos(tag, world, AREA.getX() + 0.5, AREA.getY(), AREA.getZ() + 0.5, true);
            check("it loads as Core's chest boat", e instanceof EntityWoodChestBoat, e);
            if (!(e instanceof EntityWoodChestBoat)) {
                return;
            }
            EntityWoodChestBoat boat = (EntityWoodChestBoat) e;
            check("its bare wood name becomes the wood", boat.getWood() == teak && boat.getWoodId().equals("backportcore_selftest:teak"),
                    boat.getWoodId());
            check("its chest keeps slot 3", boat.getStackInSlot(3).getItem() == Items.DIAMOND && boat.getStackInSlot(3).getCount() == 5,
                    boat.getStackInSlot(3));
            check("its chest keeps slot 26", boat.getStackInSlot(26).getItem() == Items.APPLE, boat.getStackInSlot(26));
            check("its passenger rides it", boat.getPassengers().size() == 1 && boat.getPassengers().get(0) instanceof EntityPig,
                    boat.getPassengers());
            check("it drops its own item", boat.getItemBoat() == teakChestBoat, boat.getItemBoat());
            check("it is picked as its own item", boat.getPickedResult(null).getItem() == teakChestBoat, "");
            check("its title is its item's name", boat.getDisplayName() instanceof net.minecraft.util.text.TextComponentTranslation
                    && ((net.minecraft.util.text.TextComponentTranslation) boat.getDisplayName()).getKey()
                    .equals(teakChestBoat.getTranslationKey() + ".name"), boat.getDisplayName());

            NBTTagCompound saved = new NBTTagCompound();
            boat.writeToNBTOptional(saved);
            check("it saves as backportcore:chest_boat", Boats.CHEST_BOAT.toString().equals(saved.getString("id")), saved.getString("id"));
            check("it saves its wood's id", "backportcore_selftest:teak".equals(saved.getString("Wood")), saved.getString("Wood"));
            check("it saves its passenger", saved.getTagList("Passengers", 10).tagCount() == 1, "");

            // A wood whose mod is gone: an oak boat that keeps the saved id.
            NBTTagCompound gone = new NBTTagCompound();
            gone.setString("id", Boats.BOAT.toString());
            gone.setString("Wood", "nobody:gone");
            Entity g = EntityList.createEntityFromNBT(gone, world);
            check("a removed mod's boat is an oak boat", g instanceof EntityWoodBoat && ((EntityWoodBoat) g).getWood().id.equals("minecraft:oak")
                    && ((EntityWoodBoat) g).getItemBoat() == Items.BOAT, g);
            NBTTagCompound kept = new NBTTagCompound();
            g.writeToNBTOptional(kept);
            check("and keeps its wood's id", "nobody:gone".equals(kept.getString("Wood")), kept.getString("Wood"));

            for (Entity p : boat.getPassengers()) {
                p.setDead();
            }
            boat.removePassengers();
            clearDrops(world);
            boat.setDead();
            List<EntityItem> d = drops(world, boat);
            check("removing it drops the chest's items", count(d, Items.DIAMOND) == 5 && count(d, Items.APPLE) == 2, d.size());
        });

        then(2, "Boats: rafts, crashes and the chest's seat", world -> {
            clearDrops(world);
            BoatWood raftWood = BoatWood.register(new ResourceLocation("backportcore_selftest", "reed"),
                    () -> new ItemStack(Blocks.PLANKS, 1, 4), true);
            EntityWoodBoat raft = new EntityWoodBoat(world, AREA.getX() + 0.5, AREA.getY(), AREA.getZ() + 0.5, raftWood);
            EntityWoodBoat plain = new EntityWoodBoat(world, AREA.getX() + 0.5, AREA.getY(), AREA.getZ() + 0.5, teak);
            check("a raft seats its rider at 0.25", raft.getMountedYOffset() == 0.25D, raft.getMountedYOffset());
            check("a boat seats its rider as vanilla's", plain.getMountedYOffset() == -0.1D, plain.getMountedYOffset());
            EntityWoodBoat spruce = new EntityWoodBoat(world, 0, 0, 0, BoatWood.get("minecraft:spruce"));
            check("a vanilla wood's boat carries vanilla's type", spruce.getBoatType() == EntityBoat.Type.SPRUCE, spruce.getBoatType());
            check("a new wood's boat carries oak's type", plain.getBoatType() == EntityBoat.Type.OAK, plain.getBoatType());

            EntityItem crash = plain.entityDropItem(new ItemStack(Blocks.PLANKS, 3, 0), 0.0F);
            check("a crash drops the wood's planks", crash != null && crash.getItem().getMetadata() == 3 && crash.getItem().getCount() == 3,
                    crash == null ? null : crash.getItem());
            EntityItem sticks = plain.entityDropItem(new ItemStack(Items.STICK, 2), 0.0F);
            check("and vanilla's sticks", sticks != null && sticks.getItem().getItem() == Items.STICK, "");
            if (crash != null) {
                crash.setDead();
            }
            if (sticks != null) {
                sticks.setDead();
            }

            EntityWoodChestBoat chest = new EntityWoodChestBoat(world, AREA.getX() + 0.5, AREA.getY(), AREA.getZ() + 0.5, teak);
            world.spawnEntity(chest);
            EntityPig a = new EntityPig(world);
            EntityPig b = new EntityPig(world);
            a.setPosition(chest.posX, chest.posY, chest.posZ);
            b.setPosition(chest.posX, chest.posY, chest.posZ);
            world.spawnEntity(a);
            world.spawnEntity(b);
            check("the first rider gets on", a.startRiding(chest), "");
            check("a second rider does not", !b.startRiding(chest), chest.getPassengers().size());
            check("27 slots", chest.getSizeInventory() == 27, chest.getSizeInventory());
            a.setDead();
            b.setDead();
            chest.setInventorySlotContents(0, new ItemStack(Items.EMERALD, 7));
            chest.attackEntityFrom(net.minecraft.util.DamageSource.OUT_OF_WORLD, 100.0F);
            List<EntityItem> d = drops(world, chest);
            check("broken, it drops itself", count(d, teakChestBoat) == 1, count(d, teakChestBoat));
            check("and its chest's items", count(d, Items.EMERALD) == 7, count(d, Items.EMERALD));
            clearDrops(world);
            raft.setDead();
            plain.setDead();
            spruce.setDead();
        });
    }
}
