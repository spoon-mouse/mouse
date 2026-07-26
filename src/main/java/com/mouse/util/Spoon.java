package com.mouse.util;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Spoon {

    public static BitcoinNetwork network = BitcoinNetwork.TESTNET;
    public static File walletDir = new File(".");

    public static WalletAppKit getWalletAppKit(String walletName, CharSequence password) throws Exception {

        Context context = new Context();

        WalletAppKit kit = WalletAppKit.launch(network, walletDir, walletName);
        Wallet wallet = kit.wallet();

        if(!wallet.isEncrypted()){
            wallet.encrypt(password);
        }

        boolean validPassword = wallet.checkPassword(password);
        password=null;
        if(!validPassword ){
            kit.close();
            throw new Exception("invalid password");
        }
        if(password!=null){
            throw new Exception("password not NULLED out after use");
        }

        return kit;
    }

    public static String transaction_details(Wallet wallet, String id){
        try{
            Sha256Hash hash = Sha256Hash.wrap(id);

            Transaction tx = wallet.getTransaction(hash);
            if(tx==null){
                return "ID NOT FOUND";
            }
            return " "+tx;

        }catch (IllegalArgumentException e){
            return "id NOT FOUND "+e.getMessage();
        }
    }

    public static String simple_transation_table(Wallet wallet) {
        AsciiTable table = new AsciiTable();
        table.addRule();
        table.addRow("type", "amount", "fee");
        table.addRule();

        List<Transaction> txns = wallet.getTransactionsByTime();
        txns.forEach( (tx)->{
            final long fromMe = tx.getValueSentFromMe(wallet).getValue();
            final long toMe = tx.getValueSentToMe(wallet).getValue();
            final long value = tx.getValue(wallet).getValue();
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }
            AmountType pair = AmountType.get(fromMe, toMe, fee, value);
            table.addRow(pair.type(), pair.amount(), fee);
        });

        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size()+" balance: "+wallet.getBalance().toFriendlyString();
    }

    public static String expanded_transation_table(Wallet wallet) {
        AsciiTable table = new AsciiTable();
        table.getRenderer().setCWC(new CWC_LongestWord());
        table.setPaddingLeftRight(2);
        table.addRule();
        table.addRow("id", "type", "amount", "fromMe", "toMe", "value", "fee", "confidenceType", "blockDepth");
        table.addRule();

        List<Transaction> txns = wallet.getTransactionsByTime();
        txns.forEach( (tx)->{
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();

            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            long fromMe = tx.getValueSentFromMe(wallet).getValue();
            long toMe = tx.getValueSentToMe(wallet).getValue();
            long value = tx.getValue(wallet).getValue();
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }
            AmountType pair = AmountType.get(fromMe, toMe, fee, value);
            table.addRow(id, pair.type(), pair.amount(), fromMe, toMe, value, fee, confidenceType, blockDepth);
        });
        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size()+" balance: "+wallet.getBalance().toFriendlyString();
    }


}
