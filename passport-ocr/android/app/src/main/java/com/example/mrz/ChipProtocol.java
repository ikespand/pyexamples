package com.example.mrz;
/** Executes exactly the selected protocol; failure never triggers a downgrade. */
final class ChipProtocol {
    interface Operations { void pace() throws Exception; void bac() throws Exception; }
    static void execute(ChipAccessPolicy.Mode mode,Operations operations) throws Exception {
        switch(mode){case PACE:operations.pace();break;case BAC:operations.bac();break;default:throw new IllegalArgumentException("Unsupported access mode");}
    }
}
