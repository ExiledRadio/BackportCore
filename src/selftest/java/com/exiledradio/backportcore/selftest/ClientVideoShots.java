package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.block.BlockJigsaw;
import com.exiledradio.backportcore.block.CoreBlocks;
import com.exiledradio.backportcore.block.FrontAndTop;
import com.exiledradio.backportcore.block.JigsawNetwork;
import com.exiledradio.backportcore.block.TileEntityJigsaw;
import com.exiledradio.backportcore.block.client.JigsawClient;
import com.exiledradio.backportcore.block.client.JigsawScreen;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.FullscreenModes;
import com.exiledradio.backportcore.presentation.client.DropSwing;
import com.exiledradio.backportcore.presentation.LightmapColors;
import com.exiledradio.backportcore.presentation.client.ModernClouds;
import com.exiledradio.backportcore.presentation.client.ModernLightmap;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;
import com.exiledradio.backportcore.render.client.ChunkRebuildHooks;
import com.exiledradio.backportcore.render.client.ImprovedTransparency;
import com.exiledradio.backportcore.simulation.SimulationDistance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;
import org.lwjgl.opengl.DisplayMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Dev only, environment variable {@code BACKPORTCORE_VIDEOSHOTS=1} with {@code runClient -Pselftest}: Core 1.4.5's
 * Video Settings additions at a dummy 26.3. Video Settings (Display with Exclusive Fullscreen Mode, Quality &amp;
 * Performance with Chunk Builder, Simulation Distance and Improved Transparency), the mode list, the Chunk Builder and
 * its config as one setting, the option reaching the game's own server, and a world in the rain over water with
 * Improved Transparency off and on, and today's jigsaw screen (Core 1.4.5). Saves {@code screenshots/video-*.png}, logs {@code VIDEO SHOTS DONE pass=N fail=M}
 * and closes the game. The monitor's mode is never changed.
 */
@SideOnly(Side.CLIENT)
final class ClientVideoShots {

    static final String WORLD = "backportcore_videoshots";

    static boolean active() {
        return System.getenv("BACKPORTCORE_VIDEOSHOTS") != null;
    }

    static void register() {
        Presentation.version("26.3");
        MinecraftForge.EVENT_BUS.register(new ClientVideoShots());
    }

    private final List<String> names = new ArrayList<String>();
    private final List<Supplier<Boolean>> steps = new ArrayList<Supplier<Boolean>>();
    private int step = -1;
    private int ticks;
    private String shot;
    private int pass;
    private int fail;
    private int waited;
    private int framesAt;
    private String presetAt;

    private ClientVideoShots() {
        Minecraft mc = Minecraft.getMinecraft();
        add(null, () -> {
            check("26.3 is the newest", Presentation.isAtLeast("26.3"), Presentation.newest());
            check("P17 and P42 applied", "applied".equals(Asm.state("P17")) && "applied".equals(Asm.state("P42")),
                    Asm.state("P17") + " / " + Asm.state("P42"));
            check("the dev client draws with 1.12.2's renderer", VanillaRevisions.vanillaRenderer());
            check("shown: Simulation Distance, Chunk Builder, Improved Transparency, Exclusive Fullscreen Mode",
                    Presentation.SIMULATION_DISTANCE.isShown() && Presentation.PRIORITIZE_CHUNK_UPDATES.isShown()
                            && Presentation.IMPROVED_TRANSPARENCY.isShown() && FullscreenModes.shown());
            List<DisplayMode> modes = FullscreenModes.modes();
            check("the monitor lists its modes", !modes.isEmpty(), modes.size());
            String before = Presentation.FULLSCREEN_RESOLUTION.get();
            FullscreenModes.choose(-1);
            boolean current = FullscreenModes.index() == -1 && FullscreenModes.chosen() == null;
            FullscreenModes.choose(modes.size() - 1);
            String saved = Presentation.FULLSCREEN_RESOLUTION.get();
            boolean last = FullscreenModes.index() == modes.size() - 1 && saved.matches("\\d+x\\d+@\\d+:\\d+");
            Presentation.FULLSCREEN_RESOLUTION.set("1x1@1:24");
            boolean gone = FullscreenModes.index() == -1;
            Presentation.FULLSCREEN_RESOLUTION.set(before);
            check("modes: Current, the last mode saved as vanilla's 'WxH@R:B', a mode the monitor lacks is Current",
                    current && last && gone, saved);
            boolean sorted = true;
            for (int i = 1; i < modes.size(); i++) {
                DisplayMode a = modes.get(i - 1);
                DisplayMode b = modes.get(i);
                sorted &= a.getWidth() * a.getHeight() <= b.getWidth() * b.getHeight() || a.getBitsPerPixel() < b.getBitsPerPixel();
            }
            check("modes: lowest first, as GLFW lists them", sorted, modes.get(0) + " .. " + modes.get(modes.size() - 1));
            // The Chunk Builder and rendering.blockUpdates are one setting.
            String chunkAt = Presentation.PRIORITIZE_CHUNK_UPDATES.get();
            Presentation.PRIORITIZE_CHUNK_UPDATES.set("nearby");
            boolean nearby = CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.NEARBY && ChunkRebuildHooks.nearbyDistanceSq(768.0) == 768.0;
            Presentation.PRIORITIZE_CHUNK_UPDATES.set("none");
            boolean none = CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.NONE && !ChunkRebuildHooks.needsImmediateUpdate(true)
                    && ChunkRebuildHooks.nearbyDistanceSq(768.0) < 0.0;
            Presentation.PRIORITIZE_CHUNK_UPDATES.set("player_affected");
            boolean player = CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.PLAYER_AFFECTED && ChunkRebuildHooks.needsImmediateUpdate(true)
                    && ChunkRebuildHooks.nearbyDistanceSq(768.0) < 0.0;
            Presentation.PRIORITIZE_CHUNK_UPDATES.set(chunkAt);
            check("Chunk Builder: Fully Blocking, Threaded, Semi Blocking reach P17 and the config", nearby && none && player,
                    nearby + " " + none + " " + player);
            this.presetAt = Presentation.GRAPHICS_PRESET.get();
            return true;
        });
        add("video-display", () -> show(new GuiVideoSettings(new GuiMainMenu(), mc.gameSettings)));
        add(null, () -> {
            List<String> on = optionsOnScreen(mc);
            check("Video Settings: the mode slider and the three new options are on it", on.contains(Presentation.FULLSCREEN_RESOLUTION.id())
                    && on.contains(Presentation.SIMULATION_DISTANCE.id()) && on.contains(Presentation.PRIORITIZE_CHUNK_UPDATES.id())
                    && on.contains(Presentation.IMPROVED_TRANSPARENCY.id()), on);
            int mode = on.indexOf(Presentation.FULLSCREEN_RESOLUTION.id());
            check("Exclusive Fullscreen Mode comes first, 310 wide, with 26.3's label and tooltip", mode == 0
                    && button(mc, Presentation.FULLSCREEN_RESOLUTION.id()).width == 310
                    && button(mc, Presentation.FULLSCREEN_RESOLUTION.id()).displayString.startsWith("Exclusive Fullscreen Mode: ")
                    && "options.fullscreen.exclusive.mode.tooltip".equals(Presentation.FULLSCREEN_RESOLUTION.tooltipKey()),
                    button(mc, Presentation.FULLSCREEN_RESOLUTION.id()).displayString);
            int rd = on.indexOf(Presentation.PRIORITIZE_CHUNK_UPDATES.id());
            check("Quality: Chunk Builder, then Simulation Distance (26.3's order)", rd >= 0 && on.indexOf(Presentation.SIMULATION_DISTANCE.id()) == rd + 1, on);
            check("Simulation Distance's label", button(mc, Presentation.SIMULATION_DISTANCE.id()).displayString.startsWith("Simulation Distance: "),
                    button(mc, Presentation.SIMULATION_DISTANCE.id()).displayString);
            scroll(mc, 140);
            return true;
        });
        add("video-quality", () -> true);
        add(null, () -> {
            try {
                FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
            } catch (java.io.IOException e) {
                failed("delete world", e);
            }
            mc.displayGuiScreen(null);
            mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261122L, GameType.CREATIVE, true, false, WorldType.FLAT));
            return true;
        });
        add(null, () -> mc.world != null && mc.player != null && ++this.waited > 4);
        // Today's jigsaw block (Core 1.4.5): two in front of the player, one facing up (Joint Type shown), one north.
        add(null, () -> {
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            mc.gameSettings.pauseOnLostFocus = false;
            this.jigsawAt = new BlockPos(p.posX, 200, p.posZ).add(3, 0, 3);
            BlockPos at = this.jigsawAt;
            onServer(mc, () -> {
                // An operator: the world has no cheats.
                mc.getIntegratedServer().getPlayerList().addOp(p.getGameProfile());
                WorldServer w = mc.getIntegratedServer().getWorld(0);
                w.setBlockState(at, CoreBlocks.jigsaw.getDefaultState().withProperty(BlockJigsaw.ORIENTATION, FrontAndTop.UP_NORTH), 3);
                TileEntityJigsaw te = (TileEntityJigsaw) w.getTileEntity(at);
                te.setPool("minecraft:village/plains/streets");
                te.setName("minecraft:building_entrance");
                te.setTarget("minecraft:street");
                te.setFinalState("minecraft:dirt_path");
                te.setJoint(TileEntityJigsaw.Joint.ALIGNED);
                te.setSelectionPriority(2);
                te.setPlacementPriority(1);
                te.markDirty();
                w.notifyBlockUpdate(at, w.getBlockState(at), w.getBlockState(at), 3);
                w.setBlockState(at.east(2), CoreBlocks.jigsaw.getDefaultState().withProperty(BlockJigsaw.ORIENTATION, FrontAndTop.NORTH_UP), 3);
            });
            return true;
        });
        add(null, () -> pause(3));
        add(null, () -> {
            check("jigsaw: the player may use operator blocks", mc.player.canUseCommandBlock());
            this.waited = 0;
            return true;
        });
        add("jigsaw-screen", () -> {
            JigsawClient.open(this.jigsawAt);
            if (mc.currentScreen instanceof JigsawScreen) {
                this.waited = 0;
                return true;
            }
            if (++this.waited > 5) {
                check("jigsaw: the screen opens", false, mc.currentScreen);
                return true;
            }
            return false;
        });
        add(null, () -> {
            JigsawScreen s = (JigsawScreen) mc.currentScreen;
            int cx = s.width / 2;
            check("jigsaw screen: the block's fields", "minecraft:village/plains/streets".equals(s.poolEdit.getText())
                    && "minecraft:building_entrance".equals(s.nameEdit.getText()) && "minecraft:street".equals(s.targetEdit.getText())
                    && "minecraft:dirt_path".equals(s.finalStateEdit.getText()) && "2".equals(s.selectionPriorityEdit.getText())
                    && "1".equals(s.placementPriorityEdit.getText()), s.poolEdit.getText());
            check("jigsaw screen: 26.3's places", s.poolEdit.x == cx - 153 && s.poolEdit.y == 20 && s.poolEdit.width == 300
                    && s.finalStateEdit.y == 125 && s.selectionPriorityEdit.width == 98 && s.placementPriorityEdit.x == cx - 50
                    && s.jointButton.x == cx + 54 && s.jointButton.y == 160 && s.levelsSlider.x == cx - 154 && s.levelsSlider.y == 185
                    && s.keepButton.x == cx - 50 && s.generateButton.x == cx + 54 && s.doneButton.x == cx - 154 && s.doneButton.width == 150
                    && s.cancelButton.x == cx + 4 && s.cancelButton.y == 210, cx);
            check("jigsaw screen: labels (Aligned, Levels: 0, Keep Jigsaws: ON, Generate, Done, Cancel)",
                    "Aligned".equals(s.jointButton.displayString) && "Levels: 0".equals(s.levelsSlider.displayString)
                            && "Keep Jigsaws: ON".equals(s.keepButton.displayString) && "Generate".equals(s.generateButton.displayString)
                            && "Done".equals(s.doneButton.displayString) && "Cancel".equals(s.cancelButton.displayString),
                    s.jointButton.displayString + " | " + s.levelsSlider.displayString + " | " + s.keepButton.displayString);
            check("jigsaw screen: facing up, Joint Type shows", s.jointButton.visible && s.jointButton.enabled);
            check("jigsaw screen: Target Pool has the focus", s.poolEdit.isFocused());
            s.levelsSlider.setValue(1.0);
            boolean top = s.levels == 20 && "Levels: 20".equals(s.levelsSlider.displayString);
            s.levelsSlider.setValue(0.5);
            check("jigsaw screen: Levels runs 0 to 20", top && s.levels == 10, s.levels);
            s.nameEdit.setText("Not Valid");
            s.updateValidity();
            boolean off = !s.doneButton.enabled && !s.generateButton.enabled;
            s.nameEdit.setText("minecraft:building_entrance");
            s.updateValidity();
            check("jigsaw screen: Done and Generate wait for valid ids", off && s.doneButton.enabled && s.generateButton.enabled);
            JigsawNetwork.Set m = s.message();
            check("jigsaw screen: the message Done sends", "minecraft:village/plains/streets".equals(m.pool()) && m.selectionPriority() == 2
                    && m.placementPriority() == 1 && m.joint() == TileEntityJigsaw.Joint.ALIGNED, m.pool());
            s.selectionPriorityEdit.setText("7");
            press(s, s.doneButton);
            return true;
        });
        add(null, () -> pause(3));
        add(null, () -> {
            check("jigsaw screen: Done closes it", mc.currentScreen == null);
            TileEntityJigsaw server = (TileEntityJigsaw) mc.getIntegratedServer().getWorld(0).getTileEntity(this.jigsawAt);
            TileEntityJigsaw client = (TileEntityJigsaw) mc.world.getTileEntity(this.jigsawAt);
            check("jigsaw screen: Done sets the server's block, and the client hears back", server.getSelectionPriority() == 7
                    && client.getSelectionPriority() == 7, server.getSelectionPriority() + " / " + client.getSelectionPriority());
            JigsawClient.open(this.jigsawAt.east(2));
            return mc.currentScreen instanceof JigsawScreen;
        });
        add("jigsaw-screen-side", () -> {
            JigsawScreen s = (JigsawScreen) mc.currentScreen;
            check("jigsaw screen: facing sideways, no Joint Type; a new block's fields", !s.jointButton.visible
                    && "minecraft:empty".equals(s.poolEdit.getText()) && "minecraft:air".equals(s.finalStateEdit.getText())
                    && "0".equals(s.selectionPriorityEdit.getText()), s.poolEdit.getText());
            return true;
        });
        add(null, () -> {
            press(mc.currentScreen, ((JigsawScreen) mc.currentScreen).cancelButton);
            check("jigsaw screen: Cancel closes it", mc.currentScreen == null);
            this.waited = 5;
            return true;
        });
        add(null, () -> {
            mc.gameSettings.pauseOnLostFocus = false;
            int want = Math.max(2, (int) Math.round(Presentation.SIMULATION_DISTANCE.get()));
            check("Simulation Distance reaches the game's own server", SimulationDistance.distance() == want, SimulationDistance.distance() + " want " + want);
            check("P46 applied", "applied".equals(Asm.state("P46")), Asm.state("P46"));
            WorldServer w = mc.getIntegratedServer().getWorld(0);
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            BlockPos base = new BlockPos(p.posX, 120, p.posZ);
            // A basin of water 13 wide, 10 long, in front of the camera; stained glass along its far side.
            for (int x = -6; x <= 6; x++) {
                for (int z = 2; z <= 12; z++) {
                    w.setBlockState(base.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
                    w.setBlockState(base.add(x, 0, z), x == -6 || x == 6 || z == 12 ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), 2);
                    if (z == 12) {
                        for (int y = 1; y <= 4; y++) {
                            w.setBlockState(base.add(x, y, z), Blocks.STAINED_GLASS.getDefaultState(), 2);
                        }
                    }
                }
            }
            w.getWorldInfo().setRaining(true);
            w.getWorldInfo().setRainTime(200000);
            w.setRainStrength(1.0F);
            w.setWorldTime(6000L);
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
            p.connection.setPlayerLocation(base.getX() + 0.5, base.getY() + 5, base.getZ() - 1.5, 0.0F, 40.0F);
            mc.world.setRainStrength(1.0F);
            Presentation.IMPROVED_TRANSPARENCY.set(false);
            return true;
        });
        add(null, () -> ++this.waited > 10);
        add("transparency-off", () -> {
            mc.world.setRainStrength(1.0F);
            this.framesAt = ImprovedTransparency.frames;
            return true;
        });
        add(null, () -> {
            check("off: no second weather pass", ImprovedTransparency.frames == this.framesAt, ImprovedTransparency.frames - this.framesAt);
            Presentation.IMPROVED_TRANSPARENCY.set(true);
            mc.world.setRainStrength(1.0F);
            this.framesAt = ImprovedTransparency.frames;
            return true;
        });
        add("transparency-on", () -> true);
        add(null, () -> {
            check("on: the second weather pass runs each frame it rains", ImprovedTransparency.frames > this.framesAt,
                    ImprovedTransparency.frames - this.framesAt);
            Presentation.IMPROVED_TRANSPARENCY.set(false);
            Presentation.GRAPHICS_PRESET.set(this.presetAt);
            // 1.21.2's clouds: clear sky, noon, the camera under the layer looking up at it.
            WorldServer w = mc.getIntegratedServer().getWorld(0);
            w.getWorldInfo().setRaining(false);
            w.setRainStrength(0.0F);
            mc.world.setRainStrength(0.0F);
            mc.gameSettings.clouds = 2;
            this.waited = 0;
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            p.connection.setPlayerLocation(p.posX, 100, p.posZ, 30.0F, -35.0F);
            return true;
        });
        add(null, () -> pause(6));
        add(null, () -> {
            this.framesAt = ModernClouds.frames;
            return true;
        });
        add("clouds-below", () -> true);
        add(null, () -> {
            check("1.21.2's clouds draw at 26.3", VanillaRevisions.modernClouds() && ModernClouds.frames > this.framesAt,
                    (ModernClouds.frames - this.framesAt) + " frames, " + ModernClouds.quads + " quads");
            check("P26 applied (with the modern clouds' return)", "applied".equals(Asm.state("P26")), Asm.state("P26"));
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            p.connection.setPlayerLocation(p.posX, 160, p.posZ, 30.0F, 35.0F);
            return true;
        });
        add(null, () -> pause(4));
        add("clouds-above", () -> true);
        add(null, () -> {
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            p.connection.setPlayerLocation(p.posX, 129.5, p.posZ, 30.0F, 0.0F);
            return true;
        });
        add(null, () -> pause(4));
        add("clouds-inside", () -> true);
        add(null, () -> {
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            p.connection.setPlayerLocation(p.posX, 100, p.posZ, 30.0F, -35.0F);
            mc.gameSettings.clouds = 1;
            return true;
        });
        add(null, () -> pause(4));
        add("clouds-fast", () -> true);
        add(null, () -> {
            mc.gameSettings.clouds = 2;
            CoreConfig.lookAndFeel.sky = false;
            this.framesAt = ModernClouds.frames;
            return true;
        });
        add("clouds-1.12.2", () -> true);
        add(null, () -> {
            check("sky look off: 1.12.2's clouds", ModernClouds.frames == this.framesAt, ModernClouds.frames - this.framesAt);
            CoreConfig.lookAndFeel.sky = true;
            // 1.16: throwing from the inventory swings the arm. An apple in the first hotbar slot, then a THROW click on it.
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            p.inventory.setInventorySlotContents(0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.APPLE, 3));
            p.inventory.currentItem = 0;
            return true;
        });
        add(null, () -> pause(2));
        add(null, () -> {
            this.framesAt = DropSwing.swings;
            mc.player.swingProgressInt = 0;
            mc.player.isSwingInProgress = false;
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, 36, 0, net.minecraft.inventory.ClickType.THROW, mc.player);
            check("1.16: a throw from the inventory swings the arm", DropSwing.swings == this.framesAt + 1 && mc.player.isSwingInProgress,
                    (DropSwing.swings - this.framesAt) + " swings, swinging " + mc.player.isSwingInProgress);
            return true;
        });
        add(null, () -> {
            // 1.13's recipe toast: the crafting table behind the output (P66); toasts miss 1.12.2's screenshots, so this
            // draws a toast for a few frames (loading RecipeToast) and then checks the patch.
            check("1.13: the recipe toast draws its crafting table", com.exiledradio.backportcore.presentation.VanillaRevisions.recipeToastIcon(), "off");
            net.minecraft.item.crafting.IRecipe r = net.minecraft.item.crafting.CraftingManager.getRecipe(new net.minecraft.util.ResourceLocation("minecraft", "crafting_table"));
            this.framesAt = com.exiledradio.backportcore.presentation.client.PresentationHooks.recipeToastIcons;
            net.minecraft.client.gui.toasts.RecipeToast.addOrUpdate(mc.getToastGui(), r);
            return true;
        });
        add(null, () -> pause(1));
        add("!recipe-toast-crafting", () -> true);
        add(null, () -> {
            check("P66 applied", "applied".equals(Asm.state("P66")), Asm.state("P66"));
            check("1.13: the crafting toast drew its crafting table",
                    com.exiledradio.backportcore.presentation.client.PresentationHooks.recipeToastIcons > this.framesAt,
                    com.exiledradio.backportcore.presentation.client.PresentationHooks.recipeToastIcons - this.framesAt);
            mc.getToastGui().clear();
            // 1.14's smelting toast: each book's block behind the output (a smoker's, a blast furnace's, a campfire's).
            com.exiledradio.backportcore.recipe.book.client.SmeltingToast.lastIcon = net.minecraft.item.ItemStack.EMPTY;
            com.exiledradio.backportcore.recipe.book.client.SmeltingToast.addOrUpdate(mc.getToastGui(),
                    new net.minecraft.item.ItemStack(Blocks.FURNACE), new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT));
            return true;
        });
        add(null, () -> pause(1));
        add("!recipe-toast-smelting", () -> true);
        add(null, () -> {
            net.minecraft.item.ItemStack icon = com.exiledradio.backportcore.recipe.book.client.SmeltingToast.lastIcon;
            check("1.13: the smelting toast draws its block behind the output", icon.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.FURNACE)
                    && com.exiledradio.backportcore.recipe.book.client.SmeltingToast.lastOutput.getItem() == net.minecraft.init.Items.IRON_INGOT, icon);
            // The toasts' look off: 1.12.2's toast, the output alone, for both.
            CoreConfig.lookAndFeel.toasts = false;
            mc.getToastGui().clear();
            com.exiledradio.backportcore.recipe.book.client.SmeltingToast.addOrUpdate(mc.getToastGui(),
                    new net.minecraft.item.ItemStack(Blocks.FURNACE), new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT));
            this.framesAt = com.exiledradio.backportcore.presentation.client.PresentationHooks.recipeToastIcons;
            net.minecraft.client.gui.toasts.RecipeToast.addOrUpdate(mc.getToastGui(),
                    net.minecraft.item.crafting.CraftingManager.getRecipe(new net.minecraft.util.ResourceLocation("minecraft", "crafting_table")));
            return true;
        });
        add(null, () -> pause(1));
        add(null, () -> {
            check("toasts look off: no crafting table, no block",
                    com.exiledradio.backportcore.presentation.client.PresentationHooks.recipeToastIcons == this.framesAt
                    && com.exiledradio.backportcore.recipe.book.client.SmeltingToast.lastIcon.isEmpty(),
                    (com.exiledradio.backportcore.presentation.client.PresentationHooks.recipeToastIcons - this.framesAt) + " "
                    + com.exiledradio.backportcore.recipe.book.client.SmeltingToast.lastIcon);
            CoreConfig.lookAndFeel.toasts = true;
            mc.getToastGui().clear();
            return true;
        });
        add(null, () -> {
            // 1.18.2: a render chunk the frustum moves is marked for a rebuild (P67). Moves one 16 blocks up and back.
            check("P67 applied", "applied".equals(Asm.state("P67")), Asm.state("P67"));
            net.minecraft.client.renderer.ViewFrustum frustum = net.minecraftforge.fml.relauncher.ReflectionHelper
                    .getPrivateValue(net.minecraft.client.renderer.RenderGlobal.class, mc.renderGlobal, "viewFrustum", "field_175008_n");
            net.minecraft.client.renderer.chunk.RenderChunk rc = frustum.renderChunks[0];
            BlockPos at = rc.getPosition().toImmutable();
            rc.clearNeedsUpdate();
            rc.setPosition(at.getX(), at.getY() + 16, at.getZ());
            boolean moved = rc.needsUpdate();
            rc.setPosition(at.getX(), at.getY(), at.getZ());
            check("1.18.2: a moved render chunk is rebuilt", moved && rc.needsUpdate(), "needsUpdate after the move: " + moved);
            return true;
        });
        add(null, () -> {
            check("P65 applied", "applied".equals(Asm.state("P65")), Asm.state("P65"));
            // 26.1's lightmap beside 1.12.2's: the Overworld by day and by night, a torch-lit cave, the Nether, the End.
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            this.scene = new BlockPos(p.posX, 4, p.posZ).add(0, 0, 40);
            BlockPos scene = this.scene;
            // Torches on the flat ground, two rows down the view, and a dark stone room at the far end for the cave.
            onServer(mc, () -> {
                WorldServer w = mc.getIntegratedServer().getWorld(0);
                for (int z = 4; z <= 16; z += 6) {
                    w.setBlockState(scene.add(-3, 0, z), Blocks.TORCH.getDefaultState(), 3);
                    w.setBlockState(scene.add(3, 0, z), Blocks.TORCH.getDefaultState(), 3);
                }
                room(w, scene.add(0, 60, 0), Blocks.STONE.getDefaultState());
                w.setWorldTime(6000L);
                p.connection.setPlayerLocation(scene.getX() + 0.5, scene.getY() + 3, scene.getZ() + 0.5, 0.0F, 25.0F);
            });
            return true;
        });
        lightPair("day", 6, null);
        add(null, () -> {
            onServer(mc, () -> mc.getIntegratedServer().getWorld(0).setWorldTime(18000L));
            return true;
        });
        lightPair("night", 6, null);
        add(null, () -> {
            BlockPos scene = this.scene;
            onServer(mc, () -> {
                mc.getIntegratedServer().getWorld(0).setWorldTime(6000L);
                EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                p.connection.setPlayerLocation(scene.getX() + 0.5, scene.getY() + 61, scene.getZ() - 6.5, 0.0F, 20.0F);
            });
            return true;
        });
        add(null, () -> {
            // 1.12.2 leaves sky light in a room closed in the open air; relight it on both sides.
            BlockPos room = this.scene.add(0, 60, 0);
            onServer(mc, () -> relight(mc.getIntegratedServer().getWorld(0), room));
            relight(mc.world, room);
            return true;
        });
        lightPair("cave", 4, () -> {
            int sky = mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, new BlockPos(mc.player));
            check("cave: no sky light in the room", sky == 0, sky);
        });
        add(null, () -> {
            this.waited = 0;
            onServer(mc, () -> mc.getIntegratedServer().getPlayerList().getPlayers().get(0)
                    .changeDimension(-1, (world, entity, yaw) -> entity.setLocationAndAngles(8.5, 71, 0.5, 0.0F, 20.0F)));
            return true;
        });
        add(null, () -> mc.world != null && mc.world.provider.isNether() && mc.player != null && pause(4));
        add(null, () -> {
            // The room after arriving: 1.12.2 places a player who changes dimension once more after the teleporter.
            onServer(mc, () -> {
                WorldServer n = mc.getIntegratedServer().getWorld(-1);
                room(n, new BlockPos(8, 70, 8), Blocks.NETHERRACK.getDefaultState());
                n.setBlockState(new BlockPos(8, 70, 14), Blocks.GLOWSTONE.getDefaultState(), 3);
                EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                p.connection.setPlayerLocation(8.5, 71, 0.5, 0.0F, 20.0F);
            });
            return true;
        });
        lightPair("nether", 6, () -> check("Nether: darkness is 26.3's #302821 ambient light", cell(mc, 0) == expected(mc, "nether", 0),
                Integer.toHexString(cell(mc, 0)) + " want " + Integer.toHexString(expected(mc, "nether", 0))));
        add(null, () -> {
            this.waited = 0;
            onServer(mc, () -> mc.getIntegratedServer().getPlayerList().getPlayers().get(0)
                    .changeDimension(1, (world, entity, yaw) -> entity.setLocationAndAngles(200.5, 67, 199.5, 0.0F, 25.0F)));
            return true;
        });
        add(null, () -> mc.world != null && mc.world.provider.getDimension() == 1 && mc.player != null && pause(4));
        add(null, () -> {
            // An end stone floor far from the island (past the dragon fight's 192 blocks), torches down the view.
            onServer(mc, () -> {
                WorldServer e = mc.getIntegratedServer().getWorld(1);
                BlockPos at = new BlockPos(200, 64, 200);
                for (int x = -10; x <= 10; x++) {
                    for (int z = -2; z <= 18; z++) {
                        e.setBlockState(at.add(x, -1, z), Blocks.END_STONE.getDefaultState(), 2);
                        if (x == -10 || x == 10) {
                            for (int y = 0; y <= 3; y++) {
                                e.setBlockState(at.add(x, y, z), Blocks.END_STONE.getDefaultState(), 2);
                            }
                        }
                    }
                }
                for (int z = 4; z <= 16; z += 6) {
                    e.setBlockState(at.add(-3, 0, z), Blocks.TORCH.getDefaultState(), 3);
                    e.setBlockState(at.add(3, 0, z), Blocks.TORCH.getDefaultState(), 3);
                }
                EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                p.connection.setPlayerLocation(200.5, 67, 199.5, 0.0F, 25.0F);
            });
            return true;
        });
        add(null, () -> mc.world != null && mc.world.provider.getDimension() == 1 && mc.player != null && pause(8));
        lightPair("end", 6, () -> check("End: darkness is 26.3's #3f473f ambient light", cell(mc, 0) == expected(mc, "end", 0),
                Integer.toHexString(cell(mc, 0)) + " want " + Integer.toHexString(expected(mc, "end", 0))));
    }

    private BlockPos scene;

    /** Two shots of one place: {@code light-<name>-26.1} with the sky's look on, {@code -1.12.2} with it off. */
    private void lightPair(String name, int settle, Runnable checks) {
        Minecraft mc = Minecraft.getMinecraft();
        add(null, () -> {
            // A pause menu (the window lost focus) stops the ticks that ask for a lightmap update.
            mc.gameSettings.pauseOnLostFocus = false;
            if (mc.currentScreen != null) {
                mc.displayGuiScreen(null);
            }
            return pause(settle);
        });
        add(null, () -> {
            this.framesAt = ModernLightmap.frames;
            return true;
        });
        add("light-" + name + "-26.1", () -> true);
        add(null, () -> {
            check(name + ": 26.1's lightmap fills the frames", VanillaRevisions.modernLightmap() && ModernLightmap.frames > this.framesAt,
                    ModernLightmap.frames - this.framesAt);
            if (checks != null) {
                checks.run();
            }
            CoreConfig.lookAndFeel.sky = false;
            this.framesAt = ModernLightmap.frames;
            return true;
        });
        add(null, () -> pause(2));
        add("light-" + name + "-1.12.2", () -> true);
        add(null, () -> {
            check(name + ": sky look off, 1.12.2's lightmap", !VanillaRevisions.modernLightmap() && ModernLightmap.frames == this.framesAt,
                    ModernLightmap.frames - this.framesAt);
            CoreConfig.lookAndFeel.sky = true;
            return mc.world != null;
        });
    }

    /** A closed room, 13 by 6 by 17 inside, its door side toward -z, one torch on its floor at the far end. */
    private static void room(net.minecraft.world.World w, BlockPos at, net.minecraft.block.state.IBlockState wall) {
        for (int x = -7; x <= 7; x++) {
            for (int y = -1; y <= 6; y++) {
                for (int z = -10; z <= 8; z++) {
                    boolean shell = x == -7 || x == 7 || y == -1 || y == 6 || z == -10 || z == 8;
                    w.setBlockState(at.add(x, y, z), shell ? wall : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
        w.setBlockState(at.add(0, 0, 5), Blocks.TORCH.getDefaultState(), 3);
        w.setBlockState(at.add(-4, 0, -2), Blocks.TORCH.getDefaultState(), 3);
    }

    /** Runs world edits on the integrated server's thread (the client thread would race its chunk map). */
    private BlockPos jigsawAt;

    /** Presses a screen's button as a click does ({@code actionPerformed}). */
    private void press(net.minecraft.client.gui.GuiScreen screen, GuiButton button) {
        try {
            java.lang.reflect.Method m;
            try {
                m = net.minecraft.client.gui.GuiScreen.class.getDeclaredMethod("actionPerformed", GuiButton.class);
            } catch (NoSuchMethodException e) {
                m = net.minecraft.client.gui.GuiScreen.class.getDeclaredMethod("func_146284_a", GuiButton.class);
            }
            m.setAccessible(true);
            m.invoke(screen, button);
        } catch (ReflectiveOperationException e) {
            failed("press " + button.displayString, e);
        }
    }

    private static void onServer(Minecraft mc, Runnable task) {
        mc.getIntegratedServer().addScheduledTask(task);
    }

    /** Rechecks the sky light of every block inside {@link #room}, twice (the darkening spreads one pass at a time). */
    private static void relight(net.minecraft.world.World w, BlockPos at) {
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 5; y >= 0; y--) {
                for (int x = -6; x <= 6; x++) {
                    for (int z = -9; z <= 7; z++) {
                        w.checkLightFor(net.minecraft.world.EnumSkyBlock.SKY, at.add(x, y, z));
                    }
                }
            }
        }
    }

    private static int cell(Minecraft mc, int index) {
        int[] colors = ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.EntityRenderer.class, mc.entityRenderer,
                "lightmapColors", "field_78504_Q");
        return colors[index] & 0xFFFFFF;
    }

    /** @return a block 0 cell (no flicker in it) as {@link LightmapColors} gives it for the dimension at the Brightness option */
    private static int expected(Minecraft mc, String dimension, int index) {
        LightmapColors.Inputs in = new LightmapColors.Inputs();
        if ("nether".equals(dimension)) {
            LightmapColors.nether(in);
        } else {
            LightmapColors.end(in);
        }
        in.brightness = mc.gameSettings.gammaSetting;
        int[] out = new int[256];
        LightmapColors.compute(in, null, out);
        return out[index] & 0xFFFFFF;
    }

    /** @return the ids of Core's options with a button on the open screen, in order */
    private static List<String> optionsOnScreen(Minecraft mc) {
        List<String> out = new ArrayList<String>();
        for (GuiButton b : buttons(mc)) {
            if (b instanceof OptionWidgets.OptionButton) {
                out.add(((OptionWidgets.OptionButton) b).option().id());
            }
        }
        return out;
    }

    private static GuiButton button(Minecraft mc, String id) {
        for (GuiButton b : buttons(mc)) {
            if (b instanceof OptionWidgets.OptionButton && ((OptionWidgets.OptionButton) b).option().id().equals(id)) {
                return b;
            }
        }
        return new GuiButton(0, 0, 0, 0, 0, "(missing)");
    }

    private static List<GuiButton> buttons(Minecraft mc) {
        try {
            java.lang.reflect.Method rows = Class.forName("com.exiledradio.backportcore.presentation.client.OptionsInjector")
                    .getDeclaredMethod("rowButtons", net.minecraft.client.gui.GuiScreen.class);
            rows.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<GuiButton> list = (List<GuiButton>) rows.invoke(null, mc.currentScreen);
            return list == null ? new ArrayList<GuiButton>() : list;
        } catch (ReflectiveOperationException e) {
            return new ArrayList<GuiButton>();
        }
    }

    private static void scroll(Minecraft mc, int by) {
        Object list = ReflectionHelper.getPrivateValue(GuiVideoSettings.class, (GuiVideoSettings) mc.currentScreen, "optionsRowList", "field_146501_h");
        if (list instanceof GuiSlot) {
            ((GuiSlot) list).scrollBy(by);
        }
    }

    private static boolean show(net.minecraft.client.gui.GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
        return true;
    }

    /** @return true after {@code steps} calls, then starts over */
    private boolean pause(int steps) {
        if (++this.waited > steps) {
            this.waited = 0;
            return true;
        }
        return false;
    }

    private void backBuffer(Minecraft mc, java.io.File file) {
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        java.nio.IntBuffer buf = org.lwjgl.BufferUtils.createIntBuffer(w * h);
        org.lwjgl.opengl.GL11.glReadBuffer(org.lwjgl.opengl.GL11.GL_BACK);
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
        org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL12.GL_BGRA, org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, h - 1 - y, buf.get(y * w + x) & 0xFFFFFF);
            }
        }
        try {
            file.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", file);
        } catch (java.io.IOException e) {
            failed("back buffer shot", e);
        }
    }

    private void add(String name, Supplier<Boolean> step) {
        this.names.add(name);
        this.steps.add(step);
    }

    private void check(String what, boolean ok, Object detail) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("VIDEO PASS {} ({})", what, detail);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("VIDEO FAIL {} ({})", what, detail);
        }
    }

    private void check(String what, boolean ok) {
        check(what, ok, "");
    }

    private void failed(String what, Throwable t) {
        this.fail++;
        BackportCore.LOGGER.error("VIDEO FAIL {}", what, t);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.step < 0) {
            if (mc.currentScreen instanceof GuiMainMenu && ++this.ticks > 100) {
                this.step = 0;
                this.ticks = 0;
                run();
            }
            return;
        }
        if (this.shot != null || ++this.ticks < 20) {
            return;
        }
        this.ticks = 0;
        this.step++;
        if (this.step >= this.steps.size()) {
            BackportCore.LOGGER.info("VIDEO SHOTS DONE pass={} fail={}", this.pass, this.fail);
            mc.shutdown();
            return;
        }
        run();
    }

    private void run() {
        boolean done;
        try {
            done = this.steps.get(this.step).get();
        } catch (RuntimeException e) {
            failed("step " + this.step, e);
            done = true;
        }
        if (!done) {
            this.step--;
            return;
        }
        this.shot = this.names.get(this.step);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        if (++this.ticks < 10) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.shot.startsWith("!")) {
            // Toasts are drawn on the window after the framebuffer: read the back buffer.
            this.shot = this.shot.substring(1);
            backBuffer(mc, new java.io.File(mc.gameDir, "screenshots/video-" + this.shot + ".png"));
        } else {
            ScreenShotHelper.saveScreenshot(mc.gameDir, "video-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        }
        BackportCore.LOGGER.info("VIDEO SHOT {}", this.shot);
        this.shot = null;
        this.ticks = 0;
    }
}
