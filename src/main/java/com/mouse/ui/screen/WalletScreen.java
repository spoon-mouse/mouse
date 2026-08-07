package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import org.beryx.textio.*;
import org.bitcoinj.base.Coin;
import org.bitcoinj.wallet.Wallet;
import java.io.IOException;

public class WalletScreen {
    public enum Choice { SEND, RECIVE, INFO, SEC, UTIL, BACK, EXIT; }

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    private String walletName;
    private Wallet wallet;

    public WalletScreen(String name){
        walletName=name;
        wallet = Kit.wallet(walletName);
    }

    public void show() throws IOException {
        while(true) {
            Coin balance = wallet.getBalance();
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+" balance ("+balance.toFriendlyString()+") ("+balance.value+" sats)"+" connections="+Kit.connections());
            switch (choice) {
                case SEND:
                    new SendScreen(walletName).show();
                    break;
                case INFO:
                    new InfoScreen(walletName).show();
                    break;
                case SEC:
                    new SecScreen(walletName).show();
                    break;
                case UTIL:
                    new UtilScreen(walletName).show();
                    break;
                case RECIVE:
                    terminal.println(walletName+" receive address: "+wallet.currentReceiveAddress());
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }

}
