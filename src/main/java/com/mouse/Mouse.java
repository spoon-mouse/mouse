package com.mouse;


import com.diogonunes.jcolor.Attribute;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.utils.ListenableCompletableFuture;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


import static com.diogonunes.jcolor.Ansi.colorize;
import static com.diogonunes.jcolor.Attribute.*;

public class Mouse {

    //https://coinfaucet.eu/en/btc-testnet/
    public static final String coin_faucet_return_Address = "tb1qerzrlxcfu24davlur5sqmgzzgsal6wusda40er";

    public static String password;

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        password = Files.readString(Paths.get("wallet2.pass.wallet"));

        Context context = new Context();
        BitcoinNetwork network = BitcoinNetwork.TESTNET;
        WalletAppKit kit = WalletAppKit.launch(network, new File("."), "wallet2.dat", (k) -> {      });
        Wallet wallet = kit.wallet();
        if(!wallet.isEncrypted()){
            wallet.encrypt(password);
        }

        printTxnsInWallet(wallet);
        printTxnsInWalletSimple(wallet);

        BlockChain chain = kit.chain();
        //chain.addNewBestBlockListener(block -> {System.out.println("block height: "+block.getHeight() );});

        PeerGroup peerGroup = kit.peerGroup();
        ListenableCompletableFuture<List<Peer>> listListenableCompletableFuture = peerGroup.waitForPeers(1);
        listListenableCompletableFuture.get();

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

        //addConfidenceListener(wallet);
        Mouse.doSpend(kit, coin_faucet_return_Address);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutdown")));
        System.out.println("press enter to exit");
        System.in.read();
        System.out.println("await termination");
        kit.stopAsync();
        kit.awaitTerminated();
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

    private static void printTxnsInWallet(Wallet wallet) {

        AsciiTable table = new AsciiTable();
        table.getRenderer().setCWC(new CWC_LongestWord());
        table.setPaddingLeftRight(2);
        table.addRule();
        table.addRow("id", "type", "amount", "fromMe", "toMe", "value", "fee", "confidenceType", "blockDepth");
        table.addRule();

        List<Transaction> txns = wallet.getTransactionsByTime();
        txns.forEach( (tx)->{
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();

            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            long fromMe = tx.getValueSentFromMe(wallet).getValue();
            long toMe = tx.getValueSentToMe(wallet).getValue();
            long value = tx.getValue(wallet).getValue();
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }
            TypeAmount tm = getTypeAmount(fromMe, toMe, fee, value);

            table.addRow(id, tm.type(), tm.amount(), fromMe, toMe, value, fee, confidenceType, blockDepth);
        });

        table.addRule();
        System.out.println(table.render());
        System.out.println("transactions:"+txns.size());
    }

    private static void printTxnsInWalletSimple(Wallet wallet) {

        AsciiTable table = new AsciiTable();
        table.addRule();
        table.addRow("type", "amount", "fee");
        table.addRule();

        List<Transaction> txns = wallet.getTransactionsByTime();
        txns.forEach( (tx)->{
            final long fromMe = tx.getValueSentFromMe(wallet).getValue();
            final long toMe = tx.getValueSentToMe(wallet).getValue();
            final long value = tx.getValue(wallet).getValue();
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }
            TypeAmount result = getTypeAmount(fromMe, toMe, fee, value);

            table.addRow(result.type(), result.amount(), fee);
        });

        table.addRule();
        System.out.println(table.render());
        System.out.println("transactions:"+txns.size());
    }

    private static TypeAmount getTypeAmount(long fromMe, long toMe, Coin fee, long value) {
        long amount=0;
        String type;
        if(fromMe == 0){
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
        TypeAmount result = new TypeAmount(amount, type);
        return result;
    }

    private record TypeAmount(long amount, String type) {
    }


    public static void doSpend(WalletAppKit kit, @Nullable String addressStr){

        Wallet wallet = kit.wallet();
        Address address = wallet.currentReceiveAddress();

        if(addressStr!=null){
            address = wallet.parseAddress(addressStr);
        }
        Coin sendAmount = Coin.ofSat(1010l);

        SendRequest sendRequest = SendRequest.to(address, sendAmount);
        sendRequest.feePerKb = Coin.ofSat(1044l);  //1 sat per VB

        try {
            wallet.decrypt(password);
            wallet.completeTx(sendRequest);
        } catch (InsufficientMoneyException | Wallet.TransactionCompletionException e) {
            System.out.println(e);
        } finally {
            wallet.encrypt(password);
        }

        long valueSentFromMe = sendRequest.tx.getValueSentFromMe(wallet).getValue();
        long valueSentToMe   = sendRequest.tx.getValueSentToMe(wallet).getValue();
        long value = sendRequest.tx.getValue(wallet).getValue();
        long fee = sendRequest.tx.getFee().getValue();
        long amount = (valueSentFromMe-valueSentToMe) - fee;
        long total = amount + fee;
        System.out.println("Sending txId:"+sendRequest.tx.getTxId()+" Amount:"+amount+" fee:"+fee+" total:"+total+ " value:"+value);

        PeerGroup peerGroup = kit.peerGroup();
        TransactionBroadcast transactionBroadcast = peerGroup.broadcastTransaction(sendRequest.tx, 3, true);
        transactionBroadcast.setProgressCallback( (double progress) -> {
            System.out.println("transaction progress:"+progress+"%");
        } );

        CompletableFuture<TransactionBroadcast> cast = transactionBroadcast.broadcastOnly();
        //CompletableFuture<TransactionBroadcast> sent = transactionBroadcast.awaitSent();
        CompletableFuture<TransactionBroadcast> relay = transactionBroadcast.awaitRelayed();

        try {
            cast.get();
            System.out.println("Broadcast");
            relay.get();
            System.out.println("relayed");
        } catch (InterruptedException | ExecutionException e) {
            System.out.println(e);
        }

    }

}
