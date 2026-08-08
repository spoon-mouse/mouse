package com.mouse.backend.util;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.wallet.Wallet;


public record MetaWallet(Wallet wallet, String name, String id) {
    public static MetaWallet get(String walletName, Wallet wallet){

        Sha256Hash hash = Sha256Hash.ZERO_HASH;
        if(!wallet.isEncrypted()){
            hash = Sha256Hash.of(wallet.getKeyChainSeed().getSeedBytes());
        }
        return new MetaWallet(wallet, walletName, hash.toString());
    }

    public int blockHeight(){
        return wallet.getLastBlockSeenHeight();
    }

    public boolean isEncrypted() {
        return wallet.isEncrypted();
    }

    public long balance() {
        return wallet.getBalance().getValue();
    }

    public String reciveAddress(){
        return wallet.currentReceiveAddress().toString();
    }

}
