package com.exiledradio.backportcore.util;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Block states by property name and value, as blockstate and structure files write them. */
public final class BlockStates {

    private BlockStates() {
    }

    /** @return the state's properties by name, as the strings a blockstate file uses */
    public static Map<String, String> props(IBlockState state) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            out.put(entry.getKey().getName(), valueName(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    /** @return a property value's name, as a blockstate file writes it */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String valueName(IProperty property, Comparable value) {
        return property.getName(value);
    }

    /**
     * @return the state with the named property set, or null if the block has no such property or
     * does not know the value
     */
    @Nullable
    public static IBlockState with(IBlockState state, String name, String value) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals(name)) {
                return withValue(state, property, value);
            }
        }
        return null;
    }

    /** @return the state with the named property set, or the state unchanged if it can't be */
    public static IBlockState withOrSame(IBlockState state, String name, String value) {
        IBlockState out = with(state, name, value);
        return out == null ? state : out;
    }

    @Nullable
    private static <T extends Comparable<T>> IBlockState withValue(IBlockState state, IProperty<T> property, String value) {
        com.google.common.base.Optional<T> parsed = property.parseValue(value);
        return parsed.isPresent() ? state.withProperty(property, parsed.get()) : null;
    }
}
