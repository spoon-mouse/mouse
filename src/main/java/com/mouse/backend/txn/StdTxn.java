package com.mouse.backend.txn;

import com.mouse.backend.hook.InfoHook;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StdTxn extends Txn{
    private static final Logger log = LoggerFactory.getLogger(StdTxn.class);

    public StdTxn(String walletName) {
        super(walletName);
    }

    public TxnInfo send(char[] password, InfoHook progress) throws Wallet.DustySendRequested, IllegalAmountException, InsufficientMoneyException {

        SendRequest sendRequest = SendRequest.to(address, amount);
        checkDustySendRequest(sendRequest);

        Transaction tx = selectTxnInputs(sendRequest);
        tx = deEncryptWalletAndSignTx(tx, password);

        long realizedFee = Math.round(tx.getVsize() *  feePerVbyteDouble);
        long diff = realizedFee - estFee.value;

        System.out.println("Realized fee: " + realizedFee + ", Estimated fee: " + estFee.value + ", Difference: " + diff);

        log.info("Realized fee: {}, Estimated fee: {}, Difference: {}", realizedFee, estFee.value, diff);

        return TxnInfo.get(broadcastTx(tx, progress), wallet);
    }

}
