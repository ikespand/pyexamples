package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;

public class MrzDecoderTest {
    private static final String FIRST="P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String SECOND="L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    @Test public void parsesTd3() throws Exception {
        JSONObject result=MrzDecoder.decode(FIRST+"\n"+SECOND);
        assertEquals("valid",result.getString("status"));
        assertEquals("L898902C3",result.getJSONObject("fields").getString("document_number"));
        assertEquals("ANNA MARIA",result.getJSONObject("fields").getString("given_names"));
    }
    @Test public void rejectsBadChecksum() throws Exception {
        assertEquals("invalid",MrzDecoder.decode(FIRST+"\n"+SECOND.replace("L898","L899")).getString("status"));
    }
    @Test public void flagsUnprotectedSex() throws Exception {
        String line=SECOND.substring(0,20)+"7"+SECOND.substring(21);
        JSONObject result=MrzDecoder.decode(FIRST+"\n"+line);
        assertEquals("review",result.getString("status"));
        assertEquals("Unreadable",result.getJSONObject("fields").getString("sex"));
    }
    @Test public void parsesTd1() throws Exception {
        String mrz="I<UTOD231458907<<<<<<<<<<<<<<<\n7408122F1204159UTO<<<<<<<<<<<6\nERIKSSON<<ANNA<MARIA<<<<<<<<<<";
        assertEquals("valid",MrzDecoder.decode(mrz).getString("status"));
    }
}
