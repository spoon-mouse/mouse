package com.mouse.cmd.txtio;

import com.mouse.util.TxCastCallBack;
import com.mouse.util.TxnInfo;
import com.mouse.util.AddressAmountFee;
import com.mouse.util.TxnUtil;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.utils.ListenableCompletableFuture;
import org.bitcoinj.wallet.Wallet;

import java.util.concurrent.ExecutionException;


public class SendTransactionScreen {

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();


    public static final int MIN_TO_BROADCAST_TXN = 3;

    public static void show(String walletName, WalletAppKit kit) {
        try {
            AddressAmountFee addressAmountFee = get_address_amount_fee_from_gui_or_null(kit.wallet());
            if (addressAmountFee==null) {
                return;
            }
            sendTxn(addressAmountFee, kit.wallet(), kit.peerGroup());
        } catch (InsufficientMoneyException | Wallet.TransactionCompletionException | IllegalArgumentException e) {
            terminal.println(e.getMessage());
        }catch (VerificationException e){
            terminal.println(e.getMessage());
            System.out.println(e);
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException | IllegalMonitorStateException e ) {
            System.out.println(e);
        }
    }


    public static void sendTxn(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {

        Transaction tx = TxnUtil.setup_txn(addressAmountFee, wallet);

        terminal.println( TxnInfo.get(tx, wallet).toString() );

        int now = peerGroup.numConnectedPeers();
        terminal.println("broadcasting...("+now+"/"+MIN_TO_BROADCAST_TXN+")" );
        peerGroup.addConnectedEventListener(  (p, count)  -> {terminal.println( "broadcasting...("+count+"/"+MIN_TO_BROADCAST_TXN+")"  );});

        TransactionBroadcast txnCast = peerGroup.broadcastTransaction(tx, MIN_TO_BROADCAST_TXN, true);
        txnCast.setProgressCallback(progress -> terminal.println("broadcast progress: "+String.format("%.1f", progress*100.0)+"%"));

        txnCast.broadcast().get();
        terminal.println("broadcast: done");
        txnCast.awaitRelayed().get();
        terminal.println("relayed: done");

        wallet.maybeCommitTx(tx);
    }


    private static AddressAmountFee get_address_amount_fee_from_gui_or_null(Wallet wallet) {
        String address = textIO.newStringInputReader()
                .withMinLength(0)
                .withMaxLength(62)
                .withInputTrimming(true)
                .withIgnoreCase()
                .read("address to:");

        if(address==null || address.isEmpty()){
            return null;
        }
        if(address.length()<26){
            terminal.println("address length less that 26");
            return null;
        }

        long amount = textIO.newLongInputReader()
                .withMinVal(1L)
                .withInputTrimming(true)
                .read("amount (sats):");

        long fee = textIO.newLongInputReader()
                .withDefaultValue(1L)
                .withMinVal(1L)
                .withMaxVal(99L)
                .withInputTrimming(true)
                .read("fee (sats per vbyte):");

        return AddressAmountFee.get(address, amount, fee, wallet);
    }


}
