package com.mouse.cmd.txtio;

import com.diogonunes.jcolor.Ansi;
import com.diogonunes.jcolor.Attribute;
import com.mouse.listener.DownloadTracker;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static com.diogonunes.jcolor.Ansi.colorize;


public class LaunchScreen {

    public static final Context context = new Context();

    public static final BitcoinNetwork network = BitcoinNetwork.TESTNET;
    public static final NetworkParameters netParams = NetworkParameters.of(network);

    public static final String walletDirStr = ".";
    public static final File walletDir = new File(walletDirStr);
    public static final String WALLET_FILE_POST_FIX = ".wallet";
    public static final String SPVCHAIN_FILE_POST_FIX = ".spvchain";
    public static final String REGEX_12_WORDS = "^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$";
    public static final String APP_TITLE_LINE = "Spoon Mouse BTC";

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
            Choice choice = textIO.newEnumInputReader(Choice.class).read(APP_TITLE_LINE);
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
        return textIO.newStringInputReader()
                                        .withDefaultValue(DEFAULT_PASSWORD)
                                        .withInputMasking(true)
                                        .read("password");
    }

    public static String get_wallet_name_from_gui() {
        return textIO.newStringInputReader()
                .withDefaultValue(DEFAULT_WALLET_NAME)
                .read("wallet name");
    }


    private static void load_wallet() {

        show_found_wallets();

        walletName = get_wallet_name_from_gui();
        try{
            kit = WalletAppKit.launch(network, walletDir, walletName);
            WalletScreen.show(walletName, kit);
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
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
        Files.newDirectoryStream(Path.of(walletDirStr), "*"+WALLET_FILE_POST_FIX).forEach(i-> {
            String f = i.getFileName().toString();
            f=f.substring(0, f.length() - 7);
            ref.line = ref.line+f+" ";
        });
        return ref.line;
    }


    private static void restore_from_seed() {

        String seed_txt = get_seed_from_gui();
        long epochSeconds = get_optinal_creation_epoch_seconds();
        DeterministicSeed seed;
        if(epochSeconds<=0L){
            seed = DeterministicSeed.ofMnemonic(seed_txt, "");
        }else{
            seed = DeterministicSeed.ofMnemonic(seed_txt, "", Instant.ofEpochSecond(epochSeconds));
        }



        walletName=get_wallet_name_from_gui();
        try {
            Wallet wallet = Wallet.fromSeed(network, seed, ScriptType.P2WPKH);
            wallet.clearTransactions(0);

            BlockStore blockStore = new SPVBlockStore(netParams, new File(walletName+SPVCHAIN_FILE_POST_FIX));

            BlockChain chain = new BlockChain(network, wallet, blockStore);
            PeerGroup peerGroup = new PeerGroup(network, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(network));
            peerGroup.addWallet(wallet);

            DownloadTracker listener = new DownloadTracker(terminal);
            peerGroup.start();
            peerGroup.startBlockChainDownload(listener);

            terminal.println("Restoring from seed...");
            listener.await();

            wallet.saveToFile(new File(walletName+ WALLET_FILE_POST_FIX) );
            terminal.println("Restored: "+walletName);
            terminal.println("balance: "+wallet.getBalance().toFriendlyString());
        } catch (Exception e) {
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }

    private static long get_optinal_creation_epoch_seconds(){
        return textIO.newLongInputReader().withMinVal(0l).withDefaultValue(0l).read("creation epoch seconds (optionally speeds up restoration):");
    }

    private static String get_seed_from_gui() {
        return textIO.newStringInputReader().withInputTrimming(true).withPattern(REGEX_12_WORDS).read("12 word seed phrase:");
    }

}
