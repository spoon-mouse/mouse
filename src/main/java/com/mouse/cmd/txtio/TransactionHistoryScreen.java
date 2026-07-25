package com.mouse.cmd.txtio;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.wallet.Wallet;

import static com.mouse.util.Spoon.*;


public class TransactionHistoryScreen {

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        SIMPLE_TABLE, EXPANDED_TABLE, VIEW_TRANSACTION, TRACK, BACK, EXIT;
    }

    public static void show(String walletName, Wallet wallet){
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {

            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+ " Transactions: ");
            switch (choice) {
                case SIMPLE_TABLE:
                    String table = simple_transation_table(wallet);
                    terminal.println(table);
                    break;
                case EXPANDED_TABLE:
                    table = expanded_transation_table(wallet);
                    terminal.println(table);
                    break;
                case VIEW_TRANSACTION:
                    view_a_transaction(wallet);
                    break;
                case TRACK:
                    track_a_transaction(wallet);
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }

    }

    private static void track_a_transaction(Wallet wallet) {
        String id = get_TxnId_from_gui();

        if(id==null || id.isEmpty()){
            return;
        }

    }

    private static void view_a_transaction(Wallet wallet) {

        String id = get_TxnId_from_gui();

        if(id==null || id.isEmpty()){
            return;
        }

        String details = transaction_details(wallet, id);
        terminal.println( details );
    }

    private static String get_TxnId_from_gui() {
        return textIO.newStringInputReader().withMinLength(0).withInputTrimming(true).read("transaction id: ");
    }

}
