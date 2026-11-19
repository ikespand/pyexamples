package com.example.mrz;

/** Maps image pixels to a center-cropped preview without changing orientation. */
public final class PreviewTransform {
    private PreviewTransform() {}
    public static float[] map(float x,float y,float width,float height,int imageWidth,int imageHeight,int viewWidth,int viewHeight) {
        return map(x,y,width,height,imageWidth,imageHeight,viewWidth,viewHeight,false);
    }
    public static float[] map(float x,float y,float width,float height,int imageWidth,int imageHeight,int viewWidth,int viewHeight,boolean fitCenter) {
        if(imageWidth<=0||imageHeight<=0||viewWidth<=0||viewHeight<=0)throw new IllegalArgumentException("Dimensions must be positive");
        float sx=viewWidth/(float)imageWidth,sy=viewHeight/(float)imageHeight;
        float scale=fitCenter?Math.min(sx,sy):Math.max(sx,sy);
        float dx=(viewWidth-imageWidth*scale)/2,dy=(viewHeight-imageHeight*scale)/2;
        return new float[]{dx+x*scale,dy+y*scale,dx+(x+width)*scale,dy+(y+height)*scale};
    }
}
