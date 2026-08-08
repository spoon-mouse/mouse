package com.mouse.backend.util;

import org.bitcoinj.base.Sha256Hash;

public record UtxoId(String txId, int outputIdx, Sha256Hash txIdHash){

    public static UtxoId get(String utxoUrl){

        final String[] split = utxoUrl.split(":");
        String id = split[0];
        int idx = Integer.parseInt(split[1]);

        Sha256Hash hash = Sha256Hash.wrap(id);

        return new UtxoId(id, idx, hash);
    }
}
