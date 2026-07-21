package com.mouse;

import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.Wallet;

public class Mouse {

    record Point(int x, int y) {}

    public static void main(String[] args){

        Point p = new Point(1,1);


        System.out.println("mouse");

        NetworkParameters params = TestNet3Params.get();
        Wallet wallet = Wallet.createDeterministic(params, ScriptType.P2PKH);
        //System.out.println(wallet.getKeyChainSeed().getMnemonicString());


    }
}
