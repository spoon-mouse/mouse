package com.mouse.cmd.txtio;

import com.mouse.listener.DownloadTracker;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Network;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.mouse.util.Spoon.getWalletAppKit;


public class LaunchScreen {

    private static WalletAppKit kit=null;
    private static String walletName=null;

    private static final String  DEFAULT_WALLET_NAME = "wallet";
    private static final String  DEFAULT_PASSWORD = "wallet.password";

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        WALLET, RESTORE, EXIT
    }

    public static void main(String[] args){
        launch();
    }

    public static void launch() {
        Runtime.getRuntime().addShutdownHook(new Thread(LaunchScreen::stop_kit));
        show();
    }

    public static void show(){
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read("Spoon Mouse Apps");
            switch (choice) {
                case WALLET:
                    load_wallet();
                    break;
                case RESTORE:
                    restore_from_seed();
                    break;
                case EXIT:
                    System.exit(0);
            }
            stop_kit();
        }
    }

    private static void stop_kit() {
        if(kit!=null){
            kit.stopAsync();
            terminal.println("closing: " + walletName);
            kit.awaitTerminated();
            terminal.println("closed: " + walletName);
            kit=null;
        }
    }


    public static CharSequence get_password_from_gui() {
        CharSequence password = textIO.newStringInputReader()
                                        .withDefaultValue(DEFAULT_PASSWORD)
                                        .withInputMasking(true)
                                        .read("password:");

        return password;
    }

    public static String get_wallet_name_from_gui() {
        return textIO.newStringInputReader()
                .withDefaultValue(DEFAULT_WALLET_NAME)
                .read("name:");
    }


    private static void load_wallet() {

        show_found_wallets();

        walletName = get_wallet_name_from_gui();
        try{
            kit = getWalletAppKit(walletName, get_password_from_gui());
            WalletScreen.show(walletName, kit);
        }catch(Exception e){
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }

    private static void show_found_wallets() {
        try {
            terminal.print("found wallets: "+get_string_of_found_wallet_file_names());
            terminal.println();
        } catch (IOException e) {}
    }

    private static String get_string_of_found_wallet_file_names() throws IOException {
        var ref = new Object() {
            String line = new String();
        };
        Files.newDirectoryStream(Path.of("."), "*.wallet").forEach(i-> {
            String f = i.getFileName().toString();
            f=f.substring(0, f.length() - 7);
            ref.line = ref.line+f+" ";
        });
        return ref.line;
    }


    private static void restore_from_seed() {
        Network network = BitcoinNetwork.TESTNET;
        NetworkParameters params = NetworkParameters.of(network);

        String seed_txt = get_seed_from_gui();
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(seed_txt, "");

        walletName=get_wallet_name_from_gui();
        try {
            //ScriptType.P2WPKH or ScriptType.P2PKH.  ?
            Wallet wallet = Wallet.fromSeed(network, seed, ScriptType.P2PKH);
            wallet.clearTransactions(0);

            BlockStore blockStore = new SPVBlockStore(params, new File(walletName+".spvchain"));

            //wallet in the chain constructor
            BlockChain chain = new BlockChain(network, wallet, blockStore);
            PeerGroup peerGroup = new PeerGroup(network, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(network));
            peerGroup.addWallet(wallet);

            DownloadTracker listener = new DownloadTracker(terminal);
            peerGroup.start();
            peerGroup.startBlockChainDownload(listener);

            terminal.println("Restoring from seed...");
            listener.await();

            wallet.saveToFile(new File(walletName+".wallet") );
            terminal.println(wallet.toString());

            terminal.println("Restored: "+walletName);

        } catch (Exception e) {
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }

    private static String get_seed_from_gui() {
        return textIO.newStringInputReader().withInputTrimming(true).withPattern("^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$").read("12 word seed phrase:");
    }

}
