package com.example.mrz;

import org.json.JSONObject;

/** Format only the fields visible on a result card; never include photo or MRZ. */
public final class ShareFormatter {
    private ShareFormatter() {}
    public static String format(JSONObject result) {
        String status=result.optString("status");
        JSONObject fields=result.optJSONObject("fields");
        if(fields==null||!(status.equals("valid")||status.equals("review")))return "";
        StringBuilder text=new StringBuilder("Scanned document information\n");
        if(status.equals("review"))text.append("REVIEW REQUIRED — some characters are unclear.\n");
        String[][] labels={{"given_names","Given names"},{"surname","Surname"},{"document_number","Document number"},{"issuer","Issuing country"},{"nationality","Nationality"},{"birth_date_raw","Date of birth (YYMMDD)"},{"expiry_date_raw","Expiry date (YYMMDD)"},{"sex","Sex"}};
        for(String[] item:labels){String value=fields.optString(item[0],"").trim();if(!value.isEmpty())text.append(item[1]).append(": ").append(value).append('\n');}
        text.append("\nPlease compare every field with the document. Dates use YYMMDD.");
        return text.toString();
    }
}
