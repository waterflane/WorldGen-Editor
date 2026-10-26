package org.wodichka.worldgen_editor.world;

/** Large-scale coastal shape, independent of the saved geometric mask and final cave density. */
public final class NaturalCoast {
    public static final double SURFACE_BIAS=-.025;
    private NaturalCoast(){}
    public static boolean active(GenerationContext context){return context!=null&&context.generationVersion()>=4&&context.config().hasCoasts()&&context.config().enabled();}
    public static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static double progress(CoastField.Sample sample){
        return sample.terrainProgress();
    }
    public static double influence(CoastField.Sample sample){return sample==null?0:1-smooth(Math.abs(progress(sample)));}
    public static double continents(double original,CoastField.Sample sample){
        double p=progress(sample),q=smooth(Math.abs(p));
        // Narrow islands need continental land before the much broader terrain recovery ends.
        double target=p>=0?-.17+.35*smooth(p*4):-.17-.65*q;
        return original+(target-original)*(1-q);
    }
    /** A depth offset changes the terrain before factor/base noise, caves and aquifers are evaluated. */
    public static double depthCorrection(double originalAtSea,CoastField.Sample sample,double ridges){
        double p=progress(sample),q=smooth(Math.abs(p)),weight=.88*(1-q);
        if(weight<=0)return 0;
        double target=p>=0?SURFACE_BIAS+.08*smooth(sample.bandProgress())+.4*smooth(sample.recoveryProgress()):SURFACE_BIAS-.34*q;
        // Keep a real valley in the large-scale river network, including its mouth.
        double valley=1-smooth(Math.abs(ridges)/.12);
        double river=.035*valley*(1-smooth(Math.abs(p)));
        target+=sample.variation()*.025*smooth(Math.min(1,Math.abs(p)*4))-river;
        // Never raise the offshore seabed or the river network. Support wet continental
        // foundations only inside drawn land, before base 3D noise and caves are composed.
        double delta=target-originalAtSea,h=Math.max(.012-Math.abs(delta),0);
        double landSupport=p>0?Math.max(0,Math.min(target,0)-originalAtSea)*smooth(sample.bandProgress())*(1-valley)*(1-valley):0;
        return (Math.min(0,delta)-h*h/.048+landSupport)*weight;
    }
    public static double approximateHeight(double depthAtSea,int seaLevel){return seaLevel+(depthAtSea-SURFACE_BIAS)*128;}
}
