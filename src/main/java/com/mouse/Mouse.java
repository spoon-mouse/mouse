package com.mouse;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.wallet.Wallet;

public class Mouse {

    public static void main(String[] args){

        System.out.println("mouse");
        Wallet wallet = Wallet.createDeterministic(BitcoinNetwork.TESTNET, ScriptType.P2PKH);
        System.out.println(wallet.getKeyChainSeed().getMnemonicString());

    }
}
