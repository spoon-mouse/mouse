package com.mouse.ui.input;

import static com.mouse.ui.input.Input.*;

public record AddressAmountFee(String address, long amount, long fee) {

    public static long MIN_FEE=1;
    public static long MAX_FEE=1000;

    public static AddressAmountFee get() throws IllegalArgumentException{

        String address = getAddress();
        if(address==null || address.isEmpty()){
            return null;
        }

        long amount = getAmount();

        final long fee = getFee();
        if(fee < MIN_FEE || fee > MAX_FEE){
            throw new IllegalArgumentException("fee in sats ["+MIN_FEE+"-"+MAX_FEE+"] fee was: "+ fee);
        }

        return new AddressAmountFee(address, amount, fee);
    }



}