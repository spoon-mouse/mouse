package com.mouse.util;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.wallet.Wallet;

public record AddressAmountFee(Address address, Coin amount, Coin fee) {

    public static long MIN_FEE=1;
    public static long MAX_FEE=1000;
    public static AddressAmountFee get(String address, long amount, long fee, Wallet wallet) throws IllegalArgumentException{

        final Coin coinFee = getAbsFee(fee);

        Coin coinAmount = Coin.ofSat(amount);


        Address jAddress = wallet.parseAddress(address);

        return new AddressAmountFee(jAddress, coinAmount, coinFee);
    }

    public static Coin getAbsFee(long fee) {
        if(fee < MIN_FEE || fee > MAX_FEE){
            throw new IllegalArgumentException("fee in sats ["+MIN_FEE+"-"+MAX_FEE+"] fee was: "+ fee);
        }
        Coin coinFee = Coin.ofSat( fee  );
        return coinFee;
    }
}