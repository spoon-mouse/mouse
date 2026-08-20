package com.mouse.backend.csv;

import com.mouse.backend.Kit;
import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.txn.IllegalAmountException;
import com.mouse.backend.txn.TxnInfo;
import com.mouse.backend.util.*;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.mouse.backend.csv.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;
import static com.mouse.backend.csv.CsvUtil.validateConfimationCsvSequenceNumber;
import static com.mouse.backend.util.Config.NETWORK;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;

public class CsvTxn {

    public static final int MIN_PEERS_CAST = 3;
    public static final int CAST_TIMEOUT = 10;
    public static final int RELAY_TIMEOUT = 10;
    private Wallet wallet;
    private String walletName;
    private PeerGroup peerGroup;

    private Address address;
    private Coin amount;
    private Coin feePerVkbCoin;
    private Coin feePerVbyte;
    private Coin estFee;

    private long lockDuration;
    private CoinSelector coinSelector;

    public CsvTxn(String name){
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

    public CsvTxn setAddress(String addressTxt) throws AddressFormatException{
        try{
            address = wallet.parseAddress( addressTxt );
        }catch (AddressFormatException e){
            throw new  AddressFormatException("Bad address");
        }
        return this;
    }

    public CsvTxn setAmount(long amountLong) throws IllegalAmountException {
        amount = Coin.ofSat(amountLong);
        if (amount.isZero() || amount.isNegative()) {
            throw new IllegalAmountException("Amount is invalid " + amount);
        }
        return this;
    }

    public CsvTxn setFee(double feeVbyte){
        estFee = Coin.ofSat((long)(feeVbyte * 141) );
        feePerVkbCoin = Coin.ofSat( (long) (feeVbyte * 1000));
        return this;
    }

    public CsvTxn setLockDuration(long lockDurationInBlocks){
        validateConfimationCsvSequenceNumber(lockDurationInBlocks);
        lockDuration = lockDurationInBlocks;
        return this;
    }

    public Script createRedeemScript(){
        ScriptBuilder builder = new ScriptBuilder();
        builder.number(lockDuration);
        builder.op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY);
        builder.op(ScriptOpCodes.OP_DROP);
        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_HASH160);
        builder.data(address.getHash());
        builder.op(ScriptOpCodes.OP_EQUALVERIFY);
        builder.op(ScriptOpCodes.OP_CHECKSIG);
        builder.creationTime(Instant.now());     //META-DATA
        return builder.build();
    }

    private static void printRedeemScript(InfoHook progress, Script redeemScript) {
        String kvHexStr = CsvUtil.getRedeemScriptHexKV(redeemScript);
        progress.event(kvHexStr);

        progress.event("redeemScript: " + redeemScript+" creationTime:"+redeemScript.creationTime().orElse(Instant.EPOCH).getEpochSecond());
    }

    public TxnInfo send(char[] password, InfoHook progress) throws InsufficientMoneyException, ExecutionException, InterruptedException, IllegalAmountException {

        Script redeemScript = createRedeemScript();
        Script p2wshOutputScript = createP2WSHOutputScript(redeemScript);
        p2wshOutputScript = Script.parse( p2wshOutputScript.program(), redeemScript.creationTime().orElse(Instant.EPOCH) );

        Transaction tx = new Transaction();
        tx.addOutput(amount, p2wshOutputScript);

        SendRequest sendRequest = SendRequest.forTx(tx);
        tx = selectTxnInputs(sendRequest);
        tx = deEncryptWalletAndSignTx(tx, password);

        Kit.saveIfAddressInKit(address, redeemScript, p2wshOutputScript);
        printRedeemScript(progress, redeemScript);

        return TxnInfo.get( netBroadcast(tx, progress), wallet );
    }


    private Transaction selectTxnInputs(SendRequest sendRequest) throws InsufficientMoneyException, IllegalAmountException{

        sendRequest.coinSelector = coinSelector;

        Coin target = amount.add( estFee );

        List<TransactionOutput> candidates = wallet.calculateAllSpendCandidates(true, false);
        CoinSelection selection = sendRequest.coinSelector.select(target, candidates);

        if (selection.totalValue().isLessThan(target)) {
            throw new InsufficientMoneyException(target.subtract(selection.totalValue()));
        }

        if(selection.outputs().isEmpty()){
            throw new IllegalStateException("No UTXO available");
        }

        selection.outputs().stream().forEach(o -> sendRequest.tx.addInput(o));

        handleChange(selection.totalValue(), sendRequest);
        setFlags(sendRequest);

        return sendRequest.tx;
    }

    public void handleChange(Coin totalValue, SendRequest sendRequest) throws InsufficientMoneyException {
        Coin change = totalValue.subtract(amount).subtract(estFee);
        if(change.isNegative()) {
            throw new InsufficientMoneyException(change);
        }

        if (change.isPositive()) {
            TransactionOutput changeOutput = new TransactionOutput( null, change, wallet.currentChangeAddress());
            if (!changeOutput.isDust()) {
                sendRequest.tx.addOutput(change, wallet.currentChangeAddress());
            }
        }
    }

    public void setFlags(SendRequest sendRequest){
        //VERSION 2 to enable CSV i think ?
        sendRequest.tx.setVersion(2);

        // Signal BIP125 opt-in RBF on input 0
        TransactionInput input0 = sendRequest.tx.getInput(0);
        sendRequest.tx.replaceInput(0, input0.withSequence(0xFFFFFFFDL));
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


    public Transaction netBroadcast(Transaction tx, InfoHook progress) {
        wallet.maybeCommitTx(tx);

        TransactionBroadcast txnCast = peerGroup.broadcastTransaction(tx, MIN_PEERS_CAST, false);
        progress.event("broadcasting...");
        try {
            txnCast.awaitSent().get(CAST_TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) { }

        return tx;
    }

    public Transaction deEncryptWalletAndSignTx(Transaction txn, char[] password) throws Wallet.BadWalletEncryptionKeyException {

        final boolean walletEncrypted_at_start = wallet.isEncrypted();
        CharArrayCharSequence passwordSeq = new CharArrayCharSequence(password);
        try {
            if(walletEncrypted_at_start) {
                try {
                    wallet.decrypt(passwordSeq);
                }catch (Wallet.BadWalletEncryptionKeyException e){
                    throw new Wallet.BadWalletEncryptionKeyException(e);
                }
            }
            txn =  signTransaction(txn);

            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(passwordSeq);
            }
            return txn;
        }finally {
            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(passwordSeq);
            }
            passwordSeq.wipe();
        }
    }
}
