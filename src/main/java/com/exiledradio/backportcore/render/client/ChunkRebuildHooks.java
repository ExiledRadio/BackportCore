package com.exiledradio.backportcore.render.client;

import com.exiledradio.backportcore.CoreConfig;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * What patch P17 (docs/PATCHES.md) calls from {@code RenderGlobal}: which changed chunks are rebuilt at once on the
 * render thread, by current vanilla's "Chunk Builder" setting ({@link CoreConfig.BlockUpdates}).
 *
 * <p>Vanilla from 1.18.2 ({@code LevelRenderer.compileChunks}, 1.19.2's read here) builds a chunk at once when
 * {@code NEARBY} and it is within {@code 768} (squared) of the camera or dirtied by the player, when
 * {@code PLAYER_AFFECTED} and it is dirtied by the player, and never when {@code NONE} (the default). Before
 * the option (1.12.2's {@code setupTerrain}, and still 1.15.2's and 1.16.5's, read from their jars) the rule is
 * {@code NEARBY}. "Dirtied by the player" is 1.12.2's {@code RenderChunk.needsImmediateUpdate},
 * set by a block change with flag 8, which 1.12.2's {@code PlayerControllerMP} uses for the blocks the player
 * breaks and places, as later versions do; a change the server sends ({@code SPacketBlockChange}) uses flags 3.
 */
@SideOnly(Side.CLIENT)
public final class ChunkRebuildHooks {

    /** What {@link #nearbyDistanceSq} returns to turn the distance rule off: no squared distance is below it. */
    private static final double NEVER = -1.0D;

    private ChunkRebuildHooks() {
    }

    /**
     * {@code setupTerrain}'s {@code 768.0D}: a changed chunk closer than this (squared, from its center) is built
     * at once. 768.0 when {@code NEARBY}, as 1.12.2; otherwise no distance qualifies.
     */
    public static double nearbyDistanceSq(double vanilla) {
        return CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.NEARBY ? vanilla : NEVER;
    }

    /**
     * The result of {@code renderChunk.needsImmediateUpdate()} in {@code setupTerrain} and {@code updateChunks}: the
     * player's own changes, built at once unless {@code NONE}.
     */
    public static boolean needsImmediateUpdate(boolean playerChanged) {
        return playerChanged && CoreConfig.rendering.blockUpdates != CoreConfig.BlockUpdates.NONE;
    }
}
