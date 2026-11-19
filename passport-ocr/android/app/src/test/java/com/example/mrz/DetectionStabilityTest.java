package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
public class DetectionStabilityTest {
    @Test public void freezesOnlyAfterTwoSimilarCandidates(){DetectionStability s=new DetectionStability();assertFalse(s.accept(10,20,100,30));assertTrue(s.accept(12,21,100,30));}
    @Test public void movingOrResetRequiresAnotherFrame(){DetectionStability s=new DetectionStability();assertFalse(s.accept(0,0,100,20));assertFalse(s.accept(200,0,100,20));s.reset();assertFalse(s.accept(200,0,100,20));}
}
