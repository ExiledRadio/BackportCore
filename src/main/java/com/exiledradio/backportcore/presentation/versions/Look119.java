package com.exiledradio.backportcore.presentation.versions;

/**
 * 1.19's look and feel that Backport Core owns since 1.4.7 (moved out of Wild Update Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.19 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look119Client}.
 */
public final class Look119 {

    private Look119() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
    }
}
