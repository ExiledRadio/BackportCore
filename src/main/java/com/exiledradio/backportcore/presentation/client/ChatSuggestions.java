package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.CommandSuggestionModel;
import com.exiledradio.backportcore.presentation.CommandSuggestionModel.Page;
import com.exiledradio.backportcore.presentation.CommandSuggestionModel.Suggestions;
import com.exiledradio.backportcore.presentation.CommandUsageNetwork;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.SuggestionLook;
import com.exiledradio.backportcore.presentation.option.ToggleOption;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.ICommand;
import net.minecraft.network.play.client.CPacketTabComplete;
import net.minecraft.network.play.server.SPacketTabComplete;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.13's command suggestions in 1.12.2's chat box, once an installed version turns them on
 * ({@link com.exiledradio.backportcore.presentation.Contribution#commandSuggestions}).
 *
 * <p>No patch: Forge's screen events on any {@link GuiChat} (and its subclasses, as the bed's chat). As a {@code /}
 * command is typed, the text before the cursor goes to the server in 1.12.2's own {@code CPacketTabComplete} (with
 * the looked-at block, as vanilla's Tab does), at most one at a time and once typing pauses for
 * {@value #DEBOUNCE_MS} ms; the answer is read off the connection as it arrives (vanilla's handler still gets it, and
 * ignores it, since its own Tab was never pressed). Forge's client commands are asked locally, as Forge's Tab does.
 * So any 1.12.2 server works, with or without Core.
 *
 * <p>The list is 1.13.2's: up to 10 rows above the input at the word being completed, the selected row yellow, a
 * dotted line where it scrolls on, the selected suggestion's rest in grey after the typed text. Up and Down move,
 * Tab accepts (again: the next one; Shift+Tab: the one before), Esc closes the list before the chat, the mouse wheel
 * scrolls it, a click accepts. With the version's "Command Suggestions" option off, Tab opens the list. Plain chat
 * suggests the tab list's player names on Tab, as 1.13 does. 1.12.2's own Tab completion is off meanwhile.
 *
 * <p>Usage: with a command's name typed out and no list showing, its {@code getUsage} shows grey above the input
 * (the arguments after the name), from a server running Core or from Forge's client commands; otherwise none.
 * 1.13's red error highlighting is not drawn: 1.12.2's commands do not describe their arguments.
 */
@SideOnly(Side.CLIENT)
public final class ChatSuggestions {

    static final int DEBOUNCE_MS = 80;
    private static final long TIMEOUT_MS = 3000L;
    private static final String SNIFFER = "backportcore:tab_complete";

    public static final ChatSuggestions INSTANCE = new ChatSuggestions();

    private static Field inputField;
    private static Field defaultText;
    private static Field lineScroll;
    private static boolean reflectionFailed;

    @Nullable
    private GuiChat screen;
    @Nullable
    private GuiTextField field;
    private String defaultInput = "";
    private String lastText = "";

    private boolean hasEdits;
    private boolean applying;
    private boolean historyKey;
    private boolean showWhenReady;

    /** Done suggestions for the current text, or null while asked for. */
    @Nullable
    private Suggestions pending;
    /** The prefix the current text needs answered, or null. */
    @Nullable
    private String wanted;
    private long wantedAt;
    private final Deque<Asked> inFlight = new ArrayDeque<Asked>();
    private long sentAt;

    @Nullable
    private Page list;
    private int listX;
    private int listWidth;
    @Nullable
    private String preview;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    @Nullable
    private String usage;
    private int usageX;
    @Nullable
    private String usageName;
    /** Usages the server answered this connection, by command name ("" for none). */
    private final Map<String, String> usages = new HashMap<String, String>();

    /** For the self-tests: requests sent this session. */
    public static int sent;

    private static final class Asked {
        final String prefix;
        @Nullable
        final String[] client;

        Asked(String prefix, @Nullable String[] client) {
            this.prefix = prefix;
            this.client = client;
        }
    }

    private ChatSuggestions() {
    }

    /** Cleanroom 0.6 and later draw 1.13's command suggestions in {@link GuiChat} themselves, with no switch. */
    static final String CLEANROOM_LIST = "com/cleanroommc/client/chat/suggestion/SuggestionList.class";

    public static void init() {
        if (ChatSuggestions.class.getClassLoader().getResource(CLEANROOM_LIST) != null) {
            BackportCore.LOGGER.info("Command suggestions: Cleanroom shows its own, so the backports' list stays off");
            return;
        }
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        CommandUsageNetwork.onAnswer((name, usage) -> Minecraft.getMinecraft().addScheduledTask(() -> INSTANCE.usageAnswered(name, usage)));
    }

    /** @return the option while some installed version has command suggestions, else null */
    @Nullable
    private static ToggleOption option() {
        return Presentation.commandSuggestions();
    }

    // ---------------------------------------------------------------------------------------------- connection

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // Fired on the network thread.
        Minecraft.getMinecraft().addScheduledTask(this.usages::clear);
        try {
            ChannelPipeline pipeline = event.getManager().channel().pipeline();
            if (pipeline.get(SNIFFER) == null && pipeline.get("packet_handler") != null) {
                pipeline.addBefore("packet_handler", SNIFFER, new Sniffer());
            }
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Command suggestions: could not watch the connection's tab-complete answers: {}", e.toString());
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        CommandUsageNetwork.disconnected();
        Minecraft.getMinecraft().addScheduledTask(() -> {
            this.usages.clear();
            this.inFlight.clear();
        });
    }

    /** Reads the server's tab-complete answers on their way to vanilla's handler, which still gets them. */
    private static final class Sniffer extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof SPacketTabComplete) {
                String[] matches = ((SPacketTabComplete) msg).getMatches();
                // Vanilla's handler sorts the array in place later, on the client thread.
                String[] copy = matches == null ? new String[0] : matches.clone();
                Minecraft.getMinecraft().addScheduledTask(() -> INSTANCE.answered(copy));
            }
            super.channelRead(ctx, msg);
        }
    }

    // ---------------------------------------------------------------------------------------------- screen

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiChat) {
            bind((GuiChat) event.getGui());
        }
    }

    private boolean bind(GuiChat gui) {
        GuiTextField f = inputOf(gui);
        if (f == null) {
            return false;
        }
        if (gui != this.screen || f != this.field) {
            reset();
            this.screen = gui;
            this.field = f;
            this.defaultInput = defaultTextOf(gui);
            this.lastText = f.getText();
            this.hasEdits = false;
            // 1.13 asks at once for the text the chat opens with (a "/" key press: "/").
            textChanged();
            this.hasEdits = false;
        }
        return true;
    }

    private void reset() {
        this.screen = null;
        this.field = null;
        this.pending = null;
        this.wanted = null;
        this.list = null;
        this.preview = null;
        this.usage = null;
        this.usageName = null;
        this.applying = false;
        this.historyKey = false;
        this.showWhenReady = false;
    }

    /** @return whether suggestions run on this screen now */
    private boolean active(GuiScreen gui) {
        if (option() == null || !(gui instanceof GuiChat)) {
            return false;
        }
        if (gui != this.screen && !bind((GuiChat) gui)) {
            return false;
        }
        return this.field != null && Minecraft.getMinecraft().player != null;
    }

    @Nullable
    private static GuiTextField inputOf(GuiChat gui) {
        if (reflectionFailed) {
            return null;
        }
        try {
            if (inputField == null) {
                inputField = ObfuscationReflectionHelper.findField(GuiChat.class, "field_146415_a");
                defaultText = ObfuscationReflectionHelper.findField(GuiChat.class, "field_146409_v");
                lineScroll = ObfuscationReflectionHelper.findField(GuiTextField.class, "field_146225_q");
            }
            return (GuiTextField) inputField.get(gui);
        } catch (Exception e) {
            reflectionFailed = true;
            BackportCore.LOGGER.warn("Command suggestions: GuiChat's input box not found, suggestions off: {}", e.toString());
            return null;
        }
    }

    private static String defaultTextOf(GuiChat gui) {
        try {
            Object v = defaultText.get(gui);
            return v instanceof String ? (String) v : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static int scrollOf(GuiTextField f) {
        try {
            return lineScroll.getInt(f);
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------------------------------------- input

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!active(event.getGui()) || !Keyboard.getEventKeyState()) {
            return;
        }
        if (key(Keyboard.getEventKey(), GuiScreen.isShiftKeyDown())) {
            event.setCanceled(true);
        }
    }

    /** @return whether the key was the list's (and so not the chat box's) */
    private boolean key(int key, boolean shift) {
        sync();
        if (this.list != null) {
            Page page = this.list;
            switch (key) {
                case Keyboard.KEY_UP:
                    page.cycle(-1);
                    page.accepted = false;
                    selected();
                    return true;
                case Keyboard.KEY_DOWN:
                    page.cycle(1);
                    page.accepted = false;
                    selected();
                    return true;
                case Keyboard.KEY_TAB:
                    if (page.accepted) {
                        page.cycle(shift ? -1 : 1);
                    }
                    accept();
                    return true;
                case Keyboard.KEY_ESCAPE:
                    hide();
                    return true;
                default:
                    break;
            }
        } else if (key == Keyboard.KEY_TAB) {
            this.hasEdits = true;
            if (!show()) {
                this.showWhenReady = this.wanted != null || !this.inFlight.isEmpty();
            }
            // 1.12.2's Tab completion would write into the box and print the matches to the chat.
            return true;
        }
        this.historyKey = key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN;
        return false;
    }

    @SubscribeEvent
    public void onKeyPost(GuiScreenEvent.KeyboardInputEvent.Post event) {
        if (active(event.getGui())) {
            sync();
        }
        this.historyKey = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (!active(gui) || this.list == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int x = Mouse.getEventX() * gui.width / mc.displayWidth;
        int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        Page page = this.list;
        SuggestionLook look = Presentation.suggestionLook();
        int top = top(gui, page, look);
        boolean inside = x >= this.listX - 1 && x < this.listX + this.listWidth && y >= top && y < top + page.visible() * look.rowHeight;
        if (!inside) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            page.scroll(wheel > 0 ? 1.0 : -1.0);
            event.setCanceled(true);
        } else if (Mouse.getEventButtonState() && Mouse.getEventButton() >= 0) {
            int row = (y - top) / look.rowHeight + page.offset();
            if (row >= 0 && row < page.suggestions.size()) {
                page.select(row);
                accept();
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMousePost(GuiScreenEvent.MouseInputEvent.Post event) {
        if (active(event.getGui())) {
            sync();
        }
    }

    // ---------------------------------------------------------------------------------------------- state

    /** Notices a changed text, however it changed (typing, pasting, history, a chat click inserting a name). */
    private void sync() {
        GuiTextField f = this.field;
        if (f == null) {
            return;
        }
        String text = f.getText();
        if (!text.equals(this.lastText)) {
            this.lastText = text;
            textChanged();
        }
    }

    /** 1.13's {@code GuiChat.updateSuggestion}. */
    private void textChanged() {
        GuiTextField f = this.field;
        if (f == null) {
            return;
        }
        String text = f.getText();
        this.hasEdits = !this.historyKey && !text.equals(this.defaultInput);
        if (!this.applying) {
            this.preview = null;
            this.list = null;
        }
        this.usage = null;
        this.usageName = null;
        if (text.startsWith("/")) {
            String prefix = CommandSuggestionModel.requestFor(text, f.getCursorPosition());
            if (prefix != null && (this.list == null || !this.applying)) {
                this.pending = null;
                this.wanted = prefix;
                this.wantedAt = System.currentTimeMillis();
            }
        } else {
            this.wanted = null;
            this.pending = CommandSuggestionModel.names(text, playerNames());
        }
    }

    private static List<String> playerNames() {
        List<String> names = new ArrayList<String>();
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();
        if (connection != null) {
            for (NetworkPlayerInfo info : connection.getPlayerInfoMap()) {
                names.add(info.getGameProfile().getName());
            }
        }
        return names;
    }

    /** Sends the wanted request once typing pauses and nothing is in flight. */
    private void pump() {
        long now = System.currentTimeMillis();
        if (!this.inFlight.isEmpty() && now - this.sentAt > TIMEOUT_MS) {
            this.inFlight.clear();
        }
        String prefix = this.wanted;
        if (prefix == null || !this.inFlight.isEmpty() || now - this.wantedAt < DEBOUNCE_MS) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        String[] client = null;
        try {
            ClientCommandHandler.instance.autoComplete(prefix);
            String[] latest = ClientCommandHandler.instance.latestAutoComplete;
            client = latest == null ? null : latest.clone();
        } catch (RuntimeException e) {
            BackportCore.LOGGER.debug("Command suggestions: a client command's completion threw {}", e.toString());
        }
        BlockPos target = null;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            target = mc.objectMouseOver.getBlockPos();
        }
        mc.getConnection().sendPacket(new CPacketTabComplete(prefix, target, false));
        this.inFlight.add(new Asked(prefix, client));
        this.sentAt = now;
        sent++;
    }

    /** A tab-complete answer, on the client thread. */
    void answered(String[] matches) {
        Asked asked = this.inFlight.poll();
        if (asked == null || this.field == null || !asked.prefix.equals(this.wanted)) {
            return;
        }
        this.wanted = null;
        this.pending = CommandSuggestionModel.fromCompletions(this.field.getText(), asked.prefix, asked.client, matches);
        ready();
    }

    /** 1.13's {@code updateUsageInfo}: the answer is in. */
    private void ready() {
        this.list = null;
        this.preview = null;
        updateUsage();
        ToggleOption auto = option();
        if ((this.hasEdits && auto != null && auto.get()) || this.showWhenReady) {
            show();
        }
        this.showWhenReady = false;
    }

    /** @return whether the list opened */
    private boolean show() {
        Suggestions s = this.pending;
        GuiChat gui = this.screen;
        GuiTextField f = this.field;
        if (s == null || s.isEmpty() || gui == null || f == null) {
            return false;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int w = 0;
        for (String t : s.texts) {
            w = Math.max(w, font.getStringWidth(t));
        }
        String text = f.getText();
        this.listWidth = w;
        this.listX = MathHelper.clamp(xAt(text, s.start), 0, gui.width - w);
        this.list = new Page(s, text, Presentation.suggestionLook().rows);
        this.lastMouseX = Integer.MIN_VALUE;
        selected();
        return true;
    }

    /** 1.13's {@code TextFieldWidget.func_195611_j}: the x of character {@code index}, from the box's left. */
    private int xAt(String text, int index) {
        GuiTextField f = this.field;
        int left = f == null ? 4 : f.x;
        if (index > text.length()) {
            return left;
        }
        return left + Minecraft.getMinecraft().fontRenderer.getStringWidth(text.substring(0, Math.max(0, index)));
    }

    private void hide() {
        this.list = null;
        this.preview = null;
    }

    /** The selection changed: the grey preview follows it. */
    private void selected() {
        Page page = this.list;
        GuiTextField f = this.field;
        if (page == null || f == null) {
            return;
        }
        this.preview = CommandSuggestionModel.previewSuffix(f.getText(), page.applied());
    }

    /** Accepts the selected suggestion; the list stays for the next Tab. */
    private void accept() {
        Page page = this.list;
        GuiTextField f = this.field;
        if (page == null || f == null) {
            return;
        }
        this.applying = true;
        f.setText(page.applied());
        f.setCursorPosition(page.suggestions.cursorAfter(page.selected()));
        sync();
        selected();
        this.applying = false;
        page.accepted = true;
    }

    // ---------------------------------------------------------------------------------------------- usage

    private void updateUsage() {
        GuiTextField f = this.field;
        if (f == null) {
            return;
        }
        String text = f.getText();
        String name = CommandSuggestionModel.commandName(text, f.getCursorPosition());
        this.usageName = name;
        this.usage = null;
        if (name == null) {
            return;
        }
        int cursor = Math.min(f.getCursorPosition(), text.length());
        this.usageX = xAt(text, text.lastIndexOf(' ', Math.max(0, cursor - 1)) + 1);
        Minecraft mc = Minecraft.getMinecraft();
        ICommand client = ClientCommandHandler.instance.getCommands().get(name);
        if (client != null) {
            try {
                showUsage(name, client.getUsage(mc.player));
            } catch (RuntimeException e) {
                BackportCore.LOGGER.debug("Command suggestions: client command {}'s usage threw {}", name, e.toString());
            }
            return;
        }
        if (this.usages.containsKey(name)) {
            showUsage(name, this.usages.get(name));
        } else if (mc.getConnection() != null && CommandUsageNetwork.serverHasCore()) {
            this.usages.put(name, null);
            CommandUsageNetwork.ask(name);
        }
    }

    private void usageAnswered(String name, String usage) {
        this.usages.put(name, usage);
        if (name.equals(this.usageName)) {
            showUsage(name, usage);
        }
    }

    private void showUsage(String name, @Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String text = I18n.hasKey(raw) ? I18n.format(raw) : raw;
        String args = CommandSuggestionModel.usageArguments(name, text);
        GuiChat gui = this.screen;
        if (args == null || gui == null) {
            return;
        }
        this.usage = TextFormatting.GRAY + args;
        int w = Minecraft.getMinecraft().fontRenderer.getStringWidth(this.usage);
        this.usageX = MathHelper.clamp(this.usageX, 0, Math.max(0, gui.width - w));
    }

    // ---------------------------------------------------------------------------------------------- drawing

    @SubscribeEvent
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (active(event.getGui())) {
            sync();
            pump();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!active(gui) || gui != this.screen) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        SuggestionLook look = Presentation.suggestionLook();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 200.0F);
        drawPreview(font, look);
        if (this.list != null) {
            drawList(gui, font, look, event.getMouseX(), event.getMouseY());
        } else if (this.usage != null) {
            int w = font.getStringWidth(this.usage);
            Gui.drawRect(this.usageX - 1, gui.height - 14 - 13, this.usageX + w + 1, gui.height - 2 - 13, look.usageBackground);
            font.drawStringWithShadow(this.usage, (float) this.usageX, (float) (gui.height - 14 - 13 + 2), look.usageText);
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 1.13's {@code TextFieldWidget} draws the suggestion right after the text when the cursor is at its end. */
    private void drawPreview(FontRenderer font, SuggestionLook look) {
        GuiTextField f = this.field;
        String p = this.preview;
        if (f == null || p == null || p.isEmpty()) {
            return;
        }
        String text = f.getText();
        if (f.getCursorPosition() != text.length() || text.length() >= f.getMaxStringLength()) {
            return;
        }
        int scroll = MathHelper.clamp(scrollOf(f), 0, text.length());
        String visible = font.trimStringToWidth(text.substring(scroll), f.getWidth());
        int x = f.x + font.getStringWidth(visible);
        font.drawStringWithShadow(p, (float) x, (float) f.y, look.preview);
    }

    private static int top(GuiScreen gui, Page page, SuggestionLook look) {
        return gui.height - 12 - 3 - page.visible() * look.rowHeight;
    }

    /** 1.13's {@code GuiChat.SuggestionsList.render}. */
    private void drawList(GuiScreen gui, FontRenderer font, SuggestionLook look, int mouseX, int mouseY) {
        Page page = this.list;
        int n = page.visible();
        int left = this.listX - 1;
        int width = this.listWidth + 1;
        int top = top(gui, page, look);
        int height = n * look.rowHeight;
        boolean above = page.moreAbove();
        boolean below = page.moreBelow();
        boolean moved = mouseX != this.lastMouseX || mouseY != this.lastMouseY;
        if (moved) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
        }
        if (above || below) {
            Gui.drawRect(left, top - 1, left + width, top, look.background);
            Gui.drawRect(left, top + height, left + width, top + height + 1, look.background);
            for (int k = 0; k < width; k += 2) {
                if (above) {
                    Gui.drawRect(left + k, top - 1, left + k + 1, top, look.scrollDots);
                }
                if (below) {
                    Gui.drawRect(left + k, top + height, left + k + 1, top + height + 1, look.scrollDots);
                }
            }
        }
        for (int row = 0; row < n; row++) {
            int index = row + page.offset();
            int y = top + look.rowHeight * row;
            Gui.drawRect(left, y, left + width, y + look.rowHeight, look.background);
            if (moved && mouseX > left && mouseX < left + width && mouseY > y && mouseY < y + look.rowHeight) {
                page.select(index);
                selected();
            }
            font.drawStringWithShadow(page.suggestions.texts.get(index), (float) (left + 1), (float) (y + 2),
                    index == page.selected() ? look.selected : look.unselected);
        }
    }

    // ---------------------------------------------------------------------------------------------- self-test access

    /**
     * The client self-test's key press on the open chat screen (Tab, the arrows, Esc), as the keyboard event would.
     *
     * @return whether the list took it
     */
    public boolean keyForTest(int key, boolean shift) {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (!active(gui)) {
            return false;
        }
        boolean taken = key(key, shift);
        sync();
        this.historyKey = false;
        return taken;
    }

    /** @return the shown list's rows, for the client self-test; empty when none shows */
    public List<String> shownForTest() {
        Page page = this.list;
        return page == null ? new ArrayList<String>() : new ArrayList<String>(page.suggestions.texts);
    }

    @Nullable
    public String previewForTest() {
        return this.preview;
    }

    @Nullable
    public String usageForTest() {
        return this.usage;
    }
}
