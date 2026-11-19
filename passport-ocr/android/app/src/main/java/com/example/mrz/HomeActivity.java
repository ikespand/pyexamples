package com.example.mrz;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppUi.systemBars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        root.setBackgroundColor(AppUi.PAPER);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_pageport);
        header.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView name = AppUi.text(this, getString(R.string.app_name), 20, AppUi.INK, true);
        name.setPadding(dp(10), 0, 0, 0);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView about = AppUi.legalLink(this, "About");
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        header.addView(about, new LinearLayout.LayoutParams(dp(64), -2));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(40), 0, dp(16));
        content.addView(AppUi.text(this, "Passport scanner", 30, AppUi.INK, true));
        TextView instruction = AppUi.text(this,
                "Open your passport to the photo page. Keep the whole page and both lines at the bottom in view.",
                16, AppUi.BODY, false);
        instruction.setPadding(0, dp(8), 0, dp(24));
        content.addView(instruction);
        addAction(content, "Scan passport", true, MainActivity.class);
        addAction(content, "Saved scans", false, SavedScansActivity.class);
        addAction(content, "Read passport chip", false, ChipActivity.class);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addAction(LinearLayout parent, String label, boolean primary, Class<?> destination) {
        Button action = AppUi.button(this, label, primary);
        action.setMinimumHeight(dp(54));
        action.setOnClickListener(v -> {
            Intent intent = new Intent(this, destination);
            if (destination == ChipActivity.class) intent.putExtra("start_scan", true);
            startActivity(intent);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(action, params);
    }

    private int dp(float value) { return AppUi.dp(this, value); }
}
