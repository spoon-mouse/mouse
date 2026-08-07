package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.wallet.Wallet;


import java.time.Instant;

import static com.mouse.ui.input.Input.getTxId;
import static com.mouse.ui.table.TxnTable.*;


public class InfoScreen {
    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    public enum Choice {
        SIMPLE, EXPANDED, PENDING, SENT, RECEIVED, MOVED, UTXO, ADDRES, TXN, WAL, BACK, EXIT
    }

    private String walletName;
    private Wallet wallet;

    public InfoScreen(String name){
        walletName=name;
        wallet= Kit.wallet(walletName);
    }

    public void show(){

        while(true) {

            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+ " Info");
            switch (choice) {
                case SIMPLE:
                    terminal.println( simple_transation_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case EXPANDED:
                    terminal.println( expanded_transation_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case PENDING:
                    terminal.println(expanded_transation_table(wallet.getPendingTransactions().stream().toList(), wallet) );
                    break;
                case SENT:
                    terminal.println( sent_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case RECEIVED:
                    terminal.println( recived_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case MOVED:
                    terminal.println( moved_table(wallet.getTransactionsByTime(), wallet) );
                    break;
                case UTXO:
                    terminal.println( utxo_table(wallet) );
                    break;
                case ADDRES:
                    terminal.println( send_addresses_table(wallet) );
                    break;
                case TXN:
                    view_a_transaction();
                    break;
                case WAL:
                    show_wallet_info();
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }

    }


    private void view_a_transaction() {
        String id = getTxId();

        if(id==null || id.isEmpty()){
            return;
        }

        String details = transaction_details(wallet, id);
        terminal.println( details );
    }

    private void show_wallet_info() {
        terminal.println(wallet.toString());

        final PeerGroup peerGroup = Kit.peerGroup();
        terminal.println("connected peers: "+peerGroup.numConnectedPeers());
        terminal.println("max connections: "+peerGroup.getMaxConnections());
        terminal.println("min connections for broadcast: "+peerGroup.getMinBroadcastConnections());

        final int height = Kit.chain().getBestChainHeight();
        final Instant instant = Kit.chain().estimateBlockTimeInstant(height);
        terminal.println("chain hight: "+height+" ("+instant+")");
    }

}
