package com.exiledradio.backportcore.command;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandShowSeed;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;

/**
 * {@code /seed} as 1.15 has it, with a 1.15 or newer backport installed: "Seed: [n]", the seed green and a click
 * away from the clipboard ("Click to Copy to Clipboard"), shift-click inserting it. 1.12.2 has no copy click
 * action, so the click runs {@link #COPY} with the seed, which Core's client copies instead of sending
 * ({@code presentation.client.CopyClick}). Older versions keep 1.12.2's plain reply.
 */
public final class SeedCommand extends CommandShowSeed {

    /** The chat line a copy click "runs"; Core's client takes it before it is sent. */
    public static final String COPY = "/backportcore:copy ";

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!Presentation.isAtLeast("1.15")) {
            super.execute(server, sender, args);
            return;
        }
        World world = sender.getEntityWorld() != null ? sender.getEntityWorld() : server.getWorld(0);
        String seed = String.valueOf(world.getSeed());
        sender.sendMessage(new TextComponentTranslation("commands.seed.success", copyable(seed)));
    }

    /** 1.15's {@code ComponentUtils.wrapInSquareBrackets} of the green, copyable text. */
    public static ITextComponent copyable(String text) {
        ITextComponent inner = new TextComponentString(text);
        inner.getStyle()
                .setColor(TextFormatting.GREEN)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COPY + text))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentTranslation("chat.copy.click")))
                .setInsertion(text);
        return new TextComponentString("[").appendSibling(inner).appendText("]");
    }
}
