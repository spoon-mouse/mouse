package com.mouse.util;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

import static com.mouse.cmd.txtio.LaunchScreen.NETWORK;
import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;
import static com.mouse.util.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;

public class TxnUtil {

    public static Transaction complete_txn(Wallet wallet) throws InsufficientMoneyException {
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        SendRequest sendRequest = SendRequest.emptyWallet(wallet.currentReceiveAddress());
        sendRequest.coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());
        sendRequest.missingSigsMode = Wallet.MissingSigsMode.USE_DUMMY_SIG;
        return complete_txn(sendRequest, wallet);
    }

    public static Transaction complete_txn(AddressAmountFee aaf, Wallet wallet) throws InsufficientMoneyException, Wallet.TransactionCompletionException {
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        SendRequest sendRequest = SendRequest.to(aaf.address(), aaf.amount());
        sendRequest.coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());
        sendRequest.missingSigsMode = Wallet.MissingSigsMode.USE_DUMMY_SIG;
        return complete_txn(sendRequest, wallet);
    }

    public static Transaction complete_txn(SendRequest sendRequest, Wallet wallet) throws InsufficientMoneyException, Wallet.TransactionCompletionException {

        final boolean walletEncrypted_at_start = wallet.isEncrypted();
        CharSequence password=null;
        try {
            if(walletEncrypted_at_start) {
                password=get_password_from_gui();
                wallet.decrypt(password);
            }


            List<TransactionOutput> candidates = wallet.calculateAllSpendCandidates(true, false);
            // excludeUnsignable = false — same reason as before, otherwise your CSV
            // output gets filtered out here before your selector even runs

            Coin amount = sendRequest.tx.getValue(wallet);
            System.out.println("Coin amount = sendRequest.tx.getValue(wallet) : "+amount);
            CoinSelection selection = sendRequest.coinSelector.select(amount, candidates);

            Coin gathered = Coin.ZERO;
            for (TransactionOutput output : selection.gathered) {
                sendRequest.tx.addInput(output);
                gathered = gathered.add(output.getValue());
                System.out.println(output);
            }

            //Coin estimatedFee = sendRequest.tx.getFee();
            Coin estimatedFee = Coin.ofSat(200l);

            System.out.println("Coin estimatedFee = : "+estimatedFee );

            Coin change = gathered.subtract(amount).subtract(estimatedFee); // your manual fee calc
            System.out.println("change: "+change);

            if (change.isPositive()) {
                sendRequest.tx.addOutput(change, wallet.currentChangeAddress());
            }

            sendRequest.tx.setVersion(2);


            TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(sendRequest.tx);


            CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
            CsvP2WshSigner csvP2WshSigner = new CsvP2WshSigner(ext.getRedeemScripts());
            csvP2WshSigner.signInputs(proposal, wallet);

            Transaction tx = proposal.partialTx;
            for (int i = 0; i < tx.getInputs().size(); i++) {
                TransactionInput input = tx.getInput(i);
                TransactionOutput connectedOutput = input.getConnectedOutput();
                if (connectedOutput == null) continue;

                Script scriptPubKey = connectedOutput.getScriptPubKey();
                if (scriptPubKey.getScriptType() != ScriptType.P2PKH) continue;

                byte[] pubKeyHash = scriptPubKey.getPubKeyHash();
                ECKey key = wallet.findKeyFromPubKeyHash(pubKeyHash, null);
                if (key == null) continue;

                TransactionSignature sig = tx.calculateSignature(i, key, scriptPubKey, Transaction.SigHash.ALL, false);
                Script scriptSig = ScriptBuilder.createInputScript(sig, key);

                TransactionInput signedInput = input.withScriptSig(scriptSig);
                tx.replaceInput(i, signedInput);
            }

            if(tx==sendRequest.tx || tx.equals(sendRequest.tx)){
                System.out.println("SAME  TX: "+tx);
            }


            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }
        }finally {
            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }
        }

        return sendRequest.tx;
    }

}
