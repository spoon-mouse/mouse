package com.mouse;

import org.bitcoinj.base.Network;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.BitcoinNetworkParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.protobuf.wallet.Protos;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;

public class Mouse {

    public static void main(String[] args){
        System.out.println("mouse");

        NetworkParameters params = TestNet3Params.get();
        Wallet wallet = Wallet.createDeterministic(params, ScriptType.P2PKH);
        //System.out.println(wallet.getKeyChainSeed().getMnemonicString());

    }
}
