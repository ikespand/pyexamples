package com.example.mrz;

/** One-shot in-memory transfer, never persisted in an Intent or saved state. */
final class NfcTransfer {
    private static ChipAccess pending;
    static synchronized void put(ChipAccess value){pending=value;}
    static synchronized ChipAccess take(){ChipAccess value=pending;pending=null;return value;}
}
