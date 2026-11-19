package com.example.mrz;

import java.time.LocalDate;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Deterministic ICAO TD3/TD1 decoding. OCR corrections remain review-only. */
public final class MrzDecoder {
    private static boolean digit(String text, char check) {
        if (check < '0' || check > '9') return false;
        int sum=0; int[] weights={7,3,1};
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            int v=c=='<'?0:c>='0'&&c<='9'?c-'0':c>='A'&&c<='Z'?c-'A'+10:-1000;
            if(v<0)return false; sum+=v*weights[i%3];
        }
        return sum%10==check-'0';
    }
    private static boolean date(String s) {
        if(!s.matches("[0-9]{6}"))return false;
        try { LocalDate.of(2000+Integer.parseInt(s.substring(0,2)), Integer.parseInt(s.substring(2,4)),Integer.parseInt(s.substring(4,6)));return true; }
        catch(Exception e){return false;}
    }
    private static String clean(String s) { return s.replace('<',' ').trim().replaceAll(" +"," "); }
    public static JSONObject decode(String text) {
        JSONObject out=new JSONObject();
        try {
            List<String> lines=new ArrayList<>();
            for(String s:text.toUpperCase(Locale.ROOT).split("\\R")){s=s.replaceAll("\\s","");if(!s.isEmpty())lines.add(s);}
            out.put("status","invalid");out.put("lines",new JSONArray(lines));
            boolean td3=lines.size()==2&&lines.get(0).length()==44&&lines.get(1).length()==44;
            boolean td1=lines.size()==3&&lines.stream().allMatch(s->s.length()==30);
            if(!td3&&!td1)return out;
            for(String s:lines)if(!s.matches("[A-Z0-9<]+"))return out;
            String a=lines.get(0),b=lines.get(1),number=td3?b.substring(0,9):a.substring(5,14);
            char numCheck=td3?b.charAt(9):a.charAt(14);
            String birth=td3?b.substring(13,19):b.substring(0,6);
            String expiry=td3?b.substring(21,27):b.substring(8,14);
            char sex=td3?b.charAt(20):b.charAt(7);
            String nation=td3?b.substring(10,13):b.substring(15,18);
            String issuer=a.substring(2,5),names=td3?a.substring(5):lines.get(2);
            JSONObject checks=new JSONObject();
            checks.put("number",digit(number,numCheck));
            checks.put("birth",digit(birth,td3?b.charAt(19):b.charAt(6)));
            checks.put("expiry",digit(expiry,td3?b.charAt(27):b.charAt(14)));
            if(td3&&!b.substring(28,43).equals("<<<<<<<<<<<<<<<"))checks.put("optional",digit(b.substring(28,42),b.charAt(42)));
            checks.put("composite",digit(td3?b.substring(0,10)+b.substring(13,20)+b.substring(21,43):a.substring(5,30)+b.substring(0,7)+b.substring(8,15)+b.substring(18,29),td3?b.charAt(43):b.charAt(29)));
            boolean checksPass=true; for(Iterator<String> it=checks.keys();it.hasNext();)if(!checks.getBoolean(it.next()))checksPass=false;
            boolean countryOk=issuer.matches("[A-Z<]{3}")&&nation.matches("[A-Z<]{3}");
            boolean sexOk=sex=='M'||sex=='F'||sex=='<';
            boolean basic=(td3?a.charAt(0)=='P':"IAC".indexOf(a.charAt(0))>=0)&&names.matches("[A-Z<]+")&&date(birth)&&date(expiry)&&!number.replace("<","").isEmpty();
            checks.put("structure",basic&&countryOk&&sexOk);
            String[] parts=names.split("<<",2);
            JSONObject fields=new JSONObject();fields.put("document_number",number.replaceAll("<+$",""));fields.put("surname",clean(parts[0]));fields.put("given_names",parts.length>1?clean(parts[1]):"");fields.put("issuer",issuer);fields.put("nationality",nation);fields.put("birth_date_raw",birth);fields.put("expiry_date_raw",expiry);fields.put("sex",sexOk?String.valueOf(sex):"Unreadable");
            out.put("format",td3?"TD3":"TD1");out.put("fields",fields);out.put("checks",checks);
            if(numCheck=='<')out.put("status","unsupported");
            else if(checksPass&&basic)out.put("status",countryOk&&sexOk?"valid":"review");
            out.put("warning","Compare all fields with the document; names and country codes have no checksum. Dates are YYMMDD.");
        } catch(Exception e){throw new IllegalStateException(e);}
        return out;
    }
    public static JSONObject find(String raw) {
        String[] lines=raw.toUpperCase(Locale.ROOT).split("\\R");
        List<String> rows=new ArrayList<>();for(String line:lines){String s=line.replaceAll("\\s","");if(!s.isEmpty())rows.add(s);}
        JSONObject best=decode(raw); int score=-1;
        for(int count:new int[]{2,3})for(int i=0;i+count<=rows.size();i++) {
            JSONObject candidate=decode(String.join("\n",rows.subList(i,i+count)));
            String status=candidate.optString("status");int rank=status.equals("valid")?3:status.equals("review")?2:candidate.has("format")?1:0;
            if(rank>score){score=rank;best=candidate;}
        }
        return best;
    }
}
