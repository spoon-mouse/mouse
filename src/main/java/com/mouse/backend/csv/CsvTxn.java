package com.mouse.backend.csv;

import com.mouse.backend.Kit;
import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.txn.IllegalAmountException;
import com.mouse.backend.txn.Txn;
import com.mouse.backend.txn.TxnInfo;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.*;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static com.mouse.backend.csv.CsvUtil.validateConfimationCsvSequenceNumber;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;

public class CsvTxn extends Txn {

    public CsvTxn(String walletName){
        super(walletName);
    }

    protected long lockDuration;

    public CsvTxn setCheckSeqVerDuration(long lockDurationInBlocks){
        validateConfimationCsvSequenceNumber(lockDurationInBlocks);
        lockDuration = lockDurationInBlocks;
        return this;
    }

    public Script createRedeemScript(){
        ScriptBuilder builder = new ScriptBuilder();
        builder.number(lockDuration);
        builder.op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY);
        builder.op(ScriptOpCodes.OP_DROP);
        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_HASH160);
        builder.data(address.getHash());
        builder.op(ScriptOpCodes.OP_EQUALVERIFY);
        builder.op(ScriptOpCodes.OP_CHECKSIG);
        builder.creationTime(Instant.now());     //META-DATA
        return builder.build();
    }

    private static void printRedeemScript(InfoHook progress, Script redeemScript) {
        String kvHexStr = CsvUtil.getRedeemScriptHexKV(redeemScript);
        progress.event(kvHexStr);
    }

    public TxnInfo send(char[] password, InfoHook progress) throws InsufficientMoneyException, ExecutionException, InterruptedException, IllegalAmountException {

        Script redeemScript = createRedeemScript();
        Script p2wshOutputScript = createP2WSHOutputScript(redeemScript);
        p2wshOutputScript = Script.parse( p2wshOutputScript.program(), redeemScript.creationTime().orElse(Instant.EPOCH) );

        Transaction tx = new Transaction();
        tx.addOutput(amount, p2wshOutputScript);

        SendRequest sendRequest = SendRequest.forTx(tx);
        tx = selectTxnInputs(sendRequest);
        tx = deEncryptWalletAndSignTx(tx, password);

        Kit.saveIfAddressInKit(address, redeemScript, p2wshOutputScript);
        printRedeemScript(progress, redeemScript);

        return TxnInfo.get( broadcastTx(tx, progress), wallet );
    }


}
