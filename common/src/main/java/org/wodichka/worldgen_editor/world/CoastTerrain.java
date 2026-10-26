package org.wodichka.worldgen_editor.world;

/** Broad, continuous height bounds, leaving the delegate's relief between them. */
public final class CoastTerrain {
    private CoastTerrain(){}
    public record Envelope(double floor,double ceiling){}
    public static Envelope envelope(CoastField.Sample sample,int seaLevel){
        double d=sample.distance(),b=sample.profile().beachWidthBlocks(),s=sample.profile().shallowOceanWidthBlocks(),n=sample.variation();
        if(!Double.isFinite(d))return null;
        if(d>=0){
            double endFloor=b>0?.75+.5*n:0,endCeiling=b>0?8+3*n:0;
            if(b>0&&d<=b){double t=d/b;return new Envelope(seaLevel+(.75+.5*n)*t,seaLevel+8*t+3*n*t*(.25+.75*t));}
            double delta=d-b,t=smooth(delta/CoastField.RECOVERY);
            double floor=seaLevel+lerp(endFloor,-160,t);
            double ceiling=seaLevel+endCeiling+0.5*delta+delta*delta/192;
            return new Envelope(floor,ceiling);
        }
        double depth=-d;
        if(s>0&&depth<=s){
            double t=depth/s,q=smooth(t),variation=6*n*q;
            double ceiling=seaLevel-36.8*q+variation;
            return new Envelope(ceiling-8*q,ceiling);
        }
        double t=smooth((depth-s)/CoastField.RECOVERY);
        double startCeiling=s>0?-36.8+6*n:0,startFloor=s>0?-44.8+6*n:0;
        return new Envelope(seaLevel+lerp(startFloor,-160,t),seaLevel+lerp(startCeiling,-43.2,t));
    }
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static double shape(double original,double y,Envelope envelope){
        double floorDensity=(envelope.floor-y)/8;
        double layer=smooth((y-envelope.floor+24)/16);
        double raised=original+(Math.max(original,floorDensity)-original)*layer;
        return Math.min(raised,(envelope.ceiling-y)/8);
    }
}
