package com.mouse.backend.csv;

import com.mouse.ui.screen.WalletScreen;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.KeyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.mouse.backend.csv.CsvUtil.extractCsvSequenceFromScript;
import static com.mouse.backend.csv.CsvUtil.extractPubKeyHashFromRedeemScript;

public class CsvP2WshSigner implements TransactionSigner {

    private static Logger log = LoggerFactory.getLogger(WalletScreen.class);

    private List<Script> redeemScripts;

    public CsvP2WshSigner(List<Script> redeemScripts) {
        this.redeemScripts = redeemScripts;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean signInputs(ProposedTransaction propTx, KeyBag keyBag) {
        Transaction tx = propTx.partialTx;

        for (Script redeemScript : redeemScripts) {
            for (int i = 0; i < tx.getInputs().size(); i++) {

                TransactionInput input = tx.getInput(i);
                TransactionOutput connectedOutput = input.getConnectedOutput();


                if (connectedOutput == null) {
                    continue;
                }

                Script scriptPubKey = connectedOutput.getScriptPubKey();

                // does this output pay to OUR redeem script (as P2WSH)?
                Script expectedP2wsh = ScriptBuilder.createP2WSHOutputScript(redeemScript);
                if (!scriptPubKey.equals(expectedP2wsh)) {
                    continue;
                }

                log.info("redeemScript: "+redeemScript+ " input: "+input+" connectedOutput: "+connectedOutput);


                // must set sequence BEFORE signing (it's covered by the sighash)
                long requiredSequence = extractCsvSequenceFromScript(redeemScript);
                TransactionInput seqInput = input.withSequence(requiredSequence);
                tx.replaceInput(i, seqInput);

                // figure out which key satisfies OP_CHECKSIG inside the redeem script
                byte[] pubKeyHash = extractPubKeyHashFromRedeemScript(redeemScript); // your own helper


                ECKey key = keyBag.findKeyFromPubKeyHash(pubKeyHash, null);
                if (key == null) {
                    log.error("no key for pubKeyHash: "+pubKeyHash);
                    continue; // can't sign this one, not our key
                }


                Coin value = connectedOutput.getValue();
                TransactionSignature sig = tx.calculateWitnessSignature(i, key, redeemScript, value, Transaction.SigHash.ALL, false);

                TransactionWitness witness = TransactionWitness.of(List.of(
                        sig.encodeToBitcoin(),
                        key.getPubKey(),
                        redeemScript.program()
                ));

                TransactionInput signedInput = tx.getInput(i).withWitness(witness);
                tx.replaceInput(i, signedInput);

                log.info("signed with: "+redeemScript);
            }
        }
        return true;
    }

    public byte[] serialize() { return new byte[0]; }

    public void deserialize(byte[] data) { }
}