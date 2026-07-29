package com.mouse.cmd.txtio;

import com.mouse.listener.ConfListner;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

import static com.mouse.util.TxnTable.*;


public class WalletHistoryScreen {

    private static TextIO textIO;
    private static TextTerminal terminal;

    private static final List<ConfListner> confListners = new ArrayList<>();

    public enum Choice {
        SIMPLE, EXPANDED, PENDING, SENT, VIEW, BACK, EXIT
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

    private static void stop_tracking(Wallet wallet) {
        confListners.forEach( confListner -> {
            wallet.removeTransactionConfidenceEventListener(confListner);
            terminal.println("stoped tracking: "+confListner.getId());
        });
    }

    private static void track_a_transaction(Wallet wallet) {
        String id = get_TxnId_from_gui();

        if(id==null || id.isEmpty()){
            return;
        }

        ConfListner confListner = new ConfListner(terminal, id);
        confListners.add(confListner);
        wallet.addTransactionConfidenceEventListener(confListner);
        terminal.println("tracking: "+id);

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
