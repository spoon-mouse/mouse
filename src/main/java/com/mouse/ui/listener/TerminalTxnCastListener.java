package com.mouse.ui.listener;

import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;

public class TerminalTxnCastListener implements OnTransactionBroadcastListener {
    private TextTerminal terminal;
    private Transaction myTxn;

    private int count=1;

    public TerminalTxnCastListener(TextTerminal terminal, Transaction txn){
        this.terminal=terminal;
        myTxn=txn;
    }

    @Override
    public void onTransaction(Peer peer, Transaction t) {
        terminal.println("broadcast "+count+": "+t.getTxId());
        count++;
    }
}
