package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;

import java.util.*;

/** Vanilla + WGE worldgen registries. No MinecraftServer, levels, ticking or chunk storage. */
public final class PreviewWorld {
    private static RegistryAccess.Frozen registries;
    private static String resourceFingerprint;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;
    private final BiomeManager biomes;
    private final LevelHeightAccessor height;
    private final GenerationContext context;
    private final Map<Long, Integer> heightCache = new LinkedHashMap<>(4096, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> entry) { return size() > 131072; }
    };

    private final Map<Long,Holder<Biome>> noiseBiomeCache=new LinkedHashMap<>(4096,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<Long,Holder<Biome>> entry){return size()>65536;}
    };

    public static synchronized RegistryAccess.Frozen bootstrap() {
        if (registries != null) return registries;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The standalone vanilla runtime has already frozen built-ins. Only reopen the two
        // codec registries during startup; no worldgen tasks exist yet. NeoForge uses its normal register event.
        try {
            var frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
            frozen.setBoolean(BuiltInRegistries.BIOME_SOURCE, false);
            frozen.setBoolean(BuiltInRegistries.DENSITY_FUNCTION_TYPE, false);
            IslandBiomeSource.register(); IslandContinentsFunction.register(); IslandDensityFunction.register();
            BuiltInRegistries.BIOME_SOURCE.freeze(); BuiltInRegistries.DENSITY_FUNCTION_TYPE.freeze();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported Minecraft runtime; expected 1.21.1", failure);
        }
        var pack = new VanillaPackResourcesBuilder().pushJarResources()
                .pushClasspathResources(PackType.SERVER_DATA, PreviewWorld.class)
                .exposeNamespace("minecraft", "worldgen_editor", "c", "neoforge", "forge")
                .build(new PackLocationInfo("wge-preview", Component.literal("WGE preview"), PackSource.BUILT_IN, Optional.empty()));
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
            registries = RegistryDataLoader.load(resources, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                    RegistryDataLoader.WORLDGEN_REGISTRIES);
            Registry<Biome> biomeRegistry = registries.registryOrThrow(Registries.BIOME);
            var loader = new net.minecraft.tags.TagLoader<Holder<Biome>>(biomeRegistry::getHolder, Registries.tagsDirPath(Registries.BIOME));
            var tags = new HashMap<net.minecraft.tags.TagKey<Biome>, List<Holder<Biome>>>();
            loader.loadAndBuild(resources).forEach((id, values) -> tags.put(net.minecraft.tags.TagKey.create(Registries.BIOME,id),List.copyOf(values)));
            biomeRegistry.bindTags(tags);
            try {
                var digest=java.security.MessageDigest.getInstance("SHA-256");
                var files=new TreeMap<ResourceLocation,Resource>();
                files.putAll(resources.listResources("worldgen",id->id.getPath().endsWith(".json")));
                files.putAll(resources.listResources("tags/worldgen/biome",id->id.getPath().endsWith(".json")));
                for(var entry:files.entrySet()) {
                    digest.update(entry.getKey().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    try(var input=entry.getValue().open()){digest.update(input.readAllBytes());}
                }
                resourceFingerprint="minecraft-1.21.1-wge-2-"+HexFormat.of().formatHex(digest.digest());
            } catch(Exception error){throw new IllegalStateException("Could not fingerprint worldgen resources",error);}
        }
        return registries;
    }

    public PreviewWorld(JsonElement raw, long seed, int version) throws IslandConfigException {
        RegistryAccess.Frozen access = bootstrap();
        JsonObject input = raw.getAsJsonObject().deepCopy();
        if (!input.has("enabled")) input.addProperty("enabled", true);
        IslandConfig config = IslandConfigLoader.parse(input);
        context = new GenerationContext(seed, config, version, access.registryOrThrow(Registries.BIOME));
        GenerationBinding binding = new GenerationBinding(); binding.set(context);
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        JsonObject biomeJson = new JsonObject(); biomeJson.addProperty("type", "worldgen_editor:island_biome_source");
        biomeJson.addProperty("generation_version", version);
        biomeJson.add("delegate", JsonParser.parseString("{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:overworld\"}"));
        IslandBiomeSource source = (IslandBiomeSource) BiomeSource.CODEC.parse(ops, biomeJson).getOrThrow();
        source.bind(binding);
        NoiseGeneratorSettings original = access.registryOrThrow(Registries.NOISE_SETTINGS)
                .get(ResourceLocation.fromNamespaceAndPath("worldgen_editor", "island_overworld"));
        NoiseRouter router = original.noiseRouter().mapAll(function -> {
            if (function instanceof IslandContinentsFunction island) return island.withBinding(binding);
            if (function instanceof IslandDensityFunction island) return island.withBinding(binding);
            return function;
        });
        NoiseGeneratorSettings settings = new NoiseGeneratorSettings(original.noiseSettings(), original.defaultBlock(), original.defaultFluid(),
                router, original.surfaceRule(), original.spawnTarget(), original.seaLevel(), original.disableMobGeneration(),
                original.isAquifersEnabled(), original.oreVeinsEnabled(), original.useLegacyRandomSource());
        generator = new NoiseBasedChunkGenerator(source, Holder.direct(settings));
        random = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), seed);
        biomes = new BiomeManager((x,y,z) -> {
            long key=BlockPos.asLong(x,y,z);
            synchronized(noiseBiomeCache){var cached=noiseBiomeCache.get(key);if(cached!=null)return cached;}
            var result=source.getNoiseBiome(x,y,z,random.sampler());
            synchronized(noiseBiomeCache){noiseBiomeCache.put(key,result);}return result;
        }, BiomeManager.obfuscateSeed(seed));
        height = LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());
    }

    public static String resourceFingerprint() { bootstrap(); return resourceFingerprint; }

    public int surfaceY(int x,int z) { return surfaceY(x,z,1); }
    public int surfaceY(int x,int z,int sampleStep) {
        long key=((long)x<<32)^(z&0xffffffffL);
        synchronized(heightCache){Integer cached=heightCache.get(key);if(cached!=null)return cached;}
        var settings=generator.generatorSettings().value();int width=settings.noiseSettings().getCellWidth();
        if(sampleStep>=width){
            int y=SurfaceSampler.height(random,x,z,settings);
            if(y==Integer.MIN_VALUE)y=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,height,random)-1;
            synchronized(heightCache){heightCache.put(key,y);}return y;
        }
        int cellX=Math.floorDiv(x,width)*width,cellZ=Math.floorDiv(z,width)*width;
        int[] heights=SurfaceSampler.heights(random,cellX,cellZ,settings);
        synchronized(heightCache){
            for(int dz=0;dz<width;dz++)for(int dx=0;dx<width;dx++)heightCache.put(((long)(cellX+dx)<<32)^((cellZ+dz)&0xffffffffL),heights[dz*width+dx]);
        }
        return heights[(z-cellZ)*width+x-cellX];
    }
    public Holder<Biome> biome(int x, int y, int z) { return biomes.getBiome(new BlockPos(x,y,z)); }
    public String biomeId(int x,int y,int z) { return biome(x,y,z).unwrapKey().orElseThrow().location().toString(); }
    public GenerationContext context() { return context; }
    public NoiseBasedChunkGenerator generator() { return generator; }
    public RandomState random() { return random; }
}
