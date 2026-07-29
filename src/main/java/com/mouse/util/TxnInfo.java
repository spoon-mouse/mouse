package com.mouse.util;

import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

public record TxnInfo(String id, long amount, TxType type, long total, long fee) {

    public static TxnInfo get(Transaction txn, Wallet wallet){

        String id = txn.getTxId().toString();
        long value = txn.getValue(wallet).getValue();
        long fromMe=txn.getValueSentFromMe(wallet).getValue();
        long toMe=txn.getValueSentToMe(wallet).getValue();

        Coin txnFee = txn.getFee();
        if(txnFee==null){
            txnFee=Coin.ZERO;
        }
        long fee = txnFee.getValue();

        return get(id, fromMe, toMe, fee, value);
    }

    public static TxnInfo get(String id, long fromMe, long toMe, long fee, long value) {
        long amount=0;
        TxType type;
        long total=0;
        if(fromMe == 0){
            type = TxType.RECEIVE;
            amount = toMe - fee;
            total=amount;
        }else{
            if( Math.abs(value) == fee ){
                type = TxType.MOVED;
                amount = fromMe;
                total = fee;
            }else{
                type = TxType.SENT;
                amount = (fromMe - toMe) - fee;
                total = amount + fee;
            }
        }
        return new TxnInfo(id, amount, type, total, fee);
    }

    public String toString(){
        return "transaction: "+id+" "+type+" amount: "+amount +" fee: "+fee+" total: "+total;
    }
}