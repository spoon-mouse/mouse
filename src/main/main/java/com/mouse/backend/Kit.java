package com.mouse.backend;

import com.mouse.backend.csv.CsvP2WshSigner;
import com.mouse.backend.csv.CsvScriptExtension;
import com.mouse.backend.util.Config;
import com.mouse.backend.util.MetaWallet;
import com.mouse.ui.listener.DownloadTracker;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroupStructure;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mouse.backend.util.Config.*;
import static java.util.stream.Collectors.toList;
import static org.bitcoinj.script.ScriptBuilder.createP2WSHOutputScript;

/**
 * Replaces WalletAppKit. Owns exactly ONE BlockStore/Kit/PeerGroup for the
 * whole application's lifetime, and hosts multiple wallets on top of that single
 * shared setup — rather than WalletAppKit's one-kit-per-wallet model.
 *
 * This class is backend-only: no TextIO/terminal calls, no UI concerns. UI screens
 * depend on this class + plain Wallet objects, never on PeerGroup/Kit/BlockStore
 * directly.
 */
public class Kit {

    public static final int WAIT_MIN_NUM_PEERS = 3;
    private static boolean started = false;

    private static BlockStore blockStore;
    private static org.bitcoinj.core.BlockChain chain;
    private static PeerGroup peerGroup;

    private static final Map<String, Wallet> wallets = new ConcurrentHashMap<>();

    // detects laptop sleep/resume (no portable JVM event exists for this) by
    // watching for a wall-clock jump between scheduled ticks, and forces a
    // reconnect when one's found — see notes on the maxStalls limitation below.
    private static final long WATCHDOG_INTERVAL_MS = 15_000;
    private static ScheduledExecutorService sleepWatchdog;

    private Kit() {}

    /**
     * Starts the shared node: opens one block store, one chain, one peer group,
     * for the whole app. Call once, at application startup.
     *
     */
    public static synchronized void start() throws BlockStoreException {
        if (started) {
            return;
        }

        blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(WALLET_DIR_PATH + "/shared" + SPVCHAIN_FILE_POST_FIX));

        chain = new org.bitcoinj.core.BlockChain(NETWORK, blockStore);
        peerGroup = new PeerGroup(NETWORK, chain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));

        started = true;

        try {
            Files.newDirectoryStream(Config.WALLET_DIR_PATH,"*"+ Config.WALLET_FILE_POST_FIX).forEach(path -> {
                File file = path.toFile();
                String fileName = file.getName();
                String walletName = fileName.substring(0, fileName.length() - WALLET_FILE_POST_FIX.length());
                try {
                    loadOrCreateWallet(walletName);
                } catch (UnreadableWalletException | IOException e) {
                    e.printStackTrace();
                }
            });
        }catch (IOException e) {
            e.printStackTrace();
        }

        peerGroup.start();
        peerGroup.startBlockChainDownload(new DownloadProgressTracker());

        startSleepWatchdog();
    }

    /**
     * bitcoinj's own dead-peer detection (stall disconnects) is hard-capped at
     * 3 total per PeerGroup — after that it stops trying to replace bad peers on
     * its own. A laptop sleep typically kills every peer at once, which can burn
     * through that whole budget in one wake-up and leave the app stuck. There's
     * no portable JVM "system resumed" event, so this watches for a wall-clock
     * gap between scheduled ticks instead: if far more real time passed than the
     * schedule expected, we were almost certainly asleep, so force every current
     * peer closed — dead ones drop for good, and PeerGroup's normal "connection
     * lost -> reconnect after a delay" logic re-establishes fresh ones, without
     * relying on the exhausted stall-detection counter.
     */
    private static void startSleepWatchdog() {
        sleepWatchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kit-sleep-watchdog");
            t.setDaemon(true);
            return t;
        });

        final long[] lastTick = { System.currentTimeMillis() };

        sleepWatchdog.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            long elapsed = now - lastTick[0];
            lastTick[0] = now;

            if (elapsed > WATCHDOG_INTERVAL_MS * 3) {
                System.out.println("Kit: detected likely sleep/resume (gap " + elapsed + "ms) — forcing peer reconnect");
                forceReconnectAllPeers();
            }
        }, WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static void forceReconnectAllPeers() {
        new ArrayList<>(peerGroup.getConnectedPeers()).forEach(peer -> {
            try {
                peer.close();
            } catch (Exception e) {
                System.out.println("Kit: error closing stale peer " + peer + ": " + e.getMessage());
            }
        });
    }



    /**
     * Loads an existing wallet file, or creates a fresh wallet if none exists yet,
     * attaches the CSV extension/watched-scripts/signer, and hooks it onto the
     * shared chain + peer group. Returns the ready-to-use Wallet.
     */
    public static synchronized Wallet loadOrCreateWallet(String walletName) throws UnreadableWalletException, IOException {
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
        wallet.setAcceptRiskyTransactions(true);
        attachCsvSupport(wallet, csv);

        chain.addWallet(wallet);
        peerGroup.addWallet(wallet);

        wallets.put(walletName, wallet);
        return wallet;
    }

    private static void attachCsvSupport(Wallet wallet, CsvScriptExtension csv) {
        List<Script> watchedScripts = csv.getRedeemScripts().stream()
                .map(redeemScript -> Script.parse(createP2WSHOutputScript(redeemScript).program(), redeemScript.creationTime().get()))
                .collect(toList());

        wallet.addWatchedScripts(watchedScripts);
        wallet.addTransactionSigner(new CsvP2WshSigner(csv.getRedeemScripts()));
    }

    public static PeerGroup peerGroup() {
        return peerGroup;
    }

    public static BlockChain chain() {
        return chain;
    }

    public static Wallet wallet(String walletName) {
        Wallet wallet = wallets.get(walletName);
        if (wallet == null) {
            throw new IllegalStateException("Wallet not loaded: " + walletName);
        }
        return wallet;
    }

    /**
     * Detaches a wallet from the shared chain/peer group and saves it to disk.
     * Does NOT stop the shared node — other wallets keep running.
     */
    public static synchronized void closeWallet(String walletName) throws IOException {
        final Wallet wallet = wallets.get(walletName);
        if (wallet == null) return;

        peerGroup.removeWallet(wallet);
        chain.removeWallet(wallet);
        save(walletName);
        wallets.remove(walletName);
    }

    /**
     * Stops the shared node entirely — call once, at application shutdown.
     * Saves every currently loaded wallet first.
     */
    public static synchronized void stop() {

        if (sleepWatchdog != null) {
            sleepWatchdog.shutdownNow();
        }

        for (String walletName : wallets.keySet()) {
            try {
                closeWallet(walletName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        peerGroup.stop();

        try {
            blockStore.close();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }

        started = false;
    }


    public static void restore_from_seed(String walletName, String seed_txt, long epochSeconds) {

        DeterministicSeed seed;
        if(epochSeconds<=0L){
            seed = DeterministicSeed.ofMnemonic(seed_txt, "");
        }else{
            seed = DeterministicSeed.ofMnemonic(seed_txt, "", Instant.ofEpochSecond(epochSeconds));
        }

        try {
            Wallet wallet = Wallet.fromSeed(NETWORK, seed, ScriptType.P2WPKH);
            wallet.clearTransactions(0);

            BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(walletDirStr+"/"+walletName+SPVCHAIN_FILE_POST_FIX));

            BlockChain chain = new BlockChain(NETWORK, wallet, blockStore);
            PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));
            peerGroup.addWallet(wallet);

            DownloadTracker listener = new DownloadTracker();
            peerGroup.start();
            peerGroup.startBlockChainDownload(listener);
            listener.await();

            save(walletName);

            peerGroup.stop();
            blockStore.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static int connections(){
        return peerGroup.numConnectedPeers();
    }

    public static void save(String walletName) {
        try {
            File walletFile = new File(walletDirStr, walletName + WALLET_FILE_POST_FIX);
            wallets.get(walletName).saveToFile(walletFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void save() {
        wallets.keySet().stream().forEach( k -> {save(k);});
    }


    public static Set<String> getWalletNames() {
        return wallets.keySet();
    }

    public static List<MetaWallet> getMetaWallets(){
        return wallets.entrySet().stream().map( e -> MetaWallet.get(e.getKey(), e.getValue()) ).toList();
    }

}