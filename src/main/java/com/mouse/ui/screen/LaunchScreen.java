package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import com.mouse.ui.listener.DownloadTracker;
import com.mouse.backend.csv.CsvScriptExtension;
import com.mouse.ui.table.WalletTable;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

import static com.mouse.backend.util.Config.*;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;


public class LaunchScreen {

    // UI-only constants — regex for TextIO input validation, screen title,
    // shutdown timeout, and default values shown in prompts. App-wide config
    // (network, file paths) lives in com.mouse.backend.util.Config instead.
    public static final String REGEX_12_WORDS = "^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$";
    public static final String APP_TITLE_LINE = "Spoon Mouse BTC";
    public static final int WALLET_CLOSE_TIMEOUT_SECONDS = 60;

    private static final String  DEFAULT_WALLET_NAME = "wallet";

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        WALLET, RESTORE, DIGEST, EXIT
    }

    public static void main(String[] args) throws BlockStoreException {
        launch();
    }

    public static void launch() throws BlockStoreException {
        Kit.start();
        Runtime.getRuntime().addShutdownHook(new Thread(LaunchScreen::stop));
        show();
    }

    private static void stop() {
        Kit.stop();
    }


    public static void show(){
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Context context = Context.getOrCreate();
            Context.propagate(context);

            Choice choice = textIO.newEnumInputReader(Choice.class).read(APP_TITLE_LINE);
            switch (choice) {
                case WALLET:
                    load_wallet();
                    break;
                case RESTORE:
                    restore_from_seed();
                    break;
                case DIGEST:
                    digest_of_wallets();
                    break;
                case EXIT:
                    System.exit(0);
            }
        }
    }


    private static void load_wallet() {
        terminal.print("wallets: "+ wallet_names_string());
        terminal.println();
        String walletName = get_wallet_name_from_gui();
        try{
            Kit.loadOrCreateWallet(walletName);
            WalletScreen screen = new WalletScreen(walletName);
            screen.show();
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
            terminal.println(e.getMessage());
        }
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
            Wallet wallet = Wallet.fromSeed(NETWORK, seed, ScriptType.P2WPKH);
            wallet.clearTransactions(0);

            BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(walletDirStr+"/"+walletName+SPVCHAIN_FILE_POST_FIX));

            BlockChain chain = new BlockChain(NETWORK, wallet, blockStore);
            PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));
            peerGroup.addWallet(wallet);

            DownloadTracker listener = new DownloadTracker(terminal);
            peerGroup.start();
            peerGroup.startBlockChainDownload(listener);

            terminal.println("Restoring from seed...");
            listener.await();

            wallet.addExtension(new CsvScriptExtension());
            wallet.saveToFile(new File(walletName+ WALLET_FILE_POST_FIX) );
            terminal.println("Restored: "+walletName);
            terminal.println("balance: "+wallet.getBalance().toFriendlyString());

            peerGroup.stop();
            blockStore.close();

        } catch (Exception e) {
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }

    private static void digest_of_wallets() {
        terminal.println( WalletTable.get_wallet_digest_table() );
    }


    private static long get_optinal_creation_epoch_seconds(){
        return textIO.newLongInputReader().withMinVal(0l).withDefaultValue(0l).read("creation epoch seconds (optionally speeds up restoration):");
    }

    private static String get_seed_from_gui() {
        return textIO.newStringInputReader().withInputTrimming(true).withPattern(REGEX_12_WORDS).read("12 word seed phrase:");
    }

    public static String get_wallet_name_from_gui() {
        return textIO.newStringInputReader().withDefaultValue(DEFAULT_WALLET_NAME).withInputTrimming(true)
                .read("wallet name");
    }


    public static String wallet_names_string() {
        try {
            return Files.list(WALLET_DIR_PATH).filter(f -> f.toString().endsWith(WALLET_FILE_POST_FIX)).map(Path::getFileName)
                    .map(Path::toString).map(s-> s.substring(0, s.length() - WALLET_FILE_POST_FIX.length()))
                    .sorted().collect(Collectors.joining(" "));
        } catch (IOException e) {}
        return "";
    }
}
