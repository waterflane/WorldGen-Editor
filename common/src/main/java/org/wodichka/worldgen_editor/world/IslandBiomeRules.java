package org.wodichka.worldgen_editor.world;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.config.IslandConfig;
import org.wodichka.worldgen_editor.config.IslandTemperature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class IslandBiomeRules {
    private final Holder<Biome> oceanBiome;
    private final Holder<Biome> deepOceanBiome;
    private Holder<Biome> outerOceanBiome;
    private final Holder<Biome> frozenOceanBiome;
    private final Holder<Biome> deepFrozenOceanBiome;
    private final Holder<Biome> coldOceanBiome;
    private final Holder<Biome> deepColdOceanBiome;
    private final Holder<Biome> lukewarmOceanBiome;
    private final Holder<Biome> deepLukewarmOceanBiome;
    private final Holder<Biome> warmOceanBiome;
    private final Holder<Biome> snowyBeachBiome;
    private final Holder<Biome> beachBiome;
    private final Holder<Biome> plainsBiome;
    private final Holder<Biome> forestBiome;
    private final Holder<Biome> meadowBiome;
    private final Holder<Biome> snowyPlainsBiome;
    private final Holder<Biome> taigaBiome;
    private final Holder<Biome> savannaBiome;
    private final Holder<Biome> jungleBiome;
    private final Holder<Biome> desertBiome;
    private final Holder<Biome> riverBiome;
    private final Holder<Biome> frozenRiverBiome;
    private final Set<String> EXCLUSION_WARNINGS = ConcurrentHashMap.newKeySet();
    private final List<TagKey<Biome>> OCEAN_TAGS = List.of(tag("minecraft:is_ocean"), tag("c:is_ocean"), tag("forge:is_ocean"));
    private final List<TagKey<Biome>> COLD_TAGS = List.of(tag("c:is_cold"), tag("forge:is_cold"), tag("c:cold"), tag("forge:cold"));
    private final List<TagKey<Biome>> HOT_TAGS = List.of(tag("c:is_hot"), tag("forge:is_hot"), tag("c:is_warm"), tag("forge:is_warm"), tag("c:hot"), tag("forge:hot"));
    private final List<TagKey<Biome>> SNOWY_TAGS = List.of(tag("c:is_snowy"), tag("forge:is_snowy"), tag("c:snowy"), tag("forge:snowy"));
    private final List<TagKey<Biome>> TEMPERATE_TAGS = List.of(tag("c:is_temperate"), tag("forge:is_temperate"), tag("c:temperate"), tag("forge:temperate"));

    private BiomeClimateCatalog catalog;
    private net.minecraft.world.level.biome.Climate.ParameterList<Holder<Biome>> nativeClimate;
    private record Choice(IslandTemperature temperature,List<String> excluded){}
    private final java.util.Map<Choice,java.util.Optional<net.minecraft.world.level.biome.Climate.ParameterList<Holder<Biome>>>> nativeChoices=java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(8,.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<Choice,java.util.Optional<net.minecraft.world.level.biome.Climate.ParameterList<Holder<Biome>>>> entry){return size()>8;}});
    private java.util.Set<Holder<Biome>> delegateCandidates;
    private final java.util.Map<IslandTemperature,List<Holder<Biome>>> climateCandidates=new java.util.concurrent.ConcurrentHashMap<>();
    private final Registry<Biome> biomeRegistry;
    private final IslandConfig config;
    private final long worldSeed;
    private final int generationVersion;
    private static final Logger LOGGER = LoggerFactory.getLogger(Worldgen_editor.MOD_ID);

    public IslandBiomeRules(Registry<Biome> biomes, IslandConfig config, long seed, int version) {
        this.biomeRegistry = biomes;
        this.catalog=new BiomeClimateCatalog(biomes);
        if(version>=5){
            java.util.stream.Stream.concat(java.util.stream.Stream.of(config.temperature()),config.entries().stream().map(e->e.temperature()))
                .filter(IslandTemperature::special).distinct().forEach(choice->{
                    if(catalog.candidates(choice).isEmpty())throw new IllegalArgumentException("No surface biomes available for temperature "+choice.id());
                });
        }
        this.config = config;
        this.worldSeed = seed;
        this.generationVersion = version;
        oceanBiome = biomes.getHolderOrThrow(Biomes.OCEAN);
        deepOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_OCEAN);
        frozenOceanBiome = biomes.getHolderOrThrow(Biomes.FROZEN_OCEAN);
        deepFrozenOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_FROZEN_OCEAN);
        coldOceanBiome = biomes.getHolderOrThrow(Biomes.COLD_OCEAN);
        deepColdOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_COLD_OCEAN);
        lukewarmOceanBiome = biomes.getHolderOrThrow(Biomes.LUKEWARM_OCEAN);
        deepLukewarmOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_LUKEWARM_OCEAN);
        warmOceanBiome = biomes.getHolderOrThrow(Biomes.WARM_OCEAN);
        beachBiome = biomes.getHolderOrThrow(Biomes.BEACH);
        snowyBeachBiome = biomes.getHolderOrThrow(Biomes.SNOWY_BEACH);
        plainsBiome = biomes.getHolderOrThrow(Biomes.PLAINS);
        forestBiome = biomes.getHolderOrThrow(Biomes.FOREST);
        meadowBiome = biomes.getHolderOrThrow(Biomes.MEADOW);
        snowyPlainsBiome = biomes.getHolderOrThrow(Biomes.SNOWY_PLAINS);
        taigaBiome = biomes.getHolderOrThrow(Biomes.TAIGA);
        savannaBiome = biomes.getHolderOrThrow(Biomes.SAVANNA);
        jungleBiome = biomes.getHolderOrThrow(Biomes.JUNGLE);
        desertBiome = biomes.getHolderOrThrow(Biomes.DESERT);
        riverBiome = biomes.getHolderOrThrow(Biomes.RIVER);
        frozenRiverBiome = biomes.getHolderOrThrow(Biomes.FROZEN_RIVER);
        refreshOuterOceanBiome();
    }

    public Holder<Biome> oceanBiome(double islandMask) {
        return oceanBiome(islandMask, null, null, 0, 0);
    }

    public Holder<Biome> oceanBiome(double islandMask, IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        boolean deep = islandMask < IslandTerrainHooks.DEEP_OCEAN_MASK;
        // Ocean climate is explicit: a cold delegate can sit beside temperate painted land.
        ClimateBand climate = config.oceanTemperature() == null
                ? configuredClimateBand(source) : climateBand(config.oceanTemperature());
        if (climate == null) climate = ClimateBand.TEMPERATE;
        List<Holder<Biome>> candidates = switch (climate) {
            case COLD -> nonNullBiomes(frozenOceanBiome, deepFrozenOceanBiome, coldOceanBiome, deepColdOceanBiome);
            case WARM -> nonNullBiomes(warmOceanBiome, lukewarmOceanBiome, deepLukewarmOceanBiome);
            case TEMPERATE -> deep
                    ? nonNullBiomes(deepOceanBiome, oceanBiome)
                    : nonNullBiomes(oceanBiome, deepOceanBiome);
        };
        Holder<Biome> fallback = switch (climate) {
            case COLD -> firstNonNull(frozenOceanBiome, coldOceanBiome, oceanBiome);
            case WARM -> firstNonNull(warmOceanBiome, lukewarmOceanBiome, oceanBiome, deepOceanBiome);
            case TEMPERATE -> oceanBiome;
        };
        if (climate == ClimateBand.COLD || climate == ClimateBand.WARM) {
            Holder<Biome> picked = pickAllowed(candidates, source, blockX, blockZ);
            if (picked != null) {
                return picked;
            }
            warnAllExcluded(source);
            return firstNonNull(fallback, oceanBiome, deepOceanBiome);
        }
        return firstAllowed(candidates, source, fallback);
    }

    /** Explicit metric coast bands are independent of the delegate's ocean temperature. */
    public Holder<Biome> customCoastBiome(CoastField.Sample coast,Holder<Biome> delegate,int x,int z) {
        return customCoastBiome(coast,delegate,x,z,null);
    }
    public Holder<Biome> customCoastBiome(CoastField.Sample coast,Holder<Biome> delegate,int x,int z,IslandMask.SourceInfo climateSource) {
        double distance=coast.distance();var source=climateSource!=null?climateSource:coast.source();
        if(distance<0) {
            if(-distance>coast.profile().shallowOceanWidthBlocks())return outerOceanBiome();
            ClimateBand climate=config.oceanTemperature()==null?configuredClimateBand(source):climateBand(config.oceanTemperature());
            if(climate==null)climate=ClimateBand.TEMPERATE;
            var candidates=switch(climate){
                case COLD -> nonNullBiomes(frozenOceanBiome,coldOceanBiome);
                case WARM -> nonNullBiomes(warmOceanBiome,lukewarmOceanBiome);
                case TEMPERATE -> nonNullBiomes(oceanBiome);
            };
            var result=pickAllowed(candidates,source,x,z);
            if(result!=null)return result;
            warnAllExcluded(source);return oceanBiome;
        }
        if(isUnderground(delegate)||isRiver(delegate))return null;
        if(distance<=coast.profile().beachWidthBlocks()&&coast.profile().beachWidthBlocks()>0) {
            var beach=configuredClimateBand(source)==ClimateBand.COLD?snowyBeachBiome:beachBiome;
            if(!isExcluded(beach,source))return beach;
        }
        return null;
    }

    /** Uses pre-cave depth, never a chunk heightmap or recursively generated surface. */
    public Holder<Biome> naturalCoastBiome(CoastField.Sample coast,Holder<Biome> delegate,int x,int z,
                                          IslandMask.SourceInfo climateSource,double surface,int seaLevel) {
        if(isUnderground(delegate)||isRiver(delegate))return null;
        double d=coast.distance();var source=climateSource!=null?climateSource:coast.source();
        // A natural coast can deviate slightly from the drawn boundary. Keep narrow water bodies open.
        if((d<0&&surface<seaLevel+2)||(d>=0&&d<16&&surface<seaLevel-2)){
            var water=new CoastField.Sample(-Math.max(1e-9,Math.abs(d)),coast.source(),coast.variation());
            return customCoastBiome(water,delegate,x,z,source);
        }
        double width=coast.profile().beachWidthBlocks()*(1+.15*coast.variation());
        if(width>0&&d>=-8&&d<=width&&surface>=seaLevel-2&&surface<=seaLevel+12){
            var beach=configuredClimateBand(source)==ClimateBand.COLD?snowyBeachBiome:beachBiome;
            if(!isExcluded(beach,source))return beach;
        }
        // Do not let the original continentalness beach create a second inland sand ring.
        if(generationVersion>=5&&(isOceanBiome(delegate)||isBeach(delegate)))return modernLandBiome(source,delegate,x,z);
        if(isOceanBiome(delegate)||isBeach(delegate)){
            ClimateBand climate=configuredClimateBand(source);
            var candidates=climate==ClimateBand.COLD?nonNullBiomes(snowyPlainsBiome,taigaBiome)
                    :climate==ClimateBand.WARM?nonNullBiomes(savannaBiome,jungleBiome,desertBiome)
                    :nonNullBiomes(plainsBiome,forestBiome,meadowBiome);
            return firstNonNull(pickAllowed(candidates,source,x,z),plainsBiome);
        }
        return null;
    }

    public boolean isBeach(Holder<Biome> biome){return biome!=null&&(biome.is(Biomes.BEACH)||biome.is(Biomes.SNOWY_BEACH));}

    public Holder<Biome> outerOceanBiome() {
        return firstNonNull(outerOceanBiome, deepOceanBiome, oceanBiome);
    }

    public List<Holder<Biome>> oceanBiomes() {
        return nonNullBiomes(
                oceanBiome,
                deepOceanBiome,
                outerOceanBiome,
                frozenOceanBiome,
                deepFrozenOceanBiome,
                coldOceanBiome,
                deepColdOceanBiome,
                lukewarmOceanBiome,
                deepLukewarmOceanBiome,
                warmOceanBiome
        );
    }

    public List<Holder<Biome>> fallbackLandBiomes() {
        if(generationVersion>=5)return java.util.stream.Stream.concat(catalog.surface().stream(),nonNullBiomes(beachBiome,snowyBeachBiome,riverBiome,frozenRiverBiome).stream()).toList();
        return nonNullBiomes(
                plainsBiome,
                forestBiome,
                meadowBiome,
                beachBiome, snowyBeachBiome,
                snowyPlainsBiome,
                taigaBiome,
                savannaBiome,
                jungleBiome,
                desertBiome, riverBiome, frozenRiverBiome
        );
    }

    public Holder<Biome> beachBiome() {
        return beachBiome;
    }

    public Holder<Biome> landBiome(double islandMask, int blockX, int blockZ) {
        Holder<Biome> plains = plainsBiome;
        Holder<Biome> forest = forestBiome;
        Holder<Biome> meadow = meadowBiome;
        if (plains == null || forest == null || meadow == null) {
            return null;
        }

        double inner = smoothstep(IslandTerrainHooks.LAND_MASK, 0.96D, islandMask);
        long mixed = mix(worldSeed, blockX * 341873128712L);
        mixed = mix(mixed, blockZ * 132897987541L);
        double choice = ((mixed >>> 11) * 0x1.0p-53D);

        if (inner > 0.70D && choice < 0.28D) {
            return forest;
        }
        if (inner > 0.56D && choice > 0.78D) {
            return meadow;
        }
        return plains;
    }

    public Holder<Biome> landBiome(IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        return landBiome(1.0D, source, delegate, blockX, blockZ);
    }

    public Holder<Biome> landBiome(double islandMask,IslandMask.SourceInfo source,Holder<Biome> delegate,int blockX,int blockZ){return landBiome(islandMask,source,delegate,blockX,blockZ,null);}
    public Holder<Biome> landBiome(double islandMask, IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ,net.minecraft.world.level.biome.Climate.TargetPoint target) {
        if (source == null && generationVersion<5) {
            Holder<Biome> coast = replaceOceanDelegateOnLand(islandMask, delegate);
            if (coast != null) {
                return coast;
            }
            return delegate;
        }

        ClimateBand configured = configuredClimateBand(source);
        if (generationVersion >= 2 && isRiver(delegate) && !isExcluded(delegate, source)) {
            Holder<Biome> river = delegate;
            if (delegate.is(Biomes.RIVER) || delegate.is(Biomes.FROZEN_RIVER)) {
                river = configured == null ? delegate : configured == ClimateBand.COLD ? frozenRiverBiome : riverBiome;
            }
            if (!isExcluded(river, source)) return river;
        }
        if (generationVersion >= 2 && isUnderground(delegate) && !isExcluded(delegate, source)) {
            return delegate;
        }
        if(generationVersion>=5)return modernLandBiome(source,delegate,blockX,blockZ,target);
        Holder<Biome> coast = configured == null || generationVersion < 2
                ? replaceOceanDelegateOnLand(islandMask, delegate) : null;
        if (coast != null && !isExcluded(coast, source)) {
            return coast;
        }

        if (configured == null) {
            if (!isExcluded(delegate, source)) {
                return delegate;
            }
            configured = climateBand(null, delegate, blockX, blockZ);
        } else if (delegate != null && !isExcluded(delegate, source) && !isOceanBiome(delegate) && matchesLandClimate(delegate, configured)) {
            return delegate;
        }

        List<Holder<Biome>> candidates = switch (configured) {
            case COLD -> nonNullBiomes(snowyPlainsBiome, taigaBiome);
            case WARM -> nonNullBiomes(savannaBiome, jungleBiome, desertBiome);
            case TEMPERATE -> nonNullBiomes(plainsBiome, forestBiome, meadowBiome);
        };
        Holder<Biome> fallback = pickAllowed(candidates, source, blockX, blockZ);
        if (fallback != null) {
            return fallback;
        }

        warnAllExcluded(source);
        return switch (configured) {
            case COLD -> firstNonNull(snowyPlainsBiome, taigaBiome, plainsBiome, delegate);
            case WARM -> firstNonNull(savannaBiome, jungleBiome, desertBiome, plainsBiome, delegate);
            case TEMPERATE -> firstNonNull(plainsBiome, forestBiome, meadowBiome, delegate);
        };
    }

    @SuppressWarnings("unchecked")
    public void bindDelegate(net.minecraft.world.level.biome.BiomeSource source){
        var candidates=source.possibleBiomes();
        if(generationVersion<5)return;
        delegateCandidates=java.util.Set.copyOf(candidates);catalog=new BiomeClimateCatalog(biomeRegistry,delegateCandidates);climateCandidates.clear();nativeChoices.clear();
        if(source instanceof net.minecraft.world.level.biome.MultiNoiseBiomeSource)try{
            var method=net.minecraft.world.level.biome.MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");method.setAccessible(true);
            nativeClimate=(net.minecraft.world.level.biome.Climate.ParameterList<Holder<Biome>>)method.invoke(source);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported Minecraft climate parameter source",error);}
        validateModernSelection(null);
        for(var e:config.entries())if(e.type()!=org.wodichka.worldgen_editor.config.IslandEntryType.OCEAN)
            validateModernSelection(new IslandMask.SourceInfo(e.name(),e.type(),null,0L,e.excludedBiomes(),e.temperature(),e.biomePatchSize()));
    }
    private IslandTemperature landSelection(IslandMask.SourceInfo source){
        var choice=source==null?IslandTemperature.STANDARD:source.temperature();
        return choice==IslandTemperature.STANDARD?config.temperature():choice;
    }
    private List<Holder<Biome>> allowedModern(IslandMask.SourceInfo source){
        var choice=landSelection(source);return climateCandidates.computeIfAbsent(choice,c->catalog.candidates(c).stream().filter(b->delegateCandidates==null||delegateCandidates.contains(b)).toList()).stream().filter(b->!isExcluded(b,source)).toList();
    }
    private void validateModernSelection(IslandMask.SourceInfo source){
        if(allowedModern(source).isEmpty())throw new IllegalArgumentException("No allowed surface biomes for temperature "+landSelection(source).id());
    }
    private Holder<Biome> modernLandBiome(IslandMask.SourceInfo source,Holder<Biome> delegate,int x,int z){return modernLandBiome(source,delegate,x,z,null);}
    private Holder<Biome> modernLandBiome(IslandMask.SourceInfo source,Holder<Biome> delegate,int x,int z,net.minecraft.world.level.biome.Climate.TargetPoint target){
        var choice=landSelection(source);
        if(delegate!=null&&!isExcluded(delegate,source)&&catalog.matches(delegate,choice))return delegate;
        var allowed=allowedModern(source);
        if(allowed.isEmpty())throw new IllegalStateException("No allowed surface biome for "+choice.id());
        if(target!=null&&nativeClimate!=null){
            var key=new Choice(choice,source==null?List.of():source.excludedBiomes());
            var filtered=nativeChoices.computeIfAbsent(key,k->{
                var present=new java.util.HashSet<Holder<Biome>>();var points=nativeClimate.values().stream().filter(p->allowed.contains(p.getSecond())).peek(p->present.add(p.getSecond())).toList();
                return points.isEmpty()||!present.containsAll(allowed)?java.util.Optional.empty():java.util.Optional.of(new net.minecraft.world.level.biome.Climate.ParameterList<>(points));
            });if(filtered.isPresent())return filtered.get().findValue(target);
        }
        // Preserve the delegate's climate affinity; stable IDs break ties reproducibly.
        if(delegate!=null&&!isOceanBiome(delegate)){
            double best=Double.POSITIVE_INFINITY;Holder<Biome> result=null;
            for(var b:allowed){double dt=b.value().getBaseTemperature()-delegate.value().getBaseTemperature();
                double score=dt*dt+(b.value().hasPrecipitation()==delegate.value().hasPrecipitation()?0:.25);
                if(score<best){best=score;result=b;}}
            if(result!=null)return result;
        }
        return pickAllowed(allowed,source,x,z);
    }

    private Holder<Biome> replaceOceanDelegateOnLand(double islandMask, Holder<Biome> delegate) {
        if (!isOceanBiome(delegate)) {
            return null;
        }
        if (islandMask < IslandTerrainHooks.LAND_MASK) {
            return firstNonNull(beachBiome, plainsBiome, forestBiome, meadowBiome);
        }
        return firstNonNull(plainsBiome, forestBiome, meadowBiome);
    }

    public boolean isOceanBiome(Holder<Biome> biome) {
        return biome != null && (hasAnyTag(biome, OCEAN_TAGS)
                || biome.is(Biomes.OCEAN)
                || biome.is(Biomes.DEEP_OCEAN)
                || biome.is(Biomes.COLD_OCEAN)
                || biome.is(Biomes.DEEP_COLD_OCEAN)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.is(Biomes.WARM_OCEAN));
    }

    private Holder<Biome> pickAllowed(List<Holder<Biome>> candidates, IslandMask.SourceInfo source, int blockX, int blockZ) {
        List<Holder<Biome>> allowed = new ArrayList<>();
        for (Holder<Biome> candidate : candidates) {
            if (candidate != null && !isExcluded(candidate, source)) {
                allowed.add(candidate);
            }
        }
        if (allowed.isEmpty()) {
            return null;
        }

        int patchSize = biomePatchSize(source);
        long seed = source == null ? worldSeed : source.climateSeed();
        int cellX = Math.floorDiv(blockX, patchSize);
        int cellZ = Math.floorDiv(blockZ, patchSize);
        double localX = (double) Math.floorMod(blockX, patchSize) / patchSize;
        double localZ = (double) Math.floorMod(blockZ, patchSize) / patchSize;
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestIndex = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int candidateCellX = cellX + dx;
                int candidateCellZ = cellZ + dz;
                long mixed = mix(seed, candidateCellX * 341873128712L);
                mixed = mix(mixed, candidateCellZ * 132897987541L);
                double jitterX = randomUnit(mixed, 1L);
                double jitterZ = randomUnit(mixed, 2L);
                double siteX = dx + jitterX;
                double siteZ = dz + jitterZ;
                double distanceX = localX - siteX;
                double distanceZ = localZ - siteZ;
                double distance = distanceX * distanceX + distanceZ * distanceZ;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = Math.floorMod((int) (mix(mixed, 3L) >>> 32), allowed.size());
                }
            }
        }

        return allowed.get(bestIndex);
    }

    private Holder<Biome> firstAllowed(List<Holder<Biome>> candidates, IslandMask.SourceInfo source, Holder<Biome> fallback) {
        for (Holder<Biome> candidate : candidates) {
            if (candidate != null && !isExcluded(candidate, source)) {
                return candidate;
            }
        }
        warnAllExcluded(source);
        return firstNonNull(fallback, oceanBiome, deepOceanBiome);
    }

    private boolean isExcluded(Holder<Biome> biome, IslandMask.SourceInfo source) {
        if (biome == null || source == null || source.excludedBiomes().isEmpty()) {
            return false;
        }

        for (String selector : source.excludedBiomes()) {
            if (selector.startsWith("#")) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, ResourceLocation.parse(selector.substring(1)));
                if (biome.is(tag)) {
                    return true;
                }
            } else {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(selector));
                if (biome.is(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ClimateBand climateBand(IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        ClimateBand configured = configuredClimateBand(source);
        if (configured != null) {
            return configured;
        }

        if (delegate != null) {
            if (isColdBiome(delegate)) {
                return ClimateBand.COLD;
            }
            if (isWarmBiome(delegate)) {
                return ClimateBand.WARM;
            }
            if (isTemperateBiome(delegate)) {
                return ClimateBand.TEMPERATE;
            }
        }

        return ClimateBand.TEMPERATE;
    }

    private ClimateBand configuredClimateBand(IslandMask.SourceInfo source) {
        if(generationVersion>=5)return climateBand(landSelection(source));
        if (source == null || source.temperature() == null || source.temperature() == IslandTemperature.STANDARD) return null;
        return climateBand(generationVersion>=5?landSelection(source):source.temperature());
    }

    private ClimateBand climateBand(IslandTemperature temperature) {
        return switch (temperature) {
            case COLD -> ClimateBand.COLD;
            case TEMPERATE -> ClimateBand.TEMPERATE;
            case WARM -> ClimateBand.WARM;
            default -> null;
        };
    }

    private int biomePatchSize(IslandMask.SourceInfo source) {
        if (source == null || source.biomePatchSize() <= 0) {
            return 512;
        }
        return source.biomePatchSize();
    }

    private void refreshOuterOceanBiome() {
        Registry<Biome> registry = biomeRegistry;
        Holder<Biome> fallback = firstNonNull(deepOceanBiome, oceanBiome);
        if (registry == null) {
            outerOceanBiome = fallback;
            return;
        }

        String configured = config.outerOcean();
        Holder<Biome> resolved = null;
        try {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(configured));
            resolved = registry.getHolder(key).orElse(null);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid outer_ocean '{}'; using minecraft:deep_ocean", configured);
        }

        if (resolved != null && isOceanBiome(resolved)) {
            outerOceanBiome = resolved;
            return;
        }

        if (resolved == null) {
            LOGGER.warn("outer_ocean '{}' is not registered; using minecraft:deep_ocean", configured);
        } else {
            LOGGER.warn("outer_ocean '{}' is not an ocean-like biome; using minecraft:deep_ocean", configured);
        }
        outerOceanBiome = fallback;
    }

    private boolean matchesLandClimate(Holder<Biome> biome, ClimateBand climate) {
        return switch (climate) {
            case COLD -> isColdBiome(biome);
            case WARM -> isWarmBiome(biome) && !isColdBiome(biome);
            case TEMPERATE -> isTemperateBiome(biome) && !isColdBiome(biome) && !isWarmBiome(biome);
        };
    }

    private boolean isColdBiome(Holder<Biome> biome) {
        return biome != null && ((generationVersion >= 2 && biome.value().getBaseTemperature() < 0.2F) || hasAnyTag(biome, COLD_TAGS)
                || hasAnyTag(biome, SNOWY_TAGS)
                || biome.is(Biomes.SNOWY_PLAINS)
                || biome.is(Biomes.ICE_SPIKES)
                || biome.is(Biomes.SNOWY_TAIGA)
                || biome.is(Biomes.GROVE)
                || biome.is(Biomes.SNOWY_SLOPES)
                || biome.is(Biomes.JAGGED_PEAKS)
                || biome.is(Biomes.FROZEN_PEAKS)
                || biome.is(Biomes.FROZEN_RIVER)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.COLD_OCEAN)
                || biome.is(Biomes.DEEP_COLD_OCEAN));
    }

    private boolean isWarmBiome(Holder<Biome> biome) {
        return biome != null && ((generationVersion >= 2 && biome.value().getBaseTemperature() >= 1.0F) || hasAnyTag(biome, HOT_TAGS)
                || biome.is(Biomes.DESERT)
                || biome.is(Biomes.SAVANNA)
                || biome.is(Biomes.SAVANNA_PLATEAU)
                || biome.is(Biomes.WINDSWEPT_SAVANNA)
                || biome.is(Biomes.JUNGLE)
                || biome.is(Biomes.SPARSE_JUNGLE)
                || biome.is(Biomes.BAMBOO_JUNGLE)
                || biome.is(Biomes.BADLANDS)
                || biome.is(Biomes.ERODED_BADLANDS)
                || biome.is(Biomes.WOODED_BADLANDS)
                || biome.is(Biomes.WARM_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN));
    }

    private boolean isTemperateBiome(Holder<Biome> biome) {
        return biome != null && ((generationVersion >= 2 && biome.value().getBaseTemperature() >= 0.2F && biome.value().getBaseTemperature() < 1.0F) || hasAnyTag(biome, TEMPERATE_TAGS)
                || biome.is(Biomes.PLAINS)
                || biome.is(Biomes.SUNFLOWER_PLAINS)
                || biome.is(Biomes.FOREST)
                || biome.is(Biomes.FLOWER_FOREST)
                || biome.is(Biomes.BIRCH_FOREST)
                || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
                || biome.is(Biomes.DARK_FOREST)
                || biome.is(Biomes.MEADOW)
                || biome.is(Biomes.TAIGA)
                || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA)
                || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.SWAMP)
                || biome.is(Biomes.MANGROVE_SWAMP)
                || biome.is(Biomes.RIVER)
                || biome.is(Biomes.OCEAN)
                || biome.is(Biomes.DEEP_OCEAN));
    }

    private boolean hasAnyTag(Holder<Biome> biome, List<TagKey<Biome>> tags) {
        for (TagKey<Biome> tag : tags) {
            if (biome.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private TagKey<Biome> tag(String id) {
        return TagKey.create(Registries.BIOME, ResourceLocation.parse(id));
    }

    @SafeVarargs
    private List<Holder<Biome>> nonNullBiomes(Holder<Biome>... biomes) {
        List<Holder<Biome>> holders = new ArrayList<>();
        for (Holder<Biome> biome : biomes) {
            if (biome != null) {
                holders.add(biome);
            }
        }
        return List.copyOf(holders);
    }

    @SafeVarargs
    private Holder<Biome> firstNonNull(Holder<Biome>... biomes) {
        for (Holder<Biome> biome : biomes) {
            if (biome != null) {
                return biome;
            }
        }
        return null;
    }

    private void warnAllExcluded(IslandMask.SourceInfo source) {
        if (source != null && EXCLUSION_WARNINGS.add(source.name())) {
            LOGGER.warn("All preferred biome fallbacks were excluded for '{}'; using safe vanilla fallback", source.name());
        }
    }

    private enum ClimateBand {
        COLD,
        TEMPERATE,
        WARM
    }

    private boolean isRiver(Holder<Biome> biome) {
        return biome != null && (biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER)
                || biome.is(tag("minecraft:is_river")) || biome.is(tag("c:is_river")) || biome.is(tag("forge:is_river")));
    }
    private boolean isUnderground(Holder<Biome> biome) {
        return biome != null && (biome.is(Biomes.LUSH_CAVES) || biome.is(Biomes.DRIPSTONE_CAVES)
                || biome.is(Biomes.DEEP_DARK) || biome.is(tag("c:is_underground")) || biome.is(tag("forge:is_underground")));
    }
    private double smoothstep(double min, double max, double value) {
        double x = (value - min) / (max - min);
        if (x < 0.0D) {
            x = 0.0D;
        } else if (x > 1.0D) {
            x = 1.0D;
        }
        return x * x * (3.0D - 2.0D * x);
    }

    private long mix(long seed, long value) {
        long mixed = seed ^ value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private double randomUnit(long seed, long salt) {
        long mixed = mix(seed, salt * 0x9e3779b97f4a7c15L);
        return (mixed >>> 11) * 0x1.0p-53D;
    }
}
