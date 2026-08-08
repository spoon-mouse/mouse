package com.mouse.backend.txn;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

import static com.mouse.backend.util.Config.NETWORK;

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
            amount = toMe;
            total=amount;
        }else{
            if( Math.abs(value) == fee ){
                type = TxType.MOVED;
                amount = fromMe;
                total = amount - fee;
            }else{
                type = TxType.SENT;
                amount = (fromMe - toMe) - fee;
                total = amount + fee;
            }
        }
        return new TxnInfo(wallet, txn, id, amount, type, total, fee);
    }


    private boolean hasMyAddress(TransactionOutput o){
        return wallet.isAddressMine( o.getScriptPubKey().getToAddress(NETWORK));
    }

    public static Address getAddress(TransactionOutput o){ return o.getScriptPubKey().getToAddress(NETWORK); }

    public List<Address> getAllAddresOfOutputs(){
        return tx.getOutputs().stream().map(TxnInfo::getAddress).toList();
    }

    public Address toAddress(){
        if( type == TxType.SENT ) {
            return tx.getOutputs().stream().filter(o -> o.getValue().value == amount)
                     .map(TxnInfo::getAddress).findFirst().orElse(null);

        }else if(type == TxType.MOVED){
            return tx.getOutputs().stream().filter(o->hasMyAddress(o))
                     .findFirst().map(TxnInfo::getAddress).orElse(null);

        }else if(type == TxType.RECEIVE){
            return tx.getOutputs().stream().filter(o->hasMyAddress(o))
                      .filter(o->o.getValue().value == amount).findFirst().map(TxnInfo::getAddress).orElse(null);
        }
        return null;
    }

    public boolean allOutputsMine(){
        return tx.getOutputs().stream().allMatch(o-> hasMyAddress(o));
    }

    public boolean zeroSentFromMe(){
        return tx.getValueSentFromMe(wallet).value==0;
    }

    public String toString(){
        return "transaction: "+id+" "+type+" amount: "+amount +" fee: "+fee+" total: "+total+" value: "+ tx().getValue(wallet).value;
    }

    public long value() {
        return tx.getValue(wallet).value;
    }
}