package com.example.mrz;

/** Requires two overlapping candidate regions before stopping the camera. */
public final class DetectionStability {
    private float[] previous;
    public synchronized void reset(){previous=null;}
    public synchronized boolean accept(float x,float y,float width,float height){
        float[] current={x,y,width,height};
        if(previous==null){previous=current;return false;}
        float left=Math.max(x,previous[0]),top=Math.max(y,previous[1]);
        float right=Math.min(x+width,previous[0]+previous[2]),bottom=Math.min(y+height,previous[1]+previous[3]);
        float overlap=Math.max(0,right-left)*Math.max(0,bottom-top);
        float union=width*height+previous[2]*previous[3]-overlap;
        previous=current;
        return union>0&&overlap/union>=0.6f;
    }
}
