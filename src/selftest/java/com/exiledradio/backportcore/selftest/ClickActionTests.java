package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.CopyToClipboard;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** 1.15's copy_to_clipboard click action (presentation/CopyToClipboard): tellraw's JSON keeps it both ways. */
final class ClickActionTests {

    private ClickActionTests() {
    }

    static void schedule() {
        then(1, "click actions: copy_to_clipboard", world -> {
            check("the action is added", CopyToClipboard.ACTION != null && ClickEvent.Action.getValueByCanonicalName("copy_to_clipboard")
                    == CopyToClipboard.ACTION && CopyToClipboard.ACTION.shouldAllowInChat(), CopyToClipboard.ACTION);
            ITextComponent text = ITextComponent.Serializer.jsonToComponent(
                    "{\"text\":\"seed\",\"clickEvent\":{\"action\":\"copy_to_clipboard\",\"value\":\"-650\"}}");
            ClickEvent click = text == null ? null : text.getStyle().getClickEvent();
            check("tellraw JSON with copy_to_clipboard keeps its click", click != null && click.getAction() == CopyToClipboard.ACTION
                    && "-650".equals(click.getValue()), click);
            String back = ITextComponent.Serializer.componentToJson(text);
            check("and writes it back for the clients", back.contains("\"copy_to_clipboard\"") && back.contains("-650"), back);
            check("1.12.2's actions are unchanged", ClickEvent.Action.getValueByCanonicalName("run_command") == ClickEvent.Action.RUN_COMMAND);
        });
    }
}
