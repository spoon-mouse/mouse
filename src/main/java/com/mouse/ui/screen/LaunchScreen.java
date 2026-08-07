package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import com.mouse.ui.table.WalletTable;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static com.mouse.backend.util.Config.*;


public class LaunchScreen {

    // UI-only constants — regex for TextIO input validation, screen title,
    // shutdown timeout, and default values shown in prompts. App-wide config
    // (network, file paths) lives in com.mouse.backend.util.Config instead.
    public static final String REGEX_12_WORDS = "^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$";
    public static final String APP_TITLE_LINE = "Spoon Mouse BTC";

    private static final String  DEFAULT_WALLET_NAME = "wallet";

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    public enum Choice {WALLET, RESTORE, DIGEST, EXIT}

    public LaunchScreen() throws BlockStoreException {
        Context context = Context.getOrCreate();
        Context.propagate(context);

        Kit.start();
        Runtime.getRuntime().addShutdownHook(new Thread(Kit::stop));
        show();
    }


    public static void show(){
        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read(APP_TITLE_LINE);
            switch (choice) {
                case WALLET:
                    load_wallet();
                    break;
                case RESTORE:
                    restore();
                    break;
                case DIGEST:
                    digest_of_wallets();
                    break;
                case EXIT:
                    System.exit(0);
            }
        }
    }

    private static void restore() {
        String seed_txt = get_seed_from_gui();
        long epochSeconds = get_optinal_creation_epoch_seconds();
        String walletName=get_wallet_name_from_gui();
        terminal.print("restoring...");
        Kit.restore_from_seed(walletName, seed_txt, epochSeconds);
        terminal.print("restored");
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
