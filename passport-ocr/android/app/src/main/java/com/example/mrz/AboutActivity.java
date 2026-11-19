package com.example.mrz;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Local privacy, product limits, terms and open-source notices. */
public final class AboutActivity extends AppCompatActivity {
    private int dp(float value) { return AppUi.dp(this, value); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppUi.systemBars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppUi.PAPER);
        root.setPadding(dp(20), dp(10), dp(20), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = AppUi.legalLink(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = AppUi.text(this, "About Pageport", 20, AppUi.INK, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(16), 0, dp(20));
        TextView subtitle = AppUi.text(this, "Version " + versionName(), 14, AppUi.BODY, false);
        subtitle.setPadding(0, 0, 0, dp(16));
        content.addView(subtitle);

        addLink(content, "Privacy", () -> showDocument("Privacy",
                "Scanning runs on this phone. Unsaved results and page images are kept in memory. "
                + "When you tap Save scan, Pageport encrypts the extracted fields and stores them on this phone. "
                + "You can delete them from Saved scans. "
                + "If you choose Share details, Android passes the displayed fields to the app you select."));
        addLink(content, "Terms of use", () -> showDocument("Terms of use",
                "Use Pageport only with passports you are allowed to handle. It supports standard two-line "
                + "passport MRZs. Compare the displayed fields with the passport before using them. "
                + "Pageport does not verify identity or passport chip authenticity. The software is provided as is."));
        addLink(content, "Open-source licenses", this::showLicenses);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return "unavailable";
        }
    }

    private void addLink(LinearLayout parent, String heading, Runnable open) {
        Button button = AppUi.button(this, heading, false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setOnClickListener(v -> open.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(button, params);
    }

    private void showLicenses() {
        String[] titles = {"Third-party notices", "Apache License 2.0", "Packaged library license"};
        new AlertDialog.Builder(this)
                .setTitle("Open-source licenses")
                .setItems(titles, (dialog, which) -> {
                    String text;
                    if (which == 0) {
                        text = readAsset("licenses/THIRD_PARTY_NOTICES.md")
                                .replaceAll("(?m)^#{1,2} ", "")
                                .replace("\n- ", "\n• ");
                    } else if (which == 1) {
                        text = readAsset("licenses/Apache-2.0.txt");
                    } else {
                        text = readApkResource("META-INF/LICENSE.md");
                    }
                    showDocument(titles[which], text);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "The bundled notice could not be opened. Please check the app installation.";
        }
    }

    private String readApkResource(String name) {
        try (ZipFile apk = new ZipFile(getApplicationInfo().sourceDir)) {
            ZipEntry entry = apk.getEntry(name);
            if (entry == null) return "No merged Android library license file was packaged.";
            try (InputStream input = apk.getInputStream(entry);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception error) {
            return "The packaged library license notice could not be opened.";
        }
    }

    private void showDocument(String title, String body) {
        ScrollView scroll = new ScrollView(this);
        TextView text = AppUi.text(this, body, 12, AppUi.BODY, false);
        text.setTextIsSelectable(true);
        text.setLineSpacing(dp(3), 1f);
        text.setPadding(dp(16), dp(8), dp(16), dp(8));
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }
}
