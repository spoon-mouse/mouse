package com.mouse.util;

import org.bitcoinj.base.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.*;
import org.bitcoinj.wallet.CoinSelector;

import java.util.ArrayList;
import java.util.List;

import static com.mouse.util.CsvUtil.extractCsvSequenceFromScript;

public class CsvAwareCoinSelector implements CoinSelector {

    private final CoinSelector delegate; // e.g. DefaultCoinSelector.get()
    private List<Script> redeemScripts;



    public CsvAwareCoinSelector(CoinSelector delegate, List<Script> redeemScripts) {
        this.delegate = delegate;
        this.redeemScripts = redeemScripts;
    }

    @Override
    public org.bitcoinj.wallet.CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        List<TransactionOutput> filtered = new ArrayList<>();

        for (TransactionOutput output : candidates) {

            System.out.println("candidates: "+output);


            Script scriptPubKey = output.getScriptPubKey();
            Script matchedRedeemScript = null;

            for (Script redeemScript : redeemScripts) {
                if (scriptPubKey.equals(ScriptBuilder.createP2WSHOutputScript(redeemScript))) {
                    System.out.println("ONE of mine match redeemScript");
                    matchedRedeemScript = redeemScript;
                    break;
                }
            }

            if (matchedRedeemScript != null) {
                int depth = output.getParentTransaction().getConfidence().getDepthInBlocks();
                long requiredConfirmations = extractCsvSequenceFromScript(matchedRedeemScript);
                if (depth < requiredConfirmations) {
                    continue; // immature CSV output — exclude
                }
                System.out.println("SELECTED ONE OF mine which is unlocked by confirmations");
            }

            filtered.add(output); // either a normal output, or a mature CSV output
        }
        return delegate.select(target, filtered);
    }


}