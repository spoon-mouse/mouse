package com.mouse.backend.util;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.wallet.Wallet;

import java.nio.CharBuffer;
import java.util.List;


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

    public boolean isNotEncrypted() {return ! isEncrypted();}

    public long balance() {
        return wallet.getBalance().getValue();
    }

    public String receiveAddress(){
        return wallet.currentReceiveAddress().toString();
    }

    public void encrypt(char[] password) {
        wallet.encrypt(CharBuffer.wrap(password));
    }

    public void decrypt(char[] password) {
        wallet.decrypt(CharBuffer.wrap(password));
    }

    public List<String> getMnemonic() {
        if (wallet.getKeyChainSeed() == null) return null;
        return wallet.getKeyChainSeed().getMnemonicCode();
    }

    public long getSeedCreationTime() {
        if (wallet.getKeyChainSeed() == null) return 0L;
        return wallet.getKeyChainSeed().getCreationTimeSeconds();
    }

}
