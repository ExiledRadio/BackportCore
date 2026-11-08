package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldEdit;
import net.minecraft.client.gui.toasts.SystemToast;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 1.13's Edit World buttons (17w47a): Make Backup (a zip of the world in {@code .minecraft/backups}, named
 * {@code yyyy-MM-dd_HH-mm-ss_<folder>.zip}, as 1.13's {@code SaveFormat.createBackup}) and Open Backups Folder, at
 * 1.13.2's positions in {@code GuiWorldEdit}. Optimize World (the Flattening's converter) has no 1.12.2
 * counterpart, so its row stays empty.
 */
@SideOnly(Side.CLIENT)
public final class WorldBackups {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiWorldEdit) || !Presentation.isAtLeast("1.13")) {
            return;
        }
        GuiScreen gui = event.getGui();
        String worldId;
        try {
            worldId = (String) ReflectionHelper.findField(GuiWorldEdit.class, "worldId", "field_184860_g").get(gui);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: cannot read the edited world's folder; no backup buttons", e);
            return;
        }
        List<GuiButton> buttons = event.getButtonList();
        int x = gui.width / 2 - 100;
        int top = gui.height / 4 - 16;
        for (GuiButton b : buttons) {
            if (b.id == 3) {
                b.y = top + 24;
            } else if (b.id == 4) {
                b.y = top + 48;
            } else if (b.id == 0) {
                b.x = x;
                b.y = gui.height / 4 + 144 + 5;
                b.width = 98;
            } else if (b.id == 1) {
                b.x = gui.width / 2 + 2;
                b.y = gui.height / 4 + 144 + 5;
                b.width = 98;
            }
        }
        buttons.add(new OptionWidgets.Action(x, top + 72, 200, I18n.format("selectWorld.edit.backup"), () -> backupWithToast(worldId)));
        buttons.add(new OptionWidgets.Action(x, top + 96, 200, I18n.format("selectWorld.edit.backupFolder"), () -> {
            File dir = backupsDir();
            dir.mkdirs();
            OpenGlHelper.openFile(dir);
        }));
    }

    public static File backupsDir() {
        return new File(Minecraft.getMinecraft().gameDir, "backups");
    }

    static void backupWithToast(String worldId) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            long size = backup(new File(mc.gameDir, "saves"), worldId, backupsDir());
            mc.getToastGui().add(new SystemToast(SystemToast.Type.NARRATOR_TOGGLE,
                    new TextComponentTranslation("selectWorld.edit.backupCreated", worldId),
                    new TextComponentTranslation("selectWorld.edit.backupSize", (int) Math.ceil(size / 1048576.0))));
        } catch (IOException e) {
            BackportCore.LOGGER.error("Failed to backup level {}", worldId, e);
            mc.getToastGui().add(new SystemToast(SystemToast.Type.NARRATOR_TOGGLE,
                    new TextComponentTranslation("selectWorld.edit.backupFailed"), new TextComponentTranslation(e.getMessage() == null ? "" : e.getMessage())));
        }
    }

    /** 1.13's {@code createBackup}: every file of the world but session.lock, under {@code <folder>/}. @return the zip's size */
    public static long backup(File saves, String worldId, File backups) throws IOException {
        backups.mkdirs();
        String base = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + "_" + worldId;
        File zip = new File(backups, base + ".zip");
        for (int i = 1; zip.exists(); i++) {
            zip = new File(backups, base + "_" + i + ".zip");
        }
        Path world = new File(saves, worldId).toPath();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
            Path prefix = world.getFileName();
            Files.walkFileTree(world, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("session.lock")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = prefix.resolve(world.relativize(file)).toString().replace('\\', '/');
                    out.putNextEntry(new ZipEntry(name));
                    Files.copy(file, out);
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return Files.size(zip.toPath());
    }
}
