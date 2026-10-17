package org.wodichka.worldgen_editor.verification;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.*;
import net.minecraft.gametest.framework.GameTestHelper;
import org.wodichka.worldgen_editor.world.*;
import terrablender.api.*;
import terrablender.worldgen.IExtendedParameterList;
import java.nio.file.*;
import java.util.function.Consumer;

public final class TerraBlenderVerification {
    private static final ResourceKey<Biome> BIOME=ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("worldgen_editor","verification_biome"));
    public static void register() {
        Regions.register(new Region(ResourceLocation.fromNamespaceAndPath("worldgen_editor","verification"),RegionType.OVERWORLD,100000) {
            @Override public void addBiomes(Registry<Biome> registry,Consumer<Pair<Climate.ParameterPoint,ResourceKey<Biome>>> mapper) {
                var range=Climate.Parameter.span(-2.0F,2.0F);
                mapper.accept(Pair.of(Climate.parameters(range,range,range,range,range,range,0.0F),BIOME));
            }
        });
    }
    public static void verify(GameTestHelper helper)throws Exception {
        var source=(IslandBiomeSource)helper.getLevel().getChunkSource().getGenerator().getBiomeSource();
        var delegate=(MultiNoiseBiomeSource)source.delegate();
        helper.assertTrue(((IExtendedParameterList<?>)delegate.getClass().getMethod("parameters").invoke(delegate)).isInitialized(),"TerraBlender delegate was not initialized");
        helper.assertTrue(source.possibleBiomes().stream().anyMatch(b->b.is(BIOME)),"Deferred TerraBlender biome missing from possible biomes");
        var context=IslandWorldState.context();var sampler=helper.getLevel().getChunkSource().randomState().sampler();
        boolean found=false;
        for(int x=-128;x<=128;x+=16)for(int z=-128;z<=128;z+=16) {
            if(source.getNoiseBiome(x/4,75/4,z/4,sampler).is(BIOME))found=true;
        }
        helper.assertTrue(found,"Registered TerraBlender biome never reached WGE land");
        Files.writeString(Path.of("terrablender-result.json"),"{\"version\":\"4.1.0.8\",\"delegate_initialized\":true,\"custom_biome_generated\":true}");
        helper.succeed();
    }
}
