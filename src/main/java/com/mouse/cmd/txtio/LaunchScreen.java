package com.mouse.cmd.txtio;

import com.mouse.listener.DownloadTracker;
import com.mouse.util.WalletNameId;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class LaunchScreen {


    public static final BitcoinNetwork network = BitcoinNetwork.TESTNET;
    public static final NetworkParameters netParams = NetworkParameters.of(network);
    public static final String walletDirStr = ".";
    public static final Path WALLET_DIR_PATH = Paths.get(walletDirStr);
    public static final File walletDir = new File(walletDirStr);
    public static final String WALLET_FILE_POST_FIX = ".wallet";
    public static final String SPVCHAIN_FILE_POST_FIX = ".spvchain";
    public static final String REGEX_12_WORDS = "^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$";
    public static final String APP_TITLE_LINE = "Spoon Mouse BTC";
    public static final int WALLET_CLOSE_TIMEOUT_SECONDS = 60;

    private static WalletAppKit kit=null;
    private static String walletName=null;

    private static final String  DEFAULT_WALLET_NAME = "w1";
    private static final String  DEFAULT_PASSWORD = "wallet.password";

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        WALLET, RESTORE, DIGEST, EXIT
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
                    show_wallet_digest();
                    break;
                case EXIT:
                    System.exit(0);
            }
            stop_kit();
        }
    }

    private static List<WalletNameId> listOfWallets(){
        List<WalletNameId> wallets = new ArrayList<>();
        try {
            Files.newDirectoryStream(Path.of(walletDirStr),"*"+WALLET_FILE_POST_FIX).forEach(path -> {
                try {
                    wallets.add(WalletNameId.get(Wallet.loadFromFile(path.toFile()), path.toFile()));
                } catch (UnreadableWalletException e) {}
            });
        }catch (IOException e) {}
        return wallets;
    }



    public static Map<String, List<WalletNameId>> mapById(List<WalletNameId> l){
        return l.stream().collect(Collectors.groupingBy(WalletNameId::id));
    }

    private static List<WalletNameId> sortBySeenBlocks(List<WalletNameId> wallets){
        wallets.sort(Comparator.comparing(WalletNameId::getLastBlockSeenHeight));
        return wallets;
    }

    public static Map<String, List<WalletNameId>> getWalletMap(){
        return mapById(sortBySeenBlocks(listOfWallets()));
    }

    private static void show_wallet_digest(){

        AsciiTable table = new AsciiTable();
        table.getRenderer().setCWC(new CWC_LongestWord());
        table.addRule();
        table.addRow("name", "encrypted", "balance", "block hight", "id");
        table.addRule();

        getWalletMap().values().stream().flatMap(Collection::stream).forEach(i -> {
            table.addRow(i.name(), i.wallet().isEncrypted(), i.wallet().getBalance().getValue(), i.wallet().getLastBlockSeenHeight(), i.id());
        });

        table.addRule();
        terminal.println(table.render());
    }



    private static void stop_kit() {
        if(kit!=null){
            kit.stopAsync();
            terminal.println("closing: " + walletName);
            try {
                kit.awaitTerminated(WALLET_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                terminal.println("closed: " + walletName);
            }catch(TimeoutException e) {
                terminal.println("time out closing wallet: "+walletName+" after "+WALLET_CLOSE_TIMEOUT_SECONDS+" seconds");
            }catch (IllegalStateException e){
                terminal.println("ERROR closing wallet: "+walletName+" "+e.getMessage());
                System.out.println(e);
            }finally {
                kit=null;
            }
        }
    }


    public static CharSequence get_password_from_gui() {
        return textIO.newStringInputReader()
                                        .withDefaultValue(DEFAULT_PASSWORD)
                                        .withInputMasking(true)
                                        .read("password");
    }

    public static String get_wallet_name_from_gui() {
        return textIO.newStringInputReader().withDefaultValue(DEFAULT_WALLET_NAME).withInputTrimming(true)
                .read("wallet name");
    }


    private static void load_wallet() {
        terminal.print("wallets: "+ filesString());
        terminal.println();
        walletName = get_wallet_name_from_gui();
        try{
            kit = WalletAppKit.launch(network, walletDir, walletName);
            WalletScreen.show(walletName, kit);
        }catch(Exception e){
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }


    public static String filesString() {
        try {
            return Files.list(WALLET_DIR_PATH).filter(f -> f.toString().endsWith(WALLET_FILE_POST_FIX)).map(Path::getFileName)
                    .map(Path::toString).map(s-> s.substring(0, s.length() - WALLET_FILE_POST_FIX.length()))
                    .sorted().collect(Collectors.joining(" "));
        } catch (IOException e) {}
        return "";
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

            peerGroup.stop();
            blockStore.close();

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
