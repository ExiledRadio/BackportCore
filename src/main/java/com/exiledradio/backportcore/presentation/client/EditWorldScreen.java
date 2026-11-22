package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.LookArea;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiWorldEdit;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.20.5's Edit World ({@code EditWorldScreen}, 26.3's the same): one column centered in the screen, 5 apart: a 20-high
 * space, "Enter Name" (grey), the name box, the buttons (Reset Icon, Open World Folder, Make Backup, Open Backups
 * Folder, Optimize World, 200 wide, in that order), another 20-high space, and Save and Cancel (98 each, 4 apart); the
 * title at y 15. 26.3's Edit World has no header and footer. 1.12.2's {@code GuiWorldEdit} opens as this subclass
 * (only its drawing moves: the title and label are drawn where the column puts them); every button is the screen's
 * own or {@link WorldBackups}'. Since Core 1.4.5.
 */
@SideOnly(Side.CLIENT)
public final class EditWorldScreen extends GuiWorldEdit {

    private static Field lastScreenField;
    private static Field worldIdField;
    private static Field nameEditField;

    private int labelY;

    private EditWorldScreen(GuiScreen lastScreen, String worldId) {
        super(lastScreen, worldId);
    }

    /** @return whether Edit World is laid out as 1.20.5's (the menus at 1.20.5 or later) */
    public static boolean active() {
        return HeaderFooterLayout.active(LookArea.MENUS);
    }

    /**
     * 26.3's column, from the buttons the screen and Core have placed: Save (0) and Cancel (1) at the bottom, the rest
     * top to bottom in their order.
     */
    void layOut(List<GuiButton> buttons) {
        GuiButton save = null;
        GuiButton cancel = null;
        List<GuiButton> column = new ArrayList<GuiButton>();
        for (GuiButton b : buttons) {
            if (b.id == 0 && !(b instanceof OptionWidgets.Action)) {
                save = b;
            } else if (b.id == 1 && !(b instanceof OptionWidgets.Action)) {
                cancel = b;
            } else if (b.visible) {
                column.add(b);
            }
        }
        Collections.sort(column, (a, b) -> Integer.compare(a.y, b.y));
        int gap = 5;
        int total = 20 + gap + 9 + gap + 20 + column.size() * (20 + gap) + 20 + gap + 20;
        int y = (this.height - total) / 2;
        int x = this.width / 2 - 100;
        y += 20 + gap;
        this.labelY = y;
        y += 9 + gap;
        GuiTextField name = nameEdit();
        if (name != null) {
            name.x = x;
            name.y = y;
        }
        y += 20 + gap;
        for (GuiButton b : column) {
            b.x = x;
            b.y = y;
            b.width = 200;
            y += 20 + gap;
        }
        y += 20 + gap;
        if (save != null) {
            save.x = x;
            save.y = y;
            save.width = 98;
        }
        if (cancel != null) {
            cancel.x = x + 98 + 4;
            cancel.y = y;
            cancel.width = 98;
        }
    }

    private GuiTextField nameEdit() {
        try {
            if (nameEditField == null) {
                nameEditField = ReflectionHelper.findField(GuiWorldEdit.class, "nameEdit", "field_184859_f");
            }
            return (GuiTextField) nameEditField.get(this);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRenderer, I18n.format("selectWorld.edit.title"), this.width / 2, 15, 0xFFFFFF);
        drawString(this.fontRenderer, I18n.format("selectWorld.enterName"), this.width / 2 - 100, this.labelY, 0xAAAAAA);
        GuiTextField name = nameEdit();
        if (name != null) {
            name.drawTextBox();
        }
        // GuiScreen.drawScreen, past GuiWorldEdit's own (which draws the title, label and box at 1.12.2's places).
        for (GuiButton b : this.buttonList) {
            b.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
        for (GuiLabel l : this.labelList) {
            l.drawLabel(this.mc, mouseX, mouseY);
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onOpen(GuiOpenEvent event) {
            if (event.getGui() == null || event.getGui().getClass() != GuiWorldEdit.class || !active()) {
                return;
            }
            try {
                if (lastScreenField == null) {
                    lastScreenField = ReflectionHelper.findField(GuiWorldEdit.class, "lastScreen", "field_184858_a");
                    worldIdField = ReflectionHelper.findField(GuiWorldEdit.class, "worldId", "field_184860_g");
                }
                event.setGui(new EditWorldScreen((GuiScreen) lastScreenField.get(event.getGui()), (String) worldIdField.get(event.getGui())));
            } catch (Exception e) {
                BackportCore.LOGGER.warn("Presentation: Edit World kept 1.12.2's layout ({})", e.toString());
            }
        }

        /** After {@link WorldBackups} has added its buttons (LOW). */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
            if (event.getGui() instanceof EditWorldScreen) {
                ((EditWorldScreen) event.getGui()).layOut(event.getButtonList());
            }
        }
    }
}
