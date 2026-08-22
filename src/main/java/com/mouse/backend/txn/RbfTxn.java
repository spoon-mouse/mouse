package com.mouse.backend.txn;

import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.hook.PasswordPrompt;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.IN_CONFLICT;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;


public class RbfTxn extends Txn{
    private static final Logger log = LoggerFactory.getLogger(RbfTxn.class);

    public RbfTxn(String walletName) {
        super(walletName);
    }



    public TxnInfo send(PasswordPrompt prompt, InfoHook progress) throws Wallet.DustySendRequested, InsufficientMoneyException {
        Sha256Hash id = Sha256Hash.wrap(txnId);
        Transaction orignalTx = wallet.getTransaction(id);

        final SendRequest sendRequest = SendRequest.forTx(orignalTx);

        Coin coinFeePerkvb = Coin.ofSat(Math.round(feePerVbyteDouble * 1000));
        sendRequest.setFeePerVkb(coinFeePerkvb);

        final Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);

        return TxnInfo.get(sendResult.transaction(), wallet);
    }


    public TxnInfo send2(PasswordPrompt prompt, InfoHook progress) throws Wallet.DustySendRequested, InsufficientMoneyException {

        Sha256Hash id = Sha256Hash.wrap(txnId);
        Transaction orignalTx = wallet.getTransaction(id);
        log.info("RbfTxn:  OriginalTx={}", orignalTx);

        long oldFee = orignalTx.getFee().value;

        byte[] rawBytes = orignalTx.serialize();
        Transaction txCopy = Transaction.read(ByteBuffer.wrap(rawBytes));
        log.info("RbfTxn:  txCopy={}", txCopy);
        long newFee = Math.round(txCopy.getVsize() *  feePerVbyteDouble);

        if( !txCopy.isOptInFullRBF() ){
            throw new IllegalArgumentException("txn RBF not supported");
        }

        TxnInfo txInfo = TxnInfo.get(txCopy, wallet);
        final TransactionConfidence.ConfidenceType confidenceType = txCopy.getConfidence().getConfidenceType();

        if(!(txInfo.isSend() && (confidenceType == PENDING || confidenceType == IN_CONFLICT))){
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
        log.info("RbfTxn: oldChange output={}", oldChangeOutput);

        Coin oldChangeValue = oldChangeOutput.getValue();
        log.info("RbfTxn: oldChange value={}", oldChangeValue);


        Coin newChange = oldChangeValue.subtract(feeDelta);
        log.info("RbfTxn: newChange ={}", newChange);

        TransactionOutput newChangeOutput = oldChangeOutput.withValue(newChange);
        log.info("RbfTxn: newChange output={}", newChangeOutput);

        Coin newChangeValue = newChangeOutput.getValue();
        log.info("RbfTxn: newChange value={}", newChangeValue);

        final Coin changeDelta = oldChangeValue.subtract(newChangeValue);
        log.info("RbfTxn: changeDelta={}", changeDelta);


        if(newChange.isNegative()) {
            throw new InsufficientMoneyException(newChange);
        }
        if (newChangeOutput.isDust()) {
            throw new InsufficientMoneyException(newChange);
        }

        txCopy.replaceOutput(oldChangeOutput.getIndex(), newChangeOutput);


        log.info("RbfTxn: final txCopy fee={}", txCopy.getFee());
        log.info("RbfTxn: final txCopy={}", txCopy);


        //deEncryptWalletAndSignTx(txCopy, prompt);
        //txCopy = broadcastTx(txCopy, progress);

        return TxnInfo.get(txCopy, wallet);
    }


}
