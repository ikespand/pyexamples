package com.example.mrz;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class ShareFormatterTest {
    @Test public void reviewIsFlaggedAndRawMrzIsNotShared() throws Exception {
        JSONObject fields=new JSONObject().put("given_names","ANNA").put("document_number","TEST123");
        JSONObject result=new JSONObject().put("status","review").put("fields",fields).put("rawText","SECRET_RAW_MRZ");
        String text=ShareFormatter.format(result);
        assertTrue(text.contains("REVIEW REQUIRED"));
        assertTrue(text.contains("TEST123"));
        assertFalse(text.contains("SECRET_RAW_MRZ"));
    }
    @Test public void invalidResultsCannotBeShared() throws Exception {
        assertEquals("",ShareFormatter.format(new JSONObject().put("status","invalid").put("fields",new JSONObject())));
    }
}
