package com.mouse;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import java.io.File;
import java.io.IOException;

public class Mouse {

    public static void main(String[] args) throws IOException {

        Context context = new Context();
        BitcoinNetwork network = BitcoinNetwork.TESTNET;
        WalletAppKit kit = WalletAppKit.launch(network, new File("."), "wallet2.dat", (k) -> {      });

        BlockChain chain = kit.chain();
        chain.addNewBestBlockListener(block -> {
            System.out.println("new block height: "+block.getHeight() );
        });

        System.out.println(kit.wallet().getKeyChainSeed().getMnemonicString());
        System.out.println("recive address: "+kit.wallet().currentReceiveAddress().toString());
        System.out.println("balance: "+ kit.wallet().getBalance().toFriendlyString());
        System.out.println("chain height: "+ chain.getBestChainHeight());

        kit.wallet().addCoinsSentEventListener((wallet, tx, prevBalance, newBalance) -> System.out.println("coins sent"));
        kit.wallet().addKeyChainEventListener(keys -> System.out.println("new key added"));
        kit.wallet().addScriptsChangeEventListener((wallet, scripts, isAddingScripts) -> System.out.println("new script added"));

        kit.wallet().addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) -> {
            System.out.println("transaction id: " + tx.getTxId());
            System.out.println("received: " + tx.getValue(wallet));
        });

        kit.wallet().addTransactionConfidenceEventListener((wallet, tx) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            System.out.println("confidence changed: " + tx.getTxId()+" "+confidenceType);
            System.out.println("new block depth: " + confidence.getDepthInBlocks());
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutdown")));
        System.out.println("press enter to exit");
        System.in.read();

        kit.stopAsync();
        kit.awaitTerminated();
    }
}
