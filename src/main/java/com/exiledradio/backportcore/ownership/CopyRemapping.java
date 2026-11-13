package com.exiledradio.backportcore.ownership;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.settings.Family;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nullable;

/**
 * Rule 1, third bullet: when an owner is added to a world that holds another backport's copies of its blocks, items
 * or effects, the copies become the owner's as the world loads.
 *
 * <p>A copy is registered only while its owner is absent, so a world saved then holds {@code copier:name} entries
 * that are missing once the owner is installed (the copier no longer registers them). Forge then asks
 * {@code RegistryEvent.MissingMappings}; this remaps each missing name of a family mod onto {@code owner:name} when
 * the owner owns that name ({@link BlockOwnership}, {@link ItemOwnership}), provides it, and has it registered.
 * Copies have the owner's states and metadata, so the numeric ids are all that change: chunks, inventories and
 * effects carry over as they are. Block items follow their blocks' names.
 *
 * <p>Only names under a family mod's id are touched ({@link Family#MEMBERS}); another mod's block of the same name
 * is never taken. The reverse (an owner removed, a copier taking over) is the copier's own.
 */
public final class CopyRemapping {

    /** For the self-test and the log: names remapped since start. */
    public static volatile int remapped;

    @SubscribeEvent
    public void blocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> m : event.getMappings()) {
            Block owner = target(ForgeRegistries.BLOCKS, m.key, ownerOfBlock(m.key.getPath()));
            if (owner != null) {
                m.remap(owner);
                log(m.key, owner.getRegistryName());
            }
        }
    }

    @SubscribeEvent
    public void items(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> m : event.getMappings()) {
            String path = m.key.getPath();
            String ownerId = ItemOwnership.itemProvided(path) ? ItemOwnership.itemOwner(path) : ownerOfBlock(path);
            Item owner = target(ForgeRegistries.ITEMS, m.key, ownerId);
            if (owner != null) {
                m.remap(owner);
                log(m.key, owner.getRegistryName());
            }
        }
    }

    @SubscribeEvent
    public void effects(RegistryEvent.MissingMappings<Potion> event) {
        for (RegistryEvent.MissingMappings.Mapping<Potion> m : event.getMappings()) {
            String path = m.key.getPath();
            Potion owner = target(ForgeRegistries.POTIONS, m.key, ItemOwnership.effectProvided(path) ? ItemOwnership.effectOwner(path) : null);
            if (owner != null) {
                m.remap(owner);
                log(m.key, owner.getRegistryName());
            }
        }
    }

    @Nullable
    private static String ownerOfBlock(String path) {
        return BlockOwnership.ownerProvides(path) ? BlockOwnership.owner(path) : null;
    }

    /** @return the owner's entry for a missing family name, or null when nothing should change */
    @Nullable
    public static <T extends IForgeRegistryEntry<T>> T target(IForgeRegistry<T> registry, ResourceLocation missing, @Nullable String ownerId) {
        if (ownerId == null || ownerId.equals(missing.getNamespace()) || Family.get(missing.getNamespace()) == null) {
            return null;
        }
        ResourceLocation id = new ResourceLocation(ownerId, missing.getPath());
        T value = registry.getValue(id);
        return value == null || !id.equals(value.getRegistryName()) ? null : value;
    }

    private static void log(ResourceLocation from, @Nullable ResourceLocation to) {
        remapped++;
        BackportCore.LOGGER.info("Ownership: {} is {}'s now (its owner is installed).", from, to);
    }
}
