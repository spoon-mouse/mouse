package com.mouse.backend.csv;

import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CsvScriptExtension implements WalletExtension {
    private static final Logger log = LoggerFactory.getLogger(CsvScriptExtension.class);

    public static String COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS = "com.spoon.mouse.check.seq.redeem.scripts";
    private final List<Script> redeemScripts = new ArrayList<>();

    @Override
    public String getWalletExtensionID() {
        return COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS; // unique ID, like a reverse-domain namespace
    }

    @Override
    public boolean isWalletExtensionMandatory() {
        return true; // you probably want true — silently losing CSV scripts is bad
    }

    @Override
    public byte[] serializeWalletExtension() {
        // pack your List<Script> into bytes — simplest: length-prefix each script's program()
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        try {
            dos.writeInt(redeemScripts.size());
            for (Script script : redeemScripts) {
                byte[] program = script.program();
                dos.writeInt(program.length);
                dos.write(program);
                dos.writeLong(script.creationTime().get().getEpochSecond());
            }
        } catch (IOException e) {
            log.error("Failed to serialize CSV redeem scripts for wallet extension {}", getWalletExtensionID(), e);
            throw new RuntimeException("Failed to serialize CSV redeem scripts", e);
        }
        return out.toByteArray();
    }

    @Override
    public void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                int len = dis.readInt();
                byte[] program = new byte[len];
                dis.readFully(program);

                long epochSeconds = dis.readLong();
                Instant creationTime = Instant.ofEpochSecond(epochSeconds);

                //Instant now = Instant.now();
                //Instant yesterday = now.minus(1, ChronoUnit.DAYS);

                Script redeemScript = Script.parse(program, creationTime);
                redeemScripts.add(redeemScript);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize CSV redeem scripts for wallet {}", containingWallet == null ? "unknown" : containingWallet, e);
            throw e;
        }
    }

    public List<Script> getRedeemScripts() {
        return redeemScripts;
    }

    public void addRedeemScript(Script redeemScript) {
        redeemScripts.add(redeemScript);
    }

    public Script addRedeemScript(String kvStringProgHexCreationTimeSeconds) {
        Script script = CsvUtil.deSerializeRedeemScriptHexKV(kvStringProgHexCreationTimeSeconds);
        addRedeemScript(script);
        return script;
    }

}