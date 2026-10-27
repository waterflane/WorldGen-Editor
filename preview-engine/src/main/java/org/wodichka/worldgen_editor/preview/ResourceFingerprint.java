package org.wodichka.worldgen_editor.preview;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Effective resource identity excludes ephemeral loader pack names, retaining stack order. */
public final class ResourceFingerprint {
    public static void updateStack(MessageDigest digest,String id,List<byte[]> stack){
        bytes(digest,id.getBytes(StandardCharsets.UTF_8));integer(digest,stack.size());
        for(var content:stack)bytes(digest,content);
    }
    private static void bytes(MessageDigest digest,byte[] value){integer(digest,value.length);digest.update(value);}
    private static void integer(MessageDigest digest,int value){for(int shift=24;shift>=0;shift-=8)digest.update((byte)(value>>>shift));}
    private ResourceFingerprint(){}
}
