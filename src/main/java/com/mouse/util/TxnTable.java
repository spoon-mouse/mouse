package com.mouse.util;

import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mouse.cmd.txtio.LaunchScreen.NETWORK;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class TxnTable {

    public static String transaction_details(Wallet wallet, String id){
        try{
            Sha256Hash hash = Sha256Hash.wrap(id);
            Transaction tx = wallet.getTransaction(hash);
            if(tx==null){return "ID NOT FOUND";}

            TxnInfo info = TxnInfo.get(tx, wallet);
            return tx+System.lineSeparator()
                     +"toAddress: "+info.toAddress()
                     +System.lineSeparator()
                     +info;
        }catch (IllegalArgumentException e){
            return "id NOT FOUND "+e.getMessage();
        }
    }

    public static String utxo_table(Wallet wallet) {
        AsciiTable table = getTable( "value", "chain depth", "parent txn id", "output idx", "dust", "address");

        Map<Sha256Hash, List<TransactionOutput>> map = wallet.getUnspents().stream().sorted((x, y) -> (int) (x.getValue().value - y.getValue().value))
                                                             .collect(groupingBy(TransactionOutput::getParentTransactionHash));

        map.values().stream().flatMap( l -> l.stream() ).forEach( utxo->{
            table.addRow(utxo.getValue().value,
                         utxo.getParentTransactionDepthInBlocks(),
                         utxo.getParentTransactionHash(),
                         utxo.getIndex(),
                         utxo.isDust(),
                         utxo.getScriptPubKey().getToAddress(NETWORK));
        });
        table.addRule();
        return table.render();
    }

    public static String simple_transation_table(List<Transaction> txns, Wallet wallet) {
        AsciiTable table = getTable("type", "amount", "fee", "total", "value");

        txns.stream().map(tx -> TxnInfo.get(tx, wallet)).forEach(i ->{
            table.addRow(i.type(), i.amount(), i.fee(), i.total(), i.value());
        });
        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size();
    }

    public static String expanded_transation_table(List<Transaction> txns, Wallet wallet) {
        final AsciiTable table = getTable("id", "type", "amount", "fee", "total", "value",  "fromMe", "toMe", "confidenceType", "blockDepth");

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
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size();
    }


    public static String sent_table(List<Transaction> txns, Wallet wallet) {
        AsciiTable table = getTable("id", "type", "amount", "fee", "to address");

        txns.stream().map(tx -> TxnInfo.get(tx, wallet)).filter(TxnInfo::isSend).toList().forEach(tx ->{
            table.addRow( tx.id(), tx.type(), tx.amount(), tx.fee(), tx.toAddress());
        });
        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size();
    }

    public static String recived_table(List<Transaction> txns, Wallet wallet) {
        AsciiTable table = getTable("id", "type", "amount", "address");

        txns.stream().map(tx -> TxnInfo.get(tx, wallet)).filter(TxnInfo::zeroSentFromMe).toList().forEach(tx ->{
            table.addRow( tx.id(), tx.type(), tx.amount(), tx.toAddress());
        });
        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size();
    }


    public static String moved_table(List<Transaction> txns, Wallet wallet) {
        AsciiTable table = getTable("id", "type", "amount", "fee", "total", "address");

        txns.stream().map(tx -> TxnInfo.get(tx, wallet)).filter(TxnInfo::allOutputsMine).toList().forEach(tx ->{
            String addresses = tx.getAllAddresOfOutputs().stream().map(Object::toString).collect(Collectors.joining( System.lineSeparator() ));

            table.addRow( tx.id(), tx.type(), tx.amount(), tx.fee(), tx.total(), addresses);
        });
        table.addRule();
        return table.render()+System.lineSeparator()+"Transactions: "+txns.size();
    }

    public static String send_addresses_table(Wallet wallet){
        AsciiTable table = getTable( "address");

        addressesSentTo(wallet).forEach(address -> table.addRow(address));

        table.addRule();
        return table.render()+System.lineSeparator();
    }

    public static List<Address> addressesSentTo(Wallet wallet){
        return wallet.getTransactionsByTime().stream().map(tx -> TxnInfo.get(tx, wallet)).filter(TxnInfo::isSend)
                .map(tx -> tx.toAddress() ).distinct().toList();
    }


    public static AsciiTable getTable(Object... col) {
        AsciiTable table = new AsciiTable();
        table.getRenderer().setCWC(new CWC_LongestWord());
        table.setPaddingLeftRight(2);
        table.addRule();
        table.addRow(col);
        table.addRule();
        return table;
    }
}
