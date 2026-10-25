package org.wodichka.worldgen_editor.config;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Incremental directory metadata. Large masks are validated off the render thread, then released. */
public final class PresetCatalog {
    public record Entry(String id,String name,int generationVersion,String error) {}
    private record Cached(long size,FileTime modified,Object fileKey,Entry entry) {}
    private final Path directory;
    private Map<Path,Cached> cached=Map.of();
    public PresetCatalog(Path directory){this.directory=directory;}

    public synchronized List<Entry> scan()throws IOException {
        Files.createDirectories(directory);
        var next=new HashMap<Path,Cached>();var entries=new ArrayList<Entry>();
        try(var files=Files.list(directory)){
            for(var path:files.sorted().toList()){
                String file=path.getFileName().toString();
                if(!file.toLowerCase(Locale.ROOT).endsWith(".json")||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                String id=file.substring(0,file.length()-5);
                if(!IslandConfigLoader.isSafePresetName(id))continue;
                var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                var previous=cached.get(path);Entry entry;
                if(previous!=null&&previous.size==attrs.size()&&previous.modified.equals(attrs.lastModifiedTime())&&Objects.equals(previous.fileKey,attrs.fileKey()))entry=previous.entry;
                else {
                  String declaredName=previous==null?legacyName(id):previous.entry.name();
                  try{
                    var raw=PresetDocument.readObject(path);
                    var label=raw.has("name")?raw.get("name"):raw.has("config")&&raw.get("config").isJsonObject()?raw.getAsJsonObject("config").get("name"):null;
                    if(label!=null&&label.isJsonPrimitive()&&label.getAsJsonPrimitive().isString()){
                        String value=label.getAsString().trim();if(!value.isBlank()&&value.length()<=120&&value.chars().noneMatch(Character::isISOControl))declaredName=value;
                    }
                    var document=PresetDocument.parse(raw);
                    String name=document.config().name().isBlank()?legacyName(id):document.config().name();
                    entry=new Entry(id,name,document.generationVersion(),null);
                }catch(IOException|IslandConfigException|RuntimeException error){
                    entry=new Entry(id,declaredName,previous==null?3:previous.entry.generationVersion(),error.getMessage());
                  }
                }
                next.put(path,new Cached(attrs.size(),attrs.lastModifiedTime(),attrs.fileKey(),entry));entries.add(entry);
            }
        }
        cached=next;
        entries.sort(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));
        return List.copyOf(entries);
    }

    private static String legacyName(String id){
        return switch(id){case "default"->"Islands";case "archipelago"->"Archipelago";case "small_island"->"Small Island";case "custom_map"->"Empty Ocean";default->id;};
    }
}
