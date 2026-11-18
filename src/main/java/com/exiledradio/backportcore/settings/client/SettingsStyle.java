package com.exiledradio.backportcore.settings.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.settings.Family;
import com.exiledradio.backportcore.settings.SettingsModel;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The family look of the settings screen (dark panels, Core's lavender, each backport's own color, vanilla's
 * buttons on top) and the words it shows for a setting.
 */
@SideOnly(Side.CLIENT)
final class SettingsStyle {

    static final int PANEL = 0xC8110E17;
    static final int ROW = 0x40231E2F;
    static final int ROW_HOVER = 0x902D2740;
    static final int SELECTED = 0xB02D2740;
    static final int LINE = 0xFF3A3350;
    static final int TEXT = 0xECE8F6;
    static final int MUTED = 0x9A92B0;
    static final int DIM = 0x5E5870;
    static final int CORE = 0xB9A8FF;
    static final int RESTART = 0xF2B84B;
    static final int CLIENT = 0x7CBCFF;
    static final int SERVER = 0x86DC96;
    static final int CHUNKS = 0x62D6C6;
    static final int CHANGED = 0xFFD34D;
    static final int ERROR = 0xFF7070;

    private static final Map<String, ItemStack> ICONS = new HashMap<String, ItemStack>();

    private SettingsStyle() {
    }

    // --- words ------------------------------------------------------------------------------------------------

    static String name(SettingsModel.Entry e) {
        String key = e.langKey();
        return key != null && I18n.hasKey(key) ? I18n.format(key) : words(e.name);
    }

    static String name(SettingsModel.Group g) {
        if (g.langKey != null && I18n.hasKey(g.langKey)) {
            return I18n.format(g.langKey);
        }
        int dot = g.path.lastIndexOf('.');
        return words(dot < 0 ? g.path : g.path.substring(dot + 1));
    }

    /** @return the full description: the translated tooltip, else the comment from the {@code .cfg} file */
    static String description(SettingsModel.Entry e) {
        String key = e.langKey() + ".tooltip";
        return I18n.hasKey(key) ? lines(I18n.format(key)) : e.comment();
    }

    static String description(SettingsModel.Group g) {
        String key = g.langKey + ".tooltip";
        if (g.langKey != null && I18n.hasKey(key)) {
            return lines(I18n.format(key));
        }
        return g.comment;
    }

    /** Lang files write a tooltip's line breaks as {@code \n}, as Forge's config screen reads them. */
    private static String lines(String text) {
        return text.replace("\\n", "\n");
    }

    /** @return the row's one line: {@code <langKey>.short} if the mod wrote one, else the description's first sentence */
    static String shortLine(SettingsModel.Entry e) {
        String key = e.langKey() + ".short";
        return I18n.hasKey(key) ? I18n.format(key) : firstSentence(description(e));
    }

    static String shortLine(SettingsModel.Group g) {
        String key = g.langKey + ".short";
        return g.langKey != null && I18n.hasKey(key) ? I18n.format(key) : firstSentence(description(g));
    }

    /** @return the description's first sentence, on one line */
    static String firstSentence(String text) {
        String flat = text.replace('\n', ' ').trim();
        int end = flat.indexOf(". ");
        return end < 0 ? flat : flat.substring(0, end + 1);
    }

    /** @return an enum value's words: its lang key under the setting's, else the constant made readable */
    static String enumValue(SettingsModel.Entry e, String value) {
        String key = e.langKey() + "." + value.toLowerCase(Locale.ENGLISH);
        if (I18n.hasKey(key)) {
            return I18n.format(key);
        }
        // A version ("1.20.5", the Newest look and Panorama choices) reads as itself.
        return !value.isEmpty() && Character.isDigit(value.charAt(0)) ? value : words(value);
    }

    /** @return the value as the screen shows it */
    static String value(SettingsModel.Entry e, String[] v) {
        switch (e.type) {
            case BOOL:
                return I18n.format("true".equals(v[0]) ? "options.on" : "options.off");
            case ENUM:
                return enumValue(e, v[0]);
            case LIST:
                return v.length == 0 ? I18n.format("backportcore.settings.list.empty") : String.join(", ", v);
            default:
                return v[0];
        }
    }

    /** "rebuildUntradedVillagers" and "PLAYER_AFFECTED" to "Rebuild untraded villagers" and "Player affected". */
    static String words(String id) {
        StringBuilder b = new StringBuilder();
        boolean upper = id.equals(id.toUpperCase(Locale.ENGLISH));
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '.') {
                b.append(' ');
            } else if (!upper && Character.isUpperCase(c) && i > 0) {
                b.append(' ').append(Character.toLowerCase(c));
            } else {
                b.append(upper && i > 0 ? Character.toLowerCase(c) : c);
            }
        }
        String s = b.toString().replaceAll(" +", " ").trim();
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String trim(FontRenderer font, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        return font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth("..."))) + "...";
    }

    // --- drawing ----------------------------------------------------------------------------------------------

    static void frame(int x0, int y0, int x1, int y1, int color) {
        Gui.drawRect(x0, y0, x1, y0 + 1, color);
        Gui.drawRect(x0, y1 - 1, x1, y1, color);
        Gui.drawRect(x0, y0, x0 + 1, y1, color);
        Gui.drawRect(x1 - 1, y0, x1, y1, color);
    }

    /** @return the width drawn: a small label in a thin box of its color */
    static int badge(FontRenderer font, String text, int x, int y, int color) {
        int w = font.getStringWidth(text) + 4;
        frame(x, y, x + w, y + 10, 0xFF000000 | color);
        font.drawString(text, x + 2, y + 1, color);
        return w;
    }

    static int badgeWidth(FontRenderer font, String text) {
        return font.getStringWidth(text) + 4;
    }

    /** Draws a family icon, smoothly scaled. */
    static void familyIcon(Minecraft mc, Family.Member m, int x, int y, int size, float alpha) {
        ResourceLocation rl = new ResourceLocation(BackportCore.MOD_ID, "textures/gui/family/" + m.icon + ".png");
        mc.getTextureManager().bindTexture(rl);
        ITextureObject tex = mc.getTextureManager().getTexture(rl);
        if (tex != null) {
            tex.setBlurMipmap(true, false);
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** @return the item a {@code @Feature} names, or null */
    static ItemStack icon(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (ICONS.containsKey(id)) {
            return ICONS.get(id);
        }
        // "a|b": the first of several that is installed (Wild Update's goat horn, else wheat).
        if (id.indexOf('|') >= 0) {
            ItemStack stack = null;
            for (String one : id.split("\\|")) {
                if ((stack = icon(one.trim())) != null) {
                    break;
                }
            }
            ICONS.put(id, stack);
            return stack;
        }
        String name = id;
        int meta = 0;
        int at = id.indexOf('@');
        if (at >= 0) {
            name = id.substring(0, at);
            try {
                meta = Integer.parseInt(id.substring(at + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        Item item = item(name);
        if (item == null) {
            // A copy its owner registers instead (Buzzy Bees' campfire when Village & Pillage is installed): the same
            // name under another family mod, oldest update first, as the owners are.
            int colon = name.indexOf(':');
            String path = colon >= 0 ? name.substring(colon + 1) : name;
            String namespace = colon >= 0 ? name.substring(0, colon) : "minecraft";
            for (com.exiledradio.backportcore.settings.Family.Member m : com.exiledradio.backportcore.settings.Family.MEMBERS) {
                if (!m.modid.equals(namespace) && (item = item(m.modid + ":" + path)) != null) {
                    break;
                }
            }
        }
        ItemStack stack = item == null ? null : new ItemStack(item, 1, meta);
        ICONS.put(id, stack);
        return stack;
    }

    /** @return the registered item, or a registered block's item, by name; null for neither */
    private static Item item(String name) {
        Item item = Item.getByNameOrId(name);
        if (item == null || item == net.minecraft.init.Items.AIR) {
            Block block = Block.getBlockFromName(name);
            item = block == null ? null : Item.getItemFromBlock(block);
        }
        return item == null || item == net.minecraft.init.Items.AIR ? null : item;
    }

    static void item(Minecraft mc, ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }

    /** A small square in the page's color, where a feature names no item. */
    static void swatch(int x, int y, int color) {
        Gui.drawRect(x + 3, y + 3, x + 13, y + 13, 0xFF000000 | color);
        Gui.drawRect(x + 3, y + 3, x + 13, y + 4, 0x40FFFFFF);
        Gui.drawRect(x + 3, y + 12, x + 13, y + 13, 0x50000000);
    }
}
