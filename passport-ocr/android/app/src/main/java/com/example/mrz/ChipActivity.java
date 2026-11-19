package com.example.mrz;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Foreground-only NFC reader; displayed data is not an authenticity verdict. */
public final class ChipActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicInteger generation=new AtomicInteger();
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->{if(this.waiting)stop("Read timed out. Try again and keep the passport still.");};
    private volatile IsoDep activeTag;private NfcAdapter adapter;private ChipAccess source,session;
    private EditText number,birth,expiry;private TextView status,result;private Button read,cancel,scanPassport;private volatile boolean waiting;private boolean resumed;
    private int dp(float n){return AppUi.dp(this,n);}
    private GradientDrawable bg(int color,int radius){return AppUi.shape(this,color,radius);}
    private TextView text(String value,int size,int color,boolean bold){return AppUi.text(this,value,size,color,bold);}
    private Button button(String label,boolean primary){return AppUi.button(this,label,primary);}
    private EditText input(String hint,boolean digits){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setTextSize(15);e.setTextColor(AppUi.INK);e.setHintTextColor(AppUi.MUTED);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(AppUi.SEA));e.setInputType(digits?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);e.setSaveEnabled(false);return e;}
    @Override public void onCreate(Bundle state){super.onCreate(state);source=NfcTransfer.take();adapter=NfcAdapter.getDefaultAdapter(this);
        AppUi.systemBars(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(10),dp(20),dp(20));root.setBackgroundColor(AppUi.PAPER);scroll.addView(root);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=AppUi.legalLink(this,"‹");back.setTextSize(28);back.setContentDescription("Back");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text("Read passport chip",20,AppUi.INK,true);title.setPadding(dp(8),0,0,0);header.addView(title);root.addView(header);
        root.addView(text("OPTIONAL NFC · ON DEVICE",12,AppUi.SEA,true));
        TextView intro=text("Read a compatible chip using the access details printed on the passport. No account is needed.",15,AppUi.BODY,false);intro.setLineSpacing(dp(3),1f);intro.setPadding(0,dp(8),0,dp(4));root.addView(intro);
        scanPassport=button("Scan passport with camera",true);scanPassport.setOnClickListener(v->startPassportScan());LinearLayout.LayoutParams scanParams=new LinearLayout.LayoutParams(-1,dp(52));scanParams.setMargins(0,dp(14),0,0);root.addView(scanPassport,scanParams);
        TextView manualLabel=text("ACCESS DETAILS · SCAN OR ENTER",12,AppUi.SEA,true);manualLabel.setPadding(dp(2),dp(18),0,0);root.addView(manualLabel);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(14),dp(16),dp(10));card.setBackground(AppUi.outlined(this,AppUi.CARD,AppUi.LINE,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(10),0,0);root.addView(card,cp);
        card.addView(text("Document number",12,AppUi.MUTED,true));number=input("e.g. A1234567",false);card.addView(number);
        card.addView(text("Date of birth · YYMMDD",12,AppUi.MUTED,true));birth=input("YYMMDD",true);card.addView(birth);
        card.addView(text("Expiry date · YYMMDD",12,AppUi.MUTED,true));expiry=input("YYMMDD",true);card.addView(expiry);
        if(source!=null){number.setText(source.number);birth.setText(source.birth);expiry.setText(source.expiry);}
        status=text(adapter==null?"NFC is not available on this phone.":"Ready when you are. The passport needs a compatible chip.",14,AppUi.INK,true);status.setPadding(dp(14),dp(14),dp(14),dp(14));status.setBackground(bg(AppUi.SEA_PALE,14));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(14),0,dp(12));root.addView(status,sp);
        read=button("Read chip",true);read.setEnabled(adapter!=null);read.setOnClickListener(v->begin());root.addView(read,new LinearLayout.LayoutParams(-1,dp(52)));
        cancel=button("Cancel reading",false);cancel.setVisibility(View.GONE);cancel.setOnClickListener(v->stop("Reading cancelled."));LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(-1,dp(48));c.setMargins(0,dp(8),0,0);root.addView(cancel,c);
        result=text("",14,AppUi.BODY,false);result.setTextIsSelectable(true);result.setPadding(dp(2),dp(18),dp(2),0);root.addView(result);TextView note=text("A chip read does not verify authenticity.",12,AppUi.CORAL_INK,true);note.setPadding(dp(2),dp(12),dp(2),0);root.addView(note);setContentView(scroll);
        if(state==null&&source==null&&getIntent().getBooleanExtra("start_scan",false))startPassportScan();
    }
    private void startPassportScan(){
        if(waiting||busy.get())return;
        NfcTransfer.take();
        Intent scanner=new Intent(this,MainActivity.class).putExtra("scan_for_chip",true);
        startActivityForResult(scanner,41);
    }
    @Override protected void onActivityResult(int request,int resultCode,Intent data){
        super.onActivityResult(request,resultCode,data);
        if(request!=41)return;
        ChipAccess scanned=NfcTransfer.take();
        if(resultCode==RESULT_OK&&scanned!=null){
            source=scanned;number.setText(scanned.number);birth.setText(scanned.birth);expiry.setText(scanned.expiry);
            result.setText("");status.setText("Passport scanned. Review the details, then tap Read chip.");
        }else status.setText("Scan cancelled or incomplete. Scan again or enter the details manually.");
    }
    private void begin(){
        if(adapter==null){status.setText("NFC is not available on this phone.");return;}
        if(busy.get()){status.setText("Finishing the previous attempt. Try again in a moment.");return;}
        if(!adapter.isEnabled()){status.setText("NFC is off. Turn it on in settings, then return.");startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));return;}
        final ChipAccess reviewed;
        try{reviewed=source==null?ChipAccess.manual(number.getText().toString(),birth.getText().toString(),expiry.getText().toString()):source.reviewed(number.getText().toString(),birth.getText().toString(),expiry.getText().toString());}
        catch(IllegalArgumentException e){status.setText(e.getMessage());return;}
        String details="Document number: "+reviewed.number+"  (check digit "+ChipAccess.checkDigit(reviewed.number)+")\nDate of birth: "+reviewed.birth+"  (check digit "+ChipAccess.checkDigit(reviewed.birth)+")\nExpiry date: "+reviewed.expiry+"  (check digit "+ChipAccess.checkDigit(reviewed.expiry)+")\n\nCompare these values with the passport before continuing.";
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Review access details").setMessage(details).setNegativeButton("Edit",null).setPositiveButton("Continue",(dialog,which)->startReading(reviewed)).show();
    }
    private void startReading(ChipAccess reviewed){
        if(!resumed||adapter==null||!adapter.isEnabled())return;
        session=reviewed;generation.incrementAndGet();result.setText("");waiting=true;read.setEnabled(false);scanPassport.setEnabled(false);cancel.setVisibility(View.VISIBLE);number.setEnabled(false);birth.setEnabled(false);expiry.setEnabled(false);status.setText("Hold the back of your phone against the passport and keep it still.");mainHandler.removeCallbacks(timeout);mainHandler.postDelayed(timeout,30000);enableReader();
    }
    private void enableReader(){if(resumed&&waiting&&adapter!=null&&adapter.isEnabled())adapter.enableReaderMode(this,this::onTag,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null);}
    private void onTag(Tag tag){
        if(!waiting||!busy.compareAndSet(false,true))return;
        IsoDep iso=IsoDep.get(tag);if(iso==null){busy.set(false);runOnUiThread(()->status.setText("This is not a compatible passport chip."));return;}
        activeTag=iso;final int token=generation.get();final ChipAccess input=session;
        if(!waiting||input==null||token!=generation.get()){try{iso.close();}catch(IOException ignored){}activeTag=null;busy.set(false);return;}
        try{worker.execute(()->{try{
            JSONObject output=ChipSession.read(iso,input,message->runOnUiThread(()->{if(token==generation.get()&&waiting)status.setText(message);}));
            runOnUiThread(()->{if(token==generation.get()&&waiting){finishSession();showResult(output);}});
        }catch(Exception e){String message=e instanceof ChipSession.Failure?e.getMessage():"Chip read interrupted. Keep the phone steady and try again.";runOnUiThread(()->{if(token==generation.get()&&waiting){finishSession();status.setText(message);}});}
        finally{activeTag=null;busy.set(false);}});}catch(java.util.concurrent.RejectedExecutionException ignored){try{iso.close();}catch(IOException closeIgnored){}activeTag=null;busy.set(false);}
    }
    private void showResult(JSONObject output){
        status.setText("Chip data read · authenticity not verified");JSONObject decoded=output.optJSONObject("decoded");JSONObject fields=decoded==null?null:decoded.optJSONObject("fields");String comparison=output.optString("comparison");
        StringBuilder body=new StringBuilder("CHIP DATA  ·  NOT AUTHENTICATED\n\n");
        if(comparison.equals("match"))body.append("Camera scan and chip MRZ match.\n\n");else if(comparison.equals("mismatch"))body.append("Camera scan and chip MRZ differ. Compare the document carefully.\n\n");else body.append("No camera MRZ comparison was performed.\n\n");
        JSONObject camera=output.optJSONObject("cameraFields");
        if(output.optBoolean("accessEdited"))body.append("Access details were edited after the camera scan.\n\n");
        if(fields!=null&&camera!=null&&comparison.equals("mismatch")){
            body.append("DIFFERENCES (camera → chip)\n");
            for(String key:new String[]{"given_names","surname","document_number","issuer","nationality","birth_date_raw","expiry_date_raw","sex"}){
                String old=camera.optString(key,""),current=fields.optString(key,"");
                if(!old.equals(current))body.append(key.replace('_',' ')).append(": ").append(old).append(" → ").append(current).append('\n');
            }
            body.append('\n');
        }
        if(fields!=null){
            long readAt=System.currentTimeMillis();
            for(String[] field:SavedScanStore.FIELDS){
                body.append(field[1]).append(": ")
                        .append(PassportDates.field(field[0],fields.optString(field[0]),readAt)).append('\n');
            }
            body.append("\nDate centuries are inferred from the two-digit years in the MRZ.\n");
        }
        body.append("\nAccess: ").append(output.optString("access").toUpperCase()).append("\nChip authenticity: not verified\n");JSONObject times=output.optJSONObject("timingsMs");if(times!=null)body.append("Read time: ").append(times.optLong("total")).append(" ms\n");
        result.setText(body.toString());
    }
    private void finishSession(){mainHandler.removeCallbacks(timeout);waiting=false;if(adapter!=null&&resumed)adapter.disableReaderMode(this);read.setEnabled(adapter!=null);scanPassport.setEnabled(true);cancel.setVisibility(View.GONE);number.setEnabled(true);birth.setEnabled(true);expiry.setEnabled(true);session=null;}
    private void stop(String message){mainHandler.removeCallbacks(timeout);generation.incrementAndGet();waiting=false;if(adapter!=null&&resumed)adapter.disableReaderMode(this);IsoDep tag=activeTag;if(tag!=null)try{tag.close();}catch(IOException ignored){}read.setEnabled(adapter!=null);scanPassport.setEnabled(true);cancel.setVisibility(View.GONE);number.setEnabled(true);birth.setEnabled(true);expiry.setEnabled(true);session=null;status.setText(message);}
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){if(waiting)stop("Reading stopped. Tap Read chip to try again.");resumed=false;super.onPause();}
    @Override protected void onDestroy(){mainHandler.removeCallbacks(timeout);generation.incrementAndGet();waiting=false;IsoDep tag=activeTag;if(tag!=null)try{tag.close();}catch(IOException ignored){}source=null;session=null;number.setText("");birth.setText("");expiry.setText("");worker.shutdownNow();super.onDestroy();}
}
