package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import com.mouse.ui.table.WalletTable;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static com.mouse.backend.util.Config.*;
import static com.mouse.ui.input.Input.*;


public class LaunchScreen {
    public static final String APP_TITLE_LINE = "Spoon Mouse BTC";

    public static final String  DEFAULT_WALLET_NAME = "wallet";

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
        String seed_txt = getSeed();
        long epochSeconds = getEpochSeconds();
        String walletName= getWalletName();
        terminal.print("restoring...");
        Kit.restore_from_seed(walletName, seed_txt, epochSeconds);
        terminal.print("restored");
    }


    private static void load_wallet() {
        terminal.print("wallets: "+ wallet_names_string());
        terminal.println();
        String walletName = getWalletName();
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


    public static String wallet_names_string() {
        try {
            return Files.list(WALLET_DIR_PATH).filter(f -> f.toString().endsWith(WALLET_FILE_POST_FIX)).map(Path::getFileName)
                    .map(Path::toString).map(s-> s.substring(0, s.length() - WALLET_FILE_POST_FIX.length()))
                    .sorted().collect(Collectors.joining(" "));
        } catch (IOException e) {}
        return "";
    }
}
