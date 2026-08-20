package com.mouse.backend.txn;

import com.mouse.backend.hook.InfoHook;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

public class StdTxn extends Txn{
    public StdTxn(String walletName) {
        super(walletName);
    }

    public TxnInfo send(char[] password, InfoHook progress) throws Wallet.DustySendRequested, IllegalAmountException, InsufficientMoneyException {

        SendRequest sendRequest = SendRequest.to(address, amount);
        checkDustySendRequest(sendRequest);

        Transaction tx = selectTxnInputs(sendRequest);
        tx = deEncryptWalletAndSignTx(tx, password);

        return TxnInfo.get(netBroadcast(tx, progress), wallet);
    }

}
