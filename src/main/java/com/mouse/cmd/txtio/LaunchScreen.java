package com.mouse.cmd.txtio;

import com.mouse.util.Spoon;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.kits.WalletAppKit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.mouse.util.Spoon.getWalletAppKit;


public class LaunchScreen {

    private static WalletAppKit kit=null;
    private static String walletName;

    private static final String  DEFAULT_WALLET_NAME = "wallet";
    private static final String  DEFAULT_PASSWORD = "wallet.pass";

    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        CREATE, LOAD, RESTORE, EXIT;
    }

    public static void main(String[] args){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown");
            if(kit!=null){
                kit.stopAsync();
                terminal.println("closing: " + walletName);
                kit.awaitTerminated();
                terminal.println("closed: " + walletName);
            }
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
                        go_into_the_wallet();
                    break;
                case RESTORE:
                    get_seed();
                    break;
                case EXIT:
                    System.exit(0);
            }
        }

    }

    private static void go_into_the_wallet()  {
        walletName = get_walletName();
        try{
            kit = getWalletAppKit(walletName, getPassword());
            WalletScreen.show(walletName, kit);
        }catch(Exception e){
            System.out.println(e);
            terminal.println(e.getMessage() + e.toString());
        }finally {
            if(kit!=null){
                kit.stopAsync();
                terminal.println("closing: " + walletName);
                kit.awaitTerminated();
                terminal.println("closed: " + walletName);
            }
        }
    }

    public static CharSequence getPassword() throws IOException{
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

    public static String get_walletName() {
        return textIO.newStringInputReader()
                .withDefaultValue(DEFAULT_WALLET_NAME)
                .read("name:");
    }


    private static void get_seed() {
       textIO.newStringInputReader().read("12 word seed phrase:");
    }

}
