package com.mouse.cmd.txtio;

import com.mouse.listener.PeerAddListener;
import com.mouse.util.TxCastCallBack;
import com.mouse.util.TxnInfo;
import com.mouse.util.AddressAmountFee;
import com.mouse.util.TxnUtil;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.exceptions.AddressFormatException;
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
            if (addressAmountFee == null) {
                return;
            }
            sendTxn(addressAmountFee, kit.wallet(), kit.peerGroup());
        }catch (AddressFormatException e){
            terminal.println("invalid address: "+e.getMessage());
        } catch (IllegalArgumentException | InsufficientMoneyException | Wallet.TransactionCompletionException e) {
            terminal.println(e.getMessage());
        }catch (VerificationException e){
            terminal.println(e.getMessage());
            System.out.println(e);
        } catch (ExecutionException | InterruptedException | IllegalMonitorStateException e ) {
            System.out.println(e);
        }

        try{Thread.sleep(3000);} catch (InterruptedException e) {}
    }


    public static void sendTxn(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {

        Transaction tx = TxnUtil.setup_txn(addressAmountFee, wallet);

        terminal.println( TxnInfo.get(tx, wallet).toString() );

        int now = peerGroup.numConnectedPeers();
        int target = Math.max(now-2, MIN_TO_BROADCAST_TXN);
        terminal.println("broadcasting...(target: "+target+" connected: "+now+")" );

        PeerAddListener peerAdd = new PeerAddListener();
        peerGroup.addConnectedEventListener(peerAdd);

        TransactionBroadcast txnCast = peerGroup.broadcastTransaction(tx, target, true);

        txnCast.setProgressCallback(progress -> terminal.println("broadcast progress: "+String.format("%.1f", progress*100.0)+"%"));

        txnCast.broadcast().get();
        terminal.println("broadcast: done");
        txnCast.awaitRelayed().get();
        terminal.println("relayed: done");
        peerGroup.removeConnectedEventListener(peerAdd);

        wallet.maybeCommitTx(tx);
    }


    private static AddressAmountFee get_address_amount_fee_from_gui_or_null(Wallet wallet) throws AddressFormatException {
        String address = textIO.newStringInputReader()
                .withMinLength(0)
                .withMaxLength(62)
                .withInputTrimming(true)
                .withIgnoreCase()
                .read("address to:");

        if(address==null || address.isEmpty()){
            return null;
        }

        wallet.parseAddress(address);


        long amount = textIO.newLongInputReader()
                .withMinVal(1L)
                .withInputTrimming(true)
                .read("amount (sats):");

        long fee = textIO.newLongInputReader()
                .withDefaultValue(1L)
                .withMinVal(AddressAmountFee.MIN_FEE)
                .withMaxVal(AddressAmountFee.MAX_FEE)
                .withInputTrimming(true)
                .read("fee (sats per vbyte):");

        return AddressAmountFee.get(address, amount, fee, wallet);
    }


}
