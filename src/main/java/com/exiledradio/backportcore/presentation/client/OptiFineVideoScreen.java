package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Video Settings when OptiFine has rewritten {@code GuiVideoSettings}: OptiFine's own screen (this extends it, so its
 * constructor, buttons, actions and submenus are OptiFine's), laid out as Core lays out vanilla's. OptiFine's screen is
 * a fixed grid of twelve settings and six submenu buttons with no scrolling list, so after OptiFine's {@code initGui}
 * its buttons move into a scrolling list as vanilla's {@code GuiOptionsRowList}: its settings with the backports'
 * options among them ({@link OptionsInjector#videoRows}, 1.21.11's sections when that is the newest version), then
 * OptiFine's Shaders, Quality, Details, Performance, Animations and Other, under the header "OptiFine". Pressing one
 * runs OptiFine's own action, so its submenus open with this screen as their parent and come back to it.
 *
 * <p>{@link OptionsInjector} opens this instead of OptiFine's screen when {@link #optiFineShape} holds: the class has
 * no list field, which is what OptiFine's rewrite looks like whatever its version calls things. If OptiFine's
 * {@code initGui} does not give the buttons expected (no Done, or a submenu missing), the screen stays as OptiFine
 * laid it out and Core adds nothing.
 */
@SideOnly(Side.CLIENT)
public class OptiFineVideoScreen extends GuiVideoSettings {

    static final int DONE = 200;
    /** OptiFine's submenu buttons, in its order: Shaders, Quality, Details, Performance, Animations, Other. */
    public static final int[] SUBMENUS = {231, 202, 201, 212, 211, 222};

    private static Boolean shape;
    private static Field parentField;
    private static boolean warned;
    private static Field tooltipField;
    private static Method drawTooltips;
    private static boolean tooltipsBroken;

    private final GameSettings settings;
    @Nullable
    private OptionRowList list;
    private final List<GuiButton> rowButtons = new ArrayList<GuiButton>();
    private final List<GuiButton> optiFineButtons = new ArrayList<GuiButton>();

    public OptiFineVideoScreen(GuiScreen parent, GameSettings settings) {
        super(parent, settings);
        this.settings = settings;
    }

    /** @return true when the loaded {@code GuiVideoSettings} is OptiFine's shape: no list of rows among its fields */
    static boolean optiFineShape() {
        if (shape == null) {
            boolean list = false;
            for (Field f : GuiVideoSettings.class.getDeclaredFields()) {
                if (GuiSlot.class.isAssignableFrom(f.getType())) {
                    list = true;
                }
            }
            shape = !list;
        }
        return shape;
    }

    /** @return the screen a {@code GuiVideoSettings} goes back to (its one {@code GuiScreen} field), or null */
    @Nullable
    static GuiScreen parentOf(GuiVideoSettings gui) {
        try {
            if (parentField == null) {
                for (Field f : GuiVideoSettings.class.getDeclaredFields()) {
                    if (f.getType() == GuiScreen.class) {
                        f.setAccessible(true);
                        parentField = f;
                        break;
                    }
                }
            }
            return parentField == null ? null : (GuiScreen) parentField.get(gui);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** @return true when this screen holds the list (false: OptiFine's own layout, nothing added) */
    public boolean laidOut() {
        return this.list != null;
    }

    /** @return every button in the list's rows (the settings, the options and OptiFine's submenu buttons) */
    public List<GuiButton> rowButtons() {
        return this.rowButtons;
    }

    /** @return OptiFine's submenu buttons, in {@link #SUBMENUS}' order (empty while not laid out) */
    public List<GuiButton> optiFineButtons() {
        return this.optiFineButtons;
    }

    @Override
    public void initGui() {
        float scrolled = this.list == null ? 0.0F : this.list.scrolled();
        this.list = null;
        this.rowButtons.clear();
        this.optiFineButtons.clear();
        super.initGui();
        GuiButton done = null;
        GuiButton[] submenus = new GuiButton[SUBMENUS.length];
        List<GuiButton> entries = new ArrayList<GuiButton>();
        List<String> keys = new ArrayList<String>();
        for (GuiButton b : this.buttonList) {
            int sub = indexOf(b.id);
            if (b.id == DONE) {
                done = b;
            } else if (sub >= 0) {
                submenus[sub] = b;
            } else if (b.id < DONE) {
                GameSettings.Options o = GameSettings.Options.byOrdinal(b.id);
                entries.add(b);
                keys.add(o == null ? "" : o.name());
            }
        }
        for (GuiButton s : submenus) {
            if (s == null || done == null) {
                if (!warned) {
                    warned = true;
                    BackportCore.LOGGER.warn("Presentation: OptiFine's Video Settings has no Done or submenu buttons where expected; left as it is");
                }
                return;
            }
        }
        try {
            List<GuiOptionsRowList.Row> rows = OptionsInjector.videoRows(this, entries, keys, true);
            int left = this.width / 2 - 155;
            if (VideoLayout.active()) {
                rows.add(new VideoLayout.HeaderRow("OptiFine", left));
            }
            for (int i = 0; i < submenus.length; i += 2) {
                submenus[i].x = left;
                submenus[i].width = 150;
                submenus[i + 1].x = left + 160;
                submenus[i + 1].width = 150;
                rows.add(new ActionRow(this, submenus[i], submenus[i + 1]));
            }
            for (GuiButton s : submenus) {
                this.optiFineButtons.add(s);
            }
            this.rowButtons.addAll(OptionsInjector.buttonsOf(rows));
            this.buttonList.clear();
            done.x = this.width / 2 - 100;
            done.y = this.height - 27;
            done.width = 200;
            this.buttonList.add(done);
            this.list = new OptionRowList(this.mc, this.width, this.height, 32, this.height - 32, 25, rows);
            this.list.scrollTo(scrolled);
        } catch (Exception e) {
            // Whatever went wrong, OptiFine's screen as OptiFine laid it out.
            this.list = null;
            this.rowButtons.clear();
            this.optiFineButtons.clear();
            if (!warned) {
                warned = true;
                BackportCore.LOGGER.warn("Presentation: could not lay out OptiFine's Video Settings ({}); left as it is", e.toString());
            }
            this.buttonList.clear();
            super.initGui();
        }
    }

    private static int indexOf(int id) {
        for (int i = 0; i < SUBMENUS.length; i++) {
            if (SUBMENUS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /** Scrolls the list to {@code amount} pixels (for the client self-test). */
    public void scrollTo(float amount) {
        if (this.list != null) {
            this.list.scrollTo(amount);
        }
    }

    /** Runs OptiFine's own action for one of its buttons (a submenu opens with this screen as its parent). */
    public void optiFineAction(GuiButton button) {
        try {
            actionPerformed(button);
        } catch (IOException e) {
            BackportCore.LOGGER.warn("Presentation: OptiFine's button {} failed: {}", button.id, e.toString());
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (this.list != null) {
            this.list.handleMouseInput();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int scale = this.settings.guiScale;
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.list != null && this.mc.currentScreen == this) {
            this.list.mouseClicked(mouseX, mouseY, mouseButton);
        }
        rescaleIfChanged(scale);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        int scale = this.settings.guiScale;
        super.mouseReleased(mouseX, mouseY, state);
        if (this.list != null) {
            this.list.mouseReleased(mouseX, mouseY, state);
        }
        rescaleIfChanged(scale);
    }

    /** As vanilla's Video Settings: a new GUI Scale lays the screen out again at the new size. */
    private void rescaleIfChanged(int before) {
        if (this.list != null && this.settings.guiScale != before && this.mc.currentScreen == this) {
            ScaledResolution sr = new ScaledResolution(this.mc);
            setWorldAndResolution(this.mc, sr.getScaledWidth(), sr.getScaledHeight());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.settings.saveOptions();
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.list == null) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        drawDefaultBackground();
        this.list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(this.fontRenderer, I18n.format("options.videoTitle"), this.width / 2, 5, 0xFFFFFF);
        for (GuiButton b : this.buttonList) {
            b.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
        optiFineTooltips(mouseX, mouseY);
    }

    /**
     * OptiFine's own tooltips for its settings ({@code TooltipManager.drawTooltips}, over the rows' buttons now in
     * view). Skipped, once and for good, if OptiFine's manager is not where expected.
     */
    private void optiFineTooltips(int mouseX, int mouseY) {
        if (tooltipsBroken || mouseY < this.list.top || mouseY >= this.list.bottom) {
            return;
        }
        try {
            if (drawTooltips == null) {
                for (Field f : GuiVideoSettings.class.getDeclaredFields()) {
                    if (f.getType().getName().endsWith(".TooltipManager")) {
                        f.setAccessible(true);
                        tooltipField = f;
                        drawTooltips = f.getType().getMethod("drawTooltips", int.class, int.class, List.class);
                    }
                }
                if (drawTooltips == null) {
                    tooltipsBroken = true;
                    return;
                }
            }
            Object manager = tooltipField.get(this);
            if (manager == null) {
                return;
            }
            List<GuiButton> shown = new ArrayList<GuiButton>();
            for (GuiButton b : this.rowButtons) {
                if (!(b instanceof OptionWidgets.OptionButton) && b.y >= this.list.top && b.y + b.height <= this.list.bottom) {
                    shown.add(b);
                }
            }
            drawTooltips.invoke(manager, mouseX, mouseY, shown);
        } catch (ReflectiveOperationException | RuntimeException e) {
            tooltipsBroken = true;
            BackportCore.LOGGER.debug("Presentation: OptiFine's tooltips unavailable: {}", e.toString());
        }
    }

    /** A row of OptiFine's submenu buttons: pressing one runs OptiFine's action for it. */
    static final class ActionRow extends GuiOptionsRowList.Row {
        private final OptiFineVideoScreen screen;
        private final GuiButton[] buttons;

        ActionRow(OptiFineVideoScreen screen, GuiButton a, GuiButton b) {
            super(a, b);
            this.screen = screen;
            this.buttons = new GuiButton[] {a, b};
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            Minecraft mc = Minecraft.getMinecraft();
            for (GuiButton b : this.buttons) {
                if (b.mousePressed(mc, mouseX, mouseY)) {
                    b.playPressSound(mc.getSoundHandler());
                    this.screen.optiFineAction(b);
                    return true;
                }
            }
            return false;
        }
    }
}
