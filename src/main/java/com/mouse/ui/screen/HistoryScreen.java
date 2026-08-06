package com.mouse.ui.screen;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

import static com.mouse.ui.table.TxnTable.*;


public class HistoryScreen {

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        SIMPLE, EXPANDED, PENDING, SENT, RECIVED, MOVED, UTXO, SENT_TO, VIEW, BACK, EXIT
    }

    public static void show(String walletName, Wallet wallet){
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {

            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+ " Transactions: ");
            switch (choice) {
                case SIMPLE:
                    terminal.println( simple_transation_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case EXPANDED:
                    terminal.println( expanded_transation_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case PENDING:
                    show_pending(wallet);
                    break;
                case SENT:
                    terminal.println( sent_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case RECIVED:
                    terminal.println( recived_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case MOVED:
                    terminal.println( moved_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case UTXO:
                    terminal.println( utxo_table(wallet) );
                    break;
                case SENT_TO:
                    terminal.println( send_addresses_table(wallet) );
                    break;
                case VIEW:
                    view_a_transaction(wallet);
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }

    }

    private static void show_pending(Wallet wallet) {
        List<Transaction> pending = new ArrayList<>(wallet.getPendingTransactions());
        terminal.println(expanded_transation_table(pending, wallet));
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
