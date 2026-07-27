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
            Choice choice = textIO.newEnumInputReader(Choice.class).read("wallet ("+walletName+")");
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
            CharSequence p1 = get_password_from_gui();
            terminal.print("repeat ");
            CharSequence p2 = get_password_from_gui();

            if(CharSequence.compare(p1, p2)==0){
                wallet.encrypt(p1);
                terminal.println("encrypted");
            }else{
                terminal.println("new password's did not match");
            }
        }
    }

    private static void remove(){
        if (!wallet.isEncrypted()) {
            terminal.println("wallet: is NOT encrypted");
        }else {
            try {
                wallet.decrypt(get_password_from_gui());
                terminal.println("decrypted");
            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }
    }

    private static void change() {
        if(wallet.isEncrypted()){
            try {
                terminal.print("OLD ");
                CharSequence old = get_password_from_gui();

                terminal.print("NEW ");
                CharSequence p1 = get_password_from_gui();
                terminal.print("repeat NEW ");
                CharSequence p2 = get_password_from_gui();

                if(CharSequence.compare(p1, p2)!=0) {
                    terminal.println("new password's did not match");
                    return;
                }

                wallet.decrypt(old);

                wallet.encrypt(p1);
                terminal.println("encrypted");

            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }else{
            terminal.println("wallet: is NOT encrypted");
        }
    }



}
