package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

/**
 * The player in the survival and creative inventories as 1.20.2 draws it ({@link VanillaRevisions#inventoryPreviewClipped},
 * 26.3's {@code InventoryScreen.extractEntityInInventoryFollowsMouse}): in a box, centered on the box by half its
 * bounding box plus 0.0625, looking toward the cursor from the box's center, and clipped to the box. An elytra, a
 * swim or a ride no longer spreads the model over the armor slots and the hotbar. Before 1.20.2 it is 1.12.2's
 * {@code drawEntityOnScreen}, unchanged.
 *
 * <p>Patch P78 sends both screens' {@code GuiInventory.drawEntityOnScreen} calls here with their arguments. The screen's
 * left and top and the mouse come back out of them: the survival inventory passes {@code (i + 51, j + 75, 30,
 * i + 51 - mouseX, j + 25 - mouseY)}, the creative Inventory tab {@code (i + 88, j + 45, 20, i + 88 - mouseX,
 * j + 15 - mouseY)}. Other mods' calls of {@code drawEntityOnScreen} are not patched.
 */
@SideOnly(Side.CLIENT)
public final class InventoryPreview {

    /** 26.3's {@code InventoryScreen.extractBackground}: the box, from the screen's left and top. */
    static final int[] SURVIVAL_BOX = {26, 8, 75, 78};
    /** 26.3's {@code CreativeModeInventoryScreen.extractBackground}, the Inventory tab. */
    static final int[] CREATIVE_BOX = {73, 6, 105, 49};
    /** 1.20.2's {@code yOffset} below half the bounding box. */
    static final float Y_OFFSET = 0.0625F;

    /** For the client self-test: previews drawn by Core's 1.20.2 path. */
    public static volatile int drawn;
    /** For the client self-test: the last box (GUI x0, y0, x1, y1) and its scissor (window pixels x, y, w, h). */
    public static volatile int[] lastBox = new int[0];
    public static volatile int[] lastScissor = new int[0];
    /** For the client self-test: whether the scissor test was on while the model was drawn, and the model's feet (GUI y). */
    public static volatile boolean lastClipped;
    public static volatile float lastFeetY;
    public static volatile float lastHeight;

    private static final IntBuffer BOX = BufferUtils.createIntBuffer(16);

    private InventoryPreview() {
    }

    /** P78, the survival inventory. */
    public static void survival(int posX, int posY, int scale, float mouseX, float mouseY, EntityLivingBase entity) {
        if (!VanillaRevisions.inventoryPreviewClipped()) {
            GuiInventory.drawEntityOnScreen(posX, posY, scale, mouseX, mouseY, entity);
            return;
        }
        int left = posX - 51;
        int top = posY - 75;
        draw(left, top, SURVIVAL_BOX, scale, posX - mouseX, posY - 50 - mouseY, entity);
    }

    /** P78, the creative Inventory tab. */
    public static void creative(int posX, int posY, int scale, float mouseX, float mouseY, EntityLivingBase entity) {
        if (!VanillaRevisions.inventoryPreviewClipped()) {
            GuiInventory.drawEntityOnScreen(posX, posY, scale, mouseX, mouseY, entity);
            return;
        }
        int left = posX - 88;
        int top = posY - 45;
        draw(left, top, CREATIVE_BOX, scale, posX - mouseX, posY - 30 - mouseY, entity);
    }

    /** 1.20.2's {@code renderEntityInInventoryFollowsMouse}, with 1.12.2's own drawing of the entity. */
    static void draw(int left, int top, int[] box, int scale, float mouseX, float mouseY, EntityLivingBase entity) {
        int x0 = left + box[0];
        int y0 = top + box[1];
        int x1 = left + box[2];
        int y1 = top + box[3];
        float centerX = (x0 + x1) / 2.0F;
        float centerY = (y0 + y1) / 2.0F;
        float feetY = centerY + (entity.height / 2.0F + Y_OFFSET) * scale;

        Minecraft mc = Minecraft.getMinecraft();
        int factor = new ScaledResolution(mc).getScaleFactor();
        boolean outer = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] restore = null;
        int sx = x0 * factor;
        int sy = mc.displayHeight - y1 * factor;
        int sw = (x1 - x0) * factor;
        int sh = (y1 - y0) * factor;
        if (outer) {
            // GuiGraphics.enableScissor intersects with the scissor already set: a screen that clips keeps clipping.
            BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, BOX);
            restore = new int[] {BOX.get(0), BOX.get(1), BOX.get(2), BOX.get(3)};
            int ax = Math.max(sx, restore[0]);
            int ay = Math.max(sy, restore[1]);
            int bx = Math.min(sx + sw, restore[0] + restore[2]);
            int by = Math.min(sy + sh, restore[1] + restore[3]);
            sx = ax;
            sy = ay;
            sw = Math.max(0, bx - ax);
            sh = Math.max(0, by - ay);
        } else {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glScissor(sx, sy, sw, sh);
        lastBox = new int[] {x0, y0, x1, y1};
        lastScissor = new int[] {sx, sy, sw, sh};
        lastClipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        lastFeetY = feetY;
        lastHeight = entity.height;
        try {
            GlStateManager.pushMatrix();
            GlStateManager.translate(centerX, feetY, 0.0F);
            // The look follows the cursor from the box's center (1.12.2: from the eyes of a standing player).
            GuiInventory.drawEntityOnScreen(0, 0, scale, centerX - mouseX, centerY - mouseY, entity);
            GlStateManager.popMatrix();
        } finally {
            if (outer) {
                GL11.glScissor(restore[0], restore[1], restore[2], restore[3]);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
        drawn++;
    }
}
