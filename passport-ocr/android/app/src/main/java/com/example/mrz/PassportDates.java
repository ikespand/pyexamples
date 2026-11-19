package com.example.mrz;

import android.content.Context;
import java.text.DateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;

final class PassportDates {
    private PassportDates() {}

    static String field(String key, String value, long scannedAt) {
        if (value.isEmpty()) return "—";
        if (!key.equals("birth_date_raw") && !key.equals("expiry_date_raw")) return value;
        if (!value.matches("[0-9]{6}")) return value;

        LocalDate reference = Instant.ofEpochMilli(scannedAt).atZone(ZoneId.systemDefault()).toLocalDate();
        int year = reference.getYear() / 100 * 100 + Integer.parseInt(value.substring(0, 2));
        int month = Integer.parseInt(value.substring(2, 4));
        int day = Integer.parseInt(value.substring(4, 6));
        if (key.equals("birth_date_raw")) {
            if (year > reference.getYear() || (year == reference.getYear()
                    && (month > reference.getMonthValue()
                    || (month == reference.getMonthValue() && day > reference.getDayOfMonth())))) {
                year -= 100;
            }
        } else {
            if (year - reference.getYear() > 50) year -= 100;
            if (reference.getYear() - year > 50) year += 100;
        }
        try {
            return LocalDate.of(year, month, day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        } catch (DateTimeException error) {
            return value;
        }
    }

    static String timestamp(Context context, long millis) {
        Date date = new Date(millis);
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date) + ", "
                + android.text.format.DateFormat.getTimeFormat(context).format(date);
    }

    static String recordTimestamp(Context context, org.json.JSONObject entry) {
        long captured = entry.optLong("capturedAt");
        if (captured > 0) {
            String label = entry.optBoolean("fromPhoto") ? "Photo scanned " : "Captured ";
            return label + timestamp(context, captured);
        }
        return "Saved " + timestamp(context, entry.optLong("savedAt"));
    }
}
