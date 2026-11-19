package com.example.mrz;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Shared visual language for the small, local passport tools. */
final class AppUi {
    static final int PAPER = 0xfff4f0e7;
    static final int CARD = 0xfffffdf8;
    static final int INK = 0xff19354d;
    static final int BODY = 0xff43586a;
    static final int MUTED = 0xff667780;
    static final int SEA = 0xff397773;
    static final int SEA_PALE = 0xffe5efeb;
    static final int CORAL = 0xffbd6649;
    static final int CORAL_INK = 0xff934a34;
    static final int CORAL_PALE = 0xfff5e7df;
    static final int LINE = 0xffded8ca;
    static final int WHITE = Color.WHITE;

    private AppUi() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    static GradientDrawable outlined(Context context, int color, int stroke, int radius) {
        GradientDrawable drawable = shape(context, color, radius);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static TextView text(Context context, String value, int size, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    static Button button(Context context, String value, boolean primary) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? WHITE : INK);
        button.setBackground(primary ? shape(context, INK, 16) : outlined(context, CARD, LINE, 16));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    static void systemBars(Activity activity) {
        activity.getWindow().setStatusBarColor(PAPER);
        activity.getWindow().setNavigationBarColor(PAPER);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static TextView legalLink(Context context, String value) {
        TextView link = text(context, value, 12, BODY, true);
        link.setGravity(Gravity.CENTER);
        link.setMinHeight(dp(context, 44));
        link.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        link.setBackground(shape(context, Color.TRANSPARENT, 12));
        link.setFocusable(true);
        link.setClickable(true);
        return link;
    }
}
