package com.mouse.cmd.txtio;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Address;
import org.bitcoinj.kits.WalletAppKit;


public class WalletScreen {

    private static WalletAppKit kit;

    private static String walletName;
    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        SEND, RECIVE, TXNS, LISTEN, SEED, BACK, EXIT;
    }

    public static void show(String name, WalletAppKit appkit){
        kit = appkit;
        walletName=name;
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read("name: "+walletName+" balance: "+kit.wallet().getBalance().toFriendlyString());
            switch (choice) {
                case SEND:
                    SendTransactionScreen.show(walletName, kit);
                    break;
                case RECIVE:
                    recive();
                    break;
                case TXNS:
                    TransactionHistoryScreen.show(walletName, kit.wallet());
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }


    private static void recive() {
        Address address = kit.wallet().currentReceiveAddress();
        terminal.println(walletName+" current receive address: "+address);
    }

}
