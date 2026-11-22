package com.exiledradio.backportcore.boat;

import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The family's boat woods, by id: {@code "minecraft:oak"} ... {@code "minecraft:dark_oak"} (registered here) and each
 * backport's own ({@code "wildupdatebackport:mangrove"}, {@code "trailsandtalesbackport:cherry"}, ...), which the
 * wood's owner registers while its items are registered.
 *
 * <p>1.12.2's boat type enum indexes a texture array, so no mod can add a wood to vanilla's boat. Every boat of a wood
 * vanilla's boat cannot be, and every boat with a chest, is one of Core's two entities ({@link EntityWoodBoat},
 * {@link EntityWoodChestBoat}), which save the wood's id as {@code Wood}. A wood whose owner is gone reads as oak and
 * keeps its saved id, so the boat is that wood again once the owner is back.
 *
 * <p>The owner keeps its items under its own names (an {@link ItemWoodBoat} each) and its recipes, and hands the items
 * to the wood with {@link #setBoatItem} and {@link #setChestBoatItem}. The six vanilla woods have vanilla's boat items;
 * their chest boats are Wild Update's (1.19).
 */
public final class BoatWood {

    private static final Map<String, BoatWood> BY_ID = new LinkedHashMap<String, BoatWood>();

    static {
        for (final EntityBoat.Type type : EntityBoat.Type.values()) {
            String n = type.getName();
            BoatWood w = new BoatWood("minecraft:" + n, type, n, () -> new ItemStack(Blocks.PLANKS, 1, type.getMetadata()), false);
            w.boatTexture = new ResourceLocation("textures/entity/boat/boat_" + n + ".png");
            BY_ID.put(w.id, w);
        }
    }

    /** {@code "trailsandtalesbackport:bamboo"}. */
    public final String id;
    /** Vanilla's boat type for the six old woods, null for new woods. */
    @Nullable
    public final EntityBoat.Type vanilla;
    /** The id's path: {@code oak}, {@code mangrove}, {@code bamboo}. */
    public final String name;
    /** 1.20's raft: its own model, the rider sitting higher (bamboo). */
    public final boolean raft;
    private final Supplier<ItemStack> planks;

    @Nullable
    private ResourceLocation boatTexture;
    @Nullable
    private ResourceLocation chestBoatTexture;
    @Nullable
    private Item boatItem;
    @Nullable
    private Item chestBoatItem;

    private BoatWood(String id, @Nullable EntityBoat.Type vanilla, String name, Supplier<ItemStack> planks, boolean raft) {
        this.id = id;
        this.vanilla = vanilla;
        this.name = name;
        this.planks = planks;
        this.raft = raft;
    }

    /**
     * Registers a backport's wood, or returns it if it is registered already. Call it while registering items (the
     * owner's {@code RegistryEvent.Register<Item>}), before making its {@link ItemWoodBoat}s.
     *
     * @param id     {@code "trailsandtalesbackport:cherry"}
     * @param planks one of the wood's planks (a crash drops three, as vanilla's boats drop theirs)
     * @param raft   1.20's raft model and seat height (bamboo)
     */
    public static synchronized BoatWood register(ResourceLocation id, Supplier<ItemStack> planks, boolean raft) {
        BoatWood wood = BY_ID.get(id.toString());
        if (wood == null) {
            wood = new BoatWood(id.toString(), null, id.getPath(), planks, raft);
            BY_ID.put(wood.id, wood);
        }
        return wood;
    }

    /**
     * @return the wood with this id, or oak's when it is unknown (a wood whose mod was removed). A bare name
     *         ({@code "cherry"}, as the backports' own boats saved it before Core 1.4.4) is the registered wood with
     *         that name, when there is exactly one.
     */
    public static BoatWood get(String id) {
        BoatWood wood = find(id);
        return wood != null ? wood : BY_ID.get("minecraft:oak");
    }

    /** @return the wood with this id or bare name (see {@link #get}), or null */
    @Nullable
    public static synchronized BoatWood find(String id) {
        BoatWood wood = BY_ID.get(id);
        if (wood != null || id.isEmpty() || id.indexOf(':') >= 0) {
            return wood;
        }
        for (BoatWood w : BY_ID.values()) {
            if (w.vanilla == null && w.name.equals(id)) {
                if (wood != null) {
                    return null;
                }
                wood = w;
            }
        }
        return wood;
    }

    /** @return the wood of one of vanilla's boat types */
    public static BoatWood of(EntityBoat.Type type) {
        return BY_ID.get("minecraft:" + type.getName());
    }

    /** @return every wood, vanilla's six first */
    public static synchronized List<BoatWood> all() {
        return Collections.unmodifiableList(new ArrayList<BoatWood>(BY_ID.values()));
    }

    /** Sets the wood's textures: 1.20's 128 x 64 boat (or raft) and 128 x 128 chest boat. */
    public BoatWood setTextures(@Nullable ResourceLocation boat, @Nullable ResourceLocation chestBoat) {
        if (boat != null) {
            this.boatTexture = boat;
        }
        if (chestBoat != null) {
            this.chestBoatTexture = chestBoat;
        }
        return this;
    }

    public BoatWood setBoatItem(Item item) {
        this.boatItem = item;
        return this;
    }

    public BoatWood setChestBoatItem(Item item) {
        this.chestBoatItem = item;
        return this;
    }

    /** @return the boat (or raft) item, or null when this wood's boat is not in the game */
    @Nullable
    public Item boatItem() {
        // Vanilla's items are read late: this class may load while items are being registered, before Items has.
        return this.boatItem == null && this.vanilla != null ? vanillaBoat(this.vanilla) : this.boatItem;
    }

    /** @return the boat with chest item, or null when this wood's chest boat is not in the game */
    @Nullable
    public Item chestBoatItem() {
        return this.chestBoatItem;
    }

    @Nullable
    public ResourceLocation boatTexture() {
        return this.boatTexture;
    }

    @Nullable
    public ResourceLocation chestBoatTexture() {
        return this.chestBoatTexture;
    }

    /** @return one of the wood's planks, or empty when its planks are not in the game */
    public ItemStack planks() {
        ItemStack s = this.planks.get();
        return s == null ? ItemStack.EMPTY : s;
    }

    @Override
    public String toString() {
        return this.id;
    }

    static Item vanillaBoat(EntityBoat.Type type) {
        switch (type) {
            case SPRUCE:
                return Items.SPRUCE_BOAT;
            case BIRCH:
                return Items.BIRCH_BOAT;
            case JUNGLE:
                return Items.JUNGLE_BOAT;
            case ACACIA:
                return Items.ACACIA_BOAT;
            case DARK_OAK:
                return Items.DARK_OAK_BOAT;
            default:
                return Items.BOAT;
        }
    }
}
