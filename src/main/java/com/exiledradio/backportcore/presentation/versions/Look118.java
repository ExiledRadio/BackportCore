package com.exiledradio.backportcore.presentation.versions;

/**
 * 1.17 and 1.18's look and feel that Backport Core owns since 1.4.7 (moved out of Caves & Cliffs Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.17 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look118Client}.
 */
public final class Look118 {

    private Look118() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
    }
}
