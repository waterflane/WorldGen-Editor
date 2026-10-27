package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.ZipFile;

/** Imports immutable copies. Only a successfully bootstrapped snapshot becomes a profile. */
public final class ProfileManager implements AutoCloseable {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> ROOTS=Set.of("mods","config","defaultconfigs","datapacks","kubejs","scripts","openloader","global_packs","paxi","moonlight-global-datapacks");
    private static final Set<String> CLIENT=Set.of("sodium","iris","embeddium","oculus","fancymenu","reeses_sodium_options","sodium_extra","entityculling","immediatelyfast","modmenu");
    private static final Map<String,List<String>> LOCKED=Map.of(
        "terrablender",List.of("terrablender"),"bop",List.of("biomes-o-plenty","glitchcore","terrablender"),
        "terralith",List.of("terralith","lithostitched","apollib"),"tectonic",List.of("tectonic","lithostitched","apollib"),
        "terratonic",List.of("terralith","tectonic","lithostitched","apollib"),
        "bop_terralith",List.of("biomes-o-plenty","glitchcore","terrablender","terralith","lithostitched","apollib"),
        "bop_tectonic",List.of("biomes-o-plenty","glitchcore","terrablender","tectonic","lithostitched","apollib"),
        "combined",List.of("biomes-o-plenty","glitchcore","terrablender","terralith","tectonic","lithostitched","apollib"));
    private final Path site,root,storage;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ExecutorService imports=Executors.newSingleThreadExecutor();
    private final Map<String,Path> jobSnapshots=new ConcurrentHashMap<>();
    private final Map<String,JsonObject> jobs=new ConcurrentHashMap<>();
    private final Map<String,String> remoteSessions=Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile JFileChooser activePicker;
    private volatile Process child;private String activeId,activeSnapshot;private JsonObject activeCatalog;private int childPort;private volatile Process preparation;
    public ProfileManager(Path site)throws IOException {
        this.site=site;root=site.resolveSibling("WorldGen Editor").toAbsolutePath().normalize();
        storage=root.resolve("build/preview-profiles");Files.createDirectories(storage);
    }
    public void register(HttpServer server){
        server.createContext("/api/profiles/runtime",e->PreviewServer.handle(e,this::runtimeInfo));
        server.createContext("/api/profiles/list",e->PreviewServer.handle(e,this::list));
        server.createContext("/api/profiles/status",e->PreviewServer.handle(e,()->{
            String q=e.getRequestURI().getQuery();String id=q==null?"":q.replaceFirst("^id=","");
            var state=jobs.get(id);if(state==null)throw new IllegalArgumentException("Unknown profile operation");synchronized(state){return state.deepCopy();}
        }));
        server.createContext("/api/profiles/prepare",e->PreviewServer.handle(e,()->begin(PreviewServer.read(e))));
        server.createContext("/api/profiles/cancel",e->PreviewServer.handle(e,()->{
            var request=PreviewServer.read(e);var state=jobs.get(request.get("job").getAsString());
            boolean cancelled=false;
            if(state!=null)synchronized(state){if(!Set.of("ready","failed","cancelled").contains(state.get("state").getAsString())){state.addProperty("cancelled",true);state.addProperty("state","cancelled");cancelled=true;}}
            if(cancelled){SwingUtilities.invokeLater(()->{var picker=activePicker;if(picker!=null)picker.cancelSelection();});if(preparation!=null)preparation.descendants().forEach(ProcessHandle::destroy);if(preparation!=null)preparation.destroy();}
            return new JsonObject();
        }));
    }
    private synchronized JsonObject runtimeInfo()throws Exception {var result=child==null||!child.isAlive()?new JsonObject():remote("info",null);if(child!=null&&child.isAlive()){result.addProperty("pid",child.pid());result.addProperty("profile",activeId);}return result;}
    private JsonObject list()throws IOException {
        var result=new JsonObject();var values=new JsonArray();
        var vanilla=new JsonObject();vanilla.addProperty("id","vanilla");vanilla.addProperty("name","Minecraft + WGE");vanilla.addProperty("heap_mib",512);vanilla.addProperty("status","verified");values.add(vanilla);
        LOCKED.keySet().stream().sorted().forEach(id->{if(Files.isRegularFile(storage.resolve("supported-"+id).resolve("profile.json")))return;var value=new JsonObject();value.addProperty("id","supported-"+id);value.addProperty("name",switch(id){case "bop"->"Biomes O’ Plenty";case "terratonic"->"Terralith + Tectonic";case "combined"->"All supported mods";default->id.replace('_',' ');});value.addProperty("heap_mib",2048);value.addProperty("status","not prepared");values.add(value);});
        try(var dirs=Files.list(storage)){for(var dir:dirs.filter(Files::isDirectory).toList()){
            var manifest=dir.resolve("profile.json");if(Files.isRegularFile(manifest)){var value=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();value.remove("source");value.remove("snapshot");if(!value.has("runtime_revision")||value.get("runtime_revision").getAsInt()!=1)value.addProperty("status","outdated");values.add(value);}
        }}result.add("profiles",values);return result;
    }
    private static boolean inProgress(JsonObject state){synchronized(state){return !Set.of("ready","failed","cancelled").contains(state.get("state").getAsString());}}
    private synchronized JsonObject begin(JsonObject request){
        if(jobs.values().stream().anyMatch(ProfileManager::inProgress))throw new IllegalArgumentException("A profile operation is already in progress");
        String job=UUID.randomUUID().toString();var state=new JsonObject();state.addProperty("job",job);state.addProperty("state","queued");jobs.put(job,state);
        while(jobs.size()>32){var old=jobs.entrySet().stream().filter(e->Set.of("ready","failed","cancelled").contains(e.getValue().get("state").getAsString())).findFirst();if(old.isEmpty())break;jobs.remove(old.get().getKey());}
        imports.submit(()->{
            try{prepare(request,state);}catch(Exception error){synchronized(state){state.addProperty("state",state.has("cancelled")?"cancelled":"failed");state.addProperty("error",error.getMessage()==null?error.toString():error.getMessage());}
                Path failed=jobSnapshots.get(job);if(failed!=null)try{if(failed.toString().equals(activeSnapshot))closeChild();if(!Files.exists(failed.getParent().resolve("profile.json")))removeSnapshot(failed);else prune(failed.getParent(),Path.of(manifest(failed.getParent().getFileName().toString()).get("snapshot").getAsString()));}catch(IOException cleanup){System.err.println("Could not prune failed preview snapshot: "+cleanup.getMessage());}
            }finally{jobSnapshots.remove(job);}
        });var result=new JsonObject();result.addProperty("job",job);return result;
    }
    private static void phase(JsonObject state,String phase){synchronized(state){if(state.has("cancelled"))throw new CancellationException("Profile preparation cancelled");state.addProperty("state",phase);}}
    private void prepare(JsonObject request,JsonObject state)throws Exception {
        phase(state,"selecting");String requested=request.has("profile")?request.get("profile").getAsString():"";
        boolean locked=requested.startsWith("supported-");
        if(locked&&!LOCKED.containsKey(requested.substring("supported-".length())))throw new IllegalArgumentException("Unknown supported profile");
        if(request.has("heap_mib")&&(!request.get("heap_mib").isJsonPrimitive()||!request.getAsJsonPrimitive("heap_mib").isNumber()))throw new IllegalArgumentException("Heap must be a numeric MiB limit");
        JsonObject old=null;Path source=null;
        if(!locked&&!requested.isEmpty()){
            old=manifest(requested);source=Path.of(old.get("source").getAsString());
        }else if(!locked&&request.has("path"))source=Path.of(request.get("path").getAsString());
        else if(!locked){final Path[] selected={null};SwingUtilities.invokeAndWait(()->{var picker=new JFileChooser();activePicker=picker;picker.setDialogTitle("Import Minecraft modpack");picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION)selected[0]=picker.getSelectedFile().toPath();activePicker=null;});source=selected[0];if(source==null)throw new CancellationException("Import cancelled");}
        int heap=request.has("heap_mib")?request.get("heap_mib").getAsInt():old!=null?old.get("heap_mib").getAsInt():2048;
        if(heap<512||heap>16384||request.has("heap_mib")&&request.get("heap_mib").getAsDouble()!=heap)throw new IllegalArgumentException("Heap must be an integer from 512 to 16384 MiB");
        phase(state,"copying");String id=locked?requested:old!=null?requested:"pack-"+UUID.randomUUID();
        Path base=storage.resolve(id);Files.createDirectories(base);Path snapshot=base.resolve("snapshot-"+UUID.randomUUID());Files.createDirectories(snapshot);jobSnapshots.put(state.get("job").getAsString(),snapshot);
        var profile=new JsonObject();profile.addProperty("id",id);profile.addProperty("runtime_revision",1);profile.addProperty("heap_mib",heap);profile.addProperty("status","unverified");profile.addProperty("snapshot",snapshot.toString());
        var excluded=new JsonArray();var mods=new JsonArray();
        if(locked){
            String key=requested.substring("supported-".length());if(!LOCKED.containsKey(key))throw new IllegalArgumentException("Unknown supported profile");
            Files.createDirectories(snapshot.resolve("mods"));var lock=JsonParser.parseString(Files.readString(root.resolve("tools/compatibility-lock.json"))).getAsJsonArray();
            for(var entry:lock){var artifact=entry.getAsJsonObject();if(!LOCKED.get(key).contains(artifact.get("project").getAsString()))continue;
                Path file=root.resolve("neoforge/build/compatibility-libs").resolve(artifact.get("filename").getAsString());
                if(!Files.isRegularFile(file))throw new IllegalArgumentException("Prepare locked dependencies first: tools/prepare_compatibility.py; missing "+file.getFileName());
                if(!hash(file,"SHA-512").equals(artifact.get("sha512").getAsString()))throw new IllegalArgumentException("Dependency checksum mismatch: "+file.getFileName());
                Files.copy(file,snapshot.resolve("mods").resolve(file.getFileName()));
            }profile.addProperty("name",key.equals("combined")?"All supported mods":key.replace('_',' '));profile.addProperty("neoforge","21.1.230");
        }else{
            source=source.toRealPath();if(!Files.isDirectory(source.resolve("mods")))throw new IllegalArgumentException("Select a Minecraft instance folder containing mods");
            profile.addProperty("source",source.toString());profile.addProperty("name",source.getFileName().toString());profile.addProperty("neoforge",detectVersion(source));
            long[] limits={0,0};for(String folder:ROOTS){Path dir=source.resolve(folder);if(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))copyTree(dir,snapshot.resolve(folder),limits,state);}
        }
        try(var jars=Files.list(snapshot.resolve("mods"))){for(Path file:jars.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){
            var metadata=modMetadata(file);String modId=metadata.has("id")?metadata.get("id").getAsString():"";
            if(modId.equals("worldgen_editor")||metadata.has("client_only")||CLIENT.contains(modId)){
                metadata.addProperty("reason",modId.equals("worldgen_editor")?"Replaced by current WGE runtime":"Client-only mod");excluded.add(metadata);Files.delete(file);
            }else mods.add(metadata);
        }}profile.add("mods",mods);profile.add("excluded",excluded);profile.addProperty("minecraft","1.21.1");profile.addProperty("wge_version","0.4.0");
        // Hash effective inputs after exclusions, before build artifacts are introduced.
        var digest=MessageDigest.getInstance("SHA-256");digest.update("preview-launcher-1".getBytes(StandardCharsets.UTF_8));try(var paths=Files.walk(snapshot)){for(var file:paths.filter(Files::isRegularFile).sorted().toList()){
            digest.update(snapshot.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8));digest.update(HexFormat.of().parseHex(hash(file,"SHA-256")));
        }}digest.update(profile.get("neoforge").getAsString().getBytes(StandardCharsets.UTF_8));
        profile.addProperty("fingerprint",HexFormat.of().formatHex(digest.digest()));
        NestedPreviewLibraries.prepare(snapshot);
        phase(state,"preparing runtime");
        // User jars are remapped only into Loom's cache; the imported snapshot stays unchanged.
        Path log=snapshot.resolve("prepare.log");var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-cp",root.resolve("gradle/wrapper/gradle-wrapper.jar").toString(),"org.gradle.wrapper.GradleWrapperMain",":neoforge:runModdedPreview","-PprepareModdedPreview=true","-PpreviewProfileDir="+snapshot,"-Pneoforge_version="+profile.get("neoforge").getAsString(),"--no-daemon");
        var builder=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());builder.environment().put("JAVA_HOME",System.getProperty("java.home"));
        synchronized(this){preparation=builder.start();}
        if(!preparation.waitFor(600,TimeUnit.SECONDS)){preparation.descendants().forEach(ProcessHandle::destroyForcibly);preparation.destroyForcibly();throw new IOException("Runtime preparation timed out; "+log);}
        if(preparation.exitValue()!=0)throw new IOException("Runtime preparation failed: "+tail(log));synchronized(this){preparation=null;}
        Files.writeString(snapshot.resolve("profile.json"),JSON.toJson(profile));phase(state,"starting");
        JsonObject catalog=start(profile,state);phase(state,"validating");profile.addProperty("runtime_fingerprint",catalog.get("resources").getAsString());profile.addProperty("status",catalog.get("accuracy").getAsString());profile.add("loaded_mods",catalog.get("mods"));
        // Commit only after the child exposes a valid catalog. A failed import cannot replace a saved profile.
        synchronized(state){
            if(state.has("cancelled"))throw new CancellationException("Profile preparation cancelled");
            Path temp=base.resolve("profile.pending.json");Files.writeString(temp,JSON.toJson(profile));Files.move(temp,base.resolve("profile.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            state.add("profile",publicProfile(profile));state.add("catalog",catalog);state.addProperty("state","ready");
        }
        try{prune(base,snapshot);}catch(IOException cleanup){System.err.println("Could not prune preview cache: "+cleanup.getMessage());}
    }
    private void removeSnapshot(Path snapshot)throws IOException {
        Path absolute=snapshot.toAbsolutePath().normalize(),owner=storage.toRealPath();
        if(!absolute.startsWith(owner)||!absolute.getFileName().toString().matches("snapshot-[0-9a-f-]+")||Files.isSymbolicLink(absolute))throw new IOException("Invalid preview cache deletion target");
        Path resolved=absolute.toRealPath();if(!resolved.startsWith(owner))throw new IOException("Preview cache escaped its storage directory");
        Files.walkFileTree(absolute,new SimpleFileVisitor<>(){
            public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException {if(!dir.toRealPath().startsWith(resolved))throw new IOException("Linked cache directory escaped snapshot");return FileVisitResult.CONTINUE;}
            public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException {Files.delete(file);return FileVisitResult.CONTINUE;}
            public FileVisitResult postVisitDirectory(Path dir,IOException error)throws IOException {if(error!=null)throw error;Files.delete(dir);return FileVisitResult.CONTINUE;}
        });
    }
    private void prune(Path profile,Path current)throws IOException {
        try(var paths=Files.list(profile)){var snapshots=paths.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().startsWith("snapshot-")).sorted((a,b)->{try{return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));}catch(IOException error){return 0;}}).toList();
            int retained=0;for(var snapshot:snapshots){if(snapshot.equals(current)||retained++<2)continue;try{removeSnapshot(snapshot);}catch(IOException unavailable){System.err.println("Could not prune preview snapshot: "+unavailable.getMessage());}}
        }
    }
    private static JsonObject publicProfile(JsonObject profile){var copy=profile.deepCopy();copy.remove("source");copy.remove("snapshot");return copy;}
    static void copyTree(Path source,Path target,long[] limits,JsonObject state)throws IOException {
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException{if(Files.isSymbolicLink(dir))throw new IOException("Linked import directories are not supported");Files.createDirectories(target.resolve(source.relativize(dir)));return FileVisitResult.CONTINUE;}
            public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException{
                if(state.has("cancelled"))throw new CancellationException();if(!a.isRegularFile()||Files.isSymbolicLink(file))throw new IOException("Linked import files are not supported");
                if(++limits[0]>100000||(limits[1]+=a.size())>20L*1024*1024*1024)throw new IOException("Import exceeds 100,000 files or 20 GiB");
                Files.copy(file,target.resolve(source.relativize(file)));return FileVisitResult.CONTINUE;
            }
        });
    }
    static JsonObject modMetadata(Path file)throws IOException {
        var result=new JsonObject();result.addProperty("file",file.getFileName().toString());result.addProperty("sha256",hash(file,"SHA-256"));
        try(var jar=new ZipFile(file.toFile())){
            var entry=jar.getEntry("META-INF/neoforge.mods.toml");if(entry==null)entry=jar.getEntry("META-INF/mods.toml");
            if(entry==null){if(jar.getEntry("fabric.mod.json")!=null)throw new IOException("Fabric mod cannot load in NeoForge: "+file.getFileName());return result;}
            String toml=new String(jar.getInputStream(entry).readNBytes(1024*1024),StandardCharsets.UTF_8);
            var id=Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"([^\"]+)\"").matcher(toml);if(id.find())result.addProperty("id",id.group(1));
            var version=Pattern.compile("(?m)^\\s*version\\s*=\\s*\"([^\"]+)\"").matcher(toml);if(version.find())result.addProperty("version",version.group(1));
            if(Pattern.compile("(?m)^\\s*clientSideOnly\\s*=\\s*true\\s*(?:#.*)?$").matcher(toml).find())result.addProperty("client_only",true);
        }return result;
    }
    static String detectVersion(Path source)throws IOException {
        String detected=null;
        for(String file:List.of("modrinth.index.json","manifest.json","mmc-pack.json","profile.json")){
            Path path=source.resolve(file);if(!Files.isRegularFile(path))continue;
            JsonObject data;
            try{data=JsonParser.parseString(Files.readString(path)).getAsJsonObject();}catch(RuntimeException error){throw new IOException("Invalid instance metadata: "+file,error);}
            String candidate=null,minecraft=null;
            if(file.equals("modrinth.index.json")&&data.has("dependencies")){
                var deps=data.getAsJsonObject("dependencies");minecraft=text(deps,"minecraft");candidate=text(deps,"neoforge");
                if(candidate==null&&(deps.has("fabric-loader")||deps.has("forge")))throw new IOException("NeoForge instance required: "+file);
            }else if(file.equals("manifest.json")&&data.has("minecraft")){
                var game=data.getAsJsonObject("minecraft");minecraft=text(game,"version");
                if(game.has("modLoaders"))for(var item:game.getAsJsonArray("modLoaders")){
                    String id=text(item.getAsJsonObject(),"id");if(id!=null&&id.startsWith("neoforge-"))candidate=id.substring(9);
                }
                if(candidate==null&&game.has("modLoaders")&&!game.getAsJsonArray("modLoaders").isEmpty())throw new IOException("NeoForge instance required: "+file);
            }else if(file.equals("mmc-pack.json")&&data.has("components")){
                for(var item:data.getAsJsonArray("components")){var component=item.getAsJsonObject();String uid=text(component,"uid");
                    if("net.minecraft".equals(uid))minecraft=text(component,"version");
                    if("net.neoforged".equals(uid)||"net.neoforged.neoforge".equals(uid))candidate=text(component,"version");
                    if("net.fabricmc.fabric-loader".equals(uid)||"net.minecraftforge".equals(uid))throw new IOException("NeoForge instance required: "+file);
                }
            }else if(file.equals("profile.json")){
                var metadata=data.has("metadata")&&data.get("metadata").isJsonObject()?data.getAsJsonObject("metadata"):data;
                minecraft=text(metadata,"game_version");String loader=text(metadata,"loader");
                if(loader!=null&&!loader.equalsIgnoreCase("neoforge"))throw new IOException("NeoForge instance required: "+file);
                if(loader!=null)candidate=text(metadata,"loader_version");
            }
            if(minecraft!=null&&!minecraft.equals("1.21.1"))throw new IOException("Minecraft 1.21.1 is required; "+file+" specifies "+minecraft);
            if(candidate!=null){
                if(!candidate.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][A-Za-z0-9]+)*"))throw new IOException("Invalid NeoForge version in "+file+": "+candidate);
                if(detected!=null&&!detected.equals(candidate))throw new IOException("Conflicting NeoForge versions in instance metadata: "+detected+" and "+candidate);
                detected=candidate;
            }
        }return detected==null?"21.1.230":detected;
    }
    private static String text(JsonObject object,String key)throws IOException {
        if(!object.has(key)||object.get(key).isJsonNull())return null;
        var value=object.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IOException("Instance metadata field must be a string: "+key);
        return value.getAsString();
    }
    private JsonObject manifest(String id)throws IOException {
        if(!id.matches("(?:pack-[0-9a-f-]+|supported-[a-z_]+)"))throw new IllegalArgumentException("Invalid profile ID");
        Path path=storage.resolve(id).resolve("profile.json");if(!Files.isRegularFile(path))throw new IllegalArgumentException("Profile is not prepared");return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    private JsonObject start(JsonObject profile)throws Exception {return start(profile,null);}
    private synchronized JsonObject start(JsonObject profile,JsonObject operation)throws Exception {
        if(!profile.has("runtime_revision")||profile.get("runtime_revision").getAsInt()!=1)throw new IllegalArgumentException("Profile runtime changed; reload this profile");
        String id=profile.get("id").getAsString();if(child!=null&&child.isAlive()&&id.equals(activeId)&&profile.get("snapshot").getAsString().equals(activeSnapshot))return activeCatalog.deepCopy();
        if(operation==null&&jobs.values().stream().anyMatch(ProfileManager::inProgress))throw new IllegalArgumentException("Profile preparation is in progress; apply after it finishes");
        closeChild();Path snapshot=Path.of(profile.get("snapshot").getAsString());var launch=JsonParser.parseString(Files.readString(snapshot.resolve("runtime/launch.json"))).getAsJsonObject();
        try(var socket=new java.net.ServerSocket(0,0,InetAddress.getLoopbackAddress())){childPort=socket.getLocalPort();}
        var command=new ArrayList<String>();for(var arg:launch.getAsJsonArray("command"))command.add(arg.getAsString());
        int mainIndex=command.indexOf(launch.get("main_class").getAsString());if(mainIndex<0)throw new IOException("Unsupported prepared launch command");
        command.addAll(mainIndex,List.of("-Xmx"+profile.get("heap_mib").getAsInt()+"m","-Dwge.preview.port="+childPort,"-Dwge.preview.site="+site,"-Dwge.preview.fingerprint="+profile.get("fingerprint").getAsString()));
        Path run=snapshot.resolve("run");Files.createDirectories(run);for(String dir:ROOTS){if(dir.equals("mods"))continue;Path input=snapshot.resolve(dir);if(Files.isDirectory(input))copyIfMissing(input,run.resolve(dir));}
        Path packs=snapshot.resolve("datapacks");if(Files.isDirectory(packs))copyIfMissing(packs,run.resolve("world/datapacks"));
        Path argumentFile=snapshot.resolve("runtime/start.args");
        Files.writeString(argumentFile,command.subList(1,command.size()).stream().map(ProfileManager::quoteArgument).collect(java.util.stream.Collectors.joining("\n")));
        var builder=new ProcessBuilder(command.get(0),"@"+argumentFile).directory(run.toFile()).redirectErrorStream(true).redirectOutput(snapshot.resolve("runtime.log").toFile());
        launch.getAsJsonObject("environment").entrySet().forEach(e->builder.environment().put(e.getKey(),e.getValue().getAsString()));
        child=builder.start();activeId=id;activeSnapshot=snapshot.toString();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);
        while(System.nanoTime()<deadline){
            if(operation!=null&&operation.has("cancelled")){closeChild();throw new CancellationException("Profile preparation cancelled");}
            String recent=logWindow(snapshot.resolve("runtime.log"));
            if(recent.contains("Exception in thread \"main\"")||recent.contains("Mod loading has failed")||recent.contains("Modpack preview initialization failed")){closeChild();throw new IOException("Mod profile failed: "+recent.lines().filter(line->line.contains("Caused by:")||line.contains("requires")||line.contains("NoClassDefFoundError")||line.contains("ModID:")||line.contains("Exception")||line.contains("ERROR")).limit(16).collect(java.util.stream.Collectors.joining("\n")));}
            if(!child.isAlive())throw new IOException("Mod profile failed: "+tail(snapshot.resolve("runtime.log")));try{activeCatalog=remote("catalog",null);return activeCatalog.deepCopy();}catch(Exception waiting){Thread.sleep(200);}}
        closeChild();throw new IOException("Mod profile startup timed out: "+tail(snapshot.resolve("runtime.log")));
    }
    private static void copyIfMissing(Path source,Path target)throws IOException {if(!Files.exists(target))copyTree(source,target,new long[]{0,0},new JsonObject());}
    public JsonObject catalog(String id)throws Exception {if(id.equals("vanilla"))return PreviewServer.vanillaCatalog();start(manifest(id));return remote("catalog",null);}
    public boolean owns(JsonObject request){return !"vanilla".equals(PreviewServer.profile(request))||request.has("session")&&remoteSessions.containsKey(request.get("session").getAsString());}
    public JsonObject forward(String endpoint,JsonObject request)throws Exception {
        String id=PreviewServer.profile(request);if(id.equals("vanilla")&&request.has("session"))id=remoteSessions.get(request.get("session").getAsString());
        if(id==null)throw new IllegalArgumentException("Session expired; apply the configuration again");
        start(manifest(id));var result=remote(endpoint,request);
        if(endpoint.equals("session")){remoteSessions.put(result.get("session").getAsString(),id);synchronized(remoteSessions){while(remoteSessions.size()>128)remoteSessions.remove(remoteSessions.keySet().iterator().next());}}return result;
    }
    private JsonObject remote(String endpoint,JsonObject body)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+childPort+"/api/"+endpoint)).timeout(Duration.ofSeconds(body==null?2:120));
        if(body!=null)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());var value=JsonParser.parseString(response.body()).getAsJsonObject();
        if(response.statusCode()!=200){if(value.has("cancelled"))throw new CancellationException();throw new IllegalArgumentException(value.has("error")?value.get("error").getAsString():"Mod runtime request failed");}return value;
    }
    static String hash(Path file,String algorithm)throws IOException {try{var digest=MessageDigest.getInstance(algorithm);try(var stream=Files.newInputStream(file)){byte[] buffer=new byte[65536];for(int n;(n=stream.read(buffer))>=0;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
    private static String quoteArgument(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private static String logWindow(Path path)throws IOException {
        if(!Files.isRegularFile(path))return "";
        try(var file=new java.io.RandomAccessFile(path.toFile(),"r")){long length=file.length(),start=Math.max(0,length-65536);file.seek(start);byte[] bytes=new byte[(int)(length-start)];file.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
    }
    private static String tail(Path path)throws IOException {var lines=logWindow(path).lines().toList();return String.join("\n",lines.subList(Math.max(0,lines.size()-14),lines.size()));}
    private synchronized void closeChild(){if(child!=null){child.descendants().forEach(ProcessHandle::destroy);child.destroy();try{if(!child.waitFor(5,TimeUnit.SECONDS))child.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}child=null;activeId=null;}}
    public synchronized void close(){imports.shutdownNow();if(preparation!=null)preparation.destroy();closeChild();}
}
