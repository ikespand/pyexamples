package com.example.mrz;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONException;
import org.json.JSONObject;

final class ScanTiming {
    private ScanTiming() {}

    static JSONObject fromResult(JSONObject result) throws JSONException {
        JSONObject timing = new JSONObject();
        if (result.has("cameraScanMs")) {
            timing.put("total", result.getLong("cameraScanMs"));
            timing.put("capture", result.getLong("captureMs"));
        } else if (result.has("imageReadMs")) {
            timing.put("total", result.getLong("imageReadMs"));
        }
        JSONObject mrz = result.optJSONObject("timingsMs");
        if (mrz != null) {
            timing.put("mrz", mrz.getLong("total"));
            timing.put("spotting", mrz.getLong("spotting"));
            if ("specialist_fallback".equals(result.optString("recognitionSource"))) {
                timing.put("fallback", mrz.getLong("fallback"));
            }
        }
        return timing;
    }

    static void append(LinearLayout parent, JSONObject timing) {
        if (timing == null || !timing.has("total")) return;
        Context context = parent.getContext();
        TextView heading = AppUi.text(context, "Scan timing", 16, AppUi.INK, true);
        heading.setPadding(0, AppUi.dp(context, 16), 0, AppUi.dp(context, 4));
        parent.addView(heading);
        boolean camera = timing.has("capture");
        add(parent, "Total · " + timing.optLong("total") + " ms", true);
        add(parent, camera ? "From first page detection to OCR result"
                : "From photo selection to OCR result", false);
        duration(parent, timing, "capture", "Still capture and page crop");
        duration(parent, timing, "mrz", "MRZ reading");
        duration(parent, timing, "spotting", "  Initial MRZ read");
        duration(parent, timing, "fallback", "  Fallback model inference");
    }

    private static void duration(LinearLayout parent, JSONObject timing, String key, String label) {
        if (timing.has(key)) add(parent, label + " · " + timing.optLong(key) + " ms", false);
    }

    private static void add(LinearLayout parent, String value, boolean medium) {
        parent.addView(AppUi.text(parent.getContext(), value, 14, AppUi.BODY, medium));
    }
}
