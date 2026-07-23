package com.mouse;

import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Mouse {

    //https://coinfaucet.eu/en/btc-testnet/
    public static final String coin_faucet_return_Address = "tb1qerzrlxcfu24davlur5sqmgzzgsal6wusda40er";

    public static void main(String[] args) throws IOException {

        Context context = new Context();
        BitcoinNetwork network = BitcoinNetwork.TESTNET;
        WalletAppKit kit = WalletAppKit.launch(network, new File("."), "wallet2.dat", (k) -> {      });

        BlockChain chain = kit.chain();
        chain.addNewBestBlockListener(block -> {System.out.println("block height: "+block.getHeight() );});

        //PeerGroup peerGroup = kit.peerGroup();
        //peerGroup.addOnTransactionBroadcastListener((peer, t) -> {});

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

            System.out.println("[Confidence event] txId: "+id+" sent:"+fromMe+" recived:"+toMe+" value:"+value+" "+confidenceType+" "+blockDepth);
        });

        Mouse.doSpend(kit, coin_faucet_return_Address);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutdown")));
        System.out.println("press enter to exit");
        System.in.read();

        kit.stopAsync();
        kit.awaitTerminated();
    }


    public static void doSpend(WalletAppKit kit, @Nullable String addressStr){

        Wallet wallet = kit.wallet();
        Address address = wallet.currentReceiveAddress();

        if(addressStr!=null){
            address = wallet.parseAddress(addressStr);
        }


        Coin sendAmount = Coin.ofSat(1000l);
        SendRequest sendRequest = SendRequest.to(address, sendAmount);

        System.out.println( "setup a send request" );

        sendRequest.feePerKb = Coin.ofSat(1000l);  //1 sat per VB
        System.out.println( sendRequest );
        System.out.println("sendRequest.changeAddress: "+sendRequest.changeAddress);
        System.out.println("wallet.currentChangeAddress: "+wallet.currentChangeAddress());


        try {
            wallet.completeTx(sendRequest);
        } catch (InsufficientMoneyException | Wallet.TransactionCompletionException e) {
            System.out.println(e);
        }

        Coin valueSentFromMe = sendRequest.tx.getValueSentFromMe(wallet);
        Coin valueSentToMe   = sendRequest.tx.getValueSentToMe(wallet);
        Coin value = sendRequest.tx.getValue(wallet);

        System.out.println("txId:"+sendRequest.tx.getTxId()+" fromMe:"+valueSentFromMe+" toMe:"+valueSentToMe+" value:"+value);
        System.out.println(sendRequest.tx);

        sendRequest.tx.getInputs().forEach( input -> {
            System.out.println(input);
        } );

        sendRequest.tx.getOutputs().forEach( output -> {
            System.out.println(output);
        });

        PeerGroup peerGroup = kit.peerGroup();
        TransactionBroadcast transactionBroadcast = peerGroup.broadcastTransaction(sendRequest.tx);

        CompletableFuture<TransactionBroadcast> castAndRelay = transactionBroadcast.broadcastAndAwaitRelay();

        transactionBroadcast.setProgressCallback( (double progress) -> {
            System.out.println("transactionBroadcast progress:"+progress+"%");
        } );

        try {
            TransactionBroadcast result = castAndRelay.get();
            System.out.println("TransactionBroadcast and relay "+result);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println(e);
        }

    }

}
