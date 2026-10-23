package org.wodichka.worldgen_editor.config;

import com.google.gson.*;
import java.util.*;

/** Immutable sparse 4-block node grid. Missing nodes leave the procedural mask unchanged. */
public final class PaintedMap {
    public static final int STEP=4, SIZE=64, MAX_TILES=4096;
    private final Map<Long,int[]> tiles;
    private PaintedMap(Map<Long,int[]> tiles) { this.tiles=Collections.unmodifiableMap(tiles); }
    private static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    private static int integer(JsonElement value,int min,int max) {
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("painted_map requires integers");
        double n=value.getAsDouble();
        if(!Double.isFinite(n)||n!=Math.rint(n)||n<min||n>max)throw new IllegalArgumentException("painted_map integer outside supported range");
        return (int)n;
    }
    public static PaintedMap parse(JsonElement json) throws IslandConfigException {
        try {
            JsonObject root=json.getAsJsonObject();
            if(integer(root.get("version"),1,1)!=1||integer(root.get("step"),STEP,STEP)!=STEP||integer(root.get("size"),SIZE,SIZE)!=SIZE)throw new IllegalArgumentException("Unsupported painted_map format");
            JsonArray input=root.getAsJsonArray("tiles");
            if(input==null||input.size()>MAX_TILES)throw new IllegalArgumentException("painted_map supports up to "+MAX_TILES+" tiles");
            Map<Long,int[]> tiles=new LinkedHashMap<>();
            for(JsonElement element:input) {
                JsonObject tile=element.getAsJsonObject();
                int x=integer(tile.get("x"),-117188,117187),z=integer(tile.get("z"),-117188,117187);
                JsonArray runs=tile.getAsJsonArray("runs");
                if(runs==null||runs.isEmpty()||runs.size()%3!=0||runs.size()>SIZE*SIZE*3)throw new IllegalArgumentException("Invalid painted_map runs");
                int[] data=new int[SIZE*SIZE];int offset=0;
                for(int i=0;i<runs.size();i+=3) {
                    int length=integer(runs.get(i),1,SIZE*SIZE),alpha=integer(runs.get(i+1),0,65535),value=integer(runs.get(i+2),0,65535);
                    if(length>data.length-offset)throw new IllegalArgumentException("painted_map run overflow");
                    Arrays.fill(data,offset,offset+length,(alpha<<16)|value);offset+=length;
                }
                if(offset!=data.length||tiles.putIfAbsent(key(x,z),data)!=null)throw new IllegalArgumentException("Incomplete or duplicate painted_map tile");
            }
            return new PaintedMap(tiles);
        } catch(RuntimeException error) { throw new IslandConfigException("Invalid painted_map: "+error.getMessage(),error); }
    }
    private int node(int x,int z) {
        int[] data=tiles.get(key(Math.floorDiv(x,SIZE),Math.floorDiv(z,SIZE)));
        return data==null?0:data[Math.floorMod(z,SIZE)*SIZE+Math.floorMod(x,SIZE)];
    }
    public double apply(double base,double x,double z) {
        double gx=x/STEP,gz=z/STEP;int ix=(int)Math.floor(gx),iz=(int)Math.floor(gz);
        double fx=gx-ix,fz=gz-iz,result=0;
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
            int packed=node(ix+dx,iz+dz);double alpha=(packed>>>16)/65535.0,value=(packed&65535)/65535.0;
            result+=(dx==0?1-fx:fx)*(dz==0?1-fz:fz)*(base+(value-base)*alpha);
        }
        return result;
    }
    public JsonObject toJson() {
        JsonObject root=new JsonObject();root.addProperty("version",1);root.addProperty("step",STEP);root.addProperty("size",SIZE);
        JsonArray values=new JsonArray();
        tiles.forEach((key,data)->{
            JsonObject tile=new JsonObject();tile.addProperty("x",(int)(key>>32));tile.addProperty("z",(int)(long)key);
            JsonArray runs=new JsonArray();int start=0;
            while(start<data.length){int end=start+1;while(end<data.length&&data[end]==data[start])end++;
                runs.add(end-start);runs.add(data[start]>>>16);runs.add(data[start]&65535);start=end;}
            tile.add("runs",runs);values.add(tile);
        });root.add("tiles",values);return root;
    }
}
