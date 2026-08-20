package com.mouse.backend.txn;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    public boolean isChange(){
        return value()<0 && fromMe() > 0 && toMe() > 0;
    }

    public boolean isNotChange(){
        return ! isChange();
    }


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
        try {
            return wallet.isAddressMine(o.getScriptPubKey().getToAddress(NETWORK));
        }catch (Exception e){
            return false;
        }
    }

    public static Address getAddress(TransactionOutput o){
        return o.getScriptPubKey().getToAddress(NETWORK);
    }

    public static String getAddressStr(TransactionOutput o){
        try{
            return o.getScriptPubKey().getToAddress(NETWORK).toString();
        }catch (Exception e){
            return e.getMessage();
        }
    }


    public List<Address> getAllAddresOfOutputs(){
        return tx.getOutputs().stream().map(TxnInfo::getAddress).toList();
    }

    public String getAddressSentTo(){
        if( type == TxType.SENT ) {
            Stream<TransactionOutput> notMy = tx.getOutputs().stream().filter(o -> !o.isMine(wallet));
            return notMy.filter(o -> o.getValue().value == amount).map(TxnInfo::getAddressStr).findFirst().orElse("");

        }else if(type == TxType.MOVED){
            return tx.getOutputs().stream().filter(o->hasMyAddress(o)).findFirst().map(TxnInfo::getAddressStr).orElse("");

        }else if(type == TxType.RECEIVE){
            List<TransactionOutput> out = tx.getOutputs().stream().filter(o -> o.getValue().value == amount).toList();
            String address = out.stream().filter(o -> o.isMine(wallet)).findFirst().map(TxnInfo::getAddressStr).orElse("");
            if(!address.isEmpty()){
                return address;
            }
            return out.stream().map(TxnInfo::getAddressStr).findFirst().orElse("");
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
        return "transaction: "+id+" "+type+" amount: "+amount +" fee: "+fee+" total: "+total+" value: "+ value();
    }

    public long value() {
        return tx.getValue(wallet).value;
    }

    public long fromMe() { return tx.getValueSentFromMe(wallet).getValue(); }
    public long toMe() { return tx.getValueSentToMe(wallet).getValue(); }

    public String blockHash(){
        return tx.getAppearsInHashes().entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey().toString();
    }

    public String overRidingTxnId(){
        final Sha256Hash overridingTxId = tx.getConfidence().getOverridingTxId();
        if (overridingTxId != null) {
            return overridingTxId.toString();
        }
        return null;
    }

    public boolean isDusty(){
         return tx.getOutputs().stream().anyMatch(TransactionOutput::isDust);
    }

}