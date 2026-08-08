package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static com.mouse.ui.input.Input.getPassword;

public class SecScreen {
    public static final String BAD_WALLET_DECRYPTION = "ERROR INVALID PASSWORD: bad wallet decryption";
    public enum Choice { PASSWORD, SEED, BACK, EXIT; }
    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    private String walletName;
    private Wallet wallet;

    public SecScreen(String name){
        walletName=name;
        wallet = Kit.wallet(walletName);
    }

    public void show() throws IOException {
        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+ " Security");
            switch (choice) {
                case PASSWORD:
                    PasswordScreen.show(walletName, wallet);
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


    private void show_wallet_seed() throws IOException {
        terminal.println("WARN showing SEED in plain text for wallet "+walletName);

        CharSequence password=null;
        final boolean walletEncrypted_at_start = wallet.isEncrypted();

        try {
            if(wallet.isEncrypted()){
                password = getPassword();
                wallet.decrypt(password);
            }
            DeterministicSeed deterministicSeed = wallet.getKeyChainSeed();

            final Optional<Instant> creationTime = deterministicSeed.getCreationTime();
            if(creationTime.isPresent()) {
                final long epochSeconds = creationTime.get().getEpochSecond();
                terminal.println("creation epoch seconds: "+epochSeconds);
            }
            final String seed = deterministicSeed.getMnemonicString();
            terminal.println(seed);

        }catch (Wallet.BadWalletEncryptionKeyException e){
            terminal.println(BAD_WALLET_DECRYPTION);
        }finally {
            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }
            password=null;
        }
    }
}
