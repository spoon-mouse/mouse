package com.mouse.cmd.txtio;

import com.mouse.listener.PeerAddListener;
import com.mouse.util.*;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static com.mouse.cmd.txtio.LaunchScreen.NETWORK;
import static com.mouse.util.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;


public class SendTransactionScreen {

    public enum SendTxType {
        SEND_TX, SWEEP_TX, CSV_TX;
    }

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();


    public static final int MIN_TO_BROADCAST_TXN = 3;

    public static void doTxnOfType(SendTxType txType, WalletAppKit kit) {
        try {
            switch (txType) {
                case CSV_TX:
                    AddressAmountFee addressAmountFee = get_address_amount_fee_from_gui_or_null(kit.wallet());
                    if (addressAmountFee == null) {
                        return;
                    }
                    long confimations = get_confimation_lock_gui();

                    doCheckSeqVerifyTxn(addressAmountFee, kit.wallet(), kit.peerGroup(), confimations);
                    break;
                case SEND_TX:
                    addressAmountFee = get_address_amount_fee_from_gui_or_null(kit.wallet());
                    if (addressAmountFee == null) {
                        return;
                    }
                    doSendTxn(addressAmountFee, kit.wallet(), kit.peerGroup());
                    break;
                case SWEEP_TX:
                    AddressAmountFee.getAbsFee(getFee_from_gui());
                    doSweepTxn(kit.wallet(), kit.peerGroup());
                    break;
            }
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

    public static void doSweepTxn(Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {
        Transaction tx = TxnUtil.complete_txn(wallet);
        doSendTxn(tx, wallet, peerGroup);
    }

    public static void doSendTxn(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {
        Transaction tx = TxnUtil.complete_txn(addressAmountFee, wallet);
        doSendTxn(tx, wallet, peerGroup);
    }

    public static void doCheckSeqVerifyTxn(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup, long confimations) throws InsufficientMoneyException, ExecutionException, InterruptedException {

        final Address toAddress = addressAmountFee.address();
        final Coin amount = addressAmountFee.amount();
        final Coin fee = addressAmountFee.fee();

        Transaction tx = new Transaction();

        ScriptBuilder builder = new ScriptBuilder();
        builder.number(confimations);
        builder.op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY);
        builder.op(ScriptOpCodes.OP_DROP);
        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_HASH160);
        builder.data(toAddress.getHash());
        builder.op(ScriptOpCodes.OP_EQUALVERIFY);
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        Script redeemScript = builder.build();
        Script p2wshOutputScript = ScriptBuilder.createP2WSHOutputScript(redeemScript);

        tx.addOutput(amount, p2wshOutputScript);

        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        SendRequest sendRequest = SendRequest.forTx(tx);
        sendRequest.feePerKb = fee;
        sendRequest.coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());

        wallet.completeTx(sendRequest);

        if(wallet.isAddressMine(toAddress)){
            ext.addRedeemScript(redeemScript);
            wallet.addWatchedScripts(Collections.singletonList(p2wshOutputScript));
            System.out.println( redeemScript );
            terminal.println("redeemScript: "+redeemScript.toString());
        }
        doSendTxn(sendRequest.tx, wallet, peerGroup);
    }

    public static void doSendTxn(Transaction tx , Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {

        terminal.println( TxnInfo.get(tx, wallet).toString() );

        int now = peerGroup.numConnectedPeers();
        int target = Math.max(now-2, MIN_TO_BROADCAST_TXN);
        terminal.println("broadcasting...(target: "+target+" connected: "+now+")" );

        PeerAddListener peerAdd = new PeerAddListener();
        peerGroup.addConnectedEventListener(peerAdd);

        TransactionBroadcast txnCast = peerGroup.broadcastTransaction(tx, target, true);

        txnCast.setProgressCallback(progress -> terminal.println("broadcast progress: "+String.format("%.1f", progress*100.0)+"%"));

        txnCast.broadcastOnly().get();
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

        final long fee = getFee_from_gui();

        return AddressAmountFee.get(address, amount, fee, wallet);
    }

    public static long getFee_from_gui() {
        long fee = textIO.newLongInputReader()
                .withDefaultValue(AddressAmountFee.MIN_FEE)
                .withMinVal(AddressAmountFee.MIN_FEE)
                .withMaxVal(AddressAmountFee.MAX_FEE)
                .withInputTrimming(true)
                .read("fee (sats per vbyte):");
        return fee;
    }

    private static long get_confimation_lock_gui() {

        long l = textIO.newLongInputReader()
                .withDefaultValue(1l)
                .withMinVal(1l)
                .withMaxVal(1000l)
                .withInputTrimming(true)
                .read("chain depth lock:");
        return l;
    }



    public static void fullManualTXNBuild(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {

        final Address toAddress = addressAmountFee.address();
        final Coin amount = addressAmountFee.amount();
        final Coin fee = addressAmountFee.fee();
        final Address changeAddress = wallet.freshReceiveAddress();

        Transaction tx = new Transaction();

        ScriptBuilder builder = new ScriptBuilder();
        builder.number(3);
        builder.op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY);
        builder.op(ScriptOpCodes.OP_DROP);
        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_HASH160);
        builder.data( toAddress.getHash() );
        builder.op(ScriptOpCodes.OP_EQUALVERIFY);
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        Script redeamScript = builder.build();
        Script p2wshOutputScript = ScriptBuilder.createP2WSHOutputScript(redeamScript);

        ////. P2WPKH UTXO? only works for utxo of type P2WPKH
        TransactionOutput utxo = wallet.getUnspents().getFirst();
        Coin changeAmount = utxo.getValue().minus(amount).minus(fee);
        tx.addInput(utxo);
        tx.addOutput(amount, p2wshOutputScript);
        tx.addOutput(changeAmount, changeAddress);


        final ECKey utxoKey = wallet.findKeyFromPubKeyHash(utxo.getScriptPubKey().getPubKeyHash(), null);
        final Script utxo_p2pkh_script = ScriptBuilder.createP2PKHOutputScript(utxoKey);

        TransactionSignature sig = tx.calculateWitnessSignature(0, utxoKey, utxo_p2pkh_script, utxo.getValue(), Transaction.SigHash.ALL, false);

        TransactionInput input = tx.getInput(0);
        input = input.withScriptSig(ScriptBuilder.createEmpty()).withWitness(TransactionWitness.redeemP2WPKH(sig, utxoKey));
        tx.replaceInput(0, input);


    }
}
