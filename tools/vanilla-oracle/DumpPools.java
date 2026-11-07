import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.RegistryWriteOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.feature.structures.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Writes 1.16.5's built-in template pools and processor lists as data-pack JSON, with Mojang's codecs. */
public final class DumpPools {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        Bootstrap.bootStrap();
        RegistryAccess.RegistryHolder access = RegistryAccess.builtin();
        RegistryWriteOps<JsonElement> ops = RegistryWriteOps.create(JsonOps.INSTANCE, access);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        int pools = 0;
        for (Map.Entry<ResourceKey<StructureTemplatePool>, StructureTemplatePool> e : BuiltinRegistries.TEMPLATE_POOL.entrySet()) {
            JsonElement json = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, e.getValue()).getOrThrow(false, System.err::println);
            write(gson, new File(out, "template_pool/" + e.getKey().location().getPath() + ".json"), json);
            pools++;
        }
        int lists = 0;
        for (Map.Entry<ResourceKey<StructureProcessorList>, StructureProcessorList> e : BuiltinRegistries.PROCESSOR_LIST.entrySet()) {
            JsonElement json = StructureProcessorType.DIRECT_CODEC.encodeStart(ops, e.getValue()).getOrThrow(false, System.err::println);
            write(gson, new File(out, "processor_list/" + e.getKey().location().getPath() + ".json"), json);
            lists++;
        }
        System.out.println("pools " + pools + " processor lists " + lists);
    }

    private static void write(Gson gson, File file, JsonElement json) throws Exception {
        file.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(json, w);
        }
    }
}
