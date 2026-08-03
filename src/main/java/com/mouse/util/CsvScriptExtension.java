package com.mouse.util;

import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CsvScriptExtension implements WalletExtension {

    public static final String COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS = "com.spoon.mouse.check.seq.redeem.scripts";
    private final List<Script> redeemScripts = new ArrayList<>();

    @Override
    public String getWalletExtensionID() {
        return COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS; // unique ID, like a reverse-domain namespace
    }

    @Override
    public boolean isWalletExtensionMandatory() {
        // true = wallet refuses to load if this extension can't be deserialized
        // false = wallet loads anyway, extension data just silently missing
        return true; // you probably want true — silently losing CSV scripts is bad
    }

    @Override
    public byte[] serializeWalletExtension() {
        // pack your List<Script> into bytes — simplest: length-prefix each script's program()
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        try {
            dos.writeInt(redeemScripts.size());
            for (Script s : redeemScripts) {
                byte[] program = s.program();
                dos.writeInt(program.length);
                dos.write(program);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    @Override
    public void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        int count = dis.readInt();
        for (int i = 0; i < count; i++) {
            int len = dis.readInt();
            byte[] program = new byte[len];
            dis.readFully(program);
            redeemScripts.add(Script.parse(program)); // exact parse method name may vary by version
            System.out.println("loaded: "+Script.parse(program));
        }
    }

    public List<Script> getRedeemScripts() {
        return redeemScripts;
    }

    public void addRedeemScript(Script script) {
        redeemScripts.add(script);
    }



    public boolean isTxOutputCsvScript(TransactionOutput output) {
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


}