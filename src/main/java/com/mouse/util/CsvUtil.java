package com.mouse.util;

import org.bitcoinj.base.internal.ByteUtils;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.*;

import java.util.List;

public class CsvUtil {

    private List<Script> redeemScripts;

    public CsvUtil(List<Script> redeemScripts ){
        this.redeemScripts=redeemScripts;
    }


    public boolean hasOutputLockedByCSV( List<TransactionOutput> outputs) {
        for (TransactionOutput output : outputs)
            if(isTxOutputCsvScript(output))
                return true;
        return false;
    }


    public boolean isTxOutputCsvScript( TransactionOutput output) {
        for (Script redeemScript : redeemScripts) {
            Script scriptPubKey = output.getScriptPubKey();
            Script expectedP2wsh = ScriptBuilder.createP2WSHOutputScript(redeemScript);
            if (scriptPubKey.equals(expectedP2wsh)) {
                return true;
            }
        }
        return false;
    }

    public Script getRedeemScriptForTxOutput(TransactionOutput output) {
        for (Script redeemScript : redeemScripts) {
            Script scriptPubKey = output.getScriptPubKey();
            Script expectedP2wsh = ScriptBuilder.createP2WSHOutputScript(redeemScript);
            if (scriptPubKey.equals(expectedP2wsh)) {
                return redeemScript;
            }
        }
        return null;
    }


    public long getRelativeLock(TransactionOutput output) {
        for (Script redeemScript : redeemScripts) {
            Script scriptPubKey = output.getScriptPubKey();
            Script expectedP2wsh = ScriptBuilder.createP2WSHOutputScript(redeemScript);
            if (scriptPubKey.equals(expectedP2wsh)) {
                return extractCsvSequenceFromScript(redeemScript);
            }
        }
        return 0;
    }


    public static byte[] extractPubKeyHashFromRedeemScript(Script redeemScript) {
        List<ScriptChunk> chunks = redeemScript.chunks();

        for (int i = 0; i < chunks.size() - 1; i++) {
            if (chunks.get(i).opcode == ScriptOpCodes.OP_HASH160) {
                byte[] hash = chunks.get(i + 1).data;
                if (hash != null && hash.length == 20) {
                    return hash;
                }
            }
        }

        throw new IllegalArgumentException("No pubkey hash found after OP_HASH160 in redeem script: "+redeemScript);
    }


    public static long validateConfimationCsvSequenceNumber(long confirmations){
        if (confirmations <= 0) {
            throw new IllegalArgumentException("CSV block count must be positive: " + confirmations);
        }
        if (confirmations > 0xFFFF) {
            throw new IllegalArgumentException(
                    "CSV block count exceeds BIP68's 16-bit value range (max 65535): " + confirmations);
        }

        if ((confirmations & 0x80000000L) != 0) {
            throw new IllegalStateException("CSV sequence has disable flag set — timelock would be ignored: " + confirmations);
        }
        if ((confirmations & 0x00400000L) != 0) {
            throw new IllegalStateException("CSV sequence has time-based type flag set, expected block-based: " + confirmations);
        }
        long value = confirmations & 0x0000FFFFL;
        if (confirmations != value) {
            throw new IllegalStateException("CSV sequence has bits set outside the valid value/flag range: " + confirmations);
        }

        return confirmations;
    }


    public static long extractCsvSequenceFromScript(Script redeemScript) {
        List<ScriptChunk> chunks = redeemScript.getChunks();

        for (int i = 0; i < chunks.size(); i++) {
            ScriptChunk chunk = chunks.get(i);
            if (chunk.equalsOpCode(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY)) {
                if (i == 0) {
                    throw new ScriptException(ScriptError.SCRIPT_ERR_UNKNOWN_ERROR, "OP_CHECKSEQUENCEVERIFY with no preceding value push");
                }
                ScriptChunk valueChunk = chunks.get(i - 1);

                // OP_1..OP_16: value is encoded directly in the opcode
                if (valueChunk.isOpCode()) {
                    return validateConfimationCsvSequenceNumber(valueChunk.decodeOpN());
                }

                // otherwise it's pushdata — decode as a minimally-encoded script number
                if (valueChunk.data != null) {
                    return validateConfimationCsvSequenceNumber(ByteUtils.decodeMPI(ByteUtils.reverseBytes(valueChunk.data), false).longValue());
                }


                throw new ScriptException(ScriptError.SCRIPT_ERR_UNKNOWN_ERROR, "Could not decode CSV value push");
            }
        }

        throw new ScriptException(ScriptError.SCRIPT_ERR_UNKNOWN_ERROR, "No OP_CHECKSEQUENCEVERIFY found in script");
    }




}
