package org.wodichka.worldgen_editor.world;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import org.wodichka.worldgen_editor.config.IslandTemperature;
import java.util.*;

/** Shared, registry-backed climate choices. No display-name or substring classification. */
public final class BiomeClimateCatalog {
    private final List<Holder<Biome>> surface;
    private final Set<Holder<Biome>> surfaceSet;
    public BiomeClimateCatalog(Registry<Biome> registry) {
        surface=registry.holders().filter(BiomeClimateCatalog::surface).sorted(Comparator.comparing(BiomeClimateCatalog::id)).map(b->(Holder<Biome>)b).toList();
        surfaceSet=Set.copyOf(surface);
    }
    public static String id(Holder<Biome> biome){return biome.unwrapKey().orElseThrow().location().toString();}
    public static boolean tagged(Holder<Biome> biome,String id){return biome.is(TagKey.create(Registries.BIOME,ResourceLocation.parse(id)));}
    public static boolean surface(Holder<Biome> b){
        return tagged(b,"minecraft:is_overworld") && !tagged(b,"minecraft:is_ocean") && !tagged(b,"c:is_ocean") && !tagged(b,"c:is_river")
                && !tagged(b,"minecraft:is_river") && !tagged(b,"minecraft:is_beach") && !tagged(b,"c:is_beach")
                && !tagged(b,"c:is_underground") && !tagged(b,"c:is_cave") && !tagged(b,"terralith:caves");
    }
    public List<Holder<Biome>> surface(){return surface;}
    public List<Holder<Biome>> candidates(IslandTemperature choice){return surface.stream().filter(b->matches(b,choice)).toList();}
    public boolean matches(Holder<Biome> b,IslandTemperature choice){
        if(!surfaceSet.contains(b))return false;
        String id=id(b);
        return switch(choice){
            case STANDARD,NATURAL->true;
            case COLD->tagged(b,"c:is_cold")||tagged(b,"c:is_cold/overworld")||tagged(b,"c:is_snowy")||b.value().getBaseTemperature()<.2f;
            case WARM->!(matches(b,IslandTemperature.COLD))&&(tagged(b,"c:is_hot")||tagged(b,"c:is_hot/overworld")||tagged(b,"minecraft:is_jungle")||tagged(b,"minecraft:is_savanna")||b.value().getBaseTemperature()>=1f);
            case TEMPERATE->!matches(b,IslandTemperature.COLD)&&!matches(b,IslandTemperature.WARM);
            case MUSHROOM->id.equals("minecraft:mushroom_fields");
            case BOP_MUSHROOM->id.equals("biomesoplenty:fungal_jungle");
            case BOP_MAGICAL->id.startsWith("biomesoplenty:")&&tagged(b,"c:is_magical");
            case BOP_HAUNTED->id.startsWith("biomesoplenty:")&&tagged(b,"c:is_spooky");
            case BOP_VOLCANIC->Set.of("biomesoplenty:volcano","biomesoplenty:volcanic_plains").contains(id);
            case BOP_WASTELAND->Set.of("biomesoplenty:wasteland","biomesoplenty:wasteland_steppe","biomesoplenty:crag").contains(id);
            case TERRALITH_MUSHROOM->id.startsWith("terralith:")&&tagged(b,"c:is_mushroom");
            case TERRALITH_MYSTICAL->id.startsWith("terralith:")&&tagged(b,"terralith:mystical");
            case TERRALITH_VOLCANIC->id.startsWith("terralith:")&&tagged(b,"terralith:volcanic");
        };
    }
}
