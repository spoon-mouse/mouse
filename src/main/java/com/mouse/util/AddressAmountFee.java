package com.mouse.util;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.wallet.Wallet;

public record AddressAmountFee(Address address, Coin amount, Coin fee) {

    public static AddressAmountFee get(String address, long amount, long fee, Wallet wallet) throws IllegalArgumentException{

        if(fee< 1 || fee > 99){
            throw new IllegalArgumentException("fee in sats per vbyte out of range 1-99 fee set was: "+ fee);
        }

        Coin coinAmount = Coin.ofSat(amount);

        Coin coinFee = Coin.ofSat( fee * 1000l );

        Address jAddress = wallet.parseAddress(address);

        return new AddressAmountFee(jAddress, coinAmount, coinFee);
    }
}