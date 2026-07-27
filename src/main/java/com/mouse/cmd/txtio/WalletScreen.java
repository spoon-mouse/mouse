package com.mouse.cmd.txtio;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Address;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.time.Instant;

import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;


public class WalletScreen {

    public static final String BAD_WALLET_DECRYPTION = "ERROR INVALID PASSWORD: bad wallet decryption";
    private static WalletAppKit kit;

    private static String walletName;
    private static TextIO textIO;
    private static TextTerminal terminal;

    public enum Choice {
        SEND, RECIVE, TXNS, INFO, LISTEN, PASSWORD, SEED, BACK, EXIT;
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
                case INFO:
                    show_wallet_info();
                    break;
                case PASSWORD:
                    PasswordScreen.show(walletName, kit.wallet());
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

    private static void change_password() {
        final Wallet wallet = kit.wallet();
        terminal.println("CHANGE PASSWORD for wallet: "+walletName);

        if(wallet.isEncrypted()){
            try {
                terminal.print("OLD PASSWORD: ");
                wallet.decrypt(get_password_from_gui());

                terminal.print("NEW PASSWORD: ");
                wallet.encrypt(get_password_from_gui());
            }catch (Wallet.BadWalletEncryptionKeyException e){
                terminal.println(BAD_WALLET_DECRYPTION);
            }
        }else{
            terminal.println("wallet not encrypted:");
            terminal.print("NEW PASSWORD: ");
            wallet.encrypt(get_password_from_gui());
        }
        terminal.println("encrypted:");
    }

    private static void show_wallet_info() {
        terminal.println(kit.wallet().toString());

        final PeerGroup peerGroup = kit.peerGroup();
        terminal.println("connected peers: "+peerGroup.numConnectedPeers());
        terminal.println("max connections: "+peerGroup.getMaxConnections());

        final int height = kit.chain().getBestChainHeight();
        final Instant instant = kit.chain().estimateBlockTimeInstant(height);
        terminal.println("chain hight: "+height+" ("+instant+")");
    }

    private static void show_wallet_seed() throws IOException {
        terminal.println("WARN showing SEED in plain text wallet: "+walletName);
        CharSequence password=null;
        final Wallet wallet = kit.wallet();
        try {
            if(wallet.isEncrypted()){
                password = get_password_from_gui();
                wallet.decrypt(password);
            }
            DeterministicSeed deterministicSeed = wallet.getKeyChainSeed();
            String seed = deterministicSeed.getMnemonicString();
            terminal.println(seed);
            seed=null;
        }catch (Wallet.BadWalletEncryptionKeyException e){
            terminal.println(BAD_WALLET_DECRYPTION);
        }finally {
            if(!wallet.isEncrypted()){
                wallet.encrypt(password);
                password=null;
            }
        }
    }


    private static void recive() {
        Address address = kit.wallet().currentReceiveAddress();
        terminal.println(walletName+" current receive address: "+address);
    }

}
