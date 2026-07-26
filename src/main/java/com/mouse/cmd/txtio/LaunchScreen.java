package com.mouse.cmd.txtio;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.mouse.util.Spoon.getWalletAppKit;


public class LaunchScreen {

    private static WalletAppKit kit=null;
    private static String walletName=null;

    private static final String  DEFAULT_WALLET_NAME = "wallet";
    private static final String  DEFAULT_PASSWORD = "wallet.pass";

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        CREATE, LOAD, RESTORE, EXIT;
    }

    public static void main(String[] args){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop_kit();
        }));
        show();
    }

    public static void show(){
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read("Spoon Mouse Apps");
            switch (choice) {
                case CREATE:
                    //get_walletName_password();
                    break;
                case LOAD:
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


    public static CharSequence get_password_from_gui() throws IOException{
        CharSequence password = textIO.newStringInputReader()
                                        .withDefaultValue(DEFAULT_PASSWORD)
                                        .withInputMasking(true)
                                        .read("password:");

        //QUICK HACK
        if(password.equals(DEFAULT_PASSWORD)){
            password = Files.readString(Paths.get(DEFAULT_PASSWORD));
        }

        return password;
    }

    public static String get_wallet_name_from_gui() {
        return textIO.newStringInputReader()
                .withDefaultValue(DEFAULT_WALLET_NAME)
                .read("name:");
    }


    private static void load_wallet()  {
        walletName = get_wallet_name_from_gui();
        try{
            kit = getWalletAppKit(walletName, get_password_from_gui());
            WalletScreen.show(walletName, kit);
        }catch(Exception e){
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }





    private static void restore_from_seed() {

        String seed_txt = textIO.newStringInputReader().withInputTrimming(true).withPattern("^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$").read("12 word seed phrase:");
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(seed_txt, "");

        //walletName = get_wallet_name_from_gui();
        //kit=WalletAppKit.launch(BitcoinNetwork.TESTNET, new File("."), walletName);
        //kit.restoreWalletFromSeed(seed);

        Wallet wallet = Wallet.fromSeed(BitcoinNetwork.TESTNET, seed, ScriptType.P2PKH);
        try {
            if(!wallet.isEncrypted()) {
                wallet.encrypt(get_password_from_gui());
            }
            walletName=get_wallet_name_from_gui();
            wallet.saveToFile(new File(walletName+".wallet"));
        } catch (IOException e) {
            System.out.println(e);
            terminal.println(e.getMessage());
        }

        /*
        try {
            WalletScreen.show(walletName, kit);
        }catch (Exception e){
            System.out.println(e);
            terminal.println(e.getMessage());
        }*/

    }

}
