package com.mouse.listener;

import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;

public class WalletSentListener implements WalletCoinsSentEventListener {
    private TextTerminal terminal;

    public WalletSentListener(TextTerminal terminal){
        this.terminal=terminal;
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
        String id = tx.getTxId().toString();
        Coin value = tx.getValue(wallet);

        terminal.println("transaction: "+id+" spend: "+value.toFriendlyString()+" "+confidenceType);
    }
}