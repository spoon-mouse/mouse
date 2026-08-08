package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import de.vandermeer.asciitable.AsciiTable;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;

import java.util.stream.Collectors;

import static com.mouse.ui.input.Input.*;
import static com.mouse.ui.table.TxnTable.getTable;


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
                    digest();
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
        terminal.print( "wallets: "+ Kit.getWalletNames().stream().sorted().collect(Collectors.joining(" ")) );
        terminal.println();
        String walletName = getWalletName();
        try{
            Kit.loadOrCreateWallet(walletName);
            new WalletScreen(walletName).show();
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
            terminal.println(e.getMessage());
        }
    }


    private static void digest() {
        AsciiTable table = getTable("name", "encrypted", "balance", "block hight", "id", "receive address");
        Kit.getMetaWallets().forEach( w -> {
            table.addRow(w.name(), w.isEncrypted(), w.balance(), w.blockHeight(), w.id(), w.reciveAddress());
        });
        table.addRule();
        terminal.println(table.render());
    }


}
