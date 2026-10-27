package org.wodichka.worldgen_editor.config;

public enum IslandTemperature {
    STANDARD("standard"), NATURAL("natural"), COLD("cold"), TEMPERATE("temperate"), WARM("warm"),
    MUSHROOM("minecraft:mushroom"),
    BOP_MUSHROOM("biomesoplenty:mushroom"), BOP_MAGICAL("biomesoplenty:magical"),
    BOP_HAUNTED("biomesoplenty:haunted"), BOP_VOLCANIC("biomesoplenty:volcanic"), BOP_WASTELAND("biomesoplenty:wasteland"),
    TERRALITH_MUSHROOM("terralith:mushroom"), TERRALITH_MYSTICAL("terralith:mystical"), TERRALITH_VOLCANIC("terralith:volcanic");

    private final String id;
    IslandTemperature(String id) { this.id=id; }
    public String id() { return id; }
    public boolean special() { return id.indexOf(':')>=0; }
    public boolean modern() { return this==NATURAL || special(); }
    public static IslandTemperature parse(String value) {
        value=value.toLowerCase(java.util.Locale.ROOT);
        value=switch(value){case "standart","vanilla","auto"->"standard";case "normal"->"temperate";case "hot"->"warm";default->value;};
        for(var temperature:values())if(temperature.id.equals(value))return temperature;
        throw new IllegalArgumentException("Unknown temperature: "+value);
    }
}
