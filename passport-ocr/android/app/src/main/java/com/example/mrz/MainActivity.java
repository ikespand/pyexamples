package com.example.mrz;

import android.Manifest;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.camera2.interop.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.atomic.AtomicInteger;
import org.opencv.android.OpenCVLoader;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PreviewView preview; private ImageView frozenView; private Bitmap frozenBitmap; private DetectionOverlay overlay; private TextView status; private LinearLayout results; private Button chooseImage,scanAgain,saveButton; private ScrollView resultScroll; private String shareText=""; private JSONObject savableFields; private String savedScanId; private final AtomicInteger generation=new AtomicInteger(); private final DetectionStability stability=new DetectionStability(); private ProcessCameraProvider cameraProvider; private volatile boolean active=true; private long lastFrame=0;private volatile long firstPageSeenAt=0;private CandidateScanner pageScanner;private int stablePageFrames;
    private long savableCapturedAt;
    private boolean savableFromPhoto;
    private JSONObject savableTiming;

    @Override public void onCreate(Bundle state){super.onCreate(state);
        if(!OpenCVLoader.initLocal()){finish();return;}
        if(getIntent().getBooleanExtra("benchmark",false)&&(getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0){runBenchmark();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(18),dp(20),dp(16));root.setBackgroundColor(AppUi.PAPER);
        AppUi.systemBars(this);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=label("‹",30,AppUi.INK,false);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setFocusable(true);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout headerText=new LinearLayout(this);headerText.setOrientation(LinearLayout.VERTICAL);headerText.setPadding(dp(8),0,0,0);
        headerText.addView(label("Scan passport",20,AppUi.INK,true));headerText.addView(label("PAGE MRZ  ·  ON DEVICE",12,AppUi.SEA,true));header.addView(headerText);root.addView(header);
        TextView hint=label("Hold the passport landscape with the MRZ at the bottom. Keep the whole page on a plain, contrasting surface; hands are okay if they do not cover the printed lines.",14,AppUi.BODY,false);hint.setLineSpacing(dp(3),1f);hint.setPadding(0,dp(14),0,dp(10));root.addView(hint);
        FrameLayout cameraBox=new FrameLayout(this);cameraBox.setBackground(round(AppUi.INK,22));cameraBox.setClipToOutline(true);
        preview=new PreviewView(this);frozenView=new ImageView(this);frozenView.setScaleType(ImageView.ScaleType.FIT_CENTER);frozenView.setVisibility(View.GONE);overlay=new DetectionOverlay(this);
        cameraBox.addView(preview,new FrameLayout.LayoutParams(-1,-1));cameraBox.addView(frozenView,new FrameLayout.LayoutParams(-1,-1));cameraBox.addView(overlay,new FrameLayout.LayoutParams(-1,-1));root.addView(cameraBox,new LinearLayout.LayoutParams(-1,0,4));
        status=label("Starting camera…",14,AppUi.INK,true);status.setGravity(Gravity.CENTER_VERTICAL);status.setMinHeight(dp(48));status.setPadding(dp(14),dp(10),dp(14),dp(10));status.setBackground(round(AppUi.SEA_PALE,14));LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);statusParams.setMargins(0,dp(10),0,dp(10));root.addView(status,statusParams);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);results.setPadding(dp(18),dp(16),dp(18),dp(16));results.setBackground(AppUi.outlined(this,AppUi.CARD,AppUi.LINE,20));
        resultScroll=new ScrollView(this);resultScroll.setFillViewport(false);resultScroll.setVisibility(View.GONE);resultScroll.addView(results);root.addView(resultScroll,new LinearLayout.LayoutParams(-1,0,5));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(10),0,0);
        chooseImage=button("Use a photo",false);chooseImage.setEnabled(false);chooseImage.setOnClickListener(v->{generation.incrementAndGet();stability.reset();stablePageFrames=0;active=false;scanAgain.setEnabled(false);chooseImage.setEnabled(false);Intent pick=new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");startActivityForResult(pick,7);});actions.addView(chooseImage,new LinearLayout.LayoutParams(0,dp(50),1));
        scanAgain=button("Scan again",false);scanAgain.setVisibility(View.GONE);LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,dp(50),1);gap.setMargins(dp(8),0,0,0);actions.addView(scanAgain,gap);
        saveButton=button("Save scan",true);saveButton.setVisibility(View.GONE);LinearLayout.LayoutParams shareGap=new LinearLayout.LayoutParams(0,dp(50),1);shareGap.setMargins(dp(8),0,0,0);actions.addView(saveButton,shareGap);
        scanAgain.setOnClickListener(v->{generation.incrementAndGet();stability.reset();stablePageFrames=0;lastFrame=0;shareText="";results.removeAllViews();resultScroll.setVisibility(View.GONE);clearSaveAction();chooseImage.setVisibility(View.VISIBLE);scanAgain.setVisibility(View.GONE);scanAgain.setEnabled(true);scanAgain.setText("Scan again");chooseImage.setEnabled(true);clearFrozen();overlay.update(null,0,0);active=true;status.setBackground(round(AppUi.SEA_PALE,14));status.setText("Fit the whole passport page in view.");if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();});root.addView(actions);setContentView(root);
        worker.execute(()->{try{pageScanner=new CandidateScanner(this);runOnUiThread(()->{chooseImage.setEnabled(true);status.setText("Fit the whole passport page in view.");});}catch(Exception e){runOnUiThread(()->status.setText("Scanner setup failed. The bundled models could not be loaded."));}});
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();else requestPermissions(new String[]{Manifest.permission.CAMERA},8);
    }
    @Override public void onRequestPermissionsResult(int request,@NonNull String[] permissions,@NonNull int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==8&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCamera();else if(request==8)status.setText("Camera permission denied. You can still choose an image.");}
    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{try{
            if(isFinishing()||isDestroyed()||!active)return;
            cameraProvider=future.get();Preview p=new Preview.Builder().build();p.setSurfaceProvider(preview.getSurfaceProvider());
            ImageAnalysis analysis=new ImageAnalysis.Builder().setTargetResolution(new android.util.Size(1280,720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
            ImageCapture still=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
            analysis.setAnalyzer(worker,frame->{Bitmap bitmap=null;boolean frameClosed=false;final int token=generation.get();String stage="frame conversion";try{
                if(!active||pageScanner==null||SystemClock.elapsedRealtime()-lastFrame<250)return;
                lastFrame=SystemClock.elapsedRealtime();
                stage="frame conversion";
                Bitmap converted=frame.toBitmap();bitmap=converted;int degrees=frame.getImageInfo().getRotationDegrees();
                if(degrees!=0){Matrix m=new Matrix();m.postRotate(degrees);bitmap=Bitmap.createBitmap(converted,0,0,converted.getWidth(),converted.getHeight(),m,true);if(bitmap!=converted)converted.recycle();}
                frame.close();frameClosed=true;
                stage="page detection";
                org.opencv.core.Point[] corners=pageScanner.detectPage(bitmap);final int width=bitmap.getWidth(),height=bitmap.getHeight();
                if(token!=generation.get()||!active)return;
                if(corners==null){stability.reset();stablePageFrames=0;firstPageSeenAt=0;runOnUiThread(()->{if(token==generation.get()){overlay.setPageDetected(false);overlay.updatePolygon(null,0,0);status.setBackground(round(AppUi.SEA_PALE,14));status.setText("Fit the whole passport page in view.");}});return;}
                stage="page geometry";
                org.opencv.core.Rect bounds=pageBounds(corners);float area=(float)bounds.width*bounds.height/(width*(float)height);
                if(area<.10f){stability.reset();stablePageFrames=0;firstPageSeenAt=0;runOnUiThread(()->{if(token==generation.get()){overlay.setPageDetected(false);overlay.updatePolygon(flatten(corners),width,height);status.setBackground(round(AppUi.SEA_PALE,14));status.setText("Move a little closer to the page.");}});return;}
                if(firstPageSeenAt==0)firstPageSeenAt=SystemClock.elapsedRealtime();
                boolean stable=stability.accept(bounds.x,bounds.y,bounds.width,bounds.height);stablePageFrames=stable?stablePageFrames+1:0;
                float[] points=flatten(corners);runOnUiThread(()->{if(token==generation.get()){overlay.setPageDetected(true);overlay.updatePolygon(points,width,height);status.setBackground(round(AppUi.SEA_PALE,14));status.setText(stable?"Page found · hold still":"Page found · keep it steady");}});
                if(stablePageFrames>=2){
                    active=false;
                    stablePageFrames=0;
                    long presentationStarted=firstPageSeenAt;
                    firstPageSeenAt=0;
                    runOnUiThread(()->requestStillCapture(still,token,presentationStarted));
                }
            }catch(Exception e){final String failedStage=stage;runOnUiThread(()->pauseAfterCameraError(token,failedStage,e));}
            finally{if(bitmap!=null)bitmap.recycle();if(!frameClosed)frame.close();}});
            cameraProvider.unbindAll();cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis,still);
        }catch(Exception e){status.setText("Camera unavailable. Use a photo or check camera permission.");}},ContextCompat.getMainExecutor(this));
    }
    private void requestStillCapture(ImageCapture still,int token,long scanStarted){
        if(token!=generation.get()||isFinishing()||isDestroyed())return;
        try{
            chooseImage.setEnabled(false);
            status.setBackground(round(AppUi.SEA_PALE,14));
            status.setText("Capturing the page…");
            long captureStarted=SystemClock.elapsedRealtime();
            still.takePicture(worker,new ImageCapture.OnImageCapturedCallback(){
                @Override public void onCaptureSuccess(@NonNull ImageProxy captured){
                    if(token!=generation.get()){
                        captured.close();
                        return;
                    }
                    captureCandidatePage(token,scanStarted,captureStarted,captured);
                }
                @Override public void onError(@NonNull ImageCaptureException error){
                    runOnUiThread(()->pauseAfterCameraError(token,"still capture callback",error));
                }
            });
        }catch(RuntimeException error){
            pauseAfterCameraError(token,"still capture request",error);
        }
    }

    private void pauseAfterCameraError(int token,String stage,Exception error){
        if(token!=generation.get()||isFinishing()||isDestroyed())return;
        android.util.Log.e("PassportScan","Camera failed at "+stage+" ("+error.getClass().getSimpleName()+")");
        active=false;
        overlay.setScanning(false);
        overlay.setReadingPage(false);
        overlay.setPageDetected(false);
        overlay.updatePolygon(null,0,0);
        chooseImage.setEnabled(true);
        scanAgain.setEnabled(true);
        scanAgain.setVisibility(View.VISIBLE);
        scanAgain.setText("Try again");
        status.setBackground(round(AppUi.CORAL_PALE,14));
        status.setText("Scan paused. Tap Try again.");
    }
    private static float[] flatten(org.opencv.core.Point[] points){float[] out=new float[points.length*2];for(int i=0;i<points.length;i++){out[2*i]=(float)points[i].x;out[2*i+1]=(float)points[i].y;}return out;}
    private static org.opencv.core.Rect pageBounds(org.opencv.core.Point[] points){double left=Double.MAX_VALUE,top=Double.MAX_VALUE,right=0,bottom=0;for(org.opencv.core.Point p:points){left=Math.min(left,p.x);top=Math.min(top,p.y);right=Math.max(right,p.x);bottom=Math.max(bottom,p.y);}return new org.opencv.core.Rect((int)left,(int)top,Math.max(1,(int)Math.ceil(right-left)),Math.max(1,(int)Math.ceil(bottom-top)));}
    private Bitmap upright(Bitmap source,int degrees){if(degrees==0)return source;Matrix m=new Matrix();m.postRotate(degrees);Bitmap rotated=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);if(rotated!=source)source.recycle();return rotated;}
    private void captureCandidatePage(int token,long scanStarted,long captureStarted,ImageProxy captured){
        long capturedAt=System.currentTimeMillis();
        Bitmap source=null,page=null;int captureWidth=0,captureHeight=0;
        try{
            int degrees=captured.getImageInfo().getRotationDegrees();source=upright(captured.toBitmap(),degrees);captured.close();
            org.opencv.core.Point[] corners=pageScanner.detectPage(source);if(corners==null)throw new IOException("Page edges were not clear in the high resolution capture");
            captureWidth=source.getWidth();captureHeight=source.getHeight();page=pageScanner.rectifyPage(source,corners);if(page==null)throw new IOException("Could not straighten the page");source.recycle();source=null;
            long captureMs=SystemClock.elapsedRealtime()-captureStarted;
            Bitmap selected=page;page=null;
            runOnUiThread(()->{if(token==generation.get())showDocumentPage(selected,"Reading the passport’s two lines…");});
            JSONObject result=pageScanner.scanPage(selected);result.put("cameraScanMs",SystemClock.elapsedRealtime()-scanStarted);result.put("captureMs",captureMs).put("captureWidth",captureWidth).put("captureHeight",captureHeight);
            if(token!=generation.get()){selected.recycle();return;}
            publishScan(token,result,capturedAt,false);
        }catch(Exception e){if(source!=null)source.recycle();if(page!=null)page.recycle();captured.close();runOnUiThread(()->{if(token==generation.get())resumeAfterCaptureFailure("Could not finish the scan. Keep all four page edges visible and try again.");});}
    }
    private void resumeAfterCaptureFailure(String message){if(frozenBitmap!=null)clearFrozen();results.removeAllViews();resultScroll.setVisibility(View.GONE);shareText="";clearSaveAction();chooseImage.setVisibility(View.VISIBLE);chooseImage.setEnabled(true);scanAgain.setEnabled(true);active=true;stablePageFrames=0;firstPageSeenAt=0;stability.reset();scanAgain.setVisibility(View.GONE);scanAgain.setText("Scan again");status.setBackground(round(AppUi.CORAL_PALE,14));status.setText(message);if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();}
    private void clearFrozen(){
        overlay.setScanning(false);overlay.setReadingPage(false);overlay.setPageDetected(false);overlay.setFitCenter(false);frozenView.setImageDrawable(null);frozenView.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);
        if(frozenBitmap!=null){frozenBitmap.recycle();frozenBitmap=null;}
    }
    private void showDocumentPage(Bitmap page,String message){
        clearFrozen();frozenBitmap=page;frozenView.setImageBitmap(page);frozenView.setVisibility(View.VISIBLE);preview.setVisibility(View.INVISIBLE);
        if(cameraProvider!=null)cameraProvider.unbindAll();overlay.setFitCenter(true);overlay.setReadingPage(true);overlay.update(null,page.getWidth(),page.getHeight());overlay.setScanning(true);
        chooseImage.setEnabled(false);scanAgain.setEnabled(false);scanAgain.setVisibility(View.VISIBLE);status.setBackground(round(AppUi.SEA_PALE,14));status.setText(message);
    }
    private void publishScan(int token,JSONObject result,long capturedAt,boolean fromPhoto) throws Exception{
        JSONObject timing=ScanTiming.fromResult(result);
        JSONObject previous=null;
        JSONObject fields=result.optJSONObject("fields");
        if(fields!=null&&!getIntent().getBooleanExtra("scan_for_chip",false)){
            try{
                previous=SavedScanStore.latestMatch(this,fields);
            }catch(Exception error){
                android.util.Log.w("PassportScan","Saved scan lookup failed ("+error.getClass().getSimpleName()+")");
            }
        }
        JSONObject match=previous;
        runOnUiThread(()->{
            if(token==generation.get()&&!isFinishing())finishScanning(result,capturedAt,fromPhoto,timing,match);
        });
    }

    private void finishScanning(JSONObject result,long capturedAt,boolean fromPhoto,JSONObject timing,JSONObject previous){
        overlay.setScanning(false);overlay.setReadingPage(false);chooseImage.setEnabled(true);scanAgain.setEnabled(true);JSONArray selected=result.optJSONArray("selectedBox"),boxes=new JSONArray();if(selected!=null)boxes.put(selected);
        overlay.update(boxes,result.optInt("imageWidth"),result.optInt("imageHeight"));show(result,capturedAt,fromPhoto,timing,previous);
    }
    private Bitmap loadImage(Uri uri) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Unsupported image");
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>4096)options.inSampleSize*=2;
        Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IOException("Unsupported image");
        int orientation;try(InputStream in=getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}
        Matrix matrix=new Matrix();
        switch(orientation){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.setScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_180:matrix.setRotate(180);break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.setScale(1,-1);break;
            case ExifInterface.ORIENTATION_TRANSPOSE:matrix.setRotate(90);matrix.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_90:matrix.setRotate(90);break;
            case ExifInterface.ORIENTATION_TRANSVERSE:matrix.setRotate(-90);matrix.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_270:matrix.setRotate(270);break;
            default:return bitmap;
        }
        Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();return rotated;
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==9){
            if(result==RESULT_OK&&data!=null&&savedScanId!=null
                    &&savedScanId.equals(data.getStringExtra(SavedScansActivity.EXTRA_SCAN_ID))){
                offerSave(savableFields,savableCapturedAt,savableFromPhoto,savableTiming);
            }
            return;
        }
        if(request!=7)return;if(result!=RESULT_OK||data==null){chooseImage.setVisibility(View.VISIBLE);chooseImage.setEnabled(true);scanAgain.setEnabled(true);active=frozenBitmap==null;status.setText(active?"Fit the whole passport page in view.":"Review your scan below.");if(active&&ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();return;}
        final int token=generation.get();final long imageStarted=SystemClock.elapsedRealtime(), photoScannedAt=System.currentTimeMillis();shareText="";clearSaveAction();status.setText("Reading the selected photo…");worker.execute(()->{try{
            Bitmap b=loadImage(data.getData());
            if(pageScanner==null){b.recycle();throw new IOException("Scanner not ready");}
            org.opencv.core.Point[] corners=pageScanner.detectPage(b);if(corners==null){b.recycle();runOnUiThread(()->{if(token==generation.get())resumeAfterCaptureFailure("Could not find all page edges in this photo. Choose another image or scan with the camera.");});return;}
            Bitmap page=pageScanner.rectifyPage(b,corners);b.recycle();if(page==null)throw new IOException("Could not straighten the page");
            Bitmap captured=page;runOnUiThread(()->{if(token==generation.get())showDocumentPage(captured,"Reading the passport’s two lines…");});
            JSONObject r=pageScanner.scanPage(page);r.put("imageReadMs",SystemClock.elapsedRealtime()-imageStarted);if(token!=generation.get()){page.recycle();return;}publishScan(token,r,photoScannedAt,true);
        }catch(Exception e){runOnUiThread(()->{if(token==generation.get())resumeAfterCaptureFailure("Could not read this image. Try a clearer photo with all page edges visible.");});}});
    }
    private int dp(float value){return AppUi.dp(this,value);}
    private GradientDrawable round(int color,int radius){return AppUi.shape(this,color,radius);}
    private TextView label(String text,int size,int color,boolean bold){return AppUi.text(this,text,size,color,bold);}
    private Button button(String text,boolean primary){return AppUi.button(this,text,primary);}
    private void addText(String text,int size,int color,boolean bold){TextView view=label(text,size,color,bold);view.setTextIsSelectable(true);results.addView(view);}
    private void addField(LinearLayout parent,String title,String value){LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(0,dp(10),dp(8),dp(4));TextView heading=label(title,12,AppUi.MUTED,true);column.addView(heading);TextView content=label(value.isEmpty()?"—":value,15,AppUi.INK,true);content.setPadding(0,dp(3),0,0);content.setTextIsSelectable(true);column.addView(content);parent.addView(column,new LinearLayout.LayoutParams(0,-2,1));}
    private void addRow(JSONObject fields,String key1,String title1,String key2,String title2){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);addField(row,title1,fields.optString(key1,""));addField(row,title2,fields.optString(key2,""));results.addView(row);}
    private void previewShare(){
        if(shareText.isEmpty())return;
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Review before sharing").setMessage(shareText).setNegativeButton("Cancel",null).setPositiveButton("Share",(dialog,which)->{Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,shareText);startActivity(Intent.createChooser(send,"Share document details"));}).show();
    }
    private void clearSaveAction(){
        savableFields=null;
        savableTiming=null;
        savedScanId=null;
        saveButton.setOnClickListener(null);
        saveButton.setVisibility(View.GONE);
    }

    private void offerSave(JSONObject fields,long capturedAt,boolean fromPhoto,JSONObject timing){
        clearSaveAction();
        if(fields==null)return;
        savableFields=fields;
        savableCapturedAt=capturedAt;
        savableFromPhoto=fromPhoto;
        savableTiming=timing;
        saveButton.setText("Save scan");
        saveButton.setVisibility(View.VISIBLE);
        saveButton.setOnClickListener(v->confirmSave(fields,capturedAt,fromPhoto,timing));
    }

    private void confirmSave(JSONObject fields,long capturedAt,boolean fromPhoto,JSONObject timing){
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Save passport details?")
                .setMessage("Compare the fields with the passport first. Pageport saves the text on this phone in Saved scans.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(dialog,which)->{
                    try{
                        savedScanId=SavedScanStore.save(this,fields,capturedAt,fromPhoto,timing);
                        saveButton.setText("View saved scan");
                        saveButton.setOnClickListener(v->startActivityForResult(new Intent(this,SavedScansActivity.class)
                                .putExtra(SavedScansActivity.EXTRA_SCAN_ID,savedScanId),9));
                        Toast.makeText(this,"Scan saved",Toast.LENGTH_SHORT).show();
                    }catch(Exception error){
                        Toast.makeText(this,"Couldn’t save scan.",Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void show(JSONObject r,long capturedAt,boolean fromPhoto,JSONObject timing,JSONObject previous){
        String state=r.optString("status");boolean usable=state.equals("valid")||state.equals("review");
        if(state.equals("retake")){
            shareText="";clearSaveAction();chooseImage.setVisibility(View.VISIBLE);scanAgain.setVisibility(View.VISIBLE);scanAgain.setText("Retake page");results.removeAllViews();resultScroll.setVisibility(View.VISIBLE);
            status.setText("This page needs another scan");status.setBackground(round(AppUi.CORAL_PALE,14));
            addText("Let’s try that again",20,AppUi.INK,true);
            addText("Keep all four page edges visible, move a little closer, hold steady and avoid glare. Leave the printed lines clear.",14,AppUi.BODY,false);
            ScanTiming.append(results,timing);
            resultScroll.post(()->resultScroll.scrollTo(0,0));return;
        }
        if(getIntent().getBooleanExtra("scan_for_chip",false)&&usable){
            try{
                ChipAccess access=ChipScanResult.access(r);
                NfcTransfer.put(access);setResult(RESULT_OK);finish();return;
            }catch(IllegalArgumentException ignored){}
        }
        status.setText(state.equals("valid")?"MRZ read · compare the details below":state.equals("review")?"Review these details against the passport":"No MRZ text to display");
        status.setBackground(round(state.equals("review")?AppUi.CORAL_PALE:AppUi.SEA_PALE,14));
        results.removeAllViews();resultScroll.setVisibility(View.VISIBLE);scanAgain.setVisibility(View.VISIBLE);chooseImage.setVisibility(View.GONE);JSONObject fields=r.optJSONObject("fields");
        shareText=ShareFormatter.format(r);
        offerSave(usable?fields:null,capturedAt,fromPhoto,timing);
        if(fields!=null&&usable){
            String full=(fields.optString("given_names","")+" "+fields.optString("surname","")).trim();addText(full.isEmpty()?"Name unavailable":full,20,AppUi.INK,true);
            if(previous!=null){
                long previousCapture=previous.optLong("capturedAt");
                String when=PassportDates.timestamp(this,previousCapture>0?previousCapture:previous.optLong("savedAt"));
                addText((previousCapture>0?"Previously scanned ":"Previously saved ")+when,14,AppUi.SEA,true);
            }
            addRow(fields,"document_number","Document number","nationality","Nationality");
            LinearLayout dateRow=new LinearLayout(this);
            addField(dateRow,"Date of birth",PassportDates.field("birth_date_raw",fields.optString("birth_date_raw"),capturedAt));
            addField(dateRow,"Expiry date",PassportDates.field("expiry_date_raw",fields.optString("expiry_date_raw"),capturedAt));
            results.addView(dateRow);
            addRow(fields,"issuer","Issuing country","sex","Sex");
            addText("Compare every field with the passport. Date centuries are inferred from the two-digit years in the MRZ.",14,AppUi.BODY,false);
            if(!shareText.isEmpty()){
                Button share=button("Share details",false);
                LinearLayout.LayoutParams shareParams=new LinearLayout.LayoutParams(-1,-2);
                shareParams.topMargin=dp(12);
                results.addView(share,shareParams);
                share.setOnClickListener(v->previewShare());
            }
            JSONArray accessLines=r.optJSONArray("lines");
            if(accessLines!=null&&accessLines.length()==2){
                try {
                    final ChipAccess access=ChipAccess.fromTd3(accessLines.optString(0),accessLines.optString(1));
                    Button readChip=button("Continue to passport chip",false);
                    LinearLayout.LayoutParams chipParams=new LinearLayout.LayoutParams(-1,dp(48));chipParams.setMargins(0,dp(16),0,0);
                    readChip.setOnClickListener(v->{NfcTransfer.put(access);startActivity(new Intent(this,ChipActivity.class));});results.addView(readChip,chipParams);
                }catch(IllegalArgumentException ignored){}
            }
        }else addText("No information to display",20,0xff183c35,true);
        JSONArray lines=r.optJSONArray("lines");if(lines!=null&&usable){
            TextView toggle=label("Show raw MRZ lines  +",12,AppUi.SEA,true);toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setMinHeight(dp(44));toggle.setFocusable(true);toggle.setClickable(true);
            TextView mrz=label("",12,AppUi.BODY,false);mrz.setTypeface(android.graphics.Typeface.MONOSPACE);mrz.setTextIsSelectable(true);mrz.setPadding(0,dp(6),0,dp(4));mrz.setVisibility(View.GONE);
            StringBuilder raw=new StringBuilder();for(int i=0;i<lines.length();i++)raw.append(lines.optString(i)).append("\n");mrz.setText(raw.toString());
            toggle.setOnClickListener(v->{boolean show=mrz.getVisibility()!=View.VISIBLE;mrz.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(show?"Hide raw MRZ lines  −":"Show raw MRZ lines  +");});results.addView(toggle);results.addView(mrz);
        }
        if(state.equals("review"))addText(r.optString("warning","Some characters are unclear. Compare the details and try another capture if needed."),12,AppUi.CORAL_INK,false);
        ScanTiming.append(results,timing);
        resultScroll.post(()->resultScroll.scrollTo(0,0));
    }
    private void runBenchmark(){
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView progress=new TextView(this);progress.setText("Local on-device scan benchmark running. No document text is logged.");progress.setPadding(32,64,32,32);setContentView(progress);
        worker.execute(()->{
        File dir=new File(getFilesDir(),"benchmark");
        try{
            long loadStarted=SystemClock.elapsedRealtime();Bitmap b=loadImage(Uri.fromFile(new File(dir,"input.jpg")));long loadMs=SystemClock.elapsedRealtime()-loadStarted;
            try {
                if(getIntent().getBooleanExtra("crop",false))throw new IOException("The page scanner requires a full passport image");
                JSONObject r;long initStarted=SystemClock.elapsedRealtime();PageScanner p=new CandidateScanner(this);
                long initialization=SystemClock.elapsedRealtime()-initStarted;long pageStart=SystemClock.elapsedRealtime();long detectMs=0,rectifyMs=0;
                try {
                    org.opencv.core.Point[] corners=p.detectPage(b);detectMs=SystemClock.elapsedRealtime()-pageStart;
                    if(corners==null)r=new JSONObject().put("status","retake").put("pageFound",false).put("warning","Page edges were not detected");
                    else {
                        long rectifyStart=SystemClock.elapsedRealtime();Bitmap page=p.rectifyPage(b,corners);rectifyMs=SystemClock.elapsedRealtime()-rectifyStart;
                        if(page==null)r=new JSONObject().put("status","retake").put("pageFound",false).put("warning","Page rectification failed");
                        else try {r=p.scanPage(page).put("pageFound",true).put("pageWidth",page.getWidth()).put("pageHeight",page.getHeight()).put("rectificationMs",rectifyMs);}finally{page.recycle();}
                    }
                    JSONObject scanTimes=r.optJSONObject("timingsMs");JSONObject times=new JSONObject().put("pageDetection",detectMs).put("rectification",rectifyMs).put("spotting",scanTimes==null?0:scanTimes.optLong("spotting")).put("fallback",scanTimes==null?0:scanTimes.optLong("fallback")).put("total",detectMs+rectifyMs+(scanTimes==null?0:scanTimes.optLong("total")));
                    r.put("timingsMs",times).put("pageDetectionMs",detectMs).put("initializationMs",initialization).put("imageLoadMs",loadMs).put("imageWidth",b.getWidth()).put("imageHeight",b.getHeight());
                } finally {p.close();}
                try(FileWriter w=new FileWriter(new File(dir,"result.json"))){w.write(r.toString());}
            } finally {b.recycle();}
        }catch(Exception e){try{dir.mkdirs();try(FileWriter w=new FileWriter(new File(dir,"result.json"))){w.write(new JSONObject().put("error",e.toString()).toString());}}catch(Exception ignored){}}finally{runOnUiThread(this::finish);}
    });}
    @Override protected void onDestroy(){generation.incrementAndGet();active=false;Bitmap release=frozenBitmap;frozenBitmap=null;if(frozenView!=null){frozenView.setImageDrawable(null);frozenView.setVisibility(View.GONE);}worker.execute(()->{if(release!=null&&!release.isRecycled())release.recycle();if(pageScanner!=null)pageScanner.close();});worker.shutdown();super.onDestroy();}
}
