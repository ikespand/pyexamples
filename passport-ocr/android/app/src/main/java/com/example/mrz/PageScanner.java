package com.example.mrz;

import android.graphics.Bitmap;
import org.json.JSONObject;
import org.opencv.core.Point;

/** On-device page localization, rectification and MRZ reading contract. */
public interface PageScanner extends AutoCloseable {
    Point[] detectPage(Bitmap image) throws Exception;
    Bitmap rectifyPage(Bitmap image, Point[] corners) throws Exception;
    JSONObject scanPage(Bitmap rectifiedPage) throws Exception;
    @Override void close();
}
