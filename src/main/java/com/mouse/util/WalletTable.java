package com.mouse.util;

import com.mouse.cmd.txtio.LaunchScreen;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mouse.util.TxnTable.getTable;

public class WalletTable {
    public static List<WalletNameId> listOfWallets(){
        List<WalletNameId> wallets = new ArrayList<>();
        try {
            Files.newDirectoryStream(LaunchScreen.WALLET_DIR_PATH,"*"+ LaunchScreen.WALLET_FILE_POST_FIX).forEach(path -> {
                try {
                    wallets.add(WalletNameId.get(Wallet.loadFromFile(path.toFile()), path.toFile()));
                } catch (UnreadableWalletException e) {}
            });
        }catch (IOException e) {}
        return wallets;
    }

    public static Map<String, List<WalletNameId>> mapById(List<WalletNameId> l){
        return l.stream().collect(Collectors.groupingBy(WalletNameId::id));
    }

    private static List<WalletNameId> sortBySeenBlocks(List<WalletNameId> wallets){
        wallets.sort(Comparator.comparing(WalletNameId::getLastBlockSeenHeight));
        return wallets;
    }

    public static Map<String, List<WalletNameId>> getWalletMap(){
        return mapById(sortBySeenBlocks(listOfWallets()));
    }

    public static String get_wallet_digest_table(){
        AsciiTable table = getTable("name", "encrypted", "balance", "block hight", "id", "receive address");

        sortBySeenBlocks(listOfWallets()).reversed().forEach(i -> {
            table.addRow(i.name(), i.wallet().isEncrypted(), i.wallet().getBalance().getValue(), i.wallet().getLastBlockSeenHeight(), i.id(), i.wallet().currentReceiveAddress());
        });

        table.addRule();
        return table.render();
    }
}
