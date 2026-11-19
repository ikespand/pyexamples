package com.example.mrz;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only explicitly saved OCR fields in the app's private, non-backed-up storage. */
final class SavedScanStore {
    private static final String KEY_ALIAS = "pageport.saved-scans.v1";
    private static final int IV_BYTES = 12;
    static final String[][] FIELDS = {
            {"given_names", "Given names"}, {"surname", "Surname"},
            {"document_number", "Passport number"}, {"issuer", "Issuing country"},
            {"nationality", "Nationality"}, {"birth_date_raw", "Date of birth"},
            {"expiry_date_raw", "Expiry date"}, {"sex", "Sex"}
    };

    private SavedScanStore() {}

    static synchronized JSONArray load(Context context) throws Exception {
        byte[] data;
        AtomicFile file = file(context);
        try {
            data = file.readFully();
        } catch (FileNotFoundException error) {
            if (file.getBaseFile().exists()) throw error;
            return new JSONArray();
        }
        if (data.length < IV_BYTES + 16) throw new IOException("Invalid saved scans file");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false),
                new GCMParameterSpec(128, Arrays.copyOf(data, IV_BYTES)));
        return new JSONArray(new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES),
                StandardCharsets.UTF_8));
    }

    static synchronized String save(Context context, JSONObject fields, long capturedAt,
                                    boolean fromPhoto, JSONObject timing) throws Exception {
        JSONArray entries = load(context);
        JSONObject displayed = new JSONObject();
        for (String[] field : FIELDS) displayed.put(field[0], fields.optString(field[0], ""));
        String id = UUID.randomUUID().toString();
        entries.put(new JSONObject().put("id", id).put("savedAt", System.currentTimeMillis())
                .put("capturedAt", capturedAt).put("fromPhoto", fromPhoto)
                .put("timing", timing).put("fields", displayed));
        write(context, entries);
        return id;
    }

    static JSONObject latestMatch(Context context, JSONObject fields) throws Exception {
        String issuer = fields.optString("issuer");
        String number = fields.optString("document_number");
        String birth = fields.optString("birth_date_raw");
        if (!issuer.matches("[A-Z][A-Z<]{2}") || !number.matches("[A-Z0-9<]*[A-Z0-9][A-Z0-9<]*")
                || !birth.matches("[0-9]{6}")) return null;

        JSONArray entries = load(context);
        JSONObject latest = null;
        long latestTime = 0;
        for (int i = entries.length() - 1; i >= 0; i--) {
            JSONObject entry = entries.getJSONObject(i);
            JSONObject saved = entry.getJSONObject("fields");
            if (issuer.equals(saved.optString("issuer")) && number.equals(saved.optString("document_number"))
                    && birth.equals(saved.optString("birth_date_raw"))) {
                long time = entry.optLong("capturedAt", entry.optLong("savedAt"));
                if (time > latestTime) {
                    latest = entry;
                    latestTime = time;
                }
            }
        }
        return latest;
    }

    static synchronized void delete(Context context, String id) throws Exception {
        JSONArray entries = load(context);
        for (int i = 0; i < entries.length(); i++) {
            if (id.equals(entries.getJSONObject(i).getString("id"))) {
                entries.remove(i);
                write(context, entries);
                return;
            }
        }
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "saved-scans.v1"));
    }

    private static SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;
        if (!create) throw new IOException("Saved scans key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static void write(Context context, JSONArray entries) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] iv = cipher.getIV();
        if (iv.length != IV_BYTES) throw new IOException("Unexpected encryption IV size");
        byte[] ciphertext = cipher.doFinal(entries.toString().getBytes(StandardCharsets.UTF_8));
        AtomicFile file = file(context);
        FileOutputStream output = file.startWrite();
        try {
            output.write(iv);
            output.write(ciphertext);
            output.getFD().sync();
            file.finishWrite(output);
        } catch (Exception error) {
            file.failWrite(output);
            throw error;
        }
    }
}
