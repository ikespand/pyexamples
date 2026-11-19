package com.example.mrz;
/** Conservative protocol choice; callers must not retry BAC after PACE failure. */
final class ChipAccessPolicy {
    enum Mode { PACE, BAC, UNSUPPORTED }
    static Mode choose(boolean cardAccessOrPacePresent,boolean usablePace){
        return usablePace?Mode.PACE:cardAccessOrPacePresent?Mode.UNSUPPORTED:Mode.BAC;
    }
}
