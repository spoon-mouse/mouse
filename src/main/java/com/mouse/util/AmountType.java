package com.mouse.util;

import org.bitcoinj.base.Coin;

public record AmountType(long amount, TxType type, long fee) {
    public static AmountType get(long fromMe, long toMe, Coin fee, long value) {
        long amount=0;
        TxType type;
        if(fromMe == 0){
            type = TxType.RECEIVE;
            amount = toMe - fee.getValue();
        }else{
            if( Math.abs(value) == fee.getValue() ){
                type = TxType.MOVED;
                amount = fromMe;
            }else{
                type = TxType.SENT;
                amount = (fromMe - toMe) - fee.getValue();
            }
        }
        AmountType result = new AmountType(amount, type, fee.getValue());
        return result;
    }
}