package com.mouse.listener;

import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.Wallet;

public class ConfListner implements TransactionConfidenceEventListener {

    TextTerminal terminal;
    Sha256Hash id;
    public ConfListner(TextTerminal terminal, String id){
        this.terminal=terminal;
        this.id=Sha256Hash.wrap(id);
    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
        if(tx.getTxId().equals(id)){

            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();

            int blockDepth = confidence.getDepthInBlocks();
            Coin fromMe = tx.getValueSentFromMe(wallet);
            Coin toMe = tx.getValueSentToMe(wallet);
            Coin value = tx.getValue(wallet);
            //terminal.setBookmark("mark1");
            terminal.println();
            terminal.println("[Confidence event] txId: "+id+" sent:"+fromMe+" recived:"+toMe+" value:"+value+" "+confidenceType+" "+blockDepth);
            //terminal.resetToBookmark("mark1");
        }
    }

    public String getId(){return id.toString();}
}
