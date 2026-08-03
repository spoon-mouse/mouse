package com.mouse.util;

import org.bitcoinj.base.Coin;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import static com.mouse.cmd.txtio.LaunchScreen.NETWORK;
import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;
import static com.mouse.util.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;

public class TxnUtil {

    public static Transaction complete_txn(Wallet wallet) throws InsufficientMoneyException {
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        SendRequest sendRequest = SendRequest.emptyWallet(wallet.currentReceiveAddress());
        sendRequest.feePerKb=Coin.ofSat(1L);
        sendRequest.coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());
        sendRequest.missingSigsMode = Wallet.MissingSigsMode.USE_DUMMY_SIG;
        return complete_txn(sendRequest, wallet);
    }

    public static Transaction complete_txn(AddressAmountFee aaf, Wallet wallet) throws InsufficientMoneyException, Wallet.TransactionCompletionException {
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        SendRequest sendRequest = SendRequest.to(aaf.address(), aaf.amount());
        sendRequest.feePerKb = aaf.fee();
        sendRequest.coinSelector = new CsvAwareCoinSelector(DefaultCoinSelector.get(NETWORK), ext.getRedeemScripts());
        sendRequest.missingSigsMode = Wallet.MissingSigsMode.USE_DUMMY_SIG;
        return complete_txn(sendRequest, wallet);
    }

    public static Transaction complete_txn(SendRequest sendRequest, Wallet wallet) throws InsufficientMoneyException, Wallet.TransactionCompletionException {

        final boolean walletEncrypted_at_start = wallet.isEncrypted();
        CharSequence password=null;
        try {
            if(walletEncrypted_at_start) {
                password=get_password_from_gui();
                wallet.decrypt(password);
            }


            //wallet.completeTx(sendRequest);

            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }
        }finally {
            if(!wallet.isEncrypted() && walletEncrypted_at_start){
                wallet.encrypt(password);
            }
        }

        return sendRequest.tx;
    }

}
