package com.example.mrz;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SavedScansActivity extends AppCompatActivity {
    static final String EXTRA_SCAN_ID = "scan_id";
    private LinearLayout content;
    private ScrollView scroll;
    private TextView heading;
    private String selectedId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        AppUi.systemBars(this);
        selectedId = state == null ? getIntent().getStringExtra(EXTRA_SCAN_ID)
                : state.getString(EXTRA_SCAN_ID);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), 0);
        root.setBackgroundColor(AppUi.PAPER);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = AppUi.legalLink(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> back());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        heading = AppUi.text(this, "", 20, AppUi.INK, true);
        header.addView(heading);
        root.addView(header);

        scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(20), 0, dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_SCAN_ID, selectedId);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() { back(); }

    private void back() {
        if (selectedId == null) {
            finish();
        } else {
            selectedId = null;
            render();
        }
    }

    private void render() {
        content.removeAllViews();
        scroll.scrollTo(0, 0);
        heading.setText("Saved scans");
        try {
            JSONArray entries = SavedScanStore.load(this);
            for (int i = entries.length() - 1; i >= 0; i--) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.getString("id").equals(selectedId)) {
                    showDetail(entry);
                    return;
                }
            }
            selectedId = null;
            if (entries.length() == 0) {
                addText(content, "No saved scans", 20, AppUi.INK, true);
                addText(content, "After checking a scan, tap Save scan to see it here.",
                        15, AppUi.BODY, false);
            }
            for (int i = entries.length() - 1; i >= 0; i--) addEntry(entries.getJSONObject(i));
        } catch (Exception error) {
            content.removeAllViews();
            addText(content, "Couldn’t open saved scans.", 16, AppUi.BODY, false);
        }
    }

    private void addEntry(JSONObject entry) throws Exception {
        String id = entry.getString("id");
        JSONObject fields = entry.getJSONObject("fields");
        String date = PassportDates.recordTimestamp(this, entry);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(dp(68));
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setBackground(AppUi.outlined(this, AppUi.CARD, AppUi.LINE, 12));
        row.setContentDescription(name(fields) + ", " + date);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            selectedId = id;
            render();
        });
        addText(row, name(fields), 17, AppUi.INK, true);
        addText(row, date, 13, AppUi.BODY, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        content.addView(row, params);
    }

    private void showDetail(JSONObject entry) throws Exception {
        heading.setText("Saved passport");
        JSONObject fields = entry.getJSONObject("fields");
        addText(content, name(fields), 28, AppUi.INK, true);
        addText(content, PassportDates.recordTimestamp(this, entry), 14, AppUi.BODY, false);
        long referenceTime = entry.optLong("capturedAt", entry.optLong("savedAt"));
        for (String[] field : SavedScanStore.FIELDS) {
            TextView label = addText(content, field[1], 13, AppUi.BODY, true);
            label.setPadding(0, dp(16), 0, 0);
            String value = fields.optString(field[0]);
            addText(content, PassportDates.field(field[0], value, referenceTime), 16, AppUi.INK, false);
        }
        TextView note = addText(content, "Compare these fields with the passport. Date centuries are inferred from the two-digit years in the MRZ.",
                14, AppUi.BODY, false);
        note.setPadding(0, dp(20), 0, dp(12));
        Button delete = AppUi.button(this, "Delete scan", false);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete saved scan?")
                .setMessage("This removes the saved passport details from Pageport.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteSelected())
                .show());
        content.addView(delete, new LinearLayout.LayoutParams(-1, -2));
        ScanTiming.append(content, entry.optJSONObject("timing"));
    }

    private void deleteSelected() {
        try {
            SavedScanStore.delete(this, selectedId);
            if (selectedId.equals(getIntent().getStringExtra(EXTRA_SCAN_ID))) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_SCAN_ID, selectedId));
            }
            selectedId = null;
            render();
        } catch (Exception error) {
            Toast.makeText(this, "Couldn’t delete scan.", Toast.LENGTH_LONG).show();
        }
    }

    private TextView addText(LinearLayout parent, String copy, int size, int color, boolean medium) {
        TextView text = AppUi.text(this, copy, size, color, medium);
        parent.addView(text);
        return text;
    }

    private static String name(JSONObject fields) {
        String name = (fields.optString("given_names") + " " + fields.optString("surname")).trim();
        return name.isEmpty() ? "Passport scan" : name;
    }

    private int dp(float value) { return AppUi.dp(this, value); }
}
