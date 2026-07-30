package com.mouse.cmd.txtio;

import org.beryx.textio.*;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;
import static com.mouse.util.TxnTable.expanded_transation_table;


public class WalletScreen {

    public static final String BAD_WALLET_DECRYPTION = "ERROR INVALID PASSWORD: bad wallet decryption";
    private static WalletAppKit kit;

    private static String walletName;
    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        SEND, RECIVE, TXNS, UTXO, INFO, PASSWORD, SEED, BACK, EXIT;
    }

    public static void show(String name, WalletAppKit appkit) throws IOException {
        kit = appkit;
        walletName=name;
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Coin balance = kit.wallet().getBalance();
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+" balance ("+balance.toFriendlyString()+") ("+balance.value+" sats)");
            switch (choice) {
                case SEND:
                    SendTransactionScreen.show(walletName, kit);
                    break;
                case RECIVE:
                    recive();
                    break;
                case TXNS:
                    WalletHistoryScreen.show(walletName, kit.wallet());
                    break;
                case UTXO:
                    do_some_utxo_stuff();
                    break;
                case INFO:
                    show_wallet_info();
                    break;
                case PASSWORD:
                    PasswordScreen.show(walletName, kit.wallet());
                    break;
                case SEED:
                    show_wallet_seed();
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }

    private static void do_some_utxo_stuff() {

            final List<TransactionOutput> utxos = kit.wallet().getUnspents();
            terminal.println( "UTXO count: "+utxos.size() );

            utxos.forEach(utxo -> {
                terminal.println(utxo.isAvailableForSpending()+"  "+utxo.getValue().value);
            });

    }


    private static void show_wallet_info() {
        terminal.println(kit.wallet().toString());

        final PeerGroup peerGroup = kit.peerGroup();
        terminal.println("connected peers: "+peerGroup.numConnectedPeers());
        terminal.println("max connections: "+peerGroup.getMaxConnections());
        terminal.println("min connections for broadcast: "+peerGroup.getMinBroadcastConnections());

        final int height = kit.chain().getBestChainHeight();
        final Instant instant = kit.chain().estimateBlockTimeInstant(height);
        terminal.println("chain hight: "+height+" ("+instant+")");
    }

    private static void show_wallet_seed() throws IOException {
        terminal.println("WARN showing SEED in plain text:");

        CharSequence password=null;
        final Wallet wallet = kit.wallet();
        final boolean walletEncrypted_at_start = wallet.isEncrypted();

        try {
            if(wallet.isEncrypted()){
                password = get_password_from_gui();
                wallet.decrypt(password);
            }
            DeterministicSeed deterministicSeed = wallet.getKeyChainSeed();

            final Optional<Instant> creationTime = deterministicSeed.getCreationTime();
            if(creationTime.isPresent()) {
                final long epochSeconds = creationTime.get().getEpochSecond();
                terminal.println("creation epoch seconds: "+epochSeconds);
            }

            final String seed = deterministicSeed.getMnemonicString();
            terminal.println(seed);
        }catch (Wallet.BadWalletEncryptionKeyException e){
            terminal.println(BAD_WALLET_DECRYPTION);
        }finally {
            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
                password=null;
            }
        }
    }


    private static void recive() {
        Address address = kit.wallet().currentReceiveAddress();
        terminal.println(walletName+" current receive address: "+address);
    }

}
