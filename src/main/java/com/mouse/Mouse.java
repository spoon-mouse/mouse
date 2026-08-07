package com.mouse;

import com.mouse.ui.screen.LaunchScreen;
import org.bitcoinj.store.BlockStoreException;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Mouse {

    //https://coinfaucet.eu/en/btc-testnet/
    public static final String coin_faucet_return_Address = "tb1qerzrlxcfu24davlur5sqmgzzgsal6wusda40er";


    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, BlockStoreException {
        LaunchScreen.launch();
    }

}
