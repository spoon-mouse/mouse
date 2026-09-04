package com.mouse.backend.txn;

/**
 * Representation of transaction confidence, hiding BitcoinJ specifics from the UI.
 */
public enum TxStatus {
    PENDING,
    BUILDING,
    CONFIRMED,
    DEAD,
    UNKNOWN
}
