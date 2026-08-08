package com.mouse.backend.txn;

import com.mouse.backend.Kit;
import com.mouse.backend.util.CoinSelectOption;
import com.mouse.backend.util.ManualCoinSelector;
import com.mouse.ui.input.AddressAmountFee;
import com.mouse.backend.hook.BroadcastProgressListener;
import com.mouse.backend.hook.PasswordPrompt;
import com.mouse.backend.csv.CsvAwareCoinSelector;
import com.mouse.backend.csv.CsvP2WshSigner;
import com.mouse.backend.csv.CsvScriptExtension;
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
import org.bitcoinj.wallet.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.mouse.backend.csv.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;
import static com.mouse.backend.csv.CsvUtil.validateConfimationCsvSequenceNumber;
import static com.mouse.backend.util.CoinSelectOption.Selector.DEFAULT;
import static com.mouse.backend.util.CoinSelectOption.Selector.HAND;
import static com.mouse.backend.util.Config.NETWORK;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;

/**
 * Pure backend transaction logic — no TextIO/TextTerminal imports anywhere in this
 * class. Anywhere a password or progress reporting is needed, it's supplied by the
 * caller via PasswordPrompt / BroadcastProgressListener rather than this class
 * reaching into the UI layer itself.
 */
public class TxnUtil {

    public static final int MIN_PEERS_CAST = 3;
    public static final int CAST_TIMEOUT = 30;
    public static final int RELAY_TIMEOUT = 10;
    private Wallet wallet;
    private String walletName;
    private PeerGroup peerGroup;

    private CoinSelector coinSelector;

    public TxnUtil(String name){
        walletName = name;
        wallet = Kit.wallet(walletName);
        peerGroup = Kit.peerGroup();

        setCoinSelector(CoinSelectOption.DEFAULT);
    }


    public void setCoinSelector(CoinSelectOption option) {
        switch (option){
            case DEFAULT:
                CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
                coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());
                break;
            case HAND:
                coinSelector = new ManualCoinSelector();
                break;
        }
    }

    public void sweepTxn(PasswordPrompt passwordPrompt, BroadcastProgressListener progress) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {
        SendRequest sendRequest = SendRequest.emptyWallet(wallet.currentReceiveAddress());
        Transaction tx = selectTxnInputs( addressAmountFee, sendRequest);
        tx = deEncryptWalletAndSignTx(tx, passwordPrompt);
        netBroadcast(tx, progress);
    }

    public void sendTxn(AddressAmountFee addressAmountFee, PasswordPrompt passwordPrompt, BroadcastProgressListener progress) throws Wallet.TransactionCompletionException, InsufficientMoneyException, ExecutionException, InterruptedException, VerificationException {

        final Address address = wallet.parseAddress( addressAmountFee.address() );
        final Coin amount = Coin.ofSat( addressAmountFee.amount() );

        SendRequest sendRequest = SendRequest.to(address, amount);
        Transaction txn = selectTxnInputs(addressAmountFee, sendRequest);
        Transaction tx = deEncryptWalletAndSignTx(txn, passwordPrompt);
        netBroadcast(tx, progress);
    }

    public void checkSeqVerifyTxn(AddressAmountFee addressAmountFee, long confimations, PasswordPrompt passwordPrompt, BroadcastProgressListener progress) throws InsufficientMoneyException, ExecutionException, InterruptedException {

        final Address toAddress = wallet.parseAddress( addressAmountFee.address() );
        final Coin amount = Coin.ofSat( addressAmountFee.amount() );
        validateConfimationCsvSequenceNumber(confimations);

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

        Transaction tx = new Transaction();
        tx.addOutput(amount, p2wshOutputScript);

        SendRequest sendRequest = SendRequest.forTx(tx);
        tx = selectTxnInputs(addressAmountFee, sendRequest);
        tx = deEncryptWalletAndSignTx(tx, passwordPrompt);

        if(wallet.isAddressMine(toAddress)){
            CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
            ext.addRedeemScript(redeemScript);
            wallet.addWatchedScripts(Collections.singletonList(p2wshOutputScript));
            progress.onEvent("redeemScript: " + redeemScript);
        }
        netBroadcast(tx, progress);
    }



    public Transaction deEncryptWalletAndSignTx(Transaction txn, PasswordPrompt passwordPrompt) throws InsufficientMoneyException, Wallet.TransactionCompletionException {

        final boolean walletEncrypted_at_start = wallet.isEncrypted();
        CharSequence password=null;
        try {
            if(walletEncrypted_at_start) {
                password = passwordPrompt.getPassword();
                wallet.decrypt(password);
            }
            txn =  signTransaction(txn);

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


    private Transaction signTransaction(Transaction txn) {

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

    private Transaction selectTxnInputs(AddressAmountFee addressAmountFee, SendRequest sendRequest) {

        sendRequest.coinSelector = coinSelector;

        Coin amount = Coin.ofSat( addressAmountFee.amount() );
        Coin fee = Coin.ofSat( addressAmountFee.fee() );
        Coin target = amount.add( fee );

        List<TransactionOutput> candidates = wallet.calculateAllSpendCandidates(true, false);
        CoinSelection selection = sendRequest.coinSelector.select(target, candidates);

        selection.outputs().stream().forEach(o -> sendRequest.tx.addInput(o));

        Coin change = selection.totalValue().subtract(amount).subtract(fee);
        if (change.isPositive()) {
            sendRequest.tx.addOutput(change, wallet.currentChangeAddress());
        }

        sendRequest.tx.setVersion(2);
        return sendRequest.tx;
    }


    public void netBroadcast(Transaction tx, BroadcastProgressListener progress) throws Wallet.TransactionCompletionException, ExecutionException, InterruptedException, VerificationException {
        progress.onEvent(TxnInfo.get(tx, wallet).toString());

        int now = peerGroup.numConnectedPeers();
        progress.onEvent("broadcasting...(target: " + MIN_PEERS_CAST + " connected: " + now + ")");

        TransactionBroadcast txnCast = peerGroup.broadcastTransaction(tx, MIN_PEERS_CAST, true);

        try {
            txnCast.awaitSent().get(CAST_TIMEOUT, TimeUnit.SECONDS);
            progress.onEvent("sent: done");

            txnCast.broadcastOnly().get(CAST_TIMEOUT, TimeUnit.SECONDS);
            progress.onEvent("broadcast: done");

            txnCast.awaitRelayed().get(RELAY_TIMEOUT, TimeUnit.SECONDS);
            progress.onEvent("relayed: done");
        } catch (TimeoutException e) { }

        wallet.maybeCommitTx(tx);
    }

}
