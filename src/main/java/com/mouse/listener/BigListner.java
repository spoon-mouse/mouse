package com.mouse.listener;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.utils.ListenableCompletableFuture;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

public class BigListner {


    public static void add( WalletAppKit kit ) {
        Wallet wallet = kit.wallet();

        BlockChain chain = kit.chain();
        chain.addNewBestBlockListener(block -> {System.out.println("block height: "+block.getHeight() );});



        wallet.addKeyChainEventListener(keys -> System.out.println("new key added"));
        wallet.addScriptsChangeEventListener((eWallet, scripts, isAddingScripts) -> System.out.println("new script added"));

        wallet.addCoinsSentEventListener((eWallet, tx, prevBalance, newBalance) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(eWallet);
            Coin fee = tx.getFee();

            System.out.println("Send transaction id: " + id + " confidence:" + confidenceType + " blockDepth:" + blockDepth);
            System.out.println("Sent:" + value + " fee:" + fee + " old balance:" + prevBalance + " new balance:" + newBalance);
        });

        wallet.addCoinsReceivedEventListener((eWallet, tx, prevBalance, newBalance) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(eWallet);


            System.out.println("Receive transaction id: " + id + " confidence:" + confidenceType + " blockDepth:" + blockDepth);
            System.out.println("Receive:" + value + " old balance:" + prevBalance + " new balance:" + newBalance);
        });
    }

    private static void addConfidenceListener(Wallet wallet) {

        wallet.addTransactionConfidenceEventListener((eWallet, tx) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();

            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin fromMe = tx.getValueSentFromMe(wallet);
            Coin toMe = tx.getValueSentToMe(wallet);
            Coin value = tx.getValue(eWallet);
            System.out.println("[Confidence event] txId: "+id+" sent:"+fromMe+" recived:"+toMe+" value:"+value+" "+confidenceType+" "+blockDepth);
        });
    }

}
