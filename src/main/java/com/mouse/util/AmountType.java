package com.mouse.util;

import org.bitcoinj.base.Coin;

public record AmountType(long amount, TxType type, long total) {
    public static AmountType get(long fromMe, long toMe, long fee, long value) {
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
        return new AmountType(amount, type, total);
    }
}