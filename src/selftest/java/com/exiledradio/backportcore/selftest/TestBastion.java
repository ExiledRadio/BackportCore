package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.structure.Jigsaw;
import com.exiledradio.backportcore.structure.NetherStructureSlots;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.StructureRandoms;
import com.exiledradio.backportcore.structure.VanillaPlacements;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

/** Self-test only: 1.16.5's bastion, from Mojang's own pools and templates, on 1.16.5's shared slots. */
public final class TestBastion extends ScatteredStructure {

    static final String KIND = "backportcore_selftest_bastion";
    static final ResourceLocation START = new ResourceLocation("minecraft", "bastion/starts");

    static final List<StructureStart> LAID_OUT = new ArrayList<StructureStart>();

    public TestBastion() {
        super("BCST_Bastion", VanillaPlacements.V1_16_5.BASTION_REMNANT);
    }

    @Override
    protected boolean canStartAt(World world, int chunkX, int chunkZ) {
        return NetherStructureSlots.startAt(world, chunkX, chunkZ) == NetherStructureSlots.Slot.BASTION;
    }

    @Override
    protected StructureStart getStructureStart(int chunkX, int chunkZ) {
        Start start = new Start(this.world, chunkX, chunkZ);
        LAID_OUT.add(start);
        return start;
    }

    public static final class Start extends StructureStart {
        public Start() {
        }

        Start(World world, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            // BastionFeature: start y 33, depth 6, laid out with 1.16's start random.
            this.components.addAll(Jigsaw.assemble(world, START, 6, new BlockPos(chunkX << 4, 33, chunkZ << 4),
                    StructureRandoms.start(world.getSeed(), chunkX, chunkZ)));
            updateBoundingBox();
        }
    }
}
