package com.example.mrz;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChipAccessPolicyTest {
    @Test public void usesPaceWhenAvailable(){assertEquals(ChipAccessPolicy.Mode.PACE,ChipAccessPolicy.choose(true,true));}
    @Test public void onlyUsesBacWhenPaceNotAdvertised(){assertEquals(ChipAccessPolicy.Mode.BAC,ChipAccessPolicy.choose(false,false));assertEquals(ChipAccessPolicy.Mode.UNSUPPORTED,ChipAccessPolicy.choose(true,false));}
}
