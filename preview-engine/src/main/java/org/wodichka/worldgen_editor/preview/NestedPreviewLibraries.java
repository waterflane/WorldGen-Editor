package org.wodichka.worldgen_editor.preview;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipFile;

/** Loom removes jar-in-jar payloads. Expose unchanged nested classes to its development loader. */
final class NestedPreviewLibraries {
    static void prepare(Path snapshot)throws IOException {
        Path output=snapshot.resolve("runtime-libs");Files.createDirectories(output);
        try(var jars=Files.list(snapshot.resolve("mods"))){for(var jar:jars.filter(p->p.toString().endsWith(".jar")).toList())extract(jar,output,new HashSet<>(),0);}
    }
    private static void extract(Path parent,Path output,Set<String> seen,int depth)throws IOException {
        if(depth>8)throw new IOException("Nested dependency depth exceeds eight");
        try(var jar=new ZipFile(parent.toFile())){
            for(var entry:Collections.list(jar.entries())){
                if(!entry.getName().startsWith("META-INF/jarjar/")||!entry.getName().endsWith(".jar"))continue;
                if(entry.getSize()<0||entry.getSize()>64*1024*1024)throw new IOException("Nested dependency exceeds 64 MiB");
                Path raw=Files.createTempFile(output,"nested-",".input");
                try{
                    try(var input=jar.getInputStream(entry)){Files.copy(input,raw,StandardCopyOption.REPLACE_EXISTING);}
                    String hash=ProfileManager.hash(raw,"SHA-256");if(!seen.add(hash))continue;
                    String name=Path.of(entry.getName()).getFileName().toString();Path target=output.resolve(hash.substring(0,12)+"-"+name);
                    try(var nested=new JarFile(raw.toFile())){
                        boolean mod=nested.getEntry("META-INF/neoforge.mods.toml")!=null||nested.getEntry("META-INF/mods.toml")!=null;
                        if(mod)Files.copy(raw,target,StandardCopyOption.REPLACE_EXISTING);
                        else{
                            if(Collections.list(nested.entries()).stream().anyMatch(e->e.getName().startsWith("META-INF/")&&(e.getName().endsWith(".SF")||e.getName().endsWith(".RSA"))))throw new IOException("Signed nested library needs a dedicated runtime adapter: "+name);
                            var manifest=nested.getManifest()==null?new Manifest():nested.getManifest();var attrs=manifest.getMainAttributes();
                            attrs.putValue("Manifest-Version","1.0");attrs.putValue("FMLModType","GAMELIBRARY");if(attrs.getValue("Automatic-Module-Name")==null)attrs.putValue("Automatic-Module-Name","wge.preview.lib.h"+hash.substring(0,12));
                            try(var result=new JarOutputStream(Files.newOutputStream(target),manifest)){
                                for(var member:Collections.list(nested.entries())){if(member.getName().equalsIgnoreCase("META-INF/MANIFEST.MF"))continue;
                                    result.putNextEntry(new JarEntry(member.getName()));try(var data=nested.getInputStream(member)){data.transferTo(result);}result.closeEntry();}
                            }
                        }
                    }
                    extract(target,output,seen,depth+1);
                }finally{Files.deleteIfExists(raw);}
                if(seen.size()>1024)throw new IOException("Too many nested dependencies");
            }
        }
    }
    private NestedPreviewLibraries(){}
}
