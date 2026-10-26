package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.LevelHeightAccessor;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;
import java.nio.file.*;

/** Offline two-dimensional terrain audit. Never opens a ServerLevel or writes user saves. */
public final class CoastAudit {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(System.getProperty("wge.auditOutput","coast-audit.json"));
        JsonObject fixture;
        String worldPath=System.getProperty("wge.auditWorld");
        if(worldPath!=null){
            Path world=Path.of(worldPath);
            var snapshot=JsonParser.parseString(Files.readString(world.resolve("worldgen_editor/worldgen_editor.json"))).getAsJsonObject();
            var data=NbtIo.readCompressed(world.resolve("level.dat"),NbtAccounter.unlimitedHeap()).getCompound("Data");
            fixture=new JsonObject();fixture.addProperty("seed",Long.toString(data.getCompound("WorldGenSettings").getLong("seed")));
            fixture.add("config",snapshot.getAsJsonObject("config"));
            Files.writeString(Path.of("coast-repro.json"),new Gson().toJson(fixture));
        }else fixture=JsonParser.parseString(Files.readString(Path.of(System.getProperty("wge.auditSource","../../common/src/test/resources/natural-coast-repro.json")))).getAsJsonObject();
        long seed=Long.parseLong(fixture.get("seed").getAsString());var report=new JsonObject();report.addProperty("seed",Long.toString(seed));
        report.add("columns",JsonParser.parseString("[\"x\",\"z\",\"solid_base_height\",\"surface_height\",\"biome_above_solid\",\"geometric_mask\",\"coast_distance\",\"coast_influence\",\"depth_at_sea\",\"pre_cave_height_estimate\"]"));
        var access=LevelHeightAccessor.create(-64,384);
        for(boolean enabled:new boolean[]{true,false}){
            if(!enabled&&Boolean.getBoolean("wge.auditCustomOnly"))continue;
            var config=fixture.getAsJsonObject("config").deepCopy();
            if(!enabled)for(var e:config.getAsJsonArray("entries"))e.getAsJsonObject().remove("coast");
            var world=new PreviewWorld(config,seed,4);
            var raw=new org.wodichka.worldgen_editor.world.IslandMask(IslandConfigLoader.parse(withoutCoast(config)),seed);
            var rows=new JsonArray();
            // Both reported positions and the surrounding coasts, sampled independently of water height.
            for(int z=-384;z<=256;z+=8)for(int x=-128;x<=704;x+=8){
                var column=world.generator().getBaseColumn(x,z,access,world.random());int floor=-64;
                for(int y=319;y>=-64;y--){var state=column.getBlock(y);if(!state.isAir()&&state.getFluidState().isEmpty()){floor=y;break;}}
                int surface=world.surfaceY(x,z,8);var sample=world.context().mask().sampleInfo(x,z);
                var row=new JsonArray();row.add(x);row.add(z);row.add(floor);row.add(surface);row.add(world.biomeId(x,floor+1,z));row.add(raw.sample(x,z));
                row.add(sample.coast()==null||!Double.isFinite(sample.coast().distance())?JsonNull.INSTANCE:new JsonPrimitive(sample.coast().distance()));
                row.add(org.wodichka.worldgen_editor.world.NaturalCoast.influence(sample.coast()));
                double depth=world.random().router().depth().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x,world.generator().getSeaLevel(),z));
                row.add(depth);row.add(org.wodichka.worldgen_editor.world.NaturalCoast.approximateHeight(depth,world.generator().getSeaLevel()));rows.add(row);
            }
            report.add(enabled?"custom":"original",rows);
            System.out.println("Audited "+rows.size()+" columns, custom coast="+enabled);
        }
        Files.writeString(output,new Gson().toJson(report));
    }
    private static JsonObject withoutCoast(JsonObject config){var result=config.deepCopy();for(var entry:result.getAsJsonArray("entries"))entry.getAsJsonObject().remove("coast");return result;}
}
