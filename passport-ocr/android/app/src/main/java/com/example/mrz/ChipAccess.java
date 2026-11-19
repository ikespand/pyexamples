package com.example.mrz;

import java.time.LocalDate;
import java.util.Locale;

/** MRZ access data; intentionally has no toString and is never logged. */
public final class ChipAccess {
    final String number, birth, expiry, sourceMrz; final boolean edited;
    private ChipAccess(String n,String b,String e,String source,boolean changed){number=n;birth=b;expiry=e;sourceMrz=source;edited=changed;}
    private static boolean validDate(String value){
        if(!value.matches("[0-9]{6}"))return false;
        try{LocalDate.of(2000+Integer.parseInt(value.substring(0,2)),Integer.parseInt(value.substring(2,4)),Integer.parseInt(value.substring(4,6)));return true;}catch(Exception e){return false;}
    }
    static int checkDigit(String s){int[] weights={7,3,1};int sum=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);int v=c=='<'?0:c>='0'&&c<='9'?c-'0':c>='A'&&c<='Z'?c-'A'+10:-1;if(v<0)throw new IllegalArgumentException("Invalid access character");sum+=v*weights[i%3];}return sum%10;}
    public static ChipAccess manual(String number,String birth,String expiry){return create(number,birth,expiry,null);}
    private static ChipAccess create(String number,String birth,String expiry,String source){
        number=number.trim().toUpperCase(Locale.ROOT);birth=birth.trim();expiry=expiry.trim();
        if(!number.matches("[A-Z0-9<]{1,9}")||number.replace("<","").isEmpty())throw new IllegalArgumentException("Enter a document number of up to nine MRZ characters.");
        if(!validDate(birth)||!validDate(expiry))throw new IllegalArgumentException("Enter valid dates in YYMMDD format.");
        StringBuilder n=new StringBuilder(number);while(n.length()<9)n.append('<');
        return new ChipAccess(n.toString(),birth,expiry,source,false);
    }
    public static ChipAccess fromTd3(String first,String second){
        if(first==null||second==null||first.length()!=44||second.length()!=44||first.charAt(0)!='P')throw new IllegalArgumentException("Only standard passport MRZs are supported for chip access.");
        String n=second.substring(0,9),b=second.substring(13,19),e=second.substring(21,27);
        if(second.charAt(9)=='<')throw new IllegalArgumentException("Extended document numbers are not supported yet.");
        if(second.charAt(9)!='0'+checkDigit(n)||second.charAt(19)!='0'+checkDigit(b)||second.charAt(27)!='0'+checkDigit(e))throw new IllegalArgumentException("Check the document number and date check digits.");
        return create(n,b,e,first+"\n"+second);
    }
    /** Keeps source for comparison only while the displayed inputs are unchanged. */
    public ChipAccess reviewed(String number,String birth,String expiry){
        ChipAccess edited=create(number,birth,expiry,null);
        boolean unchanged=this.number.equals(edited.number)&&this.birth.equals(edited.birth)&&this.expiry.equals(edited.expiry);
        return new ChipAccess(edited.number,edited.birth,edited.expiry,sourceMrz,!unchanged);
    }
}
