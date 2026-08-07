package com.mouse.backend;

import com.mouse.backend.csv.CsvP2WshSigner;
import com.mouse.backend.csv.CsvScriptExtension;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.KeyChainGroupStructure;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static com.mouse.backend.util.Config.*;
import static java.util.stream.Collectors.toList;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;

/**
 * Replaces WalletAppKit. Owns exactly ONE BlockStore/BlockChain/PeerGroup for the
 * whole application's lifetime, and hosts multiple wallets on top of that single
 * shared setup — rather than WalletAppKit's one-kit-per-wallet model.
 *
 * This class is backend-only: no TextIO/terminal calls, no UI concerns. UI screens
 * depend on this class + plain Wallet objects, never on PeerGroup/BlockChain/BlockStore
 * directly.
 */
public class BlockChain {

    public static final int WAIT_MIN_NUM_PEERS = 3;
    private static BlockChain instance;

    private final BlockStore blockStore;
    private final org.bitcoinj.core.BlockChain chain;
    private final PeerGroup peerGroup;

    private final Map<String, Wallet> wallets = new ConcurrentHashMap<>();

    private BlockChain(BlockStore blockStore, org.bitcoinj.core.BlockChain chain, PeerGroup peerGroup) {
        this.blockStore = blockStore;
        this.chain = chain;
        this.peerGroup = peerGroup;
    }

    /**
     * Starts the shared node: opens one block store, one chain, one peer group,
     * for the whole app. Call once, at application startup.
     *
     * @param progressListener notified of blockchain download progress. Backend has
     *                         no opinion on how progress is displayed — pass a
     *                         terminal-printing listener, a mobile progress-bar
     *                         listener, or DownloadProgressTracker's own no-op
     *                         default if you don't care.
     */
    public static synchronized BlockChain start(DownloadProgressTracker progressListener) throws BlockStoreException {
        if (instance != null) {
            return instance;
        }

        BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(WALLET_DIR_PATH + "/shared" + SPVCHAIN_FILE_POST_FIX));

        org.bitcoinj.core.BlockChain chain = new org.bitcoinj.core.BlockChain(NETWORK, blockStore);
        PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));

        peerGroup.start();

        try {
            peerGroup.waitForPeers(WAIT_MIN_NUM_PEERS).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }

        peerGroup.startBlockChainDownload(progressListener);
        try {
            progressListener.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        instance = new BlockChain(blockStore, chain, peerGroup);
        return instance;
    }

    public static BlockChain get() {
        if (instance == null) {
            throw new IllegalStateException("BlockChain.start(...) has not been called yet");
        }
        return instance;
    }

    /**
     * Loads an existing wallet file, or creates a fresh wallet if none exists yet,
     * attaches the CSV extension/watched-scripts/signer, and hooks it onto the
     * shared chain + peer group. Returns the ready-to-use Wallet.
     */
    public synchronized Wallet loadOrCreateWallet(String walletName) throws Exception {
        if (wallets.containsKey(walletName)) {
            return wallets.get(walletName);
        }

        File walletFile = new File(walletDirStr, walletName + WALLET_FILE_POST_FIX);
        CsvScriptExtension csv = new CsvScriptExtension();
        Wallet wallet;

        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile, csv);
        } else {
            wallet = Wallet.createDeterministic(NETWORK, ScriptType.P2WPKH, KeyChainGroupStructure.BIP32);
            wallet.addExtension(csv);
            wallet.saveToFile(walletFile);
        }

        attachCsvSupport(wallet, csv);

        chain.addWallet(wallet);
        peerGroup.addWallet(wallet);

        wallets.put(walletName, wallet);
        return wallet;
    }

    private void attachCsvSupport(Wallet wallet, CsvScriptExtension csv) {
        List<Script> watchedScripts = csv.getRedeemScripts().stream()
                .map(redeemScript -> Script.parse(
                        createP2WSHOutputScript(redeemScript).program(),
                        redeemScript.creationTime().get()))
                .collect(toList());

        wallet.addWatchedScripts(watchedScripts);
        wallet.addTransactionSigner(new CsvP2WshSigner(csv.getRedeemScripts()));
    }

    /**
     * Detaches a wallet from the shared chain/peer group and saves it to disk.
     * Does NOT stop the shared node — other wallets keep running.
     */
    public synchronized void closeWallet(String walletName) throws IOException {
        Wallet wallet = wallets.remove(walletName);
        if (wallet == null) return;

        peerGroup.removeWallet(wallet);
        chain.removeWallet(wallet);

        File walletFile = new File(walletDirStr, walletName + WALLET_FILE_POST_FIX);
        wallet.saveToFile(walletFile);
    }

    public PeerGroup peerGroup() {
        return peerGroup;
    }

    public org.bitcoinj.core.BlockChain chain() {
        return chain;
    }

    public Wallet wallet(String walletName) {
        Wallet wallet = wallets.get(walletName);
        if (wallet == null) {
            throw new IllegalStateException("Wallet not loaded: " + walletName);
        }
        return wallet;
    }

    /**
     * Stops the shared node entirely — call once, at application shutdown.
     * Saves every currently loaded wallet first.
     */
    public synchronized void stop() {
        for (Map.Entry<String, Wallet> entry : wallets.entrySet()) {
            try {
                entry.getValue().saveToFile(new File(walletDirStr, entry.getKey() + WALLET_FILE_POST_FIX));
            } catch (IOException e) {
                System.out.println("Failed saving wallet " + entry.getKey() + ": " + e.getMessage());
            }
        }
        peerGroup.stop();
        try {
            blockStore.close();
        } catch (BlockStoreException e) {
            System.out.println("Failed closing block store: " + e.getMessage());
        }
        instance = null;
    }
}
