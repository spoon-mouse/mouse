package com.mouse.ui.screen;


import com.mouse.ui.listener.BigListener;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.bitcoinj.kits.WalletAppKit;

public class ListenScreen {
    public enum Choice {
        START, STOP, BACK, EXIT;
    }

    public static void show(WalletAppKit kit){
        TextIO textIO = TextIoFactory.getTextIO();
        BigListener listener = new BigListener(textIO.getTextTerminal(), kit);
        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read("listen");
            switch (choice) {
                case START:
                    listener.start();
                    break;
                case STOP:
                    listener.stop();
                    return;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }
}
