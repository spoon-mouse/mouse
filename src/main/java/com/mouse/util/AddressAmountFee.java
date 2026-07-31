package com.mouse.util;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.wallet.Wallet;

public record AddressAmountFee(Address address, Coin amount, Coin fee) {

    public static long MIN_FEE=1;
    public static long MAX_FEE=99;
    public static AddressAmountFee get(String address, long amount, long fee, Wallet wallet) throws IllegalArgumentException{

        final Coin coinFee = getFeeAsSatsPerKBCoin(fee);

        Coin coinAmount = Coin.ofSat(amount);


        Address jAddress = wallet.parseAddress(address);

        return new AddressAmountFee(jAddress, coinAmount, coinFee);
    }

    public static Coin getFeeAsSatsPerKBCoin(long feeAsSafsPerVbyte) {
        if(feeAsSafsPerVbyte < MIN_FEE || feeAsSafsPerVbyte > MAX_FEE){
            throw new IllegalArgumentException("fee in sats per vbyte out of range ["+MIN_FEE+"-"+MAX_FEE+"] fee set was: "+ feeAsSafsPerVbyte);
        }
        Coin coinFee = Coin.ofSat( feeAsSafsPerVbyte * 1000l );
        return coinFee;
    }
}