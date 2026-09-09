package com.mouse.backend.util;

import org.bitcoinj.base.ScriptType;
import org.bitcoinj.wallet.KeyChainGroupStructure;
import org.bitcoinj.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.mouse.backend.util.Config.NETWORK;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Bip39UtilTest {

    @Test
    public void walletSeedTest() throws Exception{

        Wallet wallet = Wallet.createDeterministic(NETWORK, ScriptType.P2WPKH, KeyChainGroupStructure.BIP32);

        List<char[]> words = Bip39Util.getSeedPharase(wallet);

        String getMnemonicString = wallet.getKeyChainSeed().getMnemonicString();

        String charsString = "";
        for (char[] w : words) {
            String word = new String(w);
            charsString += word +" ";
        }
        charsString = charsString.trim();

        System.out.println(getMnemonicString);
        System.out.println(charsString);

        assertEquals(getMnemonicString, charsString);
    }

    @Test
    public void testIndicesFromZeroEntropy() throws Exception {
        byte[] entropy = new byte[16]; // 128 bits of zeros
        int[] indices = Bip39Util.bip39IndicesFromEntropy(entropy);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[][] wordlist = Bip39Util.loadWordlistBytes();
        try {
            Bip39Util.writeMnemonicFromIndices(out, indices, wordlist);
            String mnemonic = out.toString(StandardCharsets.UTF_8);
            String expected = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about\n";
            assertEquals(expected, mnemonic);
        } finally {
            for (byte[] w : wordlist) java.util.Arrays.fill(w, (byte)0);
        }
    }

    @Test
    public void testFillWordChars() throws Exception {
        byte[] entropy = new byte[16];
        int[] indices = Bip39Util.bip39IndicesFromEntropy(entropy);
        byte[][] wordlist = Bip39Util.loadWordlistBytes();
        try {
            char[][] dest = new char[indices.length][];
            for (int i = 0; i < dest.length; i++) dest[i] = new char[16];
            Bip39Util.fillWordCharsFromIndices(indices, wordlist, dest);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dest.length; i++) {
                int len = 0;
                while (len < dest[i].length && dest[i][len] != '\0') len++;
                if (i > 0) sb.append(' ');
                sb.append(new String(dest[i], 0, len));
            }
            sb.append('\n');
            String expected = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about\n";
            assertEquals(expected, sb.toString());
        } finally {
            for (byte[] w : wordlist) java.util.Arrays.fill(w, (byte)0);
        }
    }
}
