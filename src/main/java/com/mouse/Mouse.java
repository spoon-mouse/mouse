package com.mouse;


import com.diogonunes.jcolor.Attribute;
import de.vandermeer.asciitable.AsciiTable;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.utils.ListenableCompletableFuture;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


import static com.diogonunes.jcolor.Ansi.colorize;
import static com.diogonunes.jcolor.Attribute.*;

public class Mouse {

    //https://coinfaucet.eu/en/btc-testnet/
    public static final String coin_faucet_return_Address = "tb1qerzrlxcfu24davlur5sqmgzzgsal6wusda40er";

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        Context context = new Context();
        BitcoinNetwork network = BitcoinNetwork.TESTNET;
        WalletAppKit kit = WalletAppKit.launch(network, new File("."), "wallet2.dat", (k) -> {      });
        Wallet wallet = kit.wallet();

        printTxnsInWallet(wallet);
        printTxnsInWalletSimple(wallet);

        BlockChain chain = kit.chain();
        chain.addNewBestBlockListener(block -> {System.out.println("block height: "+block.getHeight() );});

        PeerGroup peerGroup = kit.peerGroup();
        ListenableCompletableFuture<List<Peer>> listListenableCompletableFuture = peerGroup.waitForPeers(1);
        listListenableCompletableFuture.get();

        System.out.println(wallet.getKeyChainSeed().getMnemonicString());
        System.out.println("total in: "+ wallet.getTotalReceived()+" total out:"+wallet.getTotalSent()+" balance:"+ wallet.getBalance().toFriendlyString());
        System.out.println("current receive address: "+ wallet.currentReceiveAddress().toString());
        System.out.println("chain height: "+ chain.getBestChainHeight());



        wallet.addKeyChainEventListener(keys -> System.out.println("new key added"));
        wallet.addScriptsChangeEventListener((eWallet, scripts, isAddingScripts) -> System.out.println("new script added"));

        wallet.addCoinsSentEventListener((eWallet, tx, prevBalance, newBalance) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(eWallet);
            Coin fee = tx.getFee();

            System.out.println("Send transaction id: "+id+" confidence:"+confidenceType+" blockDepth:"+blockDepth);
            System.out.println("Sent:"+value+" fee:"+fee+" old balance:"+prevBalance+" new balance:"+newBalance);
        });

        wallet.addCoinsReceivedEventListener((eWallet, tx, prevBalance, newBalance) -> {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(eWallet);


            System.out.println("Receive transaction id: "+id+" confidence:"+confidenceType+" blockDepth:"+blockDepth);
            System.out.println("Receive:"+value+" old balance:"+prevBalance+" new balance:"+newBalance);
        });

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

        //Mouse.doSpend(kit, coin_faucet_return_Address);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutdown")));
        System.out.println("press enter to exit");
        System.in.read();

        kit.stopAsync();
        kit.awaitTerminated();
    }

    private static void printTxnsInWallet(Wallet wallet) {

        AsciiTable table = new AsciiTable();
        table.addRule();
        table.addRow("type", "fromMe", " toMe", "value", "fee", "confidenceType", "blockDepth");
        table.addRule();

        wallet.getTransactionsByTime().forEach( (tx)->{
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();

            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            long fromMe = tx.getValueSentFromMe(wallet).getValue();
            long toMe = tx.getValueSentToMe(wallet).getValue();
            long value = tx.getValue(wallet).getValue();
            Coin fee = tx.getFee();

            String type="NOT SURE";
            if(fromMe==0){
                type="RECEIVE";
            }else if( value < 0 ){
                //I have sent but did I send to my self ?
                if(fee != null && (value+fee.getValue())==0){
                    type="MOVED";
                }else{
                    type="SENT";
                }
            }
            if(fee==null){
                fee=Coin.ZERO;
            }
            table.addRow(type, fromMe, toMe, value, fee, confidenceType, blockDepth);
        });

        table.addRule();
        System.out.println(table.render());
    }

    private static void printTxnsInWalletSimple(Wallet wallet) {

        AsciiTable table = new AsciiTable();
        table.addRule();
        table.addRow("type", "amount", "fee");
        table.addRule();

        wallet.getTransactionsByTime().forEach( (tx)->{

            final long fromMe = tx.getValueSentFromMe(wallet).getValue();
            final long toMe = tx.getValueSentToMe(wallet).getValue();
            final long value = tx.getValue(wallet).getValue();
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }
            long amount=0;
            String type;
            if(fromMe==0){
                type="RECEIVE";
                amount = toMe - fee.getValue();
            }else{
                if( Math.abs(value) == fee.getValue() ){
                    type="MOVED";
                    amount = fromMe;
                }else{
                    type="SENT";
                    amount = (fromMe - toMe) - fee.getValue();
                }
            }

            table.addRow(type, amount, fee);
        });

        table.addRule();
        System.out.println(table.render());
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
            input.getScriptSig();
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
