package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChipAccessTest {
    private static final String L1="P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String L2="L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    @Test public void preservesExactAccessFields(){ChipAccess a=ChipAccess.fromTd3(L1,L2);assertEquals("L898902C3",a.number);assertEquals("740812",a.birth);assertEquals("120415",a.expiry);assertNotNull(a.sourceMrz);}
    @Test public void padsShortManualNumber(){assertEquals("ABC123<<<",ChipAccess.manual("ABC123","740812","120415").number);}
    @Test public void editedInputClearsSource(){ChipAccess a=ChipAccess.fromTd3(L1,L2);assertNotNull(a.reviewed(a.number,a.birth,a.expiry).sourceMrz);assertTrue(a.reviewed("L898902C4",a.birth,a.expiry).edited);assertNotNull(a.reviewed("L898902C4",a.birth,a.expiry).sourceMrz);}
    @Test(expected=IllegalArgumentException.class) public void rejectsWrongCheckDigit(){ChipAccess.fromTd3(L1,L2.replace("L898","L899"));}
    @Test(expected=IllegalArgumentException.class) public void rejectsImpossibleDate(){ChipAccess.manual("ABC","991332","120415");}
    @Test(expected=IllegalArgumentException.class) public void rejectsExtendedNumber(){ChipAccess.fromTd3(L1,L2.substring(0,9)+"<"+L2.substring(10));}
}
