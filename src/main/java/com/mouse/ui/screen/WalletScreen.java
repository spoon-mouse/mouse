package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import org.beryx.textio.*;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static com.mouse.ui.screen.PasswordScreen.get_password_from_gui;
import static com.mouse.ui.screen.SecScreen.BAD_WALLET_DECRYPTION;

public class WalletScreen {
    public enum Choice { SEND, RECIVE, HIST, INFO, SEC, CAST, BACK, EXIT; }

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
                case RECIVE:
                    terminal.println(walletName+" receive address: "+wallet.currentReceiveAddress());
                    break;
                case HIST:
                    HistoryScreen.show(walletName, wallet);
                    break;
                case INFO:
                    show_wallet_info();
                    break;
                case SEC:
                    new SecScreen(walletName).show();
                    break;
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
