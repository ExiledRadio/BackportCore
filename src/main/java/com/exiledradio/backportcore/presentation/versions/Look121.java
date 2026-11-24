package com.exiledradio.backportcore.presentation.versions;

/**
 * 1.21's look and feel that Backport Core owns since 1.4.7 (moved out of Tricky Trials Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.21 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look121Client}.
 */
public final class Look121 {

    private Look121() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
    }
}
