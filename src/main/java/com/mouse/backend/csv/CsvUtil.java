package com.mouse.backend.csv;

import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.util.Config;
import org.bitcoinj.base.internal.ByteUtils;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.*;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public class CsvUtil {

    private final CsvScriptExtension ext;
    private final List<Script> redeemScripts;

    public CsvUtil(CsvScriptExtension ext) {
        this.ext = ext;
        this.redeemScripts = this.ext.getRedeemScripts();
    }

    public Script getRedeemScript(TransactionOutput output){
        for (Script redeemScript : redeemScripts) {
            Script scriptPubKey = output.getScriptPubKey();
            Script expectedP2wsh = ScriptBuilder.createP2WSHOutputScript(redeemScript);
            if (scriptPubKey.equals(expectedP2wsh)) {
                return redeemScript;
            }
        }
        return null;
    }

    public boolean isTxOutputCsvScript( TransactionOutput output) {
        return getRedeemScript(output) != null;
    }

    public long getRelativeLock(TransactionOutput output) {
        Script redeemScript = getRedeemScript(output);
        if(redeemScript == null) {
            return -1;
        }
        return extractCsvSequenceFromScript(redeemScript);
    }

    public String getRedeemScriptHexKV(TransactionOutput output ) {
        Script redeemScript = getRedeemScript(output);
        return getRedeemScriptHexKV(redeemScript);
    }

    public static String getRedeemScriptHexKV(Script redeemScript) {
        checkRedeemScript(redeemScript);

        final byte[] programBytes = redeemScript.program();
        String hexStr = HexFormat.of().formatHex(programBytes);

        final Instant instant = redeemScript.creationTime().orElse(Instant.EPOCH);
        final long epochSecond = instant.getEpochSecond();

        return Config.REDEEM_SCRIPT_HEX_KEY + "=" + hexStr + " " + Config.CREATION_TIME_KEY + "=" + epochSecond;
    }



    public static byte[] extractPubKeyHashFromRedeemScript(Script redeemScript) {
        checkRedeemScript(redeemScript);

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
        checkRedeemScript(redeemScript);

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



    private static void checkRedeemScript(Script redeemScript) {
        if(redeemScript == null) {
            throw new IllegalArgumentException("redeemScript cannot be null");
        }
    }

}
