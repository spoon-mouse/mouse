package com.mouse.backend.util;


public record AddressAmountFee(String address, long amount, long fee) {

    public static long MIN_FEE=1;
    public static long MAX_FEE=1000;

    public static AddressAmountFee get(String address, long amount, long fee) throws IllegalArgumentException{

        if(address==null || address.isEmpty()){
            return null;
        }

        if(fee < MIN_FEE || fee > MAX_FEE){
            throw new IllegalArgumentException("fee in sats ["+MIN_FEE+"-"+MAX_FEE+"] fee was: "+ fee);
        }

        return new AddressAmountFee(address, amount, fee);
    }



}