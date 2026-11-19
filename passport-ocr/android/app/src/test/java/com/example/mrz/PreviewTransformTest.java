package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewTransformTest {
    @Test public void scalesWithoutCrop(){assertArrayEquals(new float[]{20,40,60,80},PreviewTransform.map(10,20,20,20,100,100,200,200),0.01f);}
    @Test public void centersFullFrozenFrame(){assertArrayEquals(new float[]{0,25,100,75},PreviewTransform.map(0,0,200,100,200,100,100,100,true),0.01f);}
    @Test public void centersCroppedPreview(){assertArrayEquals(new float[]{-50,0,150,100},PreviewTransform.map(0,0,200,100,200,100,100,100),0.01f);}
}
