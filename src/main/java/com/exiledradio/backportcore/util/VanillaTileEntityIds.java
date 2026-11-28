package com.exiledradio.backportcore.util;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntityComparator;
import net.minecraft.tileentity.TileEntityDaylightDetector;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityEnchantmentTable;
import net.minecraft.tileentity.TileEntityEndGateway;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.tileentity.TileEntityStructure;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that 1.12.2's own tile entity ids still name 1.12.2's classes. A mod that registers another class under a
 * vanilla id ({@code GameRegistry.registerTileEntity(X.class, "mob_spawner")}: an id without a namespace is
 * {@code minecraft:}) silently replaces vanilla's entry, since the registry is a bimap and Forge does not warn. From
 * then on every vanilla tile entity of that kind fails to save ("class ... is missing a mapping! This is a bug!"
 * from {@code TileEntity.writeInternal}), so spawners, chests and the rest lose their contents on reload. This names
 * the class that took the id and the mod and jar it came from, at load complete and again when a server starts.
 * It only reports; putting vanilla back would break the other mod's own tile entities.
 */
public final class VanillaTileEntityIds {

    /** 1.12.2's TileEntity.registerTileEntities, in its order. */
    private static final Object[][] VANILLA = {
            {"furnace", TileEntityFurnace.class},
            {"chest", TileEntityChest.class},
            {"ender_chest", TileEntityEnderChest.class},
            {"jukebox", net.minecraft.block.BlockJukebox.TileEntityJukebox.class},
            {"dispenser", TileEntityDispenser.class},
            {"dropper", TileEntityDropper.class},
            {"sign", TileEntitySign.class},
            {"mob_spawner", TileEntityMobSpawner.class},
            {"noteblock", TileEntityNote.class},
            {"piston", TileEntityPiston.class},
            {"brewing_stand", TileEntityBrewingStand.class},
            {"enchanting_table", TileEntityEnchantmentTable.class},
            {"end_portal", TileEntityEndPortal.class},
            {"beacon", TileEntityBeacon.class},
            {"skull", TileEntitySkull.class},
            {"daylight_detector", TileEntityDaylightDetector.class},
            {"hopper", TileEntityHopper.class},
            {"comparator", TileEntityComparator.class},
            {"flower_pot", TileEntityFlowerPot.class},
            {"banner", TileEntityBanner.class},
            {"structure_block", TileEntityStructure.class},
            {"end_gateway", TileEntityEndGateway.class},
            {"command_block", TileEntityCommandBlock.class},
            {"shulker_box", TileEntityShulkerBox.class},
            {"bed", TileEntityBed.class},
    };

    /** What the last check found, so a later check logs only a change. */
    private static List<String> last = new ArrayList<>();

    private VanillaTileEntityIds() {
    }

    /** One line per vanilla id that no longer names its vanilla class (empty when all is well). */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>> registry = registry();
        for (Object[] v : VANILLA) {
            ResourceLocation id = new ResourceLocation("minecraft", (String) v[0]);
            @SuppressWarnings("unchecked")
            Class<? extends TileEntity> vanilla = (Class<? extends TileEntity>) v[1];
            ResourceLocation key = TileEntity.getKey(vanilla);
            if (id.equals(key)) {
                continue;
            }
            Class<? extends TileEntity> holder = registry == null ? null : registry.getObject(id);
            String who = holder == null ? "nothing" : holder.getName() + " (" + owner(holder) + ")";
            out.add("Tile entity id " + id + " is held by " + who + ", not 1.12.2's " + vanilla.getSimpleName()
                    + ": every vanilla " + v[0] + " fails to save (\"" + vanilla.getName()
                    + " is missing a mapping\") and loses its contents on reload.");
        }
        return out;
    }

    /** Logs any problem (as an error, once per change). when: the moment, for the log. */
    public static void check(String when) {
        List<String> now;
        try {
            now = problems();
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Could not check vanilla tile entity ids ({}).", e.toString());
            return;
        }
        if (now.equals(last)) {
            return;
        }
        last = now;
        for (String line : now) {
            BackportCore.LOGGER.error("{} ({}). Remove or update the mod named here, or ask its author to register "
                    + "its tile entity under its own mod id.", line, when);
        }
    }

    @Nullable
    private static RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>> registry() {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(TileEntity.class, null, "field_190562_f");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The mod (id, name, version) whose jar or folder a class was loaded from, or that jar's name. */
    static String owner(Class<?> c) {
        File source = source(c);
        if (source == null) {
            return "unknown jar";
        }
        for (ModContainer mod : Loader.instance().getModList()) {
            File f = mod.getSource();
            if (f != null && sameFile(f, source)) {
                return "mod " + mod.getModId() + ", " + mod.getName() + " " + mod.getVersion() + ", " + source.getName();
            }
        }
        return source.getName();
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (java.io.IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    @Nullable
    private static File source(Class<?> c) {
        try {
            CodeSource cs = c.getProtectionDomain().getCodeSource();
            URL url = cs == null ? null : cs.getLocation();
            if (url == null) {
                return null;
            }
            String path = url.getPath();
            int bang = path.indexOf("!/");
            if (path.startsWith("file:")) {
                path = new URL(path.substring(0, bang < 0 ? path.length() : bang)).getPath();
            } else if (bang >= 0) {
                path = path.substring(0, bang);
            }
            return new File(java.net.URLDecoder.decode(path, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }
}
