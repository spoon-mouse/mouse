package com.mouse.util;

import org.bitcoinj.base.Coin;

public record SendTxnInfo(String address, long amount, long fee) {

}