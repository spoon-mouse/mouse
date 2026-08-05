package com.mouse.cmd.txtio;

import com.mouse.listener.DownloadTracker;
import com.mouse.util.CsvAwareCoinSelector;
import com.mouse.util.CsvP2WshSigner;
import com.mouse.util.CsvScriptExtension;
import com.mouse.util.WalletTable;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mouse.util.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;
import static java.util.stream.Collectors.toList;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;


public class LaunchScreen {


    public static final BitcoinNetwork NETWORK = BitcoinNetwork.TESTNET;
    public static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.of(NETWORK);
    public static final String walletDirStr = "wallet";
    public static final Path WALLET_DIR_PATH = Path.of(walletDirStr);
    public static final File walletDir = new File(walletDirStr);
    public static final String WALLET_FILE_POST_FIX = ".wallet";
    public static final String SPVCHAIN_FILE_POST_FIX = ".spvchain";
    public static final String REGEX_12_WORDS = "^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$";
    public static final String APP_TITLE_LINE = "Spoon Mouse BTC";
    public static final int WALLET_CLOSE_TIMEOUT_SECONDS = 60;

    private static WalletAppKit kit=null;
    private static String walletName=null;

    private static final String  DEFAULT_WALLET_NAME = "wallet";
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
                    digest_of_wallets();
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
        terminal.print("wallets: "+ wallet_names_string());
        terminal.println();
        walletName = get_wallet_name_from_gui();
        try{


            kit = new WalletAppKit(NETWORK_PARAMETERS, ScriptType.P2WPKH, KeyChainGroupStructure.BIP32, walletDir, walletName) {

                private  CsvScriptExtension csv = new CsvScriptExtension();
                @Override
                protected List<WalletExtension> provideWalletExtensions() {
                    System.out.println("provideWalletExtensions : "+csv);
                    return List.of(csv);
                }

                @Override
                protected void onSetupCompleted() {

                    List<Script> watchedScripts = csv.getRedeemScripts().stream().map( redeemScript ->
                                 Script.parse(createP2WSHOutputScript(redeemScript).program(), redeemScript.creationTime().get() ))
                            .collect(toList());

                    wallet().addWatchedScripts(watchedScripts);
                    wallet().addTransactionSigner(new CsvP2WshSigner(csv.getRedeemScripts()));
                    System.out.println("onSetupCompleted : done");
                }
            };
            kit.setBlockingStartup(false);
            kit.startAsync();
            kit.awaitRunning();

            WalletScreen.show(walletName, kit);
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
            terminal.println(e.getMessage());
        }
    }


    public static String wallet_names_string() {
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

    private static long get_optinal_creation_epoch_seconds(){
        return textIO.newLongInputReader().withMinVal(0l).withDefaultValue(0l).read("creation epoch seconds (optionally speeds up restoration):");
    }

    private static String get_seed_from_gui() {
        return textIO.newStringInputReader().withInputTrimming(true).withPattern(REGEX_12_WORDS).read("12 word seed phrase:");
    }


    private static void digest_of_wallets() {
        terminal.println( WalletTable.get_wallet_digest_table() );
    }
}
