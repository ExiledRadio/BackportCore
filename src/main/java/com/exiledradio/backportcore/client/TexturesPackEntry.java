package com.exiledradio.backportcore.client;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.LookFeelClient;
import com.exiledradio.backportcore.settings.BackportSettings;
import com.exiledradio.backportcore.settings.SettingsModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The modern textures as a row of 1.12.2's Resource Packs screen, "Backport Textures", so a player looking there to turn
 * them off finds them. It is only a switch: the pack itself stays where {@link ModernTextures} puts it, among the game's
 * default packs, and the row stands for {@code textures.modernTextures} (also on the Look &amp; Feel page). Selected
 * means on.
 *
 * <p>Moving the row between the columns takes effect on Done, as for any pack: the setting is written and saved, and
 * the resources are read again once (vanilla's own reload when other packs changed too, else the Look &amp; Feel
 * listener's). Escape leaves it as it was, as it does for every pack.
 *
 * <h2>Its place</h2>
 * Selected, it always sits just above Default, under every pack the player picked, because that is where the textures
 * really are: any pack the player picks wins over them. It shows no up or down arrows, and the pack above it shows no
 * down arrow (a move within the column is not offered at all); a pack moved over by some other means is put back
 * before the next frame.
 */
@SideOnly(Side.CLIENT)
public final class TexturesPackEntry extends ResourcePackListEntry {

    /** For the self-tests: frames of the Resource Packs screen drawn with the row on it. */
    public static int frames;
    private static final ResourceLocation ICON = new ResourceLocation(BackportCore.MOD_ID, "textures/gui/family/backport-core.png");

    /** The row on the open screen; it outlives a resize, which builds the screen's lists again. */
    private static WeakReference<TexturesPackEntry> current = new WeakReference<>(null);

    /** Set while vanilla's own code asks this row something for itself (drawing it). */
    private boolean own;
    /** Its column; a resize without a change to vanilla's packs builds the lists again, and this says where it goes. */
    private boolean on;

    private TexturesPackEntry(GuiScreenResourcePacks screen) {
        super(screen);
    }

    /** For the self-tests: the row on the open Resource Packs screen, or null. */
    @Nullable
    public static TexturesPackEntry on(GuiScreen screen) {
        TexturesPackEntry e = current.get();
        return e != null && e.resourcePacksGUI == screen ? e : null;
    }

    /** @return whether it is in the selected column, which means the textures on */
    public boolean selected() {
        return this.resourcePacksGUI.getSelectedResourcePacks().contains(this);
    }

    /** For the self-tests: what a click on the row's arrow does, left half or right half of the icon. */
    public void click(boolean leftHalf) {
        mousePressed(0, 0, 0, 0, leftHalf ? 8 : 24, 8);
    }

    /** @return {@code textures.modernTextures} as the settings screen has it (saving it tells every screen), or null */
    @Nullable
    public static SettingsModel.Entry setting() {
        SettingsModel.Page page = BackportSettings.page(BackportCore.MOD_ID);
        if (page != null) {
            for (SettingsModel.Entry e : page.entries) {
                if ("modernTextures".equals(e.name) && (e.group.path.equals("textures") || e.group.path.endsWith(".textures"))) {
                    return e;
                }
            }
        }
        return null;
    }

    @Override
    protected int getResourcePackFormat() {
        return 3;
    }

    @Override
    protected String getResourcePackDescription() {
        return I18n.format("backportcore.resourcepack.textures.description", Presentation.installedNewest());
    }

    @Override
    protected String getResourcePackName() {
        return I18n.format("backportcore.resourcepack.textures");
    }

    @Override
    protected void bindResourcePackIcon() {
        this.mc.getTextureManager().bindTexture(ICON);
        ITextureObject tex = this.mc.getTextureManager().getTexture(ICON);
        if (tex != null) {
            tex.setBlurMipmap(true, false);
        }
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected,
            float partialTicks) {
        this.own = true;
        try {
            super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected, partialTicks);
        } finally {
            this.own = false;
        }
    }

    @Override
    public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
        if (relativeX > 32) {
            return false;
        }
        List<ResourcePackListEntry> available = this.resourcePacksGUI.getAvailableResourcePacks();
        List<ResourcePackListEntry> selected = this.resourcePacksGUI.getSelectedResourcePacks();
        // Not markChanged: this row is not one of vanilla's packs, and a Done with only this change should not make
        // vanilla rewrite the pack list and reload as well.
        if (available.contains(this)) {
            available.remove(this);
            place(selected, this);
            this.on = true;
            return true;
        }
        if (relativeX < 16 && selected.contains(this)) {
            selected.remove(this);
            available.add(0, this);
            this.on = false;
            return true;
        }
        return false;
    }

    /** Only for its own drawing and clicks; to the pack above it, it is like Default, which no pack moves below. */
    @Override
    protected boolean showHoverOverlay() {
        return this.own;
    }

    @Override
    protected boolean canMoveUp() {
        return false;
    }

    @Override
    protected boolean canMoveDown() {
        return false;
    }

    /** Puts it just above Default (at the end when there is no Default row). */
    private static void place(List<ResourcePackListEntry> selected, TexturesPackEntry entry) {
        selected.remove(entry);
        int at = selected.size();
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i) instanceof ResourcePackListEntryDefault) {
                at = i;
                break;
            }
        }
        selected.add(at, entry);
    }

    /** The screen's events: adding the row, keeping its place, and Done. Registered by {@link ModernTextures#install()}. */
    public static final class Events {

        @Nullable
        private static Field changed;
        private static boolean changedLooked;

        @SubscribeEvent
        public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
            if (!(event.getGui() instanceof GuiScreenResourcePacks)) {
                return;
            }
            try {
                GuiScreenResourcePacks screen = (GuiScreenResourcePacks) event.getGui();
                List<ResourcePackListEntry> available = screen.getAvailableResourcePacks();
                List<ResourcePackListEntry> selected = screen.getSelectedResourcePacks();
                if (available == null || selected == null) {
                    return;
                }
                TexturesPackEntry entry = on(screen);
                if (entry == null) {
                    entry = new TexturesPackEntry(screen);
                    entry.on = CoreConfig.textures.modernTextures;
                    current = new WeakReference<>(entry);
                }
                // A resize keeps vanilla's lists only after a change; otherwise they are new and the row goes in again.
                available.remove(entry);
                selected.remove(entry);
                if (entry.on) {
                    place(selected, entry);
                } else {
                    available.add(0, entry);
                }
            } catch (RuntimeException e) {
                BackportCore.LOGGER.warn("Resource Packs screen: could not add the Backport Textures row", e);
            }
        }

        /** Keeps it above Default; see the class comment. */
        @SubscribeEvent
        public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
            TexturesPackEntry entry = on(event.getGui());
            if (entry == null) {
                return;
            }
            frames++;
            List<ResourcePackListEntry> selected = entry.resourcePacksGUI.getSelectedResourcePacks();
            int i = selected.indexOf(entry);
            if (i >= 0 && (i + 1 < selected.size() ? !(selected.get(i + 1) instanceof ResourcePackListEntryDefault) : hasDefault(selected))) {
                place(selected, entry);
            }
        }

        private static boolean hasDefault(List<ResourcePackListEntry> list) {
            for (ResourcePackListEntry e : list) {
                if (e instanceof ResourcePackListEntryDefault) {
                    return true;
                }
            }
            return false;
        }

        /** Done: before vanilla's own handling, so its reload (when it makes one) already has the new pack in place. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onDone(GuiScreenEvent.ActionPerformedEvent.Pre event) {
            GuiButton button = event.getButton();
            TexturesPackEntry entry = on(event.getGui());
            if (entry == null || button == null || button.id != 1 || !button.enabled || event.isCanceled()) {
                return;
            }
            current = new WeakReference<>(null);
            boolean on = entry.selected();
            if (on == CoreConfig.textures.modernTextures) {
                return;
            }
            SettingsModel.Entry setting = setting();
            if (setting == null) {
                BackportCore.LOGGER.warn("Resource Packs screen: no textures.modernTextures setting; Backport Textures stays {}",
                        CoreConfig.textures.modernTextures);
                return;
            }
            if (vanillaWillReload((GuiScreenResourcePacks) event.getGui())) {
                LookFeelClient.skipNextReload();
            }
            BackportSettings.apply(Collections.singletonMap(setting, new String[]{Boolean.toString(on)}), Minecraft.getMinecraft().world != null);
            BackportCore.LOGGER.info("Resource Packs screen: Backport Textures {}", on ? "on" : "off");
        }

        /** @return the screen's {@code changed}: whether vanilla's Done will save the pack list and reload */
        private static boolean vanillaWillReload(GuiScreenResourcePacks screen) {
            if (!changedLooked) {
                changedLooked = true;
                try {
                    changed = ReflectionHelper.findField(GuiScreenResourcePacks.class, "changed", "field_175289_s");
                } catch (RuntimeException e) {
                    BackportCore.LOGGER.warn("Resource Packs screen: no 'changed' field; a Done may reload twice", e);
                }
            }
            try {
                return changed != null && changed.getBoolean(screen);
            } catch (IllegalAccessException | RuntimeException e) {
                return false;
            }
        }
    }

}
