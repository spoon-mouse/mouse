package com.mouse.backend.util;

import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Bip39Util {

    private Bip39Util() {}

    private static volatile byte[][] CACHED_WORDLIST = null;

    public static synchronized byte[][] loadWordlistBytes() throws IOException {
        try (InputStream in = Bip39Util.class.getResourceAsStream("/bip39.txt")) {
            if (in == null) throw new IOException("BIP39 wordlist resource not found");
            byte[] all = in.readAllBytes();
            List<byte[]> words = new ArrayList<>(2048);
            int start = 0;
            for (int i = 0; i < all.length; i++) {
                if (all[i] == '\n' || all[i] == '\r') {
                    if (i > start) {
                        int len = i - start;
                        byte[] w = new byte[len];
                        System.arraycopy(all, start, w, 0, len);
                        words.add(w);
                    }
                    // skip repeated newline chars
                    while (i + 1 < all.length && (all[i+1] == '\n' || all[i+1] == '\r')) i++;
                    start = i + 1;
                }
            }
            if (start < all.length) {
                int len = all.length - start;
                byte[] w = new byte[len];
                System.arraycopy(all, start, w, 0, len);
                words.add(w);
            }
            Arrays.fill(all, (byte)0);
            byte[][] arr = words.toArray(new byte[0][]);
            // do not wipe arr: caller may use it; caching handled separately
            return arr;
        }
    }

    public static synchronized byte[][] getCachedWordlist() throws IOException {
        if (CACHED_WORDLIST == null) {
            CACHED_WORDLIST = loadWordlistBytes();
        }
        return CACHED_WORDLIST;
    }

    public static synchronized void clearCachedWordlist() {
        if (CACHED_WORDLIST != null) {
            for (byte[] w : CACHED_WORDLIST) Arrays.fill(w, (byte)0);
            CACHED_WORDLIST = null;
        }
    }

    private static int bitAt(byte[] data, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitInByte = 7 - (bitIndex % 8);
        return (data[byteIndex] >> bitInByte) & 1;
    }

    public static int[] bip39IndicesFromEntropy(byte[] entropy) throws NoSuchAlgorithmException {
        int ENT = entropy.length * 8;
        int CS = ENT / 32;
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] checksum = sha.digest(entropy);
        int totalBits = ENT + CS;
        int count = totalBits / 11;
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            int index = 0;
            for (int j = 0; j < 11; j++) {
                int bitPos = i * 11 + j;
                int b;
                if (bitPos < ENT) {
                    b = bitAt(entropy, bitPos);
                } else {
                    b = bitAt(checksum, bitPos - ENT);
                }
                index = (index << 1) | b;
            }
            indices[i] = index;
        }
        Arrays.fill(checksum, (byte)0);
        return indices;
    }

    public static void writeMnemonicFromIndices(OutputStream out, int[] indices, byte[][] wordBytes) throws IOException {
        for (int i = 0; i < indices.length; i++) {
            byte[] w = wordBytes[indices[i]];
            out.write(w);
            if (i + 1 < indices.length) out.write(' ');
        }
        out.write('\n');
        out.flush();
    }

    public static List<char[]> mnemonicCharsFromIndices(int[] indices, byte[][] wordBytes) throws java.nio.charset.CharacterCodingException {
        java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
        List<char[]> result = new ArrayList();

        for (int i = 0; i < indices.length; i++) {
            byte[] wb = wordBytes[indices[i]];
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(wb);
            java.nio.CharBuffer cb = decoder.decode(bb);
            char[] chars = new char[cb.length()];
            cb.get(chars);
            result.add(chars);
            // clear char buffer
            cb.clear();
        }
        return result;
    }

    public static void fillWordCharsFromIndices(int[] indices, byte[][] wordBytes, char[][] dest) throws java.nio.charset.CharacterCodingException {
        if (dest == null) throw new IllegalArgumentException("dest must not be null");
        if (dest.length < indices.length) throw new IllegalArgumentException("dest length too small");
        for (int i = 0; i < indices.length; i++) {
            byte[] wb = wordBytes[indices[i]];
            java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(wb);
            java.nio.CharBuffer cb = decoder.decode(bb);
            int len = cb.remaining();
            if (dest[i] == null || dest[i].length < len) {
                throw new IllegalArgumentException("dest[" + i + "] too small for word");
            }
            cb.get(dest[i], 0, len);
            // zero any remaining chars in dest[i]
            for (int j = len; j < dest[i].length; j++) dest[i][j] = '\0';
            cb.clear();
        }
    }

    public static int[] bip39IndicesFromSeedBytes(byte[] seedBytes) throws NoSuchAlgorithmException {
        return bip39IndicesFromEntropy(seedBytes);
    }

    public static byte[] entropyFromSeed(DeterministicSeed seed) throws ReflectiveOperationException {
        if (seed == null) throw new IllegalArgumentException("seed must not be null");
        Method method = DeterministicSeed.class.getDeclaredMethod("getEntropyBytes");
        method.setAccessible(true);
        byte[] entropy = (byte[]) method.invoke(seed);
        if (entropy == null || entropy.length == 0) {
            throw new IllegalStateException("No entropy available from wallet seed");
        }
        return entropy;
    }

    public static List<char[]> getMnemonicChars(Wallet wallet) throws ReflectiveOperationException, NoSuchAlgorithmException, IOException {
        byte[] entropy = Bip39Util.entropyFromSeed(wallet.getKeyChainSeed());
        int[] wordIndices = Bip39Util.bip39IndicesFromEntropy(entropy);
        Arrays.fill(entropy, (byte) 0);
        List<char[]> words = Bip39Util.mnemonicCharsFromIndices(wordIndices, Bip39Util.getCachedWordlist());
        Arrays.fill(wordIndices, (byte) 0);
        return words;
    }

}
