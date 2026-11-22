package com.exiledradio.backportcore.worldgen.client;

import com.exiledradio.backportcore.presentation.client.DebugText;
import com.exiledradio.backportcore.worldgen.UndergroundBiomes;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The client's side of {@link UndergroundBiomes}: F3's "Biome:" line names the underground biome at the camera's block
 * (1.18's {@code Level.getBiome} there), and a chunk whose spans arrive is drawn again with their colors.
 */
@SideOnly(Side.CLIENT)
public final class UndergroundBiomesClient {

    private UndergroundBiomesClient() {
    }

    public static void init() {
        UndergroundBiomes.redraw = k -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null && k[3] > k[2]) {
                int x = (int) k[0] << 4, z = (int) k[1] << 4;
                // One past each side: the 3 by 3 color blend reaches into the neighbors.
                mc.world.markBlockRangeForRenderUpdate(x - 1, (int) k[2] - 1, z - 1, x + 16, (int) k[3], z + 16);
            }
        };
        DebugText.add("1.18", "underground biome name", lines -> {
            Minecraft mc = Minecraft.getMinecraft();
            Entity e = mc.getRenderViewEntity();
            if (mc.world == null || e == null) {
                return;
            }
            Biome b = UndergroundBiomes.at(mc.world, new BlockPos(e.posX, e.getEntityBoundingBox().minY, e.posZ));
            if (b == null) {
                return;
            }
            for (int i = 0; i < lines.left.size(); i++) {
                String s = lines.left.get(i);
                if (s != null && s.startsWith("Biome: ")) {
                    lines.left.set(i, "Biome: " + b.getBiomeName());
                }
            }
        });
    }
}
