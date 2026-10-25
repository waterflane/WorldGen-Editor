package org.wodichka.worldgen_editor.config;

import java.nio.file.*;
import com.google.gson.JsonParser;

public final class PresetCatalogTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception {
        Path dir=Files.createTempDirectory(Path.of("build").toAbsolutePath(),"preset-catalog-");
        Path file=dir.resolve("My drawn map.wge.json");
        String project="""
            {"format":"wge-map-project","version":1,"name":"Hidden Coast","seed":"42",
             "config":{"enabled":true,"entries":[],"painted_map":{"version":1,"step":4,"size":64,"tiles":[]}},"editor":{}}
            """;
        Files.writeString(file,project);
        var catalog=new PresetCatalog(dir);var entries=catalog.scan();
        check(entries.size()==1&&entries.getFirst().name().equals("Hidden Coast"),"display name must come from JSON");
        check(entries.getFirst().generationVersion()==3,"project must select painted generation automatically");
        check(IslandConfigLoader.load(file).name().equals("Hidden Coast"),"project file cannot be loaded as a preset");
        check(IslandConfigLoader.toJson(IslandConfigLoader.load(file)).get("name").getAsString().equals("Hidden Coast"),"snapshot lost preset name");
        Files.writeString(dir.resolve("other.json"),"{\"name\":\"Another Map\",\"entries\":[]}");
        check(catalog.scan().size()==2,"added JSON did not appear");
        Files.writeString(file,"{");
        var broken=catalog.scan().stream().filter(e->e.id().equals("My drawn map.wge")).findFirst().orElseThrow();
        check(broken.error()!=null&&broken.name().equals("Hidden Coast"),"bad edits must report an error without silently selecting another map");
        Files.delete(file);check(catalog.scan().size()==1,"removed JSON remained selectable");
        Files.writeString(dir.resolve("broken.json"),"{\"name\":\"Broken Map\",\"entries\":\"invalid\"}");
        check(catalog.scan().stream().anyMatch(e->e.name().equals("Broken Map")&&e.error()!=null),"invalid named map lost its display name");
        for(String name:new String[]{"../escape","..","/root","a\\b","bad:name","CON","name."," name"})check(!IslandConfigLoader.isSafePresetName(name),"unsafe filename accepted: "+name);
        for(String invalid:new String[]{"{\"format\":\"future\",\"config\":{\"entries\":[]}}","{\"format\":\"wge-map-project\",\"version\":2,\"config\":{\"entries\":[]}}","{\"generation_version\":4,\"entries\":[]}"}){
            try{PresetDocument.parse(JsonParser.parseString(invalid).getAsJsonObject());throw new AssertionError("unsupported document accepted");}catch(IslandConfigException expected){}
        }
        if(args.length>0){var actual=PresetDocument.read(Path.of(args[0]));check(actual.generationVersion()==3&&actual.config().paintedMap()!=null,"actual project import failed");System.out.println("Actual editor project validated");}
        System.out.println("Preset discovery, project imports, metadata and rollback checks passed");
    }
}
