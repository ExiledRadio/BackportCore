package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.worldupgrade.WorldOptimizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.List;

/**
 * Edit World's Optimize World (1.13): {@link Confirm} (1.13's {@code GuiConfirmBackup} as Optimize World asks it:
 * "Optimize World", its warning, Create Backup and Load / I know what I'm doing! / Cancel; 1.14's Erase Cached Data
 * box; 1.21.2's "Create Backup and Optimize"), then {@link Progress} (1.13's {@code GuiOptimizeWorld}: the counts, a
 * bar per dimension in its color, the stage, the chunk counter and percentage, Cancel). Since Core 1.4.4.
 */
@SideOnly(Side.CLIENT)
public final class OptimizeWorldScreens {

    /** For the client self-test: optimizers started. */
    public static volatile int started;

    private OptimizeWorldScreens() {
    }

    /** @return whether Edit World has Optimize World (1.13's look or newer) */
    public static boolean available() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.13");
    }

    /** 1.21.2 (24w33a): the proceed button says what it does. */
    static String proceedLabel() {
        return I18n.format(Presentation.isAtLeast(LookArea.MENUS, "1.21.2") ? "optimizeWorld.confirm.proceed" : "selectWorld.backupJoinConfirmButton");
    }

    /** 1.14 (18w43a): the Erase Cached Data box. */
    static boolean eraseCacheBox() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.14");
    }

    /** The world's name as the world list shows it. */
    static String worldName(String worldId) {
        WorldInfo info = Minecraft.getMinecraft().getSaveLoader().getWorldInfo(worldId);
        return info != null ? info.getWorldName() : worldId;
    }

    /** {@code BackupConfirmScreen} for Optimize World. */
    public static final class Confirm extends GuiScreen {
        private final GuiScreen editWorld;
        private final GuiScreen worldList;
        private final String worldId;
        private List<String> lines;
        private MultiplayerWarningScreen.Checkbox eraseCache;
        private boolean erase;

        public Confirm(GuiScreen editWorld, GuiScreen worldList, String worldId) {
            this.editWorld = editWorld;
            this.worldList = worldList;
            this.worldId = worldId;
        }

        @Override
        public void initGui() {
            this.buttonList.clear();
            this.lines = this.fontRenderer.listFormattedStringToWidth(I18n.format("optimizeWorld.confirm.description"), this.width - 50);
            int text = (this.lines.size() + 1) * 9;
            this.eraseCache = new MultiplayerWarningScreen.Checkbox(3, this.width / 2 - 155 + 80, 76 + text, 150,
                    I18n.format("selectWorld.backupEraseCache"), this.erase);
            if (eraseCacheBox()) {
                this.buttonList.add(this.eraseCache);
            }
            this.buttonList.add(new GuiButton(0, this.width / 2 - 155, 100 + text, 150, 20, proceedLabel()));
            this.buttonList.add(new GuiButton(1, this.width / 2 - 155 + 160, 100 + text, 150, 20, I18n.format("selectWorld.backupJoinSkipButton")));
            this.buttonList.add(new GuiButton(2, this.width / 2 - 155 + 80, 124 + text, 150, 20, I18n.format("gui.cancel")));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0 || button.id == 1) {
                proceed(button.id == 0, this.erase && eraseCacheBox());
            } else if (button.id == 2) {
                this.mc.displayGuiScreen(this.editWorld);
            } else if (button.id == 3) {
                this.erase = !this.erase;
                this.eraseCache.selected = this.erase;
            }
        }

        /** The listener: a backup first if asked, then the progress screen. */
        public void proceed(boolean backup, boolean eraseCache) {
            if (backup) {
                WorldBackups.backupWithToast(this.worldId);
            }
            this.mc.displayGuiScreen(new Progress(this.worldList, this.worldId, eraseCache));
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.mc.displayGuiScreen(this.editWorld);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(this.fontRenderer, I18n.format("optimizeWorld.confirm.title"), this.width / 2, 50, 0xFFFFFF);
            int y = 70;
            for (String line : this.lines) {
                drawCenteredString(this.fontRenderer, line, this.width / 2, y, 0xFFFFFF);
                y += 9;
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        public List<GuiButton> buttons() {
            return this.buttonList;
        }
    }

    /** {@code OptimizeWorldScreen}. */
    public static final class Progress extends GuiScreen {
        private final GuiScreen worldList;
        private final String title;
        private final WorldOptimizer optimizer;
        private boolean left;

        public Progress(GuiScreen worldList, String worldId, boolean eraseCache) {
            this.worldList = worldList;
            this.title = I18n.format("optimizeWorld.title", worldName(worldId));
            File folder = new File(new File(Minecraft.getMinecraft().gameDir, "saves"), worldId);
            this.optimizer = new WorldOptimizer(folder, FMLCommonHandler.instance().getDataFixer(), eraseCache).start();
            started++;
        }

        public WorldOptimizer optimizer() {
            return this.optimizer;
        }

        @Override
        public void initGui() {
            this.buttonList.clear();
            this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 4 + 150, 200, 20, I18n.format("gui.cancel")));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0) {
                this.optimizer.cancel();
                leave();
            }
        }

        @Override
        public void updateScreen() {
            if (this.optimizer.isFinished()) {
                leave();
            }
        }

        private void leave() {
            if (!this.left) {
                this.left = true;
                this.mc.displayGuiScreen(this.worldList);
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            // shouldCloseOnEsc: false.
        }

        @Override
        public void onGuiClosed() {
            this.optimizer.cancel();
        }

        /** 1.16.5's {@code DIMENSION_COLORS}. */
        static int color(int dimension) {
            switch (dimension) {
                case 0: return 0xFF336622;
                case -1: return 0xFF663333;
                case 1: return 0xFF777755;
                default: return 0xFFDDDDDD;
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(this.fontRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
            int x0 = this.width / 2 - 150;
            int x1 = this.width / 2 + 150;
            int y0 = this.height / 4 + 100;
            int y1 = y0 + 10;
            int gray = 0xA0A0A0;
            drawCenteredString(this.fontRenderer, I18n.format(this.optimizer.stage().langKey), this.width / 2, y0 - 9 - 2, gray);
            int total = this.optimizer.total();
            if (total > 0) {
                drawRect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
                drawString(this.fontRenderer, I18n.format("optimizeWorld.info.converted", this.optimizer.converted()), x0, 40, gray);
                drawString(this.fontRenderer, I18n.format("optimizeWorld.info.skipped", this.optimizer.skipped()), x0, 40 + 9 + 3, gray);
                drawString(this.fontRenderer, I18n.format("optimizeWorld.info.total", total), x0, 40 + (9 + 3) * 2, gray);
                int done = 0;
                for (WorldOptimizer.Dimension d : this.optimizer.dimensions()) {
                    int length = MathHelper.floor(d.progress() * (x1 - x0));
                    drawRect(x0 + done, y0, x0 + done + length, y1, color(d.id));
                    done += length;
                }
                int count = this.optimizer.converted() + this.optimizer.skipped();
                drawCenteredString(this.fontRenderer, I18n.format("optimizeWorld.progress.counter", count, total), this.width / 2, y0 + 2 * 9 + 2, gray);
                drawCenteredString(this.fontRenderer, I18n.format("optimizeWorld.progress.percentage", MathHelper.floor(this.optimizer.progress() * 100.0F)),
                        this.width / 2, y0 + (y1 - y0) / 2 - 9 / 2, gray);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }
}
