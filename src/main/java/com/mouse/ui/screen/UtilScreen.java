package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;

public class UtilScreen {
    public enum Choice { CAST, BACK, EXIT; }
    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    private String walletName;
    private Wallet wallet;

    public UtilScreen(String name){
        walletName=name;
        wallet = Kit.wallet(walletName);
    }

    public void show() throws IOException {
        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+ " Security");
            switch (choice) {
                case CAST:
                    terminal.println("casting:");
                    wallet.getPendingTransactions().stream().forEach(tx->{Kit.peerGroup().broadcastTransaction(tx, 3, false);});
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }

}
