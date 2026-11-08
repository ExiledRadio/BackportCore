package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one owner of the creative inventory's order: each tab sorted by the newest installed version's list
 * ({@link com.exiledradio.backportcore.presentation.Contribution#creativeOrder}), as that version lists them.
 * Items the list does not name keep their own order, after the ones it does. No patch: the open tab's item list is
 * sorted once each time the tab is filled (a tab change), before it is drawn. The search tab and the survival
 * inventory are left alone.
 */
@SideOnly(Side.CLIENT)
public final class CreativeOrder {

    private static Field selectedTab;
    private static int sortedTab = -1;
    private static int sortedSize = -1;
    @Nullable
    private static ItemStack sortedFirst;
    @Nullable
    private static ResourceLocation loadedFrom;
    private static Map<String, Integer> index = new HashMap<String, Integer>();
    /** For the self-test: tabs sorted. */
    public static volatile int sorts;

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.getGui() instanceof GuiContainerCreative) {
            sortOpenTab((GuiContainerCreative) event.getGui());
        }
    }

    static void sortOpenTab(GuiContainerCreative gui) {
        ResourceLocation file = Presentation.creativeOrder();
        if (file == null) {
            return;
        }
        try {
            if (selectedTab == null) {
                selectedTab = ReflectionHelper.findField(GuiContainerCreative.class, "selectedTabIndex", "field_147058_w");
            }
            int tab = selectedTab.getInt(null);
            if (tab == CreativeTabs.SEARCH.getIndex() || tab == CreativeTabs.INVENTORY.getIndex()) {
                return;
            }
            NonNullList<ItemStack> items = ((GuiContainerCreative.ContainerCreative) gui.inventorySlots).itemList;
            ItemStack first = items.isEmpty() ? null : items.get(0);
            if (tab == sortedTab && items.size() == sortedSize && first == sortedFirst) {
                return;
            }
            Map<String, Integer> order = order(file);
            List<ItemStack> copy = new ArrayList<ItemStack>(items);
            copy.sort(Comparator.comparingInt(s -> rank(order, s)));
            for (int i = 0; i < copy.size(); i++) {
                items.set(i, copy.get(i));
            }
            ((GuiContainerCreative.ContainerCreative) gui.inventorySlots).scrollTo(0.0F);
            sortedTab = tab;
            sortedSize = items.size();
            sortedFirst = items.isEmpty() ? null : items.get(0);
            sorts++;
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: could not sort the creative tab", e);
            loadedFrom = file;
            index = new HashMap<String, Integer>();
        }
    }

    /** @return the position of a stack in the order ({@code name@meta} first, then {@code name}), or past the end */
    static int rank(Map<String, Integer> order, ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return Integer.MAX_VALUE;
        }
        Integer exact = order.get(name + "@" + stack.getMetadata());
        if (exact != null) {
            return exact;
        }
        Integer any = order.get(name.toString());
        return any == null ? Integer.MAX_VALUE : any;
    }

    /** @return the file's order, read once: each line {@code modid:name} or {@code modid:name@meta} */
    static synchronized Map<String, Integer> order(ResourceLocation file) {
        if (file.equals(loadedFrom)) {
            return index;
        }
        Map<String, Integer> out = new HashMap<String, Integer>();
        String path = "assets/" + file.getNamespace() + "/" + file.getPath();
        try (InputStream in = CreativeOrder.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                int n = 0;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String key = line.indexOf(':') < 0 ? "minecraft:" + line : line;
                    if (!out.containsKey(key)) {
                        out.put(key, n++);
                    }
                }
            } else {
                BackportCore.LOGGER.warn("Presentation: creative order file {} is missing", path);
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: could not read the creative order {}", path, e);
        }
        loadedFrom = file;
        index = out;
        return out;
    }
}
