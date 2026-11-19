package com.example.mrz;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;
import org.json.JSONArray;

/** Maps image bounds to the live center-cropped preview or frozen fit-center frame. */
public final class DetectionOverlay extends View {
    private JSONArray boxes;private float[] polygon;private int imageWidth,imageHeight;private boolean fitCenter,pageDetected,readingPage;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator,pulseAnimator;private float sweep,pulse;
    public DetectionOverlay(Context context){
        super(context);paint.setColor(AppUi.SEA);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
        animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(1050);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value->{sweep=(float)value.getAnimatedValue();invalidate();});
        pulseAnimator=ValueAnimator.ofFloat(0f,1f);pulseAnimator.setDuration(420);pulseAnimator.setRepeatCount(1);pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(value->{pulse=(float)value.getAnimatedValue();invalidate();});
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    public void setFitCenter(boolean value){fitCenter=value;invalidate();}
    public void setPageDetected(boolean value){if(pageDetected==value)return;pageDetected=value;if(value){pulse=0;pulseAnimator.start();}else{pulseAnimator.cancel();pulse=0;}invalidate();}
    public void setReadingPage(boolean value){readingPage=value;invalidate();}
    public void setScanning(boolean value){if(value){if(!animator.isStarted())animator.start();}else{animator.cancel();sweep=0;invalidate();}}
    public void update(JSONArray b,int w,int h){boxes=b;polygon=null;imageWidth=w;imageHeight=h;invalidate();}
    public void updatePolygon(float[] points,int w,int h){polygon=points==null?null:points.clone();boxes=null;imageWidth=w;imageHeight=h;invalidate();}
    @Override protected void onDetachedFromWindow(){animator.cancel();pulseAnimator.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(imageWidth<=0||imageHeight<=0||getWidth()<=0||getHeight()<=0)return;
        if(polygon!=null&&polygon.length>=8){
            Path path=new Path();
            for(int i=0;i<polygon.length/2;i++){
                float[] mapped=PreviewTransform.map(polygon[2*i],polygon[2*i+1],0,0,imageWidth,imageHeight,getWidth(),getHeight(),fitCenter);
                if(i==0)path.moveTo(mapped[0],mapped[1]);else path.lineTo(mapped[0],mapped[1]);
            }
            path.close();
            paint.setStyle(Paint.Style.FILL);paint.setColor(pageDetected?0x182f817b:0x10000000);canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(pageDetected?AppUi.SEA:AppUi.CORAL);paint.setStrokeWidth(dp(pageDetected?2.2f:1.5f));canvas.drawPath(path,paint);
            if(pageDetected){
                for(int i=0;i<polygon.length/2;i++){
                    float[] point=PreviewTransform.map(polygon[2*i],polygon[2*i+1],0,0,imageWidth,imageHeight,getWidth(),getHeight(),fitCenter);
                    paint.setStyle(Paint.Style.FILL);paint.setColor(AppUi.CORAL);canvas.drawCircle(point[0],point[1],dp(4+2*pulse),paint);
                    paint.setColor(AppUi.CARD);canvas.drawCircle(point[0],point[1],dp(1.6f),paint);
                }
                RectF bounds=new RectF();path.computeBounds(bounds,true);
                drawBadge(canvas,bounds.left+dp(10),bounds.top+dp(10),"PAGE FOUND",AppUi.INK);
            }
            return;
        }
        if(readingPage&&boxes==null){drawReadingBand(canvas);return;}
        if(boxes==null)return;
        for(int i=0;i<boxes.length();i++){
            JSONArray r=boxes.optJSONArray(i);if(r==null)continue;
            float[] mapped=PreviewTransform.map((float)r.optDouble(0),(float)r.optDouble(1),(float)r.optDouble(2),(float)r.optDouble(3),imageWidth,imageHeight,getWidth(),getHeight(),fitCenter);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(AppUi.SEA);paint.setStrokeWidth(dp(2));canvas.drawRoundRect(mapped[0],mapped[1],mapped[2],mapped[3],dp(10),dp(10),paint);
            if(animator.isStarted()){
                float y=mapped[1]+(mapped[3]-mapped[1])*sweep;
                paint.setStyle(Paint.Style.FILL);paint.setColor(0x33bd6649);canvas.drawRect(mapped[0],Math.max(mapped[1],y-dp(8)),mapped[2],y,paint);
                paint.setColor(AppUi.CORAL);paint.setStrokeWidth(dp(2));canvas.drawLine(mapped[0],y,mapped[2],y,paint);
            }
        }
    }

    private void drawReadingBand(Canvas canvas){
        float[] bounds=PreviewTransform.map(imageWidth*.045f,imageHeight*.70f,imageWidth*.91f,imageHeight*.26f,
                imageWidth,imageHeight,getWidth(),getHeight(),true);
        RectF band=new RectF(bounds[0],bounds[1],bounds[2],bounds[3]);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x22bd6649);canvas.drawRoundRect(band,dp(12),dp(12),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(AppUi.CORAL);
        canvas.drawRoundRect(band,dp(12),dp(12),paint);
        drawBadge(canvas,band.left+dp(8),band.top+dp(8),"READING MRZ",AppUi.INK);
        float y=band.top+dp(34)+(band.height()-dp(40))*sweep;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(AppUi.CARD);
        canvas.drawLine(band.left+dp(8),y,band.right-dp(8),y,paint);
        paint.setStrokeWidth(dp(1.5f));paint.setColor(AppUi.CORAL);
        canvas.drawLine(band.left+dp(8),y,band.right-dp(8),y,paint);
    }

    private void drawBadge(Canvas canvas,float x,float y,String title,int color){
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(dp(10));
        float width=paint.measureText(title)+dp(18),height=dp(25);
        paint.setColor(0xe8fffdf8);canvas.drawRoundRect(x,y,x+width,y+height,dp(12),dp(12),paint);
        paint.setColor(color);canvas.drawText(title,x+dp(9),y+dp(17),paint);
    }
}
