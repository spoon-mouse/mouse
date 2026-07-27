package com.mouse.cmd.txtio;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Address;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.time.Instant;

import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;
import static com.mouse.cmd.txtio.WalletScreen.BAD_WALLET_DECRYPTION;


public class PasswordScreen {
    private static Wallet wallet;

    private static String walletName;
    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        ADD, REMOVE, CHANGE, STATUS, BACK, EXIT
    }

    public static void show(String name, Wallet wal) throws IOException {
        wallet=wal;
        walletName=name;
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read("Password for wallet "+walletName);
            switch (choice) {
                case ADD:
                    add();
                    break;
                case REMOVE:
                    remove();
                    break;
                case CHANGE:
                    change();
                    break;
                case STATUS:
                    status();
                    break;
                case BACK:
                    return;
                case EXIT:
                    System.exit(0);
            }
        }
    }

    private static void status() {

        if(wallet.isEncrypted()){
            terminal.println("wallet is encrypted password is set");
        }else {
            terminal.println("wallet is NOT encrypted, password is NOT set");
        }

    }

    private static void add() {
        if (wallet.isEncrypted()) {
            terminal.println("wallet: is encrypted");
        }else {
            wallet.encrypt(get_password_from_gui());
        }
    }

    private static void remove(){
        if (!wallet.isEncrypted()) {
            terminal.println("wallet: is NOT encrypted");
        }else {
            try {
                wallet.decrypt(get_password_from_gui());
            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }
    }

    private static void change() {
        if(wallet.isEncrypted()){
            try {
                terminal.print("OLD ");
                wallet.decrypt(get_password_from_gui());
                terminal.print("NEW ");
                wallet.encrypt(get_password_from_gui());
            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }else{
            terminal.println("wallet: is NOT encrypted");
        }
    }



}
