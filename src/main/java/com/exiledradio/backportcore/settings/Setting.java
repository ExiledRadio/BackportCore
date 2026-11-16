package com.exiledradio.backportcore.settings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional marks on a backport's {@code @Config} fields that tell Core's settings screen how to show them. A field
 * without any still shows: {@code enabled} becomes its category's on/off switch, Forge's restart flags become
 * badges, and an unmarked setting counts as {@link Server}. None of these change the {@code .cfg} file.
 */
public final class Setting {

    private Setting() {
    }

    /** On a category field: its row on the mod's page. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Feature {
        /**
         * The item drawn beside it, as {@code "modid:name"} or {@code "modid:name@meta"}; several separated by
         * {@code |} take the first one installed. Empty for none.
         */
        String icon() default "";

        /** The boolean field in the category that switches it on and off. Default: {@code enabled}, if it has one. */
        String toggle() default "";
    }

    /** Read only by the client: each player sets it for their own game, also on a server. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Client {
    }

    /** Read by the server (or the single-player world): on a multiplayer server, the server's value counts. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Server {
    }

    /** Only changes terrain generated after it is changed. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NewChunks {
    }

    /** Vanilla's value, where the default differs from it. Without this, the default is taken to be vanilla's. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Vanilla {
        String value();
    }

    /** What a list's entries name, for the list editor's check and suggestions. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ListOf {
        Kind value();
    }

    /** What a list entry names. The part before the first {@code =}, space or {@code @} is the id. */
    public enum Kind {
        BLOCK, ITEM, ENTITY, BIOME, ANY
    }
}
