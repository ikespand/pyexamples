package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChipProtocolTest {
    @Test public void paceFailureNeverFallsBackToBac(){
        final boolean[] bac={false};
        try{ChipProtocol.execute(ChipAccessPolicy.Mode.PACE,new ChipProtocol.Operations(){public void pace() throws Exception{throw new Exception("failed");}public void bac(){bac[0]=true;}});fail("PACE failure must propagate");}catch(Exception expected){}
        assertFalse(bac[0]);
    }
    @Test public void bacOnlyWhenSelected() throws Exception {
        final boolean[] called={false};ChipProtocol.execute(ChipAccessPolicy.Mode.BAC,new ChipProtocol.Operations(){public void pace(){fail("PACE not selected");}public void bac(){called[0]=true;}});assertTrue(called[0]);
    }
}
