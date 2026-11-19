package com.example.mrz;

import android.nfc.tech.IsoDep;
import android.os.SystemClock;
import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;
import org.jmrtd.BACKey;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG1File;
import org.json.JSONObject;
import java.io.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A single NFC session on a worker thread; never logs chip data or access secrets. */
final class ChipSession {
    interface Progress { void update(String text); }
    static final class Failure extends Exception {
        final String category;
        Failure(String category,String message){super(message);this.category=category;}
    }
    private static final int LIMIT=512*1024;
    private static byte[] bounded(InputStream source) throws IOException {
        try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count,total=0;
            while((count=in.read(buffer))!=-1){total+=count;if(total>LIMIT)throw new IOException("Chip file too large");out.write(buffer,0,count);}return out.toByteArray();
        }
    }
    private static boolean missingFile(Throwable error){
        for(Throwable e=error;e!=null;e=e.getCause())if(e instanceof CardServiceException){int sw=((CardServiceException)e).getSW()&0xffff;if(sw==0x6a82||sw==0x6a88)return true;}
        return false;
    }
    static JSONObject read(IsoDep iso,ChipAccess input,Progress progress) throws Exception {
        Logger.getLogger("org.jmrtd").setLevel(Level.OFF);Logger.getLogger("net.sf.scuba").setLevel(Level.OFF);
        long start=SystemClock.elapsedRealtime();PassportService service=null;
        try{
            iso.setTimeout(8000);progress.update("Connecting to chip…");
            CardService card=CardService.getInstance(iso);
            service=new PassportService(card,PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,PassportService.DEFAULT_MAX_BLOCKSIZE,false,true);
            service.open();BACKey key=new BACKey(input.number,input.birth,input.expiry);
            boolean advertised=false,cardAccessPresent=false;PACEInfo supported=null;AlgorithmParameterSpec params=null;
            try {
                byte[] bytes=bounded(service.getInputStream(PassportService.EF_CARD_ACCESS));
                cardAccessPresent=true;
                CardAccessFile access=new CardAccessFile(new ByteArrayInputStream(bytes));
                for(SecurityInfo info:access.getSecurityInfos())if(info instanceof PACEInfo){
                    advertised=true;PACEInfo pace=(PACEInfo)info;
                    if(supported==null&&pace.getParameterId()!=null)try{AlgorithmParameterSpec p=PACEInfo.toParameterSpec(pace.getParameterId());if(p!=null){supported=pace;params=p;}}catch(IllegalArgumentException ignored){}
                }
            } catch(Exception e){if(!missingFile(e))throw new Failure("access_configuration","Could not read chip access settings. Hold the phone steady and try again.");}
            ChipAccessPolicy.Mode mode=ChipAccessPolicy.choose(advertised||cardAccessPresent,supported!=null);
            if(mode==ChipAccessPolicy.Mode.UNSUPPORTED)throw new Failure("unsupported_access","This passport uses an unsupported access method.");
            progress.update("Opening secure chip session…");
            final PassportService selectedService=service;final PACEInfo selectedPace=supported;final AlgorithmParameterSpec selectedParams=params;
            try{
                ChipProtocol.execute(mode,new ChipProtocol.Operations(){
                    public void pace() throws Exception {selectedService.doPACE(PACEKeySpec.createMRZKey(key),selectedPace.getObjectIdentifier(),selectedParams,selectedPace.getParameterId());selectedService.sendSelectApplet(true);}
                    public void bac() throws Exception {selectedService.sendSelectApplet(false);selectedService.doBAC(key);}
                });
            }catch(Exception e){
                if(mode==ChipAccessPolicy.Mode.PACE)throw new Failure("pace_failed","PACE could not be completed. Check the access details, positioning, and document compatibility.");
                throw new Failure("bac_failed","Chip access failed. Check the access details and hold the phone steady.");
            }
            long accessMs=SystemClock.elapsedRealtime()-start;progress.update("Reading passport data…");
            long readStart=SystemClock.elapsedRealtime();byte[] dg1=bounded(service.getInputStream(PassportService.EF_DG1));
            String chipMrz=new DG1File(new ByteArrayInputStream(dg1)).getMRZInfo().toString().trim();
            JSONObject decoded=MrzDecoder.decode(chipMrz);String state=decoded.optString("status");
            if(!state.equals("valid")&&!state.equals("review"))throw new Failure("unsupported_data","Chip data was read but its MRZ format is not supported.");
            boolean sodRead=false;try{byte[] sod=bounded(service.getInputStream(PassportService.EF_SOD));sodRead=sod.length>0;}catch(Exception ignored){}
            JSONObject timings=new JSONObject().put("access",accessMs).put("read",SystemClock.elapsedRealtime()-readStart).put("total",SystemClock.elapsedRealtime()-start);
            String comparison=input.sourceMrz==null?"not_compared":input.sourceMrz.equals(chipMrz)?"match":"mismatch";
            JSONObject camera=input.sourceMrz==null?null:MrzDecoder.decode(input.sourceMrz);
            return new JSONObject().put("cameraFields",camera==null?JSONObject.NULL:camera.optJSONObject("fields")).put("accessEdited",input.edited).put("access",mode==ChipAccessPolicy.Mode.PACE?"pace":"bac").put("read","success").put("comparison",comparison).put("passive_auth","not_attempted").put("chip_auth","not_attempted").put("sodRead",sodRead).put("decoded",decoded).put("timingsMs",timings);
        }finally{if(service!=null)service.close();try{iso.close();}catch(IOException ignored){}}
    }
}
