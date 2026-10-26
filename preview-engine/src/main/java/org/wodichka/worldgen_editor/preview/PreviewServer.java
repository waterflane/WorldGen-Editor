package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.wodichka.worldgen_editor.world.GenerationContext;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Loopback-only, bounded, on-demand map service. */
public final class PreviewServer {
    private static final Gson JSON = new Gson();
    private static final Map<String, Session> SESSIONS = new LinkedHashMap<>(4,.75f,true);
    private static String RESOURCES;
    private static final int WORKERS=Math.max(1,Math.min(6,Runtime.getRuntime().availableProcessors()-2));
    private static final Semaphore GENERATION_SLOTS=new Semaphore(WORKERS,true);
    private record Session(PreviewWorld world, ConcurrentMap<String, AtomicLong> clients, Map<String, JsonObject> tiles) {}

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        PreviewWorld.bootstrap();
        RESOURCES=PreviewWorld.resourceFingerprint();
        Path site = Path.of(args.length == 0 ? "../WorldGen Editor Preview" : args[0]).toAbsolutePath().normalize();
        int port = Integer.getInteger("wge.preview.port", 8765);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),16);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/info", exchange -> handle(exchange, () -> {
            JsonObject value = new JsonObject(); value.addProperty("protocol",2);value.addProperty("minecraft","1.21.1");
            value.addProperty("generation_version",3);value.add("supported_generation_versions",JsonParser.parseString("[1,2,3]"));value.add("capabilities",JsonParser.parseString("[\"painted_map_v1\",\"vanilla_wge_only\",\"ocean_temperature_v1\",\"island_outline_v1\"]"));value.addProperty("resources",RESOURCES);
            value.addProperty("workers",WORKERS);
            value.addProperty("sampling_engine",Boolean.getBoolean("wge.preview.referenceSampler")?"reference":"column");
            value.addProperty("heap_used_mib",(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);return value;
        }));
        server.createContext("/api/session", exchange -> handle(exchange, () -> session(read(exchange))));
        server.createContext("/api/cancel", exchange -> handle(exchange, () -> {
            JsonObject request=read(exchange);Session session;
            synchronized(PreviewServer.class){session=SESSIONS.get(request.get("session").getAsString());}
            if(session!=null){String client=request.get("client").getAsString();session.clients.computeIfAbsent(client,ignored->new AtomicLong())
                    .accumulateAndGet(request.get("request").getAsLong(),Math::max);}
            return new JsonObject();
        }));
        server.createContext("/api/tile", exchange -> handle(exchange, () -> tile(read(exchange))));
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                Path file = site.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
                if (!file.startsWith(site) || !Files.isRegularFile(file)) { exchange.sendResponseHeaders(404,-1); return; }
                String mime = file.toString().endsWith(".js") ? "text/javascript" : file.toString().endsWith(".css") ? "text/css" : "text/html";
                exchange.getResponseHeaders().set("Content-Type",mime+"; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control","no-cache");
                byte[] bytes=Files.readAllBytes(file);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            } finally {exchange.close();}
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.printf("WGE preview ready: http://127.0.0.1:%d/ (startup %.2fs, max heap %d MiB)%n",server.getAddress().getPort(),
                (System.nanoTime()-start)/1e9,Runtime.getRuntime().maxMemory()/1048576);
    }

    private static synchronized JsonObject session(JsonObject request) throws Exception {
        String text = request.get("seed").getAsString();
        long seed = WorldOptions.parseSeed(text).orElseThrow(() -> new IllegalArgumentException("Resolve the random seed before opening a session"));
        if(request.has("generation_version") && (!request.get("generation_version").isJsonPrimitive()
                || !request.getAsJsonPrimitive("generation_version").isNumber()))
            throw new IllegalArgumentException("generation_version must be a number");
        int version=request.has("generation_version") ? integer(request,"generation_version",1,3) : 2;
        JsonElement config=request.get("config");
        String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((RESOURCES+":"+version+":"+seed+":"+config).getBytes(StandardCharsets.UTF_8)));
        if (!SESSIONS.containsKey(id)) {
            var cache=new LinkedHashMap<String,JsonObject>(32,.75f,true) {
                protected boolean removeEldestEntry(Map.Entry<String,JsonObject> value) { return size()>512; }
            };
            SESSIONS.put(id,new Session(new PreviewWorld(config,seed,version),new ConcurrentHashMap<>(),cache));
            while(SESSIONS.size()>2) SESSIONS.remove(SESSIONS.keySet().iterator().next());
        }
        JsonObject value=new JsonObject();value.addProperty("session",id);value.addProperty("seed",Long.toString(seed));
        value.addProperty("resources",RESOURCES);value.addProperty("generation_version",version);
        value.addProperty("workers",WORKERS);
        String client=request.has("client")?request.get("client").getAsString():"default";
        value.addProperty("request_base",SESSIONS.get(id).clients.computeIfAbsent(client,ignored->new AtomicLong()).get());return value;
    }

    private static JsonObject tile(JsonObject request) {
        Session session;
        synchronized(PreviewServer.class) {session=SESSIONS.get(request.get("session").getAsString());}
        if(session==null) throw new IllegalArgumentException("Session expired; apply the configuration again");
        String client=request.has("client")?request.get("client").getAsString():"default";
        AtomicLong latest=session.clients.computeIfAbsent(client,ignored->new AtomicLong());
        long serial=request.get("request").getAsLong();latest.accumulateAndGet(serial,Math::max);
        int x=integer(request,"x",-30000000,30000000),z=integer(request,"z",-30000000,30000000);
        int step=integer(request,"step",1,4096),size=integer(request,"size",1,32);
        if((long)x+(long)(size-1)*step>30000000 || (long)z+(long)(size-1)*step>30000000)throw new IllegalArgumentException("Tile exceeds world bounds");
        boolean surface="surface".equals(request.get("height_mode").getAsString());
        if(request.has("include_heights") && (!request.get("include_heights").isJsonPrimitive()
                || !request.getAsJsonPrimitive("include_heights").isBoolean()))throw new IllegalArgumentException("include_heights must be a boolean");
        boolean includeHeights=!request.has("include_heights") || request.get("include_heights").getAsBoolean();
        int fixedY=surface ? 0 : integer(request,"y",-64,319);
        String key=x+":"+z+":"+step+":"+size+":"+surface+":"+fixedY+":"+includeHeights;
        if(serial!=latest.get())throw new CancellationException();
        synchronized(session){JsonObject cached=session.tiles.get(key);if(cached!=null)return cached;}
        boolean acquired=false;
        try {
            while(!(acquired=GENERATION_SLOTS.tryAcquire(25,TimeUnit.MILLISECONDS))){if(serial!=latest.get())throw new CancellationException();}
            if(serial!=latest.get())throw new CancellationException();
            synchronized(session){JsonObject cached=session.tiles.get(key);if(cached!=null)return cached;}
            long started=System.nanoTime();var palette=new ArrayList<String>();var indices=new int[size*size];var heights=new int[size*size];
            for(int row=0;row<size;row++) {
                for(int col=0;col<size;col++) {
                    if(serial!=latest.get())throw new CancellationException();
                    int bx=x+col*step,bz=z+row*step,i=row*size+col;
                    String id=surface && !includeHeights ? session.world.heightIndependentBiomeId(bx,bz) : null;
                    int y=id!=null ? Short.MIN_VALUE : surface ? session.world.surfaceY(bx,bz,step) : fixedY;
                    heights[i]=y;if(id==null)id=session.world.biomeId(bx,y,bz);int index=palette.indexOf(id);
                    if(index<0){index=palette.size();palette.add(id);}indices[i]=index;
                }
            }
            JsonObject value=new JsonObject();value.add("palette",JSON.toJsonTree(palette));value.add("biomes",JSON.toJsonTree(indices));
            value.add("heights",JSON.toJsonTree(heights));value.addProperty("elapsed_ms",(System.nanoTime()-started)/1000000);
            if(serial!=latest.get())throw new CancellationException();
            synchronized(session){session.tiles.put(key,value);}return value;
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException();}
        finally {if(acquired)GENERATION_SLOTS.release();}
    }

    private static int integer(JsonObject request,String key,int min,int max) {
        double value=request.get(key).getAsDouble();if(!Double.isFinite(value)||value!=Math.rint(value)||value<min||value>max)
            throw new IllegalArgumentException(key+" must be an integer between "+min+" and "+max);return (int)value;
    }
    private static JsonObject read(HttpExchange exchange) throws Exception {
        if(!exchange.getRequestMethod().equals("POST"))throw new IllegalArgumentException("POST required");
        String origin=exchange.getRequestHeaders().getFirst("Origin");
        if(origin!=null && !origin.equals("http://"+exchange.getRequestHeaders().getFirst("Host")))throw new IllegalArgumentException("Same-origin requests required");
        byte[] bytes=exchange.getRequestBody().readNBytes(33554433);if(bytes.length>33554432)throw new IllegalArgumentException("Configuration too large");
        return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private interface Action {JsonObject run() throws Exception;}
    private static void handle(HttpExchange exchange,Action action) throws java.io.IOException {
        int status=200;JsonObject value;
        try {value=action.run();}
        catch(CancellationException cancelled){status=409;value=new JsonObject();value.addProperty("cancelled",true);}
        catch(Exception error){status=400;value=new JsonObject();value.addProperty("error",error.getMessage()==null ? error.toString() : error.getMessage());}
        byte[] bytes=JSON.toJson(value).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(status,bytes.length);
        try(var out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
    }
}
