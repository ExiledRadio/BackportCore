package com.exiledradio.backportcore.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Backport Core's own small coremod: the bytecode patches the family's vanilla parity needs where 1.12.2
 * has no hook. One transformer per patch, each documented in {@code docs/PATCHES.md}. No MixinBooter.
 *
 * <p>Every patch only adds a call into the library's hook classes ({@code pose.MovementHooks},
 * {@code pose.client.ClientHooks}, {@code presentation.client.PresentationHooks}, {@code worldgen.WorldgenHooks}), which do exactly what vanilla 1.12.2 did until a backport turns the
 * matching rules on. A transformer that does not find the exact shape it expects changes nothing and logs
 * a warning; it never half-patches a method.
 *
 * <p>Named in the jar's manifest ({@code FMLCorePlugin}, with {@code FMLCorePluginContainsFMLMod}); in the
 * dev environment by {@code -Dfml.coreMods.load} for this repository's own runs, and found on the
 * classpath through the dev jar's manifest in the backports' runs. Sorted after Fluidlogged API's
 * transformers (its index is 1401), so these see the methods as Fluidlogged API leaves them; none of the
 * patched call sites is one Fluidlogged API changes.
 */
@IFMLLoadingPlugin.Name("Backport Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.exiledradio.backportcore.asm.")
@IFMLLoadingPlugin.SortingIndex(1500)
public final class BackportCorePlugin implements IFMLLoadingPlugin {

    /** Every transformer, in the order FML runs them. */
    static final String[] TRANSFORMERS = {
            WaterTravelTransformer.class.getName(),
            PlayerSprintTransformer.class.getName(),
            BipedAnglesTransformer.class.getName(),
            PlayerRotationsTransformer.class.getName(),
            CameraHeightTransformer.class.getName(),
            CameraTerrainTransformer.class.getName(),
            TitlePanoramaTransformer.class.getName(),
            DebugKeysTransformer.class.getName(),
            BiomeSearchTransformer.class.getName(),
            VillageBiomeTransformer.class.getName(),
            LayerRulesTransformer.class.getName(),
            ScreenPatchesTransformer.class.getName(),
            ChunkRebuildsTransformer.class.getName(),
            WaterloggableTransformer.class.getName(),
            ExtendedFluidStatesTransformer.class.getName(),
            QuarkCandlesTransformer.class.getName(),
            MenuFramerateTransformer.class.getName(),
            SlotHighlightTransformer.class.getName(),
            SunriseTransformer.class.getName(),
            CloudTimeTransformer.class.getName(),
            CloudDistanceTransformer.class.getName(),
            WeatherRadiusTransformer.class.getName(),
            ChunkFadeTransformer.class.getName(),
            TitleLogoTransformer.class.getName(),
            DebugBindsTransformer.class.getName(),
            MenuBackgroundTransformer.class.getName(),
            EyeHeightTransformer.class.getName(),
            MapIconTransformer.class.getName(),
            PaneTorchTransformer.class.getName(),
            SpawnListSwitchTransformer.class.getName(),
            GroundSpawnTransformer.class.getName(),
            TickTransformer.class.getName(),
            JukeboxSignalTransformer.class.getName(),
            FullscreenTransformer.class.getName(),
            BiomeColorTransformer.class.getName(),
            MapFacingTransformer.class.getName(),
            ModListLogoTransformer.class.getName(),
            SimulationTransformer.class.getName(),
    };

    @Override
    public String[] getASMTransformerClass() {
        return TRANSFORMERS.clone();
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
