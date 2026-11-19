package com.example.mrz;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChipScanResultTest {
    private static final String L1="P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String L2="L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    @Test public void scannerResultSuppliesAccessFields() throws Exception {
        JSONObject scan=new JSONObject().put("status","valid").put("lines",new JSONArray().put(L1).put(L2));
        ChipAccess access=ChipScanResult.access(scan);assertEquals("L898902C3",access.number);assertEquals("740812",access.birth);assertEquals("120415",access.expiry);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidScan() throws Exception {
        ChipScanResult.access(new JSONObject().put("status","invalid").put("lines",new JSONArray().put(L1).put(L2)));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsBadAccessCheckDigitEvenIfMarkedReview() throws Exception {
        ChipScanResult.access(new JSONObject().put("status","review").put("lines",new JSONArray().put(L1).put(L2.replace("L898","L899"))));
    }
}
