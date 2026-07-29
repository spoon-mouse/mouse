package com.mouse.util;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

public class TxnTable {

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

    public static String simple_transation_table(List<Transaction> txns, Wallet wallet) {
        AsciiTable table = new AsciiTable();
        table.addRule();
        table.addRow("type", "amount", "fee");
        table.addRule();

        txns.forEach( (tx)->{
            TxnInfo info = TxnInfo.get(tx, wallet);
            table.addRow(info.type(), info.amount(), info.fee());
        });

        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size()+" balance: "+wallet.getBalance().toFriendlyString();
    }

    public static String expanded_transation_table(List<Transaction> txns, Wallet wallet) {
        AsciiTable table = new AsciiTable();
        table.getRenderer().setCWC(new CWC_LongestWord());
        table.setPaddingLeftRight(2);
        table.addRule();
        table.addRow("id", "type", "amount", "fee", "total", "value",  "fromMe", "toMe", "confidenceType", "blockDepth");
        table.addRule();

        txns.forEach( (tx)->{
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            int blockDepth = confidence.getDepthInBlocks();

            long fromMe = tx.getValueSentFromMe(wallet).getValue();
            long toMe = tx.getValueSentToMe(wallet).getValue();
            long value = tx.getValue(wallet).getValue();

            TxnInfo info = TxnInfo.get(tx, wallet);
            table.addRow(info.id(), info.type(), info.amount(), info.fee(), info.total(), value, fromMe, toMe, confidenceType, blockDepth);
        });
        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size()+" balance: "+wallet.getBalance().toFriendlyString();
    }


}
