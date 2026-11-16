package com.exiledradio.backportcore.smithing;

import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 1.20's armor trims: a pattern (from its smithing template) in a material (from the addition), written onto a
 * piece of armor and drawn over it, worn and as an item.
 *
 * <h2>Data</h2>
 * {@code {Trim: {pattern: "<modid>:<name>", material: "<modid>:<name>"}}} on the armor stack, as 1.20.4's tag.
 * Unknown ids are kept and ignored, so a removed mod's trim comes back when the mod does.
 *
 * <h2>Who registers what</h2>
 * Patterns come with their templates ({@link #registerPattern}: Trails &amp; Tales' sixteen, Tricky Trials' bolt and
 * flow); 1.20's materials are Trails &amp; Tales' ({@link #registerMaterial}). Registering a pattern adds its trim
 * recipe to {@link SmithingTemplates}. Register from pre-init or the item registry event.
 *
 * <h2>Textures, as 1.20's paletted atlas</h2>
 * The pattern's grayscale textures are {@code <ns>:textures/trims/models/armor/<path>.png} and
 * {@code <path>_leggings.png} (from its asset id); a material's palette is
 * {@code <ns>:textures/trims/color_palettes/<path>.png}, 8 colors matched against Core's
 * {@code trim_palette.png}. The client builds each pattern x material x layer on first use
 * ({@code smithing.client.ArmorTrimsClient}), and each material's four item overlays into the item atlas.
 */
public final class ArmorTrims {

    public static final String TAG = "Trim";
    public static final String PATTERN = "pattern";
    public static final String MATERIAL = "material";

    /** 1.20's {@code HideFlags} bit for the upgrade lines (TooltipPart.UPGRADES). */
    public static final int HIDE_UPGRADES = 128;

    /** A trim pattern. */
    public static final class Pattern {

        public final ResourceLocation id;
        /** Where its textures are: {@code <ns>:textures/trims/models/armor/<path>[_leggings].png}. */
        public final ResourceLocation assetId;
        private final Supplier<Item> template;
        /** 1.20's decal flag (drawn with a depth offset); no 1.20 pattern sets it. */
        public final boolean decal;

        Pattern(ResourceLocation id, Supplier<Item> template, ResourceLocation assetId, boolean decal) {
            this.id = id;
            this.template = template;
            this.assetId = assetId;
            this.decal = decal;
        }

        @Nullable
        public Item template() {
            return this.template.get();
        }

        /** {@code trim_pattern.<ns>.<path>}, as 1.20. */
        public String translationKey() {
            return "trim_pattern." + this.id.getNamespace() + "." + this.id.getPath();
        }

        /** @return the grayscale texture for the outer model ({@code leggings} false) or the leggings model */
        public ResourceLocation texture(boolean leggings) {
            return new ResourceLocation(this.assetId.getNamespace(),
                    "textures/trims/models/armor/" + this.assetId.getPath() + (leggings ? "_leggings" : "") + ".png");
        }
    }

    /** A trim material. */
    public static final class Material {

        public final ResourceLocation id;
        private final Predicate<ItemStack> ingredient;
        private final Supplier<List<ItemStack>> examples;
        /** Its palette: {@code <ns>:textures/trims/color_palettes/<path>.png}. */
        public final ResourceLocation palette;
        /** The tooltip color, 0xRRGGBB. */
        public final int color;
        private final List<Predicate<ItemStack>> darkerOn = new ArrayList<Predicate<ItemStack>>();
        private final List<ResourceLocation> darkerPalettes = new ArrayList<ResourceLocation>();

        Material(ResourceLocation id, Predicate<ItemStack> ingredient, Supplier<List<ItemStack>> examples,
                 ResourceLocation palette, int color) {
            this.id = id;
            this.ingredient = ingredient;
            this.examples = examples;
            this.palette = palette;
            this.color = color;
        }

        /**
         * 1.20's {@code override_armor_materials}: on armor of this material's own kind (iron trim on iron armor), the
         * trim is drawn with a darker palette so it shows.
         */
        public Material darkerOn(Predicate<ItemStack> armor, ResourceLocation palette) {
            synchronized (ArmorTrims.class) {
                this.darkerOn.add(armor);
                this.darkerPalettes.add(palette);
                PALETTES.put(palette.toString(), palette);
            }
            return this;
        }

        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && this.ingredient.test(stack);
        }

        public List<ItemStack> examples() {
            return this.examples.get();
        }

        /** {@code trim_material.<ns>.<path>}, as 1.20. */
        public String translationKey() {
            return "trim_material." + this.id.getNamespace() + "." + this.id.getPath();
        }

        /** @return the palette for a trim of this material on that armor */
        public ResourceLocation paletteFor(ItemStack armor) {
            for (int i = 0; i < this.darkerOn.size(); i++) {
                if (this.darkerOn.get(i).test(armor)) {
                    return this.darkerPalettes.get(i);
                }
            }
            return this.palette;
        }
    }

    /** A trim read from a stack: both parts known. */
    public static final class Trim {

        public final Pattern pattern;
        public final Material material;

        Trim(Pattern pattern, Material material) {
            this.pattern = pattern;
            this.material = material;
        }
    }

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<String, Pattern>();
    private static final Map<String, Material> MATERIALS = new LinkedHashMap<String, Material>();
    /** Every palette in use, darker ones included, for the item atlas. */
    private static final Map<String, ResourceLocation> PALETTES = new LinkedHashMap<String, ResourceLocation>();
    private static final List<Predicate<ItemStack>> UNTRIMMABLE = new ArrayList<Predicate<ItemStack>>();
    private static final List<Predicate<Item>> ICONS = new ArrayList<Predicate<Item>>();

    private ArmorTrims() {
    }

    // ---------------------------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------------------------

    /**
     * A pattern and its template, which also becomes a trim recipe at the smithing table.
     *
     * @param template its smithing template (looked up when needed, so it may be registered later)
     * @param assetId  where its textures are (see {@link Pattern#assetId})
     */
    public static synchronized Pattern registerPattern(ResourceLocation id, Supplier<Item> template,
                                                       ResourceLocation assetId, boolean decal) {
        Pattern pattern = new Pattern(id, template, assetId, decal);
        PATTERNS.put(id.toString(), pattern);
        SmithingTemplates.addTrim(new TemplateIngredient(pattern), ArmorTrims::isTrimmable,
                (Predicate<ItemStack>) s -> material(s) != null, ArmorTrims::applyFromTable, id.getNamespace());
        return pattern;
    }

    /**
     * A material.
     *
     * @param ingredient what in the addition slot is this material
     * @param examples   what a recipe viewer shows for it
     * @param palette    its palette (see {@link Material#palette})
     * @param color      the tooltip color, 0xRRGGBB
     */
    public static synchronized Material registerMaterial(ResourceLocation id, Predicate<ItemStack> ingredient,
                                                         Supplier<List<ItemStack>> examples, ResourceLocation palette,
                                                         int color) {
        Material material = new Material(id, ingredient, examples, palette, color);
        MATERIALS.put(id.toString(), material);
        PALETTES.put(palette.toString(), palette);
        return material;
    }

    /** Armor that takes no trim (a mod's own setting, say). Everything that is {@link ItemArmor} does otherwise. */
    public static synchronized void excludeArmor(Predicate<ItemStack> armor) {
        UNTRIMMABLE.add(armor);
    }

    /**
     * Armor items whose icons are drawn in vanilla's shapes, so 1.20's item overlays fit them: vanilla's, every
     * netherite mod's (named {@code netherite}), and what mods add here (Update Aquatic's turtle shell, say). Other
     * armor takes trims and shows them worn, but its icon stays as it is, as a 1.20 mod's armor without trim models.
     */
    public static synchronized void drawOnIcon(Predicate<Item> items) {
        ICONS.add(items);
    }

    /** @return true if this armor item's icon shows its trim */
    public static synchronized boolean drawsOnIcon(Item item) {
        if (!(item instanceof ItemArmor) || item.getRegistryName() == null) {
            return false;
        }
        ResourceLocation name = item.getRegistryName();
        if ("minecraft".equals(name.getNamespace()) || name.getPath().contains("netherite")) {
            return true;
        }
        for (Predicate<Item> p : ICONS) {
            if (p.test(item)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------------------------------------

    public static synchronized Collection<Pattern> patterns() {
        return Collections.unmodifiableCollection(new ArrayList<Pattern>(PATTERNS.values()));
    }

    public static synchronized Collection<Material> materials() {
        return Collections.unmodifiableCollection(new ArrayList<Material>(MATERIALS.values()));
    }

    /** Every palette a trim may be drawn in, darker ones included. */
    public static synchronized Collection<ResourceLocation> palettes() {
        return Collections.unmodifiableCollection(new ArrayList<ResourceLocation>(PALETTES.values()));
    }

    @Nullable
    public static synchronized Pattern pattern(ResourceLocation id) {
        return PATTERNS.get(id.toString());
    }

    @Nullable
    public static synchronized Material material(ResourceLocation id) {
        return MATERIALS.get(id.toString());
    }

    /** @return the pattern this template applies, or null */
    @Nullable
    public static synchronized Pattern patternForTemplate(ItemStack template) {
        if (template.isEmpty()) {
            return null;
        }
        for (Pattern p : PATTERNS.values()) {
            if (p.template() == template.getItem()) {
                return p;
            }
        }
        return null;
    }

    /** @return the material this addition is, or null */
    @Nullable
    public static synchronized Material material(ItemStack addition) {
        for (Material m : MATERIALS.values()) {
            if (m.matches(addition)) {
                return m;
            }
        }
        return null;
    }

    /** @return true if this stack can take a trim: any armor piece (1.20's {@code trimmable_armor}), less exclusions */
    public static synchronized boolean isTrimmable(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) {
            return false;
        }
        for (Predicate<ItemStack> p : UNTRIMMABLE) {
            if (p.test(stack)) {
                return false;
            }
        }
        return true;
    }

    /** @return the stack's trim, or null when it has none or either part is not registered in this game */
    @Nullable
    public static Trim get(ItemStack stack) {
        NBTTagCompound tag = stack.isEmpty() ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound trim = tag.getCompoundTag(TAG);
        Pattern pattern;
        Material material;
        synchronized (ArmorTrims.class) {
            pattern = PATTERNS.get(trim.getString(PATTERN));
            material = MATERIALS.get(trim.getString(MATERIAL));
        }
        return pattern == null || material == null ? null : new Trim(pattern, material);
    }

    /** @return a copy of {@code armor}, one item, with this trim */
    public static ItemStack apply(ItemStack armor, Pattern pattern, Material material) {
        ItemStack out = armor.copy();
        out.setCount(1);
        NBTTagCompound trim = new NBTTagCompound();
        trim.setString(PATTERN, pattern.id.toString());
        trim.setString(MATERIAL, material.id.toString());
        if (!out.hasTagCompound()) {
            out.setTagCompound(new NBTTagCompound());
        }
        out.getTagCompound().setTag(TAG, trim);
        return out;
    }

    /**
     * 1.20's {@code SmithingTrimRecipe.assemble}: the base with the template's pattern in the addition's material;
     * nothing if the base already has exactly that trim.
     */
    static ItemStack applyFromTable(ItemStack template, ItemStack base, ItemStack addition) {
        Pattern pattern = patternForTemplate(template);
        Material material = material(addition);
        if (pattern == null || material == null || !isTrimmable(base)) {
            return ItemStack.EMPTY;
        }
        Trim now = get(base);
        if (now != null && now.pattern == pattern && now.material == material) {
            return ItemStack.EMPTY;
        }
        return apply(base, pattern, material);
    }

    /** @return true if the stack's tooltip shows its trim (1.20's hide flag 128 is clear) */
    public static boolean showsInTooltip(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey("HideFlags", Constants.NBT.TAG_ANY_NUMERIC)
                || (tag.getInteger("HideFlags") & HIDE_UPGRADES) == 0;
    }

    /** One pattern's template, as an ingredient that finds the item when asked. */
    private static final class TemplateIngredient extends Ingredient {

        private final Pattern pattern;

        TemplateIngredient(Pattern pattern) {
            super(0);
            this.pattern = pattern;
        }

        @Override
        public boolean apply(@Nullable ItemStack stack) {
            return stack != null && !stack.isEmpty() && this.pattern.template() == stack.getItem();
        }

        @Override
        public ItemStack[] getMatchingStacks() {
            Item item = this.pattern.template();
            return item == null ? new ItemStack[0] : new ItemStack[] {new ItemStack(item)};
        }

        @Override
        public boolean isSimple() {
            return false;
        }
    }
}
