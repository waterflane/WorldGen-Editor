package org.wodichka.worldgen_editor.config;

import com.google.gson.*;

/** Immutable local-space painted contour. Entry radii/rotation transform it without changing its data. */
public record IslandOutline(double radiusX,double radiusZ,PaintedMap mask) {
    public static IslandOutline parse(JsonElement input)throws IslandConfigException {
        try {
            var value=input.getAsJsonObject();
            double x=radius(value.get("radius_x")),z=radius(value.get("radius_z"));
            var mask=PaintedMap.parse(value.get("mask"));var bounds=mask.bounds();
            if(bounds==null)throw new IllegalArgumentException("outline must contain painted samples");
            if(bounds.minX() < -x || bounds.maxX()>x || bounds.minZ() < -z || bounds.maxZ()>z)
                throw new IllegalArgumentException("outline samples exceed reference radii");
            return new IslandOutline(x,z,mask);
        }catch(RuntimeException error){throw new IslandConfigException("Invalid island outline: "+error.getMessage(),error);}
    }
    private static double radius(JsonElement value) {
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("outline reference radii must be numbers");
        double result=value.getAsDouble();
        if(!Double.isFinite(result)||result<.001||result>60000000)throw new IllegalArgumentException("outline reference radii outside supported range");
        return result;
    }
    public JsonObject toJson() {
        var value=new JsonObject();value.addProperty("radius_x",radiusX);value.addProperty("radius_z",radiusZ);value.add("mask",mask.toJson());return value;
    }
}
