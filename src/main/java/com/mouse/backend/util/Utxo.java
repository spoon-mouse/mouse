package com.mouse.backend.util;


import com.mouse.backend.csv.CsvUtil;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import java.util.Objects;

import static com.mouse.backend.util.Config.NETWORK;

public record Utxo(TransactionOutput output, CsvUtil chekSeqVerUtil){

        public String txId(){
            return Objects.requireNonNull( output.getParentTransactionHash() ).toString();
        }

        public int idx(){
            return output.getIndex();
        }

        public String address(){
            return output.getScriptPubKey().getToAddress(NETWORK).toString();
        }

        public boolean isCheckSeqVer(){
            return chekSeqVerUtil.isTxOutputCsvScript(output);
        }

        public long relativeBlocksLock(){
            return chekSeqVerUtil.getRelativeLock(output);
        }


        public long value(){
            return output.getValue().value;
        }

        public int blockDepth(){
            return output.getParentTransactionDepthInBlocks();
        }

        public boolean isCheckSeqVerLocked(){
            return blocksRemaining() > 0;
        }

        public long blocksRemaining (){
            return relativeBlocksLock() - blockDepth();
        }

        public Script getRedeemScript(){
            return chekSeqVerUtil.getRedeemScript(output);
        }

        public  String getRedeemScriptHexKV() {
            return chekSeqVerUtil.getRedeemScriptHexKV(output);
        }
}
