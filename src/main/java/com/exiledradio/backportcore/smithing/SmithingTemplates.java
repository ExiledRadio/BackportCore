package com.exiledradio.backportcore.smithing;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * 1.20's smithing table: a template, a base and an addition make the result.
 *
 * <h2>What 1.20 does</h2>
 * The table has three inputs. A <b>transform</b> (the netherite upgrade) turns the base into another item and keeps
 * everything the base carried; a <b>trim</b> writes an armor trim onto the base ({@link ArmorTrims}). Every 1.20
 * recipe uses up one template. There is no two-slot smithing in 1.20.
 *
 * <h2>Who does what</h2>
 * <ul>
 * <li>A backport that brings templates (Trails &amp; Tales; later Tricky Trials for bolt and flow) says which item
 * is the netherite upgrade template with {@link #requireForUpgrades} and adds its recipes with
 * {@link #addTransform} and {@link #addTrim}. {@link ArmorTrims#registerPattern} adds its pattern's trim recipe
 * itself.</li>
 * <li>The mod that owns a table says its table has the template slot with {@link #declareTableSupport}, and asks
 * {@link #find}.</li>
 * <li>{@link #list} is for a recipe viewer.</li>
 * </ul>
 *
 * <h2>Netherite upgrades</h2>
 * {@link SmithingUpgrades} keeps gathering diamond-to-netherite upgrades from every netherite mod. While
 * {@link #upgradesNeedTemplate()} is true they are offered here only as upgrade template + base + ingot, and
 * {@link SmithingUpgrades#find} answers null, so no two-slot table can skip the template. With no table declaring
 * support, nothing changes: an older two-slot table keeps making netherite, and simply has no trims.
 */
public final class SmithingTemplates {

    /** Makes the trimmed copy of a base, or {@link ItemStack#EMPTY} when these three make nothing. */
    @FunctionalInterface
    public interface TrimApplier {
        ItemStack apply(ItemStack template, ItemStack base, ItemStack addition);
    }

    /** What three inputs make. */
    public static final class Result {

        private final ItemStack result;
        private final boolean consumesTemplate;

        Result(ItemStack result, boolean consumesTemplate) {
            this.result = result;
            this.consumesTemplate = consumesTemplate;
        }

        /** A new stack, the caller's to keep. */
        public ItemStack result() {
            return this.result.copy();
        }

        /** True for every 1.20 recipe; false only for a netherite upgrade made without a template (setting off). */
        public boolean consumesTemplate() {
            return this.consumesTemplate;
        }
    }

    /** One recipe, for a recipe viewer: example inputs and what they make. */
    public static final class Recipe {

        public final List<ItemStack> templates;
        public final List<ItemStack> bases;
        public final List<ItemStack> additions;
        /** The result for the first base and addition. */
        public final ItemStack example;
        public final boolean trim;
        public final String source;

        Recipe(List<ItemStack> templates, List<ItemStack> bases, List<ItemStack> additions, ItemStack example,
               boolean trim, String source) {
            this.templates = templates;
            this.bases = bases;
            this.additions = additions;
            this.example = example;
            this.trim = trim;
            this.source = source;
        }
    }

    private static final class Transform {
        final Ingredient template;
        final Ingredient base;
        final Ingredient addition;
        final ItemStack result;
        final String source;

        Transform(Ingredient template, Ingredient base, Ingredient addition, ItemStack result, String source) {
            this.template = template;
            this.base = base;
            this.addition = addition;
            this.result = result;
            this.source = source;
        }
    }

    private static final class Trim {
        final Ingredient template;
        final Predicate<ItemStack> base;
        final Predicate<ItemStack> addition;
        final TrimApplier applier;
        final String source;

        Trim(Ingredient template, Predicate<ItemStack> base, Predicate<ItemStack> addition, TrimApplier applier, String source) {
            this.template = template;
            this.base = base;
            this.addition = addition;
            this.applier = applier;
            this.source = source;
        }
    }

    private static final List<Transform> TRANSFORMS = new ArrayList<Transform>();
    private static final List<Trim> TRIMS = new ArrayList<Trim>();
    private static final Set<String> TABLES = new LinkedHashSet<String>();
    private static final Set<String> TEMPLATE_MODS = new LinkedHashSet<String>();

    @Nullable
    private static Ingredient upgradeTemplate;
    private static BooleanSupplier upgradeTemplateRequired = () -> false;

    private SmithingTemplates() {
    }

    // ---------------------------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------------------------

    /**
     * The netherite upgrade template, from the mod that brings it. Every {@link SmithingUpgrades} upgrade then takes
     * it in the template slot.
     *
     * @param required whether an upgrade needs the template (1.20's rule; a setting of the template's mod)
     */
    public static synchronized void requireForUpgrades(String modId, Ingredient template, BooleanSupplier required) {
        upgradeTemplate = template;
        upgradeTemplateRequired = required;
        TEMPLATE_MODS.add(modId);
    }

    /** A transform: template + base + addition make {@code result}, which keeps the base's tag and damage. */
    public static synchronized void addTransform(Ingredient template, Ingredient base, Ingredient addition,
                                                 ItemStack result, String source) {
        TRANSFORMS.add(new Transform(template, base, addition, result.copy(), source));
        TEMPLATE_MODS.add(source);
    }

    /** A trim recipe: the applier makes the trimmed copy. */
    public static synchronized void addTrim(Ingredient template, Predicate<ItemStack> base, Ingredient addition,
                                            TrimApplier applier, String source) {
        addTrim(template, base, (Predicate<ItemStack>) addition::apply, applier, source);
    }

    /** As the other {@code addTrim}, with the addition as a test (trim materials are found by ore name too). */
    public static synchronized void addTrim(Ingredient template, Predicate<ItemStack> base, Predicate<ItemStack> addition,
                                            TrimApplier applier, String source) {
        TRIMS.add(new Trim(template, base, addition, applier, source));
        TEMPLATE_MODS.add(source);
    }

    /** From the mod that owns a smithing table, in pre-init: its table has 1.20's template slot and asks {@link #find}. */
    public static synchronized void declareTableSupport(String tableModId) {
        if (TABLES.add(tableModId)) {
            BackportCore.LOGGER.info("Smithing table with a template slot: {}.", tableModId);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // What a table and a recipe viewer read
    // ---------------------------------------------------------------------------------------------

    /** @return true if some mod brings templates and some table has the slot for them */
    public static synchronized boolean templateSlot() {
        return !TEMPLATE_MODS.isEmpty() && !TABLES.isEmpty();
    }

    /** @return the tables that declared support */
    public static synchronized Set<String> tables() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(TABLES));
    }

    /** @return true if netherite upgrades need the upgrade template (and so never work in two slots) */
    public static synchronized boolean upgradesNeedTemplate() {
        return templateSlot() && upgradeTemplate != null && upgradeTemplateRequired.getAsBoolean();
    }

    /** @return true if this goes in the template slot */
    public static synchronized boolean isTemplate(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (upgradeTemplate != null && upgradeTemplate.apply(stack)) {
            return true;
        }
        for (Transform t : TRANSFORMS) {
            if (t.template.apply(stack)) {
                return true;
            }
        }
        for (Trim t : TRIMS) {
            if (t.template.apply(stack)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if some recipe takes this as its base (1.20's quick-move sorting) */
    public static synchronized boolean isBase(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (upgradeTemplate != null && SmithingUpgrades.isUpgradable(stack)) {
            return true;
        }
        for (Transform t : TRANSFORMS) {
            if (t.base.apply(stack)) {
                return true;
            }
        }
        for (Trim t : TRIMS) {
            if (t.base.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if some recipe takes this as its addition */
    public static synchronized boolean isAddition(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (upgradeTemplate != null && SmithingUpgrades.isIngot(stack)) {
            return true;
        }
        for (Transform t : TRANSFORMS) {
            if (t.addition.apply(stack)) {
                return true;
            }
        }
        for (Trim t : TRIMS) {
            if (t.addition.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if this template's recipes are trims (for the screen's empty-slot icons and hints) */
    public static synchronized boolean isTrimTemplate(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Trim t : TRIMS) {
            if (t.template.apply(stack)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if this is the netherite upgrade template */
    public static synchronized boolean isUpgradeTemplate(ItemStack stack) {
        return !stack.isEmpty() && upgradeTemplate != null && upgradeTemplate.apply(stack);
    }

    /**
     * @return what these three inputs make, or null. The template may be empty only for a netherite upgrade while
     * {@link #upgradesNeedTemplate()} is false.
     */
    @Nullable
    public static synchronized Result find(ItemStack template, ItemStack base, ItemStack addition) {
        if (base.isEmpty() || addition.isEmpty()) {
            return null;
        }
        if (!template.isEmpty()) {
            for (Transform t : TRANSFORMS) {
                if (t.template.apply(template) && t.base.apply(base) && t.addition.apply(addition)) {
                    return new Result(carryOver(base, t.result.copy()), true);
                }
            }
            for (Trim t : TRIMS) {
                if (t.template.apply(template) && t.base.test(base) && t.addition.test(addition)) {
                    ItemStack out = t.applier.apply(template, base, addition);
                    if (!out.isEmpty()) {
                        return new Result(out, true);
                    }
                }
            }
        }
        boolean withTemplate = upgradeTemplate != null && !template.isEmpty() && upgradeTemplate.apply(template);
        boolean withoutTemplate = template.isEmpty() && !upgradesNeedTemplate();
        if (withTemplate || withoutTemplate) {
            SmithingUpgrades.Upgrade upgrade = SmithingUpgrades.findAny(base, addition);
            if (upgrade != null) {
                return new Result(carryOver(base, upgrade.result()), withTemplate);
            }
        }
        return null;
    }

    /**
     * 1.20's transform: the result keeps everything the base carried (enchantments, name, repair cost, trim) and its
     * damage. 1.20 copies the whole tag across, damage included; in 1.12.2 damage is the metadata, so it is copied
     * separately.
     */
    public static ItemStack carryOver(ItemStack base, ItemStack out) {
        NBTTagCompound tag = base.getTagCompound();
        if (tag != null) {
            NBTTagCompound merged = out.hasTagCompound() ? out.getTagCompound().copy() : new NBTTagCompound();
            merged.merge(tag);
            out.setTagCompound(merged);
        }
        if (out.isItemStackDamageable() && base.isItemStackDamageable()) {
            out.setItemDamage(Math.min(base.getItemDamage(), out.getMaxDamage() - 1));
        }
        return out;
    }

    /** Every recipe, for a recipe viewer: transforms, netherite upgrades with the template, then trims. */
    public static synchronized List<Recipe> list() {
        List<Recipe> list = new ArrayList<Recipe>();
        for (Transform t : TRANSFORMS) {
            List<ItemStack> bases = stacks(t.base);
            List<ItemStack> additions = stacks(t.addition);
            ItemStack example = bases.isEmpty() ? ItemStack.EMPTY : carryOver(bases.get(0), t.result.copy());
            list.add(new Recipe(stacks(t.template), bases, additions, example, false, t.source));
        }
        if (upgradeTemplate != null) {
            for (SmithingUpgrades.Upgrade u : SmithingUpgrades.listAny()) {
                List<ItemStack> additions = u.additions();
                list.add(new Recipe(stacks(upgradeTemplate), Collections.singletonList(new ItemStack(u.base)), additions,
                        u.result(), false, u.source));
            }
        }
        for (Trim t : TRIMS) {
            List<ItemStack> templates = stacks(t.template);
            list.add(new Recipe(templates, Collections.<ItemStack>emptyList(), Collections.<ItemStack>emptyList(),
                    ItemStack.EMPTY, true, t.source));
        }
        return list;
    }

    private static List<ItemStack> stacks(Ingredient ingredient) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ItemStack s : ingredient.getMatchingStacks()) {
            out.add(s.copy());
        }
        return out;
    }

    /** @return an ingredient of exactly these items (any metadata) */
    public static Ingredient of(Item... items) {
        return Ingredient.fromItems(items);
    }
}
