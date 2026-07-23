package com.mouse;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import java.io.File;
import java.io.IOException;

public class Mouse {

    //https://coinfaucet.eu/en/btc-testnet/

    public static void main(String[] args) throws IOException {

        Context context = new Context();
        BitcoinNetwork network = BitcoinNetwork.TESTNET;
        WalletAppKit kit = WalletAppKit.launch(network, new File("."), "wallet2.dat", (k) -> {      });

        BlockChain chain = kit.chain();
        chain.addNewBestBlockListener(block -> {
            System.out.println("block height: "+block.getHeight() );
        });

        System.out.println(kit.wallet().getKeyChainSeed().getMnemonicString());
        System.out.println("recive address: "+kit.wallet().currentReceiveAddress().toString());
        System.out.println("balance: "+ kit.wallet().getBalance().toFriendlyString());
        System.out.println("chain height: "+ chain.getBestChainHeight());

        kit.wallet().addKeyChainEventListener(keys -> System.out.println("new key added"));
        kit.wallet().addScriptsChangeEventListener((wallet, scripts, isAddingScripts) -> System.out.println("new script added"));

        kit.wallet().addCoinsSentEventListener((wallet, tx, prevBalance, newBalance) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(wallet);
            Coin fee = tx.getFee();

            System.out.println("Send transaction id: "+id+" confidence:"+confidenceType+" blockDepth:"+blockDepth);
            System.out.println("Sent:"+value+" fee:"+fee+" old balance:"+prevBalance+" new balance:"+newBalance);
        });

        kit.wallet().addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(wallet);

            System.out.println("Receive transaction id: "+id+" confidence:"+confidenceType+" blockDepth:"+blockDepth);
            System.out.println("Receive:"+value+" old balance:"+prevBalance+" new balance:"+newBalance);
        });

        kit.wallet().addTransactionConfidenceEventListener((wallet, tx) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();

            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin fromMe = tx.getValueSentFromMe(wallet);
            Coin toMe = tx.getValueSentToMe(wallet);
            Coin value = tx.getValue(wallet);

            System.out.println("txId: "+id+" sent:"+fromMe+" recived:"+toMe+" value:"+value+" "+confidenceType+" "+blockDepth);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutdown")));
        System.out.println("press enter to exit");
        System.in.read();

        kit.stopAsync();
        kit.awaitTerminated();
    }
}
