package com.mouse.backend.txn;

import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.hook.PasswordPrompt;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class RbfTxn extends Txn{
    private static final Logger log = LoggerFactory.getLogger(RbfTxn.class);

    public RbfTxn(String walletName) {
        super(walletName);
    }

    public TxnInfo send(PasswordPrompt prompt, InfoHook progress) throws Wallet.DustySendRequested, InsufficientMoneyException {

        Sha256Hash id = Sha256Hash.wrap(txnId);
        Transaction tx = wallet.getTransaction(id);
        TxnInfo txInfo = TxnInfo.get(tx, wallet);

        if( !tx.isOptInFullRBF() ){
            throw new IllegalArgumentException("txn RBF not supported");
        }

        if( false == (txInfo.isSend() && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) ){
            throw new IllegalArgumentException("expected state sent pending");
        }

        long newFee = Math.round(tx.getVsize() *  feePerVbyteDouble);
        long oldFee = tx.getFee().value;
        if(newFee <= oldFee){
            throw new IllegalArgumentException("Fee new "+newFee+" <= old "+ oldFee);
        }
        Coin feeDelta = Coin.ofSat(newFee - oldFee);

        List<TransactionOutput> myOutputs = tx.getOutputs().stream().filter(o -> o.isMine(wallet)).toList();
        if(myOutputs.size() == 0){
            throw new IllegalStateException("tx no outputs !");
        }
        if(myOutputs.size() > 1){
            throw new IllegalStateException("more that 1 output to this wallet which is change ?");
        }

        TransactionOutput oldChangeOutput = myOutputs.get(0);
        Coin oldChange = oldChangeOutput.getValue();

        Coin newChange = oldChange.subtract(feeDelta);
        TransactionOutput newChangeOutput = oldChangeOutput.withValue(newChange);


        if(newChange.isNegative()) {
            throw new InsufficientMoneyException(newChange);
        }
        if (newChangeOutput.isDust()) {
            throw new InsufficientMoneyException(newChange);
        }

        tx.replaceOutput(oldChangeOutput.getIndex(), newChangeOutput);
        deEncryptWalletAndSignTx(tx, prompt);
        tx = broadcastTx(tx, progress);

        return TxnInfo.get(tx, wallet);
    }


}
