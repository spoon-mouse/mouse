package com.mouse.util;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;

public class TxCastCallBack implements TransactionBroadcast.ProgressCallback {

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    private Transaction tx;

    public TxCastCallBack(Transaction tx){
        this.tx=tx;
    }

    @Override public void onBroadcastProgress(double progress) {
        progress = progress * 100.0;
        terminal.println("broadcast: " + tx.getTxId() + " progress: " +progress+ "%");
        if(progress>=100){
            this.notifyAll();
        }
    }

    public void await(){
        try {
            this.wait();
        } catch (InterruptedException e) {}
    }

}
