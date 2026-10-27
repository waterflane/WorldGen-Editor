package org.wodichka.worldgen_editor.preview;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ResourceFingerprintTest {
    private static String hash(String id,String... contents)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        ResourceFingerprint.updateStack(digest,id,Arrays.stream(contents).map(s->s.getBytes(StandardCharsets.UTF_8)).toList());
        return HexFormat.of().formatHex(digest.digest());
    }
    static void run()throws Exception {
        var before=hash("minecraft:tags/worldgen/biome/example.json","base","override");
        GenerationContextTest.check(before.equals(hash("minecraft:tags/worldgen/biome/example.json","base","override")),"Resource fingerprint changed without effective changes");
        GenerationContextTest.check(!before.equals(hash("minecraft:tags/worldgen/biome/example.json","override","base")),"Resource order was ignored");
        GenerationContextTest.check(!before.equals(hash("minecraft:tags/worldgen/biome/example.json","base","changed")),"Resource changes were ignored");
        GenerationContextTest.check(!hash("example:a","ab","c").equals(hash("example:a","a","bc")),"Resource boundaries were ambiguous");
        GenerationContextTest.check(!hash("example:a","same").equals(hash("example:b","same")),"Resource IDs were ignored");
        System.out.println("Effective resource fingerprint identity, order and boundaries passed");
    }
}
