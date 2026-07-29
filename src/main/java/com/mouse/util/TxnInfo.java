package com.mouse.util;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;

import static com.mouse.cmd.txtio.LaunchScreen.NETWORK;

public record TxnInfo(Wallet wallet, Transaction tx, String id, long amount, TxType type, long total, long fee) {

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

        return get(wallet, txn, id, fromMe, toMe, fee, value);
    }

    public boolean isSend(){ return type == TxType.SENT; }

    private static TxnInfo get(Wallet wallet, Transaction txn, String id, long fromMe, long toMe, long fee, long value) {
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
        return new TxnInfo(wallet, txn, id, amount, type, total, fee);
    }

    /*
    * if its a TxType.SENT and the output with value == to amount is what was sent
    * so that output address is the toAddress. or returns null
    * */
    public Address toAddress(){
        if( type == TxType.SENT ) {
            return tx.getOutputs().stream().filter(o -> o.getValue().value == amount)
                    .map( o -> o.getScriptPubKey().getToAddress(NETWORK)).findFirst().orElse(null);
        }
        return null;
    }

    public boolean allOutputsMine(){
        return tx.getOutputs().stream().allMatch(o-> wallet.isAddressMine( o.getScriptPubKey().getToAddress(NETWORK) ));
    }

    public boolean zeroSentFromMe(){
        return tx.getValueSentFromMe(wallet).value==0;
    }

    public String toString(){
        switch (type){
            case MOVED:
                return "transaction: "+id+" "+type+" "+amount +" fee: "+fee;
            case SENT:
            case RECEIVE:
                return "transaction: "+id+" "+type+" "+amount +" fee: "+fee+" total: "+total;
        }
        return null;
    }
}