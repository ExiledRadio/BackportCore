package com.exiledradio.backportcore.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.concurrent.atomic.AtomicInteger;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * 1.14's light update packet ({@code light.LightUpdates}): light the server changes with no block change, in a chunk
 * the client has, reaches the client. The server sets sky light inside the solid block under the player (nothing on
 * either side rechecks light in a solid block, so only the packet can carry it); the client must then read the same
 * value. 1.12.2 never sends it: the client keeps the chunk's old light. Runs after {@link ClientSwingTests}.
 */
@SideOnly(Side.CLIENT)
final class ClientLightTests {

    static boolean done;
    private static int step;
    private static int polls;
    private static BlockPos pos;
    private static int want;
    private static final AtomicInteger server = new AtomicInteger(-1);

    private ClientLightTests() {
    }

    static void tick(Minecraft mc) {
        if (done) {
            return;
        }
        IntegratedServer srv = mc.getIntegratedServer();
        switch (step) {
            case 0: {
                BlockPos p = new BlockPos(mc.player).down();
                while (p.getY() > 1 && !mc.world.getBlockState(p).isOpaqueCube()) {
                    p = p.down();
                }
                pos = p;
                int before = mc.world.getLightFor(EnumSkyBlock.SKY, p);
                want = before >= 8 ? 2 : 11;
                final BlockPos at = p;
                final int value = want;
                srv.addScheduledTask(() -> {
                    WorldServer w = srv.getWorld(mc.player.dimension);
                    w.setLightFor(EnumSkyBlock.SKY, at, value);
                    server.set(w.getLightFor(EnumSkyBlock.SKY, at));
                });
                step++;
                break;
            }
            case 1: {
                int got = mc.world.getLightFor(EnumSkyBlock.SKY, pos);
                if (got != want && ++polls < 100) {
                    return;
                }
                check("client light: sky light the server set at " + pos + " with no block change reached the client",
                        server.get() == want && got == want, "server " + server.get() + ", client " + got + ", want " + want);
                done = true;
                break;
            }
            default:
                done = true;
                break;
        }
    }
}
