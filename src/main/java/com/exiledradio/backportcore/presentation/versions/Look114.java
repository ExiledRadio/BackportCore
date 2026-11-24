package com.exiledradio.backportcore.presentation.versions;

/**
 * 1.14's look and feel that Backport Core owns since 1.4.7 (moved out of Village & Pillage Backport (villagerbackport)): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.14 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look114Client}.
 */
public final class Look114 {

    private Look114() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
    }
}
