package com.mouse.backend;

import com.mouse.backend.csv.CsvP2WshSigner;
import com.mouse.backend.csv.CsvScriptExtension;
import com.mouse.backend.csv.CsvUtil;
import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.txn.TxnInfo;
import com.mouse.backend.util.Config;
import com.mouse.backend.util.MetaWallet;
import com.mouse.backend.hook.DownloadTracker;
import com.mouse.backend.util.Utxo;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
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
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.mouse.backend.csv.CsvScriptExtension.COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS;
import static com.mouse.backend.util.Config.*;
import static java.util.stream.Collectors.groupingBy;
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

    private static Logger log = LoggerFactory.getLogger(Kit.class);

    public static final int WAIT_MIN_NUM_PEERS = 3;
    private static Kit instance;

    private static BlockStore blockStore;
    private static org.bitcoinj.core.BlockChain chain;
    private static PeerGroup peerGroup;

    private static final Map<String, Wallet> wallets = new ConcurrentHashMap<>();

    private Kit(BlockStore blockStore, org.bitcoinj.core.BlockChain chain, PeerGroup peerGroup) {
        this.blockStore = blockStore;
        this.chain = chain;
        this.peerGroup = peerGroup;
    }

    /**
     * Starts the shared node: opens one block store, one chain, one peer group,
     * for the whole app. Call once, at application startup.
     *
     */
    public static synchronized void start(File filePathbase) {

        if (instance != null) {
            return;
        }

        WALLET_DIR_PATH = filePathbase.toPath();

        try {
            BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(WALLET_DIR_PATH + "/shared" + SPVCHAIN_FILE_POST_FIX));
            org.bitcoinj.core.BlockChain chain = new org.bitcoinj.core.BlockChain(NETWORK, blockStore);

            PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));

            instance = new Kit(blockStore, chain, peerGroup);

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

            peerGroup.start();
            //peerGroup.startBlockChainDownload(new DownloadProgressTracker());
            peerGroup.startBlockChainDownload(new DownloadProgressTracker() {
                @Override
                protected void doneDownload() {
                    log.info("Chain Sync complete");
                    // your code here: update UI, enable send button, etc.
                }
            });


        }catch (IOException | BlockStoreException e) {
            e.printStackTrace();
        }

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

        File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        CsvScriptExtension csv = new CsvScriptExtension();
        Wallet wallet;

        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile, csv);
            wallet.addOrGetExistingExtension(csv);
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

    public static Wallet getWallet(String walletName) {
        return wallet(walletName);
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

    public static void deleteWallet(String walletName) throws IOException {
        closeWallet(walletName);
        File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        if (walletFile.exists()) {
            walletFile.delete();
        }
    }


    /**
     * Stops the shared node entirely — call once, at application shutdown.
     * Saves every currently loaded wallet first.
     */
    public static synchronized void stop() {

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

        instance = null;
    }


    public static synchronized void restore_from_seed(String walletName, String seed_txt, long epochSeconds, InfoHook progress) {

        if (wallets.containsKey(walletName)) {
            throw new IllegalArgumentException("Wallet already exists: " + walletName);
        }

        DeterministicSeed seed;
        if(epochSeconds<=0L){
            seed = DeterministicSeed.ofMnemonic(seed_txt, "");
        }else{
            seed = DeterministicSeed.ofMnemonic(seed_txt, "", Instant.ofEpochSecond(epochSeconds));
        }

        try {
            Wallet wallet = Wallet.fromSeed(NETWORK, seed, ScriptType.P2WPKH);
            wallet.clearTransactions(0);

            final Path f = WALLET_DIR_PATH.resolve("restore" + SPVCHAIN_FILE_POST_FIX);
            Files.deleteIfExists(f);

            BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(WALLET_DIR_PATH.toFile(), "restore" + SPVCHAIN_FILE_POST_FIX));

            BlockChain chain = new BlockChain(NETWORK, wallet, blockStore);
            PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));
            peerGroup.addWallet(wallet);

            DownloadTracker listener = new DownloadTracker(progress);
            peerGroup.start();
            peerGroup.startBlockChainDownload(listener);

            peerGroup.addConnectedEventListener((peer, connected) -> {
                progress.event("connections: ["+peerGroup.numConnectedPeers()+"/"+ peerGroup.getMaxConnections()+"]");
            });

            listener.await();

            File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
            wallet.saveToFile(walletFile);

            Kit.wallets.put(walletName, wallet);

            peerGroup.stop();
            blockStore.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static int connections(){
        return peerGroup.numConnectedPeers();
    }

    public static synchronized void save(String walletName) {
        try {
            File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
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

    public static MetaWallet getMetaWallet(String walletName){
        return MetaWallet.get(walletName, getWallet(walletName));
    }

    public static void addRedeemScript(String walletName, String kvStringProgHexCreationTime) {
        log.info("", kvStringProgHexCreationTime);

        final Wallet wallet = wallets.get(walletName);
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        final Script redeemScript = ext.addRedeemScript(kvStringProgHexCreationTime);

        Script p2wshOutputScript = createP2WSHOutputScript(redeemScript);
        p2wshOutputScript = Script.parse(p2wshOutputScript.program(), redeemScript.creationTime().get() );

        wallet.addWatchedScripts(Collections.singletonList(p2wshOutputScript));

        log.info("add watched script: "+redeemScript);
    }

    public static void viewRedeemScripts(String walletName, InfoHook react) {
        final Wallet wallet = wallets.get(walletName);
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        ext.getRedeemScripts().forEach(s->react.event(s.toString()+" "+s.creationTime().get().getEpochSecond()));
    }

    public static void viewWatchedScripts(String walletName, InfoHook react) {
        final Wallet wallet = wallets.get(walletName);
        wallet.getWatchedScripts().forEach(s->react.event(s.toString()+" "+s.creationTime().get().getEpochSecond()));
    }

    public static List<Utxo> utxos(String walletName) {
        final Wallet wallet = wallets.get(walletName);

        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        CsvUtil scvUtil = new CsvUtil( ext.getRedeemScripts() );

        return wallet.getUnspents().stream().map( o -> new Utxo(o.getParentTransactionHash().toString(), o.getIndex(), o.getScriptPubKey().getToAddress(NETWORK).toString(), scvUtil.isTxOutputCsvScript(o), scvUtil.getRelativeLock(o), o.getValue().value, o.getParentTransactionDepthInBlocks()) ).toList();
    }


    public static List<TxnInfo> getTxns(String walletName) {
        final Wallet wallet = wallets.get(walletName);
        return wallet.getTransactionsByTime().stream().map(txn -> TxnInfo.get(txn, wallet)).toList();
    }


    public static String getCurrentReceiveAddress(String walletName) {
        final Wallet wallet = wallets.get(walletName);
        return wallet.currentReceiveAddress().toString();
    }





    public static List<String> getIssuedReceiveAddresses(String walletName) {
        final Wallet wallet = wallets.get(walletName);
        return wallet.getIssuedReceiveAddresses().stream().map(Address::toString).toList();
    }



    public static void doSomeListningOrSomeThingLiekThis(){

        peerGroup().addBlocksDownloadedEventListener((peer, block, filteredBlock, blocksLeft)-> {
            log.info("Blocks downloaded: left {}", blocksLeft);
        } );
/*
        peerGroup.addChainDownloadStartedEventListener((peer, blocks) -> {
            log.info("Chain download started: {}", blocks);
            react.event("Chain download started: "+blocks);
        });
*/

        peerGroup.addOnTransactionBroadcastListener((peer, tx) -> {
            log.info("Transaction broadcast: {}", tx);
        });

        wallets.entrySet().forEach( e -> e.getValue().addCoinsSentEventListener((wallet, txn, prevBalance, newBalance) -> {
            log.info(e.getKey()+" sent "+txn.getTxId());
        }));

        wallets.entrySet().forEach( e -> e.getValue().addCoinsReceivedEventListener((wallet, txn, prevBalance, newBalance) -> {
            log.info(e.getKey()+" recived "+txn.getTxId());
        }));

        /*
        wallets.entrySet().forEach( e -> e.getValue().addTransactionConfidenceEventListener((wallet, txn) -> {
            log.info(e.getKey()+" confidence change "+txn.getTxId());
            react.event(e.getKey()+" confidence change "+txn.getTxId());
        }));
        */



        wallets.entrySet().forEach( e -> e.getValue().addChangeEventListener((wallet) -> {
            log.info(e.getKey()+" changed ");
        }));
    }


    public static void txnListener(InfoHook progress){

        peerGroup().addBlocksDownloadedEventListener((peer, block, filteredBlock, blocksLeft)-> {
            if(blocksLeft==0)
                progress.event("Block downloaded:");
        } );

        wallets.entrySet().forEach( e -> e.getValue().addCoinsSentEventListener((wallet, txn, prevBalance, newBalance) -> {
            progress.event("wallet: " + e.getKey() + " sent " + txn.getValue(wallet));
        }));

        wallets.entrySet().forEach( e -> e.getValue().addCoinsReceivedEventListener((wallet, txn, prevBalance, newBalance) -> {
            progress.event("wallet: " + e.getKey() + " received " + txn.getValue(wallet));
        }));

    }

}