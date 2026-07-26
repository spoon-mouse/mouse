package com.mouse.cmd.txtio;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Address;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.DeterministicSeed;

import java.io.IOException;

import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;


public class WalletScreen {

    private static WalletAppKit kit;

    private static String walletName;
    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        SEND, RECIVE, TXNS, LISTEN, SEED, BACK, EXIT;
    }

    public static void show(String name, WalletAppKit appkit) throws IOException {
        kit = appkit;
        walletName=name;
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+" balance: "+kit.wallet().getBalance().toFriendlyString());
            switch (choice) {
                case SEND:
                    SendTransactionScreen.show(walletName, kit);
                    break;
                case RECIVE:
                    recive();
                    break;
                case TXNS:
                    TransactionHistoryScreen.show(walletName, kit.wallet());
                    break;
                case SEED:
                    show_wallet_seed();
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }

    private static void show_wallet_seed() throws IOException {
        terminal.println("WARN showing seed in plain text!");
        CharSequence password=null;
        try {
            if(kit.wallet().isEncrypted()){
                password = get_password_from_gui();
                kit.wallet().decrypt(password);
            }

            DeterministicSeed deterministicSeed = kit.wallet().getKeyChainSeed();
            String seed = deterministicSeed.getMnemonicString();
            System.out.println(seed);
            terminal.println(seed);
            seed=null;
        }catch (Exception e){
            if( e instanceof KeyCrypterException.InvalidCipherText){
                terminal.println("Could not decrypt seed: invalid password");
            }else{
                terminal.println(e.getMessage());
            }
        }finally {
            if(!kit.wallet().isEncrypted()){
                kit.wallet().encrypt(password);
                password=null;
            }
        }
    }


    private static void recive() {
        Address address = kit.wallet().currentReceiveAddress();
        terminal.println(walletName+" current receive address: "+address);
    }

}
