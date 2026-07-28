package com.mouse.util;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.wallet.Wallet;

import java.io.File;

import static com.mouse.cmd.txtio.LaunchScreen.WALLET_FILE_POST_FIX;


public record WalletNameId(Wallet wallet, String name, String id) {
    public static WalletNameId get(Wallet wallet, File file){

        String fileName = file.getName();

        String name = fileName.substring(0, fileName.length() - WALLET_FILE_POST_FIX.length());

        Sha256Hash hash = Sha256Hash.ZERO_HASH;
        if(!wallet.isEncrypted()){
            hash = Sha256Hash.of(wallet.getKeyChainSeed().getSeedBytes());
        }

        return new WalletNameId(wallet,name, hash.toString());
    }

    public int getLastBlockSeenHeight(){return wallet().getLastBlockSeenHeight();}
}
