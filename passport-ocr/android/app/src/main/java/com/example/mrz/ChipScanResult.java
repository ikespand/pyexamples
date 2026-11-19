package com.example.mrz;
import org.json.JSONArray;
import org.json.JSONObject;
/** Convert a validated scan into in-memory chip access inputs. */
final class ChipScanResult {
    static ChipAccess access(JSONObject result){
        if(result==null)throw new IllegalArgumentException("No scan result");
        String status=result.optString("status");
        if(!status.equals("valid")&&!status.equals("review"))throw new IllegalArgumentException("Scan needs review or retry");
        JSONArray lines=result.optJSONArray("lines");
        if(lines==null||lines.length()!=2)throw new IllegalArgumentException("Only passport MRZs are supported");
        return ChipAccess.fromTd3(lines.optString(0),lines.optString(1));
    }
}
