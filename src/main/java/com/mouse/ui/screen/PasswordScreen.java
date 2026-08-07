package com.mouse.ui.screen;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;

import static com.mouse.ui.screen.SecScreen.BAD_WALLET_DECRYPTION;

public class PasswordScreen {
    public static final String  DEFAULT_PASSWORD = "wallet.password";
    public static final String PASSWORDS_DID_NOT_MATCH = "failed: new password's did not match";
    public static final String WALLET_IS_NOT_ENCRYPTED = "wallet: is NOT encrypted";
    public static final String OLD_Msg = "Old ";
    public static final String REPEAT_NEW_MSG = "repeat New ";
    public static final String NEW_MSG = "New ";
    public static final String WALLET_IS_ENCRYPTED = "wallet: is encrypted";
    public static final String ENCRYPTED_MSG = "encrypted";
    public static final String DECRYPTED_MSG = "decrypted";
    private static Wallet wallet;

    private static String walletName;
    private static TextIO textIO;
    private static TextTerminal terminal;

    public static CharSequence get_password_from_gui() {
        return textIO.newStringInputReader()
                                        .withDefaultValue(DEFAULT_PASSWORD)
                                        .withInputMasking(true)
                                        .read("password");
    }

    public enum Choice {
        ADD, REMOVE, CHANGE, STATUS, BACK, EXIT
    }

    public static void show(String name, Wallet wal) throws IOException {
        wallet=wal;
        walletName=name;
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();

        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read("password options wallet("+walletName+")");
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
            terminal.println(WALLET_IS_ENCRYPTED+" password is set");
        }else {
            terminal.println(WALLET_IS_NOT_ENCRYPTED+" password is NOT set");
        }

    }

    private static void add() {
        if (wallet.isEncrypted()) {
            terminal.println(WALLET_IS_ENCRYPTED);
        }else {
            CharSequence p1 = get_password_from_gui();
            terminal.print("repeat ");
            CharSequence p2 = get_password_from_gui();

            if(CharSequence.compare(p1, p2)==0){
                wallet.encrypt(p1);
                terminal.println(ENCRYPTED_MSG);
            }else{
                terminal.println(PASSWORDS_DID_NOT_MATCH);

            }
        }
    }

    private static void remove(){
        if (!wallet.isEncrypted()) {
            terminal.println(WALLET_IS_NOT_ENCRYPTED);
        }else {
            try {
                wallet.decrypt(get_password_from_gui());
                terminal.println(DECRYPTED_MSG);
            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }
    }

    private static void change() {
        if(wallet.isEncrypted()){
            try {
                terminal.print(OLD_Msg);
                CharSequence old = get_password_from_gui();

                terminal.print(NEW_MSG);
                CharSequence p1 = get_password_from_gui();
                terminal.print(REPEAT_NEW_MSG);
                CharSequence p2 = get_password_from_gui();

                if(CharSequence.compare(p1, p2)!=0) {
                    terminal.println(PASSWORDS_DID_NOT_MATCH);
                    return;
                }

                wallet.decrypt(old);

                wallet.encrypt(p1);
                terminal.println(ENCRYPTED_MSG);

            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }else{
            terminal.println(WALLET_IS_NOT_ENCRYPTED);
        }
    }



}
