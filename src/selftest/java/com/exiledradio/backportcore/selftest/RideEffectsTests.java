package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.advancement.AllEffects;
import com.exiledradio.backportcore.boat.BoatNetwork;
import com.exiledradio.backportcore.boat.BoatWood;
import com.exiledradio.backportcore.boat.EntityWoodBoat;
import com.exiledradio.backportcore.boat.EntityWoodChestBoat;
import com.exiledradio.backportcore.presentation.GameVersion;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The xisumavoid audit's two Core rows: 1.19's inventory key on a chest boat (the server side of {@link BoatNetwork}),
 * and How Did We Get Here? with the backports' effects ({@link AllEffects}).
 */
final class RideEffectsTests {

    private RideEffectsTests() {
    }

    static void schedule() {
        then(1, "Chest boat: the inventory key while riding opens its chest", world -> {
            EntityPlayerMP player = PoseTests.player(world, "chestboatkey");
            player.setPosition(4700.5, 120, 4700.5);
            world.spawnEntity(player);
            EntityWoodChestBoat chest = new EntityWoodChestBoat(world, 4700.5, 120, 4700.5, BoatWood.get("minecraft:oak"));
            world.spawnEntity(chest);
            check("not riding: nothing opens", !BoatNetwork.open(player) && player.openContainer instanceof ContainerPlayer,
                    player.openContainer);
            check("the player rides the chest boat", player.startRiding(chest), player.getRidingEntity());
            check("riding: the request opens the boat's chest", BoatNetwork.open(player) && player.openContainer instanceof ContainerChest,
                    player.openContainer);
            check("that chest is the boat's", player.openContainer instanceof ContainerChest
                    && ((ContainerChest) player.openContainer).getLowerChestInventory() == chest, "");
            player.closeScreen();
            player.dismountRidingEntity();
            EntityWoodBoat plain = new EntityWoodBoat(world, 4703.5, 120, 4700.5, BoatWood.get("minecraft:oak"));
            world.spawnEntity(plain);
            player.startRiding(plain, true);
            check("a boat without a chest opens nothing", !BoatNetwork.open(player) && player.openContainer instanceof ContainerPlayer,
                    player.openContainer);
            player.dismountRidingEntity();
            plain.setDead();
            chest.setDead();
            world.removeEntity(player);
        });

        then(1, "How Did We Get Here?: vanilla's lists and the backports' effects", world -> {
            check("1.12.2 asks for 20", AllEffects.listAt(GameVersion.BASE).size() == 20, AllEffects.listAt(GameVersion.BASE).size());
            check("1.21.11 asks for 34 (the wiki's list)", AllEffects.listAt(GameVersion.parse("1.21.11")).size() == 34,
                    AllEffects.listAt(GameVersion.parse("1.21.11")));
            check("26.4 adds Freezing: 35", AllEffects.listAt(GameVersion.parse("26.4")).size() == 35
                    && AllEffects.listAt(GameVersion.parse("26.4")).contains("freezing"), "");
            check("1.14 adds Blindness, Bad Omen, Hero of the Village; Saturation never",
                    AllEffects.listAt(GameVersion.parse("1.14")).containsAll(Arrays.asList("blindness", "bad_omen", "hero_of_the_village"))
                            && !AllEffects.listAt(GameVersion.parse("26.4")).contains("saturation"), "");
            check("since: Darkness 1.19, Conduit Power 1.13, Speed 1.12.2's", "1.19".equals(AllEffects.since("darkness"))
                    && "1.13".equals(AllEffects.since("conduit_power")) && AllEffects.since("speed") == null, "");
            Map<String, Potion> none = AllEffects.required(GameVersion.parse("1.21.11"));
            check("no backport's effects: 1.12.2's 20 and vanilla's Blindness", none.size() == 21 && none.containsKey("minecraft:blindness"),
                    none.keySet());
            check("at 1.12.2 Blindness is not asked", !AllEffects.required(GameVersion.BASE).containsKey("minecraft:blindness"), "");

            // A backport's Darkness (vanilla's Saturation stands in: a registered effect 1.12.2's list lacks).
            AllEffects.register("minecraft:darkness", () -> MobEffects.SATURATION);
            AllEffects.register("not_an_effect", () -> MobEffects.LUCK);
            AllEffects.register("wind_charged", () -> null);
            Map<String, Potion> with = AllEffects.required(GameVersion.parse("1.21"));
            check("a registered effect is asked for from its version", with.get("minecraft:darkness") == MobEffects.SATURATION
                    && with.size() == 22, with.keySet());
            check("not before it", !AllEffects.required(GameVersion.parse("1.18")).containsKey("minecraft:darkness"), "");
            check("a name that is not one of vanilla's later effects is ignored",
                    !AllEffects.required(GameVersion.parse("26.4")).containsValue(MobEffects.LUCK), "");
            check("an effect not in this game is never asked", !with.containsKey("minecraft:wind_charged"), "");

            Advancement advancement = world.getMinecraftServer().getAdvancementManager().getAdvancement(AllEffects.ID);
            check("vanilla's advancement is loaded", advancement != null, "");
            if (advancement == null) {
                return;
            }
            check("the advancement takes the list", AllEffects.change(world.getMinecraftServer(), advancement, with.values())
                    && advancement.getCriteria().size() == 1 && advancement.getCriteria().containsKey(AllEffects.CRITERION)
                    && advancement.getRequirements().length == 1, advancement.getCriteria().keySet());
            Map<Potion, PotionEffect> on = new HashMap<Potion, PotionEffect>();
            for (Potion p : with.values()) {
                on.put(p, new PotionEffect(p, 200));
            }
            check("all 22 at once: done", AllEffects.lastPredicate != null && AllEffects.lastPredicate.test(on), on.size());
            on.remove(MobEffects.SATURATION);
            check("without the backport's: not done", AllEffects.lastPredicate != null && !AllEffects.lastPredicate.test(on), on.size());
            on.put(MobEffects.SATURATION, new PotionEffect(MobEffects.SATURATION, 200));
            on.put(MobEffects.LUCK, new PotionEffect(MobEffects.LUCK, 200));
            check("other effects as well: still done", AllEffects.lastPredicate != null && AllEffects.lastPredicate.test(on), "");
            AllEffects.unregister("darkness");
            AllEffects.unregister("not_an_effect");
            AllEffects.unregister("wind_charged");
            check("put back to 1.12.2's 20", AllEffects.change(world.getMinecraftServer(), advancement,
                    AllEffects.required(GameVersion.BASE).values()) && AllEffects.required(GameVersion.BASE).size() == 20, "");
        });
    }
}
