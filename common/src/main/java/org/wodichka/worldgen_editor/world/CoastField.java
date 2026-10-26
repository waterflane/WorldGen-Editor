package org.wodichka.worldgen_editor.world;

import org.wodichka.worldgen_editor.config.CoastProfile;
import java.util.*;
import java.util.function.BiFunction;

/** World-space contour distances. Geometry tiles are shared, bounded and built only on demand. */
public final class CoastField {
    static final double WATERLINE=.54;
    static final int TILE=64, STEP=4, CACHE_TILES=512;
    record Bounds(double minX,double minZ,double maxX,double maxZ) {}
    public record Sample(double distance,IslandMask.SourceInfo source) {
        public CoastProfile profile(){return source.coast();}
        double value(double raw) {
            double b=profile().beachWidthBlocks(),s=profile().shallowOceanWidthBlocks();
            if(distance>=0) {
                if(b==0)return Math.max(.72,raw);
                if(distance<=b)return WATERLINE+.04*distance/b;
                double t=Math.min(1,(distance-b)/64);t=t*t*(3-2*t);
                return .58+(Math.max(.58,raw)-.58)*t;
            }
            double d=-distance;
            if(s>0&&d<=s)return WATERLINE-.46*d/s;
            return Math.min(raw,.079*Math.max(0,1-(d-s)/64));
        }
    }
    private record Segment(double ax,double az,double bx,double bz,IslandMask.SourceInfo source) {
        double distance(double x,double z){double dx=bx-ax,dz=bz-az,t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz)));return Math.hypot(x-ax-t*dx,z-az-t*dz);}
    }
    private static double band(Segment segment,boolean land){return land?segment.source.coast().beachWidthBlocks():segment.source.coast().shallowOceanWidthBlocks();}
    private record Candidate(int x,int z,double distance) {}
    private final BiFunction<Double,Double,IslandMask.SampleInfo> raw;
    private final List<Bounds> bounds;
    private final double landReach,waterReach;
    private static final ThreadLocal<java.util.function.BooleanSupplier> CANCELLATION=new ThreadLocal<>();
    public static void cancellationGuard(java.util.function.BooleanSupplier active){if(active==null)CANCELLATION.remove();else CANCELLATION.set(active);}
    private static void checkActive(){var active=CANCELLATION.get();if(active!=null&&!active.getAsBoolean())throw new java.util.concurrent.CancellationException();}
    private final Set<Long> emptyTiles=new LinkedHashSet<>();
    private final Map<Long,List<Segment>> tiles=new LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,List<Segment>> e){return size()>CACHE_TILES;}};
    private final Map<Long,Segment> hints=new LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,Segment> e){return size()>1024;}};
    CoastField(BiFunction<Double,Double,IslandMask.SampleInfo> raw,List<Bounds> bounds,List<CoastProfile> profiles) {
        this.raw=raw;this.bounds=List.copyOf(bounds);
        landReach=profiles.stream().mapToInt(CoastProfile::beachWidthBlocks).max().orElse(0)+64;
        waterReach=profiles.stream().mapToInt(CoastProfile::shallowOceanWidthBlocks).max().orElse(0)+64;
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static IslandMask.SourceInfo owner(IslandMask.SampleInfo sample){return sample.coastOwner()!=null?sample.coastOwner():sample.landSource()!=null?sample.landSource():sample.archipelagoSource();}
    Sample sample(double x,double z,IslandMask.SampleInfo original) {
        boolean land=original.value()>=WATERLINE;
        var own=owner(original);
        if(land&&(own==null||own.coast()==null))return null;
        double reach=land?landReach:waterReach,best=reach;
        long hintKey=key((int)Math.floor(x/TILE),(int)Math.floor(z/TILE));Segment nearest;
        synchronized(hints){nearest=hints.get(hintKey);}
        if(nearest!=null){double d=nearest.distance(x,z);if(d<=best&&d<=band(nearest,land)&&band(nearest,land)>0)best=Math.min(reach,d+1e-9);nearest=null;}
        Segment recovery=null;double recoveryDistance=reach;
        Set<Long> seen=new HashSet<>();List<Candidate> candidates=new ArrayList<>();
        for(var b:bounds) {
            int minX=(int)Math.floor(Math.max(b.minX,x-best)/TILE),maxX=(int)Math.floor(Math.min(b.maxX,x+best)/TILE);
            int minZ=(int)Math.floor(Math.max(b.minZ,z-best)/TILE),maxZ=(int)Math.floor(Math.min(b.maxZ,z+best)/TILE);
            for(int tz=minZ;tz<=maxZ;tz++)for(int tx=minX;tx<=maxX;tx++) {
                double dx=Math.max(0,Math.max(tx*TILE-x,x-(tx+1)*TILE)),dz=Math.max(0,Math.max(tz*TILE-z,z-(tz+1)*TILE));
                double distance=Math.hypot(dx,dz);if(distance<=best&&seen.add(key(tx,tz)))candidates.add(new Candidate(tx,tz,distance));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingInt(Candidate::z).thenComparingInt(Candidate::x));
        for(var candidate:candidates) {
            checkActive();
            if(candidate.distance>best)break;
            for(var segment:tile(candidate.x,candidate.z)) {
                double d=segment.distance(x,z),width=band(segment,land);
                if(width>0&&d<=width&&d<best){best=d;nearest=segment;}
                else if(d<=width+64&&d<recoveryDistance){recoveryDistance=d;recovery=segment;}
            }
        }
        if(nearest==null){nearest=recovery;best=recoveryDistance;}
        if(nearest!=null) {
            synchronized(hints){hints.put(hintKey,nearest);}
            var profile=nearest.source.coast();double limit=(land?profile.beachWidthBlocks():profile.shallowOceanWidthBlocks())+64;
            if(best<=limit)return new Sample(land?best:-Math.max(1e-9,best),nearest.source);
        }
        return own!=null&&own.coast()!=null?new Sample(land?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY,own):null;
    }
    private synchronized List<Segment> tile(int tx,int tz) {
        long key=key(tx,tz);if(emptyTiles.contains(key))return List.of();checkActive();var cached=tiles.get(key);if(cached!=null)return cached;
        int n=TILE/STEP;IslandMask.SampleInfo[] nodes=new IslandMask.SampleInfo[(n+1)*(n+1)];
        for(int z=0;z<=n;z++){checkActive();for(int x=0;x<=n;x++)nodes[z*(n+1)+x]=raw.apply((double)(tx*TILE+x*STEP),(double)(tz*TILE+z*STEP));}
        List<Segment> segments=new ArrayList<>();
        for(int z=0;z<n;z++)for(int x=0;x<n;x++) {
            var a=nodes[z*(n+1)+x];var b=nodes[z*(n+1)+x+1];var c=nodes[(z+1)*(n+1)+x+1];var d=nodes[(z+1)*(n+1)+x];
            double bx=tx*TILE+x*STEP,bz=tz*TILE+z*STEP;
            triangle(segments,bx,bz,a,bx+STEP,bz,b,bx+STEP,bz+STEP,c);
            triangle(segments,bx,bz,a,bx+STEP,bz+STEP,c,bx,bz+STEP,d);
        }
        var result=List.copyOf(segments);if(result.isEmpty()){emptyTiles.add(key);if(emptyTiles.size()>32768)emptyTiles.remove(emptyTiles.iterator().next());}else tiles.put(key,result);return result;
    }
    private static void triangle(List<Segment> output,double ax,double az,IslandMask.SampleInfo a,double bx,double bz,IslandMask.SampleInfo b,double cx,double cz,IslandMask.SampleInfo c) {
        boolean sa=a.value()>=WATERLINE,sb=b.value()>=WATERLINE,sc=c.value()>=WATERLINE;
        if(sa==sb&&sb==sc)return;
        double[] xs={ax,bx,cx},zs={az,bz,cz},values={a.value(),b.value(),c.value()},px=new double[2],pz=new double[2];int count=0;
        for(int i=0;i<3;i++){int j=(i+1)%3;if((values[i]>=WATERLINE)==(values[j]>=WATERLINE))continue;double t=(WATERLINE-values[i])/(values[j]-values[i]);px[count]=xs[i]+t*(xs[j]-xs[i]);pz[count++]=zs[i]+t*(zs[j]-zs[i]);}
        IslandMask.SampleInfo inside=sa?a:sb?b:c;var source=owner(inside);
        if(count==2&&source!=null&&source.coast()!=null&&Math.hypot(px[1]-px[0],pz[1]-pz[0])>1e-12)output.add(new Segment(px[0],pz[0],px[1],pz[1],source));
    }
}
