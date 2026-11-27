// 26.4's creative tabs, as the game fills them: every tab in the creative menu's order, each item once per tab
// (the first stack of it), with default feature flags and no operator permission. The input of build_order_263.py.
//
// Run from the 26.4 server jar's own jar (META-INF/versions/26.4/server-26.4.jar) with the libraries it bundles
// (META-INF/libraries/**) on the class path, Java 25:
//   javac -cp <cp> -d cls CreativeOrder264.java
//   java -cp "cls;<cp>" CreativeOrder264 > oracle.txt     (each line comes through the game's logger: "[STDOUT]: ...")
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashSet;
import java.lang.reflect.Method;
import java.util.Collections;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.TagKey;
import java.util.Set;

public class CreativeOrder264 {
    public static void main(String[] a) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        // Tags are bound by a data pack reload; only two matter to the tabs (every painting is placeable, every horn a
        // goat horn; the item is the same either way). Every other holder gets none.
        Method bind = Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
        bind.setAccessible(true);
        for (HolderLookup.RegistryLookup<?> reg : (Iterable<HolderLookup.RegistryLookup<?>>) lookup.listRegistries()::iterator) {
            for (Object h : reg.listElements().toArray()) {
                Holder.Reference<?> r = (Holder.Reference<?>) h;
                Object tag = null;
                if (reg.key().equals(Registries.PAINTING_VARIANT)) tag = PaintingVariantTags.PLACEABLE;
                if (reg.key().equals(Registries.INSTRUMENT)) tag = InstrumentTags.GOAT_HORNS;
                try { r.tags(); continue; } catch (IllegalStateException unbound) { }
                bind.invoke(r, tag == null ? Collections.emptyList() : Collections.singletonList(tag));
            }
        }
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(p -> p.apply());
        // Goat horns need the instrument tag's contents, which only a reload builds: the tabs see no instrument
        // registry, and the goat horn is put back where the tab lists it (after the item before it in the source).
        HolderLookup.Provider tabs = HolderLookup.Provider.create(lookup.listRegistries().filter(r -> !r.key().equals(Registries.INSTRUMENT)));
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, false, tabs);
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            if (BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).getPath().equals("op_blocks")) continue;
            String name = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).getPath();
            Set<String> seen = new LinkedHashSet<String>();
            for (ItemStack s : tab.getDisplayItems()) {
                seen.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            }
            System.out.println("== " + name + " " + seen.size());
            for (String id : seen) System.out.println(id);
        }
    }
}
