package org.wodichka.worldgen_editor.preview;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

final class ProfileImportTest {
    static void run()throws Exception {
        Path root=Files.createTempDirectory("wge-profile-test"),source=root.resolve("source"),copy=root.resolve("copy");
        Files.createDirectories(source.resolve("config"));Files.writeString(source.resolve("config/worldgen.json"),"{\"seed\":2}");
        ProfileManager.copyTree(source,copy,new long[]{0,0},new JsonObject());
        GenerationContextTest.check(Files.readString(copy.resolve("config/worldgen.json")).equals(Files.readString(source.resolve("config/worldgen.json"))),"Import changed config bytes");
        var cancel=new JsonObject();cancel.addProperty("cancelled",true);
        try{ProfileManager.copyTree(source,root.resolve("cancelled"),new long[]{0,0},cancel);throw new AssertionError("Cancelled import copied files");}catch(java.util.concurrent.CancellationException expected){}
        Path mods=root.resolve("mods");Files.createDirectories(mods);Path jar=mods.resolve("test.jar");
        var nested=new java.io.ByteArrayOutputStream();try(var library=new JarOutputStream(nested)){library.putNextEntry(new JarEntry("example/Marker.txt"));library.write("unchanged classes".getBytes());library.closeEntry();}
        try(var archive=new ZipOutputStream(Files.newOutputStream(jar))){
            archive.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));archive.write("modLoader=\"javafml\"\n[[mods]]\nmodId=\"example\"\nversion=\"1.2.3\"\n".getBytes());archive.closeEntry();
            archive.putNextEntry(new ZipEntry("META-INF/jarjar/library.jar"));archive.write(nested.toByteArray());archive.closeEntry();
        }
        var before=ProfileManager.hash(jar,"SHA-256");var metadata=ProfileManager.modMetadata(jar);
        GenerationContextTest.check(metadata.get("id").getAsString().equals("example")&&metadata.get("version").getAsString().equals("1.2.3"),"Mod identity detection failed");
        NestedPreviewLibraries.prepare(root);
        try(var files=Files.list(root.resolve("runtime-libs"))){var file=files.filter(p->p.toString().endsWith(".jar")).findFirst().orElseThrow();try(var library=new JarFile(file.toFile())){
            GenerationContextTest.check(library.getManifest().getMainAttributes().getValue("FMLModType").equals("GAMELIBRARY"),"Nested library not loadable by NeoForge");
            GenerationContextTest.check(new String(library.getInputStream(library.getEntry("example/Marker.txt")).readAllBytes()).equals("unchanged classes"),"Nested content changed");
        }}
        GenerationContextTest.check(ProfileManager.hash(jar,"SHA-256").equals(before),"Nested extraction modified source jar");
        metadataVersions(root);
        System.out.println("Profile copy, cancellation, identity and nested-library preservation passed");
    }
    private static void metadataVersions(Path root)throws Exception {
        Path prism=root.resolve("prism");Files.createDirectories(prism);
        Files.writeString(prism.resolve("mmc-pack.json"),"{\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.21.1\"},{\"uid\":\"net.neoforged\",\"version\":\"21.1.172\"}]}");
        GenerationContextTest.check(ProfileManager.detectVersion(prism).equals("21.1.172"),"Prism loader version replaced by test lock");
        Path curse=root.resolve("curse");Files.createDirectories(curse);
        Files.writeString(curse.resolve("manifest.json"),"{\"minecraft\":{\"version\":\"1.21.1\",\"modLoaders\":[{\"id\":\"neoforge-21.1.209\"}]}}");
        GenerationContextTest.check(ProfileManager.detectVersion(curse).equals("21.1.209"),"CurseForge loader version replaced by test lock");
        Path modrinth=root.resolve("modrinth");Files.createDirectories(modrinth);
        Files.writeString(modrinth.resolve("profile.json"),"{\"metadata\":{\"game_version\":\"1.21.1\",\"loader\":\"neoforge\",\"loader_version\":\"21.1.220\"}}");
        GenerationContextTest.check(ProfileManager.detectVersion(modrinth).equals("21.1.220"),"Modrinth profile loader version replaced by test lock");
        Files.writeString(curse.resolve("manifest.json"),"{\"minecraft\":{\"version\":\"1.20.4\",\"modLoaders\":[{\"id\":\"neoforge-20.4.237\"}]}}");
        try{ProfileManager.detectVersion(curse);throw new AssertionError("Wrong Minecraft version accepted");}catch(java.io.IOException expected){}
        Files.writeString(prism.resolve("modrinth.index.json"),"{\"dependencies\":{\"minecraft\":\"1.21.1\",\"neoforge\":\"21.1.230\"}}");
        try{ProfileManager.detectVersion(prism);throw new AssertionError("Conflicting loader versions silently replaced");}catch(java.io.IOException expected){}
    }
}
