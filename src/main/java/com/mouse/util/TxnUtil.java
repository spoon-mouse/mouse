package com.mouse.util;

import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.mouse.cmd.txtio.LaunchScreen.NETWORK;
import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;
import static com.mouse.util.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;


public class TxnUtil {

    public static void sweepTxn(Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {
        //SendRequest sendRequest = SendRequest.emptyWallet(wallet.currentReceiveAddress());
    }

    public static void sendTxn(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {
        SendRequest sendRequest = SendRequest.to(addressAmountFee.address(), addressAmountFee.amount());
        Transaction txn = selectTxnInputs(addressAmountFee, sendRequest, wallet);
        Transaction tx = deEncryptWalletAndSignTx(txn, wallet);
        netBroadcast(tx, wallet, peerGroup);
    }

    public static void checkSeqVerifyTxn(AddressAmountFee addressAmountFee, Wallet wallet, PeerGroup peerGroup, long confimations) throws InsufficientMoneyException, ExecutionException, InterruptedException {
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
        builder.creationTime(Instant.now());     //META-DATA
        Script redeemScript = builder.build();

        Script p2wshOutputScript = createP2WSHOutputScript(redeemScript);
        p2wshOutputScript = Script.parse(p2wshOutputScript.program(), redeemScript.creationTime().get() );
        tx.addOutput(amount, p2wshOutputScript);


        SendRequest sendRequest = SendRequest.forTx(tx);
        tx = selectTxnInputs(addressAmountFee, sendRequest, wallet);
        tx = deEncryptWalletAndSignTx(tx, wallet);

        if(wallet.isAddressMine(toAddress)){
            CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
            ext.addRedeemScript(redeemScript);
            wallet.addWatchedScripts(Collections.singletonList(p2wshOutputScript));
            System.out.println( redeemScript );
        }
        netBroadcast(tx, wallet, peerGroup);
    }



    public static Transaction deEncryptWalletAndSignTx(Transaction txn , Wallet wallet) throws InsufficientMoneyException, Wallet.TransactionCompletionException {

        final boolean walletEncrypted_at_start = wallet.isEncrypted();
        CharSequence password=null;
        try {
            if(walletEncrypted_at_start) {
                password=get_password_from_gui();
                wallet.decrypt(password);
            }

            txn =  signTransaction(txn, wallet);

            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }

            return txn;
        }finally {
            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }
        }
    }


    private static Transaction signTransaction(Transaction txn, Wallet wallet) {

        TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(txn);

        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        CsvP2WshSigner csvP2WshSigner = new CsvP2WshSigner(ext.getRedeemScripts());
        csvP2WshSigner.signInputs(proposal, wallet);

        txn = proposal.partialTx;
        for (int i = 0; i < txn.getInputs().size(); i++) {
            TransactionInput input = txn.getInput(i);
            TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput == null) continue;

            Script scriptPubKey = connectedOutput.getScriptPubKey();
            ScriptType type = scriptPubKey.getScriptType();
            if (type == null) continue; // CSV / unrecognized — already handled by CsvP2WshSigner

            byte[] pubKeyHash = scriptPubKey.getPubKeyHash();
            ECKey key = wallet.findKeyFromPubKeyHash(pubKeyHash, null);
            if (key == null) continue;

            if (type == ScriptType.P2PKH) {
                TransactionSignature sig = txn.calculateSignature(i, key, scriptPubKey, Transaction.SigHash.ALL, false);
                Script scriptSig = ScriptBuilder.createInputScript(sig, key);
                txn.replaceInput(i, input.withScriptSig(scriptSig));
            } else if (type == ScriptType.P2WPKH) {
                Coin value = connectedOutput.getValue();
                Script scriptCode = ScriptBuilder.createP2PKHOutputScript(pubKeyHash); // synthesized, NOT scriptPubKey
                TransactionSignature sig = txn.calculateWitnessSignature(i, key, scriptCode, value, Transaction.SigHash.ALL, false);
                TransactionWitness witness = TransactionWitness.redeemP2WPKH(sig, key);
                txn.replaceInput(i, input.withWitness(witness));
            }
        }
        return txn;
    }

    private static Transaction selectTxnInputs(AddressAmountFee addressAmountFee, SendRequest sendRequest, Wallet wallet) {

        List<TransactionOutput> candidates = wallet.calculateAllSpendCandidates(true, false);
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        sendRequest.coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());
        sendRequest.missingSigsMode = Wallet.MissingSigsMode.USE_DUMMY_SIG;

        CoinSelection selection = sendRequest.coinSelector.select(addressAmountFee.amount(), candidates);

        Coin gathered = Coin.ZERO;
        for (TransactionOutput output : selection.gathered) {
            System.out.println("tx add input: "+output);
            sendRequest.tx.addInput(output);
            gathered = gathered.add(output.getValue());
        }

        Coin change = gathered.subtract(addressAmountFee.amount()).subtract(addressAmountFee.fee()); // your manual fee calc
        System.out.println("change: "+change);

        if (change.isPositive()) {
            sendRequest.tx.addOutput(change, wallet.currentChangeAddress());
        }

        sendRequest.tx.setVersion(2);

        return sendRequest.tx;
    }


    public static void netBroadcast(Transaction tx, Wallet wallet, PeerGroup peerGroup) throws Wallet.TransactionCompletionException, ExecutionException, InterruptedException, VerificationException {
        final TextTerminal terminal = TextIoFactory.getTextIO().getTextTerminal();
        terminal.println(TxnInfo.get(tx, wallet).toString());

        int now = peerGroup.numConnectedPeers();
        int target = 3;
        terminal.println("broadcasting...(target: " + target + " connected: " + now + ")");

        TransactionBroadcast txnCast = peerGroup.broadcastTransaction(tx, target, true);

        try {
            txnCast.awaitSent().get(30, TimeUnit.SECONDS);
            terminal.println("sent: done");

            txnCast.broadcastOnly().get(30, TimeUnit.SECONDS);
            terminal.println("broadcast: done");

            txnCast.awaitRelayed().get(10, TimeUnit.SECONDS);
            terminal.println("relayed: done");
        } catch (TimeoutException e) { }

        wallet.maybeCommitTx(tx);
    }
}
