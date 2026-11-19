package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
public class NfcTransferTest {
    @Test public void scannedAccessIsTransferredOnlyOnce(){
        String first="P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
        String second="L898902C36UTO7408122F1204159ZE184226B<<<<<10";
        ChipAccess scanned=ChipAccess.fromTd3(first,second);
        NfcTransfer.put(scanned);
        ChipAccess received=NfcTransfer.take();
        assertEquals("L898902C3",received.number);
        assertEquals("740812",received.birth);
        assertEquals("120415",received.expiry);
        assertEquals(first+"\n"+second,received.sourceMrz);
        assertNull(NfcTransfer.take());
    }
}
