package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.List;

/**
 * The chat as the newest installed version draws it, through patches P13 and P14: 1.12.2's {@code GuiNewChat} with
 * 1.14's Text Background Opacity (the background no longer follows the text's opacity) and Text Background
 * (Everywhere: the input box too), and 1.16's Line Spacing and Chat Delay (player chat held back and released one
 * line per delay, with 1.16's "[+N pending lines]"). The numbers are 1.16.5's {@code ChatComponent.render},
 * {@code getClickedComponentStyleAt} and {@code enqueueMessage}.
 *
 * <p>Before 1.14 nothing here runs and vanilla's {@code drawChat} draws, other mods' changes to it included.
 */
@SideOnly(Side.CLIENT)
public final class ChatRenderer {

    private static Field drawnLines;
    private static Field scrollPos;
    private static Field isScrolled;
    private static boolean broken;

    private static final Deque<ITextComponent> QUEUE = new java.util.concurrent.ConcurrentLinkedDeque<ITextComponent>();
    private static long lastMessage;
    /** For the client self-test: frames Core's chat drew. */
    public static volatile int frames;

    private ChatRenderer() {
    }

    /** @return true when Core draws the chat: from 1.14 on, as long as its fields could be read */
    public static boolean active() {
        return !broken && Presentation.TEXT_BACKGROUND_OPACITY.isShown();
    }

    private static double spacing() {
        return Presentation.CHAT_LINE_SPACING.isShown() ? Presentation.CHAT_LINE_SPACING.get() : 0.0;
    }

    private static double delaySeconds() {
        return Presentation.CHAT_DELAY.isShown() ? Presentation.CHAT_DELAY.get() : 0.0;
    }

    /** @return lines per page: 1.16.5's {@code getHeight() / 9}, the height divided by the line spacing */
    static int linesPerPage(GuiNewChat chat) {
        return MathHelper.floor(chat.getChatHeight() / (spacing() + 1.0)) / 9;
    }

    @SuppressWarnings("unchecked")
    private static boolean fields() {
        if (drawnLines == null && !broken) {
            try {
                drawnLines = ReflectionHelper.findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
                scrollPos = ReflectionHelper.findField(GuiNewChat.class, "scrollPos", "field_146250_j");
                isScrolled = ReflectionHelper.findField(GuiNewChat.class, "isScrolled", "field_146251_k");
            } catch (RuntimeException e) {
                broken = true;
                BackportCore.LOGGER.error("Presentation: cannot read the chat's lines; vanilla's chat draws", e);
            }
        }
        return !broken;
    }

    /** 1.16.5's {@code getTimeFactor}. */
    private static double timeFactor(int age) {
        double d = 1.0 - age / 200.0;
        d = MathHelper.clamp(d * 10.0, 0.0, 1.0);
        return d * d;
    }

    @SuppressWarnings("unchecked")
    static void draw(GuiNewChat chat, int updateCounter) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!fields()) {
            chat.drawChat(updateCounter);
            return;
        }
        releaseDue();
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) {
            return;
        }
        List<ChatLine> lines;
        int scroll;
        boolean scrolled;
        try {
            lines = (List<ChatLine>) drawnLines.get(chat);
            scroll = scrollPos.getInt(chat);
            scrolled = isScrolled.getBoolean(chat);
        } catch (IllegalAccessException e) {
            broken = true;
            chat.drawChat(updateCounter);
            return;
        }
        int perPage = linesPerPage(chat);
        int total = lines.size();
        if (total <= 0) {
            return;
        }
        frames++;
        boolean open = chat.getChatOpen();
        float scale = chat.getChatScale();
        int width = MathHelper.ceil(chat.getChatWidth() / scale);
        GlStateManager.pushMatrix();
        GlStateManager.translate(2.0F, 8.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        double textOpacity = mc.gameSettings.chatOpacity * 0.9F + 0.1F;
        double backOpacity = Presentation.TEXT_BACKGROUND_OPACITY.get();
        double spacing = spacing();
        double lineHeight = 9.0 * (spacing + 1.0);
        double textOffset = -8.0 * (spacing + 1.0) + 4.0 * spacing;
        int shown = 0;
        for (int i = 0; i + scroll < total && i < perPage; i++) {
            ChatLine line = lines.get(i + scroll);
            if (line == null) {
                continue;
            }
            int age = updateCounter - line.getUpdatedCounter();
            if (age < 200 || open) {
                double fade = open ? 1.0 : timeFactor(age);
                int text = (int) (255.0 * fade * textOpacity);
                int back = (int) (255.0 * fade * backOpacity);
                shown++;
                if (text > 3) {
                    double y = -i * lineHeight;
                    Gui.drawRect(-2, (int) (y - lineHeight), width + 4, (int) y, back << 24);
                    GlStateManager.enableBlend();
                    mc.fontRenderer.drawStringWithShadow(line.getChatComponent().getFormattedText(), 0.0F, (float) (int) (y + textOffset), 0xFFFFFF + (text << 24));
                    GlStateManager.disableAlpha();
                    GlStateManager.disableBlend();
                }
            }
        }
        if (!QUEUE.isEmpty()) {
            int text = (int) (128.0 * textOpacity);
            int back = (int) (255.0 * backOpacity);
            Gui.drawRect(-2, 0, width + 4, 9, back << 24);
            GlStateManager.enableBlend();
            mc.fontRenderer.drawStringWithShadow(I18n.format("chat.queue", QUEUE.size()), 0.0F, 1.0F, 0xFFFFFF + (text << 24));
            GlStateManager.disableAlpha();
            GlStateManager.disableBlend();
        }
        if (open) {
            // 1.12.2's scroll bar, unchanged in 1.16.5.
            int fh = mc.fontRenderer.FONT_HEIGHT;
            GlStateManager.translate(-3.0F, 0.0F, 0.0F);
            int all = total * fh + total;
            int page = shown * fh + shown;
            int top = scroll * page / total;
            int size = page * page / all;
            if (all != page) {
                int alpha = top > 0 ? 170 : 96;
                int color = scrolled ? 13382451 : 3355562;
                Gui.drawRect(0, -top, 2, -top - size, color + (alpha << 24));
                Gui.drawRect(2, -top, 1, -top - size, 13421772 + (alpha << 24));
            }
        }
        GlStateManager.popMatrix();
    }

    /** 1.12.2's {@code getChatComponent} with 1.16.5's division by the line spacing. */
    @Nullable
    @SuppressWarnings("unchecked")
    static ITextComponent componentAt(GuiNewChat chat, int mouseX, int mouseY) {
        if (!chat.getChatOpen() || !fields()) {
            return chat.getChatComponent(mouseX, mouseY);
        }
        Minecraft mc = Minecraft.getMinecraft();
        List<ChatLine> lines;
        int scroll;
        try {
            lines = (List<ChatLine>) drawnLines.get(chat);
            scroll = scrollPos.getInt(chat);
        } catch (IllegalAccessException e) {
            return chat.getChatComponent(mouseX, mouseY);
        }
        int factor = new ScaledResolution(mc).getScaleFactor();
        float scale = chat.getChatScale();
        int x = MathHelper.floor((mouseX / factor - 2) / scale);
        int y = MathHelper.floor((mouseY / factor - 40) / (scale * (spacing() + 1.0)));
        if (x < 0 || y < 0) {
            return null;
        }
        int count = Math.min(linesPerPage(chat), lines.size());
        if (x > MathHelper.floor(chat.getChatWidth() / scale) || y >= mc.fontRenderer.FONT_HEIGHT * count + count) {
            return null;
        }
        int index = y / mc.fontRenderer.FONT_HEIGHT + scroll;
        if (index < 0 || index >= lines.size()) {
            return null;
        }
        int w = 0;
        for (ITextComponent part : lines.get(index).getChatComponent()) {
            if (part instanceof TextComponentString) {
                w += mc.fontRenderer.getStringWidth(GuiUtilRenderComponents.removeTextColorsIfConfigured(((TextComponentString) part).getText(), false));
                if (w > x) {
                    return part;
                }
            }
        }
        return null;
    }

    /** 1.14.4's {@code Options.getBackgroundColor(int)}: Chat Only keeps each place's own, Everywhere uses the opacity. */
    static int boxColor(int vanilla) {
        if (!Presentation.TEXT_BACKGROUND.isShown() || !"everywhere".equals(Presentation.TEXT_BACKGROUND.get())) {
            return vanilla;
        }
        return (int) (Presentation.TEXT_BACKGROUND_OPACITY.get() * 255.0) << 24;
    }

    // ---- 1.16's Chat Delay ----

    private static long delayMillis() {
        return (long) (delaySeconds() * 1000.0);
    }

    /** As 1.16.5's {@code processPendingMessages}: one queued line once the delay has passed. */
    static void releaseDue() {
        if (QUEUE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMessage >= delayMillis()) {
            ITextComponent next = QUEUE.poll();
            lastMessage = now;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.ingameGUI != null) {
                mc.ingameGUI.addChatMessage(ChatType.CHAT, next);
            }
        }
    }

    /** For the self-test: lines waiting. */
    public static int queued() {
        return QUEUE.size();
    }

    /**
     * Forge's chat event, for player chat only (1.16.5's {@code StandardChatListener}: {@code ChatType.CHAT} is
     * queued, system and game-info messages never). Lowest priority, after every other mod changed or dropped it.
     */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onChat(ClientChatReceivedEvent event) {
            if (event.isCanceled() || event.getType() != ChatType.CHAT || event.getMessage() == null || !active()) {
                return;
            }
            if (!(delaySeconds() > 0.0)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (QUEUE.isEmpty() && now - lastMessage >= delayMillis()) {
                lastMessage = now;
                return;
            }
            QUEUE.add(event.getMessage());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                releaseDue();
            }
        }

        /** 1.16.5's {@code clearMessages} empties the queue; leaving a server does too. */
        @SubscribeEvent
        public void onLeave(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            QUEUE.clear();
        }
    }

    /** For the self-test: queue a line as the chat event would. */
    public static void enqueueForTest(ITextComponent line) {
        QUEUE.add(line);
    }
}
