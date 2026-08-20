package com.mouse.backend.util;


import com.mouse.backend.csv.CsvUtil;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import java.util.Objects;

import static com.mouse.backend.util.Config.NETWORK;

public record Utxo(TransactionOutput output, CsvUtil scvUtil){

        public String txId(){
            return Objects.requireNonNull( output.getParentTransactionHash() ).toString();
        }

        public int idx(){
            return output.getIndex();
        }

        public String address(){
            return output.getScriptPubKey().getToAddress(NETWORK).toString();
        }

        public boolean csv(){
            return scvUtil.isTxOutputCsvScript(output);
        }

        public long relLock(){
            return scvUtil.getRelativeLock(output);
        }

        public long value(){
            return output.getValue().value;
        }

        public int depth(){
            return output.getParentTransactionDepthInBlocks();
        }

        public long lockedBlockCount(){
            return relLock() - depth();
        }

        public Script getRedeemScript(){
            return scvUtil.getRedeemScript(output);
        }

        public  String getRedeemScriptHexKV() {
            return scvUtil.getRedeemScriptHexKV(output);
        }
}
