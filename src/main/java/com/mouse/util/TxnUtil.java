package com.mouse.util;

import org.bitcoinj.core.*;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;

public class TxnUtil {

    public static Transaction setup_txn(AddressAmountFee aaf, Wallet wallet) throws Wallet.BadWalletEncryptionKeyException, InsufficientMoneyException, Wallet.DustySendRequested, Wallet.TransactionCompletionException {
        SendRequest sendRequest = SendRequest.to(aaf.address(), aaf.amount());
        sendRequest.feePerKb = aaf.fee();

        final boolean walletEncrypted_at_start = wallet.isEncrypted();
        CharSequence password=null;
        try {
            if(walletEncrypted_at_start) {
                password=get_password_from_gui();
                wallet.decrypt(password);
            }

            wallet.completeTx(sendRequest);

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
