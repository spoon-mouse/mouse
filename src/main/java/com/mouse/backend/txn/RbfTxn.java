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

import java.nio.ByteBuffer;
import java.util.List;


public class RbfTxn extends Txn{
    private static final Logger log = LoggerFactory.getLogger(RbfTxn.class);

    public RbfTxn(String walletName) {
        super(walletName);
    }

    public TxnInfo send(PasswordPrompt prompt, InfoHook progress) throws Wallet.DustySendRequested, InsufficientMoneyException {

        Sha256Hash id = Sha256Hash.wrap(txnId);
        Transaction orignalTx = wallet.getTransaction(id);
        long oldFee = orignalTx.getFee().value;

        byte[] rawBytes = orignalTx.serialize();
        Transaction txCopy = Transaction.read(ByteBuffer.wrap(rawBytes));
        long newFee = Math.round(txCopy.getVsize() *  feePerVbyteDouble);

        if( !txCopy.isOptInFullRBF() ){
            throw new IllegalArgumentException("txn RBF not supported");
        }

        TxnInfo txInfo = TxnInfo.get(txCopy, wallet);
        if( false == (txInfo.isSend() && txCopy.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) ){
            throw new IllegalArgumentException("expected state sent pending");
        }

        if(newFee <= oldFee){
            throw new IllegalArgumentException("Fee new "+newFee+" <= old "+ oldFee);
        }
        Coin feeDelta = Coin.ofSat(newFee - oldFee);
        log.info("RbfTxn: newFee={}, oldFee={}, feeDelta={}", newFee, oldFee, feeDelta);


        List<TransactionOutput> myOutputs = txCopy.getOutputs().stream().filter(o -> o.isMine(wallet)).toList();
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

        txCopy.replaceOutput(oldChangeOutput.getIndex(), newChangeOutput);
        deEncryptWalletAndSignTx(txCopy, prompt);
        txCopy = broadcastTx(txCopy, progress);

        return TxnInfo.get(txCopy, wallet);
    }


}
