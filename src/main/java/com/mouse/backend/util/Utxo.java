package com.mouse.backend.util;

import org.bitcoinj.base.Sha256Hash;

public record Utxo(String txId, int idx, String address, boolean scv, long relLock, long value, long depth){

}
