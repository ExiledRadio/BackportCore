package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The End credits' text as 1.17's {@code WinScreen} reads it, once a backport supplies {@code texts/credits.json}
 * (Core ships no Mojang text): the End Poem when it is the poem's turn, then the credits file's sections, then
 * {@code texts/postcredits.txt} (when supplied) as the poem is read. 1.12.2's screen draws and scrolls them; Core only
 * fills its {@code lines} before its {@code initGui}, which then keeps them.
 * <p>
 * Each section (26.3's {@code addCreditsFile}): a white {@code ============} line, the section's name in yellow and
 * another {@code ============}, all centered, two empty lines; each discipline's name centered in yellow (when it has
 * one) and two empty lines; each title in grey, its names in white under it, two empty lines. 1.12.2's own
 * {@code credits.txt} lines are followed by an empty line each; the JSON's are not.
 */
@SideOnly(Side.CLIENT)
public final class Credits {

    static final String CREDITS = "texts/credits.json";
    static final String POSTCREDITS = "texts/postcredits.txt";
    /** 1.12.2's column ({@code GuiWinGame}): the lines are wrapped to it and centered in it. */
    static final int WIDTH = 274;

    /** For the client self-test: the lines Core gave the last credits screen. */
    public static volatile List<String> lastLines;

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiWinGame) || event.getGui().getClass() != GuiWinGame.class
                || !VanillaRevisions.jsonCredits() || Presentation.asset(CREDITS) == null) {
            return;
        }
        GuiWinGame gui = (GuiWinGame) event.getGui();
        try {
            boolean poem = ObfuscationReflectionHelper.<Boolean, GuiWinGame>getPrivateValue(GuiWinGame.class, gui, "field_193980_h");
            List<String> lines = build(Minecraft.getMinecraft(), poem);
            if (lines == null) {
                return;
            }
            ObfuscationReflectionHelper.setPrivateValue(GuiWinGame.class, gui, lines, "field_146582_i");
            ObfuscationReflectionHelper.setPrivateValue(GuiWinGame.class, gui, lines.size() * 12, "field_146579_r");
            lastLines = lines;
        } catch (RuntimeException e) {
            BackportCore.LOGGER.error("Presentation: couldn't load the credits from {}; 1.12.2's are shown", CREDITS, e);
        }
    }

    /** @return the screen's lines ("[C]" before a centered one), or null when the credits file can't be read */
    static List<String> build(Minecraft mc, boolean poem) {
        List<String> lines = new ArrayList<String>();
        try {
            if (poem) {
                poem(mc, lines, "texts/end.txt");
            }
            credits(mc, lines);
            if (Presentation.asset(POSTCREDITS) != null) {
                poem(mc, lines, POSTCREDITS);
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: couldn't read the credits", e);
            return null;
        }
        return lines;
    }

    /** 1.12.2's (and 26.3's) {@code addPoemFile}: the player's name, the obfuscated words, eight empty lines after. */
    static void poem(Minecraft mc, List<String> lines, String path) throws Exception {
        String token = "" + TextFormatting.WHITE + TextFormatting.OBFUSCATED + TextFormatting.GREEN + TextFormatting.AQUA;
        Random random = new Random(8124371L);
        IResource resource = mc.getResourceManager().getResource(new ResourceLocation(path));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("PLAYERNAME", mc.getSession().getUsername());
                int i;
                while ((i = line.indexOf(token)) != -1) {
                    String before = line.substring(0, i);
                    String after = line.substring(i + token.length());
                    line = before + TextFormatting.WHITE + TextFormatting.OBFUSCATED + "XXXXXXXX".substring(0, random.nextInt(4) + 3) + after;
                }
                lines.addAll(mc.fontRenderer.listFormattedStringToWidth(line, WIDTH));
                lines.add("");
            }
        } finally {
            resource.close();
        }
        for (int k = 0; k < 8; k++) {
            lines.add("");
        }
    }

    static void credits(Minecraft mc, List<String> lines) throws Exception {
        IResource resource = mc.getResourceManager().getResource(new ResourceLocation(CREDITS));
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            add(new JsonParser().parse(reader).getAsJsonArray(), lines);
        } finally {
            resource.close();
        }
    }

    /** 26.3's {@code addCreditsFile} over the parsed file. */
    static void add(JsonArray sections, List<String> lines) {
        String heading = TextFormatting.WHITE + "============";
        for (JsonElement s : sections) {
            JsonObject section = s.getAsJsonObject();
            lines.add("[C]" + heading);
            lines.add("[C]" + TextFormatting.YELLOW + section.get("section").getAsString());
            lines.add("[C]" + heading);
            lines.add("");
            lines.add("");
            for (JsonElement d : section.getAsJsonArray("disciplines")) {
                JsonObject discipline = d.getAsJsonObject();
                String name = discipline.get("discipline").getAsString();
                if (!name.isEmpty()) {
                    lines.add("[C]" + TextFormatting.YELLOW + name);
                    lines.add("");
                    lines.add("");
                }
                for (JsonElement t : discipline.getAsJsonArray("titles")) {
                    JsonObject title = t.getAsJsonObject();
                    lines.add(TextFormatting.GRAY + title.get("title").getAsString());
                    for (JsonElement n : title.getAsJsonArray("names")) {
                        lines.add(TextFormatting.WHITE + n.getAsString());
                    }
                    lines.add("");
                    lines.add("");
                }
            }
        }
    }
}
