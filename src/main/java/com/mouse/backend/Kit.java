package com.mouse.backend;

import com.mouse.backend.csv.CsvP2WshSigner;
import com.mouse.backend.csv.CsvScriptExtension;
import com.mouse.backend.csv.CsvUtil;
import com.mouse.backend.hook.InfoHook;
import com.mouse.backend.hook.PasswordPrompt;
import com.mouse.backend.txn.IllegalAmountException;
import com.mouse.backend.txn.TxnInfo;
import com.mouse.backend.util.*;
import com.mouse.backend.hook.DownloadTracker;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

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

    private static AppendOnlyMultiMapStore checkSeqVerRepo;

    private static Logger log = LoggerFactory.getLogger(Kit.class);

    private static Kit instance;

    private static BlockStore blockStore;
    private static org.bitcoinj.core.BlockChain chain;
    private static PeerGroup peerGroup;

    private static java.time.Duration autosaveDuration = java.time.Duration.ofSeconds(5);

    private static final Map<String, Wallet> wallets = new ConcurrentHashMap<>();
    private static final Pattern ALLOWED_WALLET_NAME = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9_-]{0,62}[A-Za-z0-9])?");
    private static final Object WALLET_STATE_LOCK = new Object();

    private static boolean isInitialized() {
        return instance != null && peerGroup != null && chain != null && blockStore != null;
    }

    private static void ensureInitialized(String operation) {
        if (!isInitialized()) {
            throw new IllegalStateException("Wallet backend not initialized; cannot " + operation);
        }
    }

    private static void atomicMove(Path from, Path to, boolean replaceExisting) throws IOException {
        try {
            if (replaceExisting) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException atomicFailure) {
            if (replaceExisting) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(from, to);
            }
        }
    }

    private static void fsyncPath(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Some filesystems do not support fsync on the target, so we fail open for durability.
        }
    }

    private static void fsyncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Best-effort fsync for parent directories.
        }
    }

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
            Files.createDirectories(WALLET_DIR_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            checkSeqVerRepo = new AppendOnlyMultiMapStore(WALLET_DIR_PATH+"/checkSeqVer.log");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


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
                } catch (UnreadableWalletException | IOException | IllegalStateException e) {
                    log.error(Kit.class.getName(), "loading wallet " + walletName + " failed: ", e);
                }
            });

            peerGroup.start();
            peerGroup.startBlockChainDownload(new DownloadProgressTracker() {
                @Override
                protected void doneDownload() {
                    log.info(Kit.class.getName(), "Chain Sync complete");
                }
            });


        }catch (IOException | BlockStoreException e) {
            log.error(Kit.class.getName(), "Error occurred while starting the node: ", e);
        }

    }

    public static boolean checkWalletName(String walletName){
           return ALLOWED_WALLET_NAME.matcher(walletName).matches();
    }


    public static synchronized Wallet reName(String walletName, String newName) throws UnreadableWalletException, IOException {
        if (!checkWalletName(walletName)) {
            throw new IllegalArgumentException("Invalid wallet name: " + walletName);
        }

        if (!checkWalletName(newName)) {
            throw new IllegalArgumentException("Invalid wallet name: " + newName);
        }

        if (walletName.equals(newName)) {
            throw new IllegalArgumentException("Wallet name must differ from current name");
        }

        if (wallets.containsKey(newName)) {
            throw new IllegalArgumentException("Wallet with name " + newName + " already exists");
        }

        File oldWalletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        File newWalletFile = new File(WALLET_DIR_PATH.toFile(), newName + WALLET_FILE_POST_FIX);

        if (!oldWalletFile.exists() && !wallets.containsKey(walletName)) {
            throw new IllegalArgumentException("Wallet with name " + walletName + " does not exist");
        }

        if (newWalletFile.exists()) {
            throw new IllegalArgumentException("Wallet file for " + newName + " already exists");
        }

        Wallet originalWallet = wallets.get(walletName);
        closeWallet(walletName);

        Path oldPath = oldWalletFile.toPath();
        Path backupPath = WALLET_DIR_PATH.resolve(walletName + WALLET_FILE_POST_FIX + ".backup-" + UUID.randomUUID());
        try {
            atomicMove(oldPath, backupPath, false);
            atomicMove(backupPath, newWalletFile.toPath(), false);
        } catch (IOException e) {
            if (Files.exists(backupPath) && !Files.exists(oldPath)) {
                try {
                    atomicMove(backupPath, oldPath, false);
                } catch (IOException rollbackFailure) {
                    log.error(Kit.class.getName(), "Failed to restore wallet file after rename rollback", rollbackFailure);
                }
            }
            if (originalWallet != null) {
                try {
                    loadOrCreateWallet(walletName);
                } catch (UnreadableWalletException | IOException ex) {
                    log.error(Kit.class.getName(), "Failed to restore wallet in memory after rename failure", ex);
                }
            }
            throw new IOException("Failed to rename wallet file: " + e.getMessage(), e);
        }

        try {
            Files.deleteIfExists(backupPath);
        } catch (IOException e) {
            log.warn(Kit.class.getName(), "Rename succeeded but backup cleanup failed for " + walletName, e);
        }

        return loadOrCreateWallet(newName);
    }


    /**
     * Loads an existing wallet file, or creates a fresh wallet if none exists yet,
     * attaches the CSV extension/watched-scripts/signer, and hooks it onto the
     * shared chain + peer group. Returns the ready-to-use Wallet.
     */
    public static synchronized Wallet loadOrCreateWallet(String walletName) throws UnreadableWalletException, IOException {

        if (!checkWalletName(walletName)) {
            throw new IllegalArgumentException("Invalid wallet name: " + walletName);
        }

        if (wallets.containsKey(walletName)) {
            return getWallet(walletName);
        }

        File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        CsvScriptExtension csv = new CsvScriptExtension();
        Wallet wallet;

        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile, csv);
            wallet.addOrGetExistingExtension(csv);
            wallet.autosaveToFile(walletFile, autosaveDuration, null);
        } else {
            wallet = Wallet.createDeterministic(NETWORK, ScriptType.P2WPKH, KeyChainGroupStructure.BIP32);
            wallet.addExtension(csv);
            wallet.saveToFile(walletFile);
            wallet.autosaveToFile(walletFile, autosaveDuration, null);
        }
        wallet.setAcceptRiskyTransactions(true);
        attachCsvSupport(wallet, csv);

        try {
            chain.addWallet(wallet);
            peerGroup.addWallet(wallet);
        } catch (Exception e) {
            log.error(Kit.class.getName(), "Error occurred while attaching wallet to chain/peer group: " + walletName, e);
        }

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
        return wallets.get(walletName);
    }

    /**
     * Detaches a wallet from the shared chain/peer group and saves it to disk.
     * Does NOT stop the shared node — other wallets keep running.
     */
    public static synchronized void closeWallet(String walletName) throws IOException {
        ensureInitialized("close wallet");
        final Wallet wallet = wallets.get(walletName);
        if (wallet == null) return;

        wallet.shutdownAutosaveAndWait();
        peerGroup.removeWallet(wallet);
        chain.removeWallet(wallet);
        save(walletName);
        wallets.remove(walletName);
    }

    public static void deleteWallet(String walletName) throws IOException {
        closeWallet(walletName);
        File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        if (!walletFile.exists()) {
            return;
        }

        Path tempDeletePath = WALLET_DIR_PATH.resolve(walletName + WALLET_FILE_POST_FIX + ".delete-" + UUID.randomUUID());
        try {
            atomicMove(walletFile.toPath(), tempDeletePath, false);
            Files.deleteIfExists(tempDeletePath);
        } catch (IOException e) {
            if (Files.exists(tempDeletePath) && !Files.exists(walletFile.toPath())) {
                try {
                    atomicMove(tempDeletePath, walletFile.toPath(), false);
                } catch (IOException rollbackFailure) {
                    log.error(Kit.class.getName(), "Failed to restore wallet file after delete rollback", rollbackFailure);
                }
            }
            throw new IOException("Failed to delete wallet file: " + walletName, e);
        }
    }


    /**
     * Stops the shared node entirely — call once, at application shutdown.
     * Saves every currently loaded wallet first.
     */
    public static synchronized void stop() {
        if (!isInitialized()) {
            instance = null;
            return;
        }

        for (String walletName : new ArrayList<>(wallets.keySet())) {
            try {
                closeWallet(walletName);
            } catch (IOException e) {
                throw new RuntimeException("Failed to close wallet during shutdown: " + walletName, e);
            }
        }

        peerGroup.stopAsync();

        try {
            blockStore.close();
        } catch (BlockStoreException e) {
            throw new RuntimeException("Failed to close block store during shutdown", e);
        }

        instance = null;
        peerGroup = null;
        chain = null;
        blockStore = null;
    }

    public static synchronized void restoreWallet(String walletName, PasswordPrompt prompt,  InfoHook progress) throws UnreadableWalletException, IOException, MnemonicException {
        if (!checkWalletName(walletName)) {
            throw new IllegalArgumentException("Invalid wallet name: " + walletName);
        }

        if (prompt == null) {
            throw new IllegalArgumentException("Password prompt is required for wallet restore: " + walletName);
        }

        File walletFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        File backupFile = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX + ".restore-backup-" + UUID.randomUUID());
        String tempName = walletName + "_restore_" + UUID.randomUUID().toString().replace("-", "");
        File restoredFile = new File(WALLET_DIR_PATH.toFile(), tempName + WALLET_FILE_POST_FIX);

        final boolean hadOriginalWallet = wallets.containsKey(walletName);
        final boolean hadOriginalFile = walletFile.exists();

        String seed = getWalletSeed(walletName, prompt);
        long epochSeconds = getWalletCreationTime(walletName);

        try {
            if (hadOriginalWallet) {
                closeWallet(walletName);
            }

            if (hadOriginalFile) {
                atomicMove(walletFile.toPath(), backupFile.toPath(), false);
            }

            restore_from_seed(tempName, seed, epochSeconds, progress);
            progress.event("Restored wallet: " + tempName);

            if (restoredFile.exists()) {
                atomicMove(restoredFile.toPath(), walletFile.toPath(), true);
                fsyncDirectory(WALLET_DIR_PATH);
            }

            if (backupFile.exists()) {
                Files.deleteIfExists(backupFile.toPath());
            }

            Wallet tempWallet = wallets.remove(tempName);

            if (wallets.containsKey(walletName)) {
                wallets.remove(walletName);
            }
            loadOrCreateWallet(walletName);
            progress.event("wallet restore complete: " + walletName);
        } catch (IOException | MnemonicException | UnreadableWalletException | RuntimeException e) {
            if (restoredFile.exists()) {
                try {
                    Files.deleteIfExists(restoredFile.toPath());
                } catch (IOException ignored) {
                    log.warn(Kit.class.getName(), "Could not remove temp restored wallet after failure", ignored);
                }
            }
            if (backupFile.exists()) {
                try {
                    atomicMove(backupFile.toPath(), walletFile.toPath(), true);
                } catch (IOException rollbackFailure) {
                    log.error(Kit.class.getName(), "Failed to restore backup wallet after restore failure", rollbackFailure);
                }
            }
            throw e;
        }
    }

    public static synchronized long getWalletCreationTime(String walletName){
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return 0;
        final Optional<Instant> creationTime = wallet.getKeyChainSeed().getCreationTime();
        if(creationTime.isPresent()) {
            return creationTime.get().getEpochSecond();
        }
        return 0;
    }

    public static synchronized String getWalletSeed(String walletName, PasswordPrompt prompt) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletName);
        }

        final boolean wasEncrypted = wallet.isEncrypted();
        CharArrayCharSequence password = null;

        if (wasEncrypted) {
            if (prompt == null) {
                throw new IllegalArgumentException("Password prompt is required for encrypted wallet: " + walletName);
            }
            password = CharArrayCharSequence.of(prompt.getPassword());
        }

        try {
            if (wasEncrypted) {
                wallet.decrypt(password);
            }
            return wallet.getKeyChainSeed().getMnemonicString();
        } catch (Wallet.BadWalletEncryptionKeyException e) {
            throw new IllegalArgumentException("Invalid wallet password for: " + walletName, e);
        } finally {
            if (wasEncrypted && password != null) {
                wallet.encrypt(password);
            }
        }
    }

    public static synchronized void restore_from_seed(String walletName, String seed_txt, long epochSeconds, InfoHook progress) throws MnemonicException {

        if (!checkWalletName(walletName)) {
            throw new IllegalArgumentException("Invalid wallet name: " + walletName);
        }

        File walletFile    = new File(WALLET_DIR_PATH.toFile(), walletName + WALLET_FILE_POST_FIX);
        if (walletFile.exists() || wallets.containsKey(walletName)) {
            throw new IllegalArgumentException("Wallet with name " + walletName + " already exists");
        }

        MnemonicCode.INSTANCE.check( Arrays.asList( seed_txt.trim().split(" ")  ) );

        DeterministicSeed seed;
        if(epochSeconds<=0L){
            seed = DeterministicSeed.ofMnemonic(seed_txt, "");
        }else{
            seed = DeterministicSeed.ofMnemonic(seed_txt, "", Instant.ofEpochSecond(epochSeconds));
        }

        try {
            Wallet wallet = Wallet.fromSeed(NETWORK, seed, ScriptType.P2WPKH, KeyChainGroupStructure.BIP32);
            wallet.clearTransactions(0);



            CsvScriptExtension csv = new CsvScriptExtension();
            wallet.addExtension(csv);
            //magic wallet init keys, or the wallet will not recognise keys that it really owns
            log.info("Current receive address: {}", wallet.currentReceiveAddress().toString());

            checkSeqVerRepo.restoreRedeemScripts(wallet, csv);
            attachCsvSupport(wallet, csv);



            final Path f = WALLET_DIR_PATH.resolve("restore" + SPVCHAIN_FILE_POST_FIX);
            Files.deleteIfExists(f);

            BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(WALLET_DIR_PATH.toFile(), "restore" + SPVCHAIN_FILE_POST_FIX));

            BlockChain chain = new BlockChain(NETWORK, wallet, blockStore);
            PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));
            peerGroup.addWallet(wallet);

            peerGroup.setMinRequiredProtocolVersion(70016);peerGroup.start();

            peerGroup.addConnectedEventListener((peer, connected) -> {
                progress.event("connections: ["+peerGroup.numConnectedPeers()+"/"+ peerGroup.getMaxConnections()+"]");
            });

            DownloadTracker listener = new DownloadTracker(progress);
            peerGroup.startBlockChainDownload(listener);
            listener.await();
            log.info("Wallet restored: {}", walletName+ Instant.now());

            log.info("saving: {}", walletName+ Instant.now());

            wallet.saveToFile(walletFile);
            log.info("saved: {}", walletName+ Instant.now());

            peerGroup.stopAsync();
            blockStore.close();

            log.info("peerG blockS stoped: {}", walletName+ Instant.now());

            log.info("adding to kit {}", walletName+ Instant.now());
            //wallets.put(walletName, wallet);
            loadOrCreateWallet(walletName);
            log.info("done ", walletName+ Instant.now());

        } catch (Exception e) {
            log.error(Kit.class.getName(), "Error occurred while restoring wallet: "+walletName, e);
            progress.event("Error occurred while restoring wallet: "+walletName+" "+e.getMessage());
        }
    }


    public static int connections(){
        return peerGroup.numConnectedPeers();
    }

    private static synchronized void save(String walletName) {
        if (!checkWalletName(walletName)) {
            throw new IllegalArgumentException("Invalid wallet name: " + walletName);
        }

        final Wallet wallet = getWallet(walletName);
        if (wallet == null) {
            return;
        }

        Path walletPath = WALLET_DIR_PATH.resolve(walletName + WALLET_FILE_POST_FIX);
        Path tmpWalletPath = WALLET_DIR_PATH.resolve(walletName + WALLET_FILE_POST_FIX + ".tmp-" + UUID.randomUUID());

        try {
            wallet.saveToFile(tmpWalletPath.toFile());
            fsyncPath(tmpWalletPath);
            atomicMove(tmpWalletPath, walletPath, true);
            fsyncDirectory(WALLET_DIR_PATH);
        } catch (IOException e) {
            log.error(Kit.class.getName(), "Error occurred while saving wallet: " + walletName, e);
            throw new RuntimeException(e);
        } finally {
            try {
                Files.deleteIfExists(tmpWalletPath);
            } catch (IOException ignored) {
                // best effort cleanup after move or fallback path
            }
        }
    }

    public static void save() {
        wallets.keySet().stream().forEach( k -> {save(k);});
    }

    public static void cleanup() {
        wallets.values().stream().forEach( w -> w.cleanup());
    }

    public static void cleanup(String walletName) {
        final Wallet wallet = getWallet(walletName);
        if (wallet != null) {
            wallet.cleanup();
        }
    }


    public static Set<String> getWalletNames() {
        return wallets.keySet();
    }

    public static List<MetaWallet> getMetaWallets(){
        return wallets.entrySet().stream().map( e -> MetaWallet.get(e.getKey(), e.getValue()) ).toList();
    }

    public static MetaWallet getMetaWallet(String walletName){
        Wallet wallet = getWallet(walletName);
        if (wallet == null) return null;
        return MetaWallet.get(walletName, wallet);
    }

    public static List<MetaWallet> getMetaWalletByAddress(String address){
        final List<Map.Entry<String, Wallet>> list = wallets.entrySet().stream().filter(entry -> entry.getValue().isAddressMine(entry.getValue().parseAddress(address))).toList();
        return list.stream().map(entry -> MetaWallet.get(entry.getKey(), entry.getValue())).toList();
    }

    public static void restoreRedeemScripts(String walletName) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return;
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        checkSeqVerRepo.restoreRedeemScripts(wallet, ext);
    }

    public static void saveRedeemQr(String qr) throws Exception {
        try {
            final AddressScript r = CsvUtil.importFromQR(qr);
            saveRedeemScript(r.address(), r.script());
        }catch (Exception e) {
            log.error(Kit.class.getName(), "Error occurred while importing QR code: "+qr, e);
            throw e;
        }
    }

    public static void saveRedeemScript(Address address, Script redeemScript) throws IOException {

        final Script p2wshOutputScript = Script.parse( createP2WSHOutputScript(redeemScript).program(), redeemScript.creationTime().orElse(Instant.EPOCH) );

        wallets.values().stream().filter(wallet -> wallet.isAddressMine(address)).forEach(wallet -> {
            CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
            ext.addRedeemScript(redeemScript);
            wallet.addWatchedScripts(Collections.singletonList(p2wshOutputScript));
        });

        String kvHexStr = CsvUtil.serializeRedeemScriptHexKV(redeemScript);
        checkSeqVerRepo.put(address.toString(), kvHexStr);
    }

    public static void viewRedeemScripts(String walletName, InfoHook react) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return;
        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        ext.getRedeemScripts().forEach(s->react.event(s.toString()+" "+s.creationTime().get().getEpochSecond()));
    }

    public static void viewWatchedScripts(String walletName, InfoHook react) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return;
        wallet.getWatchedScripts().forEach(s->react.event(s.toString()+" "+s.creationTime().get().getEpochSecond()));
    }

    public static long getLockedBalance(String walletName) {
        return getCheckSeqVerLockedUtxos(walletName).stream().mapToLong(Utxo::value).sum();
    }

    public static List<Utxo> getCheckSeqVerLockedUtxos(String walletName) {
        return getCheckSeqVerUtxos(walletName).stream().filter(Utxo::isCheckSeqVerLocked).toList();
    }

    public static List<Utxo> getCheckSeqVerUtxos(String walletName) {
        return utxos(walletName).stream().filter(Utxo::isCheckSeqVer).toList();
    }

    public static List<Utxo> utxos(String walletName) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return Collections.emptyList();

        CsvScriptExtension ext = (CsvScriptExtension) wallet.getExtensions().get(COM_SPOON_MOUSE_CSV_REDEEM_SCRIPTS);
        CsvUtil scvUtil = new CsvUtil( ext );

        return wallet.getUnspents().stream().map(o -> new Utxo(o, scvUtil)).toList();
    }


    public static TxnInfo getTxn(String walletName, String id) {
        return getTxns(walletName).stream().filter(txn -> txn.id().equals(id)).findFirst().orElse(null);
    }

    public static List<TxnInfo> getTxns(String walletName) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return Collections.emptyList();
        return wallet.getTransactionsByTime().stream().map(txn -> TxnInfo.get(txn, wallet)).toList();
    }

    public static TxnInfo setTxnInConflict(String walletName, String id) {
        TxnInfo tx = getTxn(walletName, id);
        if (tx == null) return null;
        tx.tx().getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.IN_CONFLICT);
        save(walletName);
        return tx;
    }

    public static TxnInfo setTxnDead(String walletName, String id) {
        TxnInfo tx = getTxn(walletName, id);
        if (tx == null) return null;
        tx.tx().getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.DEAD);
        save(walletName);
        return tx;
    }

    public static TxnInfo setTxnPending(String walletName, String id) {
        TxnInfo tx = getTxn(walletName, id);
        if (tx == null) return null;
        tx.tx().getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        save(walletName);
        return tx;
    }

    public static TxnInfo abandonTxn(String walletName, String id) {
        Wallet wallet = getWallet(walletName);
        if (wallet == null) return null;
        TxnInfo info = getTxn(walletName, id);

        if (info == null)
            throw new IllegalArgumentException("Transaction not found: " + id);

        Transaction tx = info.tx();

        if (tx.getConfidence().getConfidenceType()
                != TransactionConfidence.ConfidenceType.PENDING) {
            throw new IllegalStateException(
                    "Expected PENDING, was " + tx.getConfidence().getConfidenceType());
        }

        Set<Transaction> parents = new HashSet<>();

        for (TransactionInput input : tx.getInputs()) {
            TransactionOutput output = input.getConnectedOutput();
            if (output != null && output.getParentTransaction() != null)
                parents.add(output.getParentTransaction());
        }

        RiskAnalysis.Analyzer originalAnalyzer = wallet.getRiskAnalyzer();
        boolean originalAcceptRisky = wallet.isAcceptRiskyTransactions();

        wallet.setRiskAnalyzer((w, candidate, dependencies) ->
                () -> candidate.getTxId().equals(tx.getTxId())
                        ? RiskAnalysis.Result.NON_STANDARD
                        : originalAnalyzer.create(w, candidate, dependencies).analyze());

        wallet.setAcceptRiskyTransactions(false);

        try {
            try {
                wallet.cleanup();
            } catch (IllegalStateException e) {
                if (e.getMessage() == null ||
                        !e.getMessage().startsWith("Inconsistent spent tx:")) {
                    throw e;
                }

                // cleanup() already disconnected the inputs.
                Map<Sha256Hash, Transaction> spent =
                        wallet.getTransactionPool(WalletTransaction.Pool.SPENT);

                for (Transaction parent : parents) {
                    if (spent.remove(parent.getTxId()) != null) {
                        wallet.addWalletTransaction(
                                new WalletTransaction(
                                        WalletTransaction.Pool.UNSPENT,
                                        parent));
                    }
                }
            }

            wallet.isConsistentOrThrow();

        } finally {
            wallet.setRiskAnalyzer(originalAnalyzer);
            wallet.setAcceptRiskyTransactions(originalAcceptRisky);
        }

        save(walletName);
        return info;
    }

    public static String getCurrentReceiveAddress(String walletName) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return "";
        return wallet.currentReceiveAddress().toString();
    }


    public static List<String> getIssuedReceiveAddresses(String walletName) {
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return Collections.emptyList();
        return wallet.getIssuedReceiveAddresses().stream().map(Address::toString).toList();
    }

    public static void reCast(String walletName, InfoHook progress){
        final Wallet wallet = getWallet(walletName);
        if (wallet == null) return;

        List<TransactionBroadcast> casts = wallet.getPendingTransactions().stream().map(tx -> Kit.peerGroup().broadcastTransaction(tx, MIN_PEERS_TO_CAST_TXN, false)).toList();

        CompletableFuture[] sent = casts.stream().map(cast -> cast.awaitSent()).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sent).get(30, TimeUnit.SECONDS);
            progress.event("broadcast txn:"+sent.length+" connections:"+peerGroup().numConnectedPeers());
        }catch (Exception e){
            progress.event("broadcast timeout");
        }

    }


    public static void addLoggingInfoForWalletBlockEvents(){

        peerGroup().addBlocksDownloadedEventListener((peer, block, filteredBlock, blocksLeft)-> {
            log.info("Blocks downloaded: left {}", blocksLeft);
        } );

        peerGroup.addOnTransactionBroadcastListener((peer, tx) -> {
            log.info("Transaction broadcast: {}", tx);
        });

        wallets.entrySet().forEach( e -> e.getValue().addCoinsSentEventListener((wallet, txn, prevBalance, newBalance) -> {
            log.info(e.getKey()+" sent "+txn.getTxId());
        }));

        wallets.entrySet().forEach( e -> e.getValue().addCoinsReceivedEventListener((wallet, txn, prevBalance, newBalance) -> {
            log.info(e.getKey()+" recived "+txn.getTxId());
        }));

        wallets.entrySet().forEach( e -> e.getValue().addChangeEventListener((wallet) -> {
            log.info(e.getKey()+" changed ");
        }));
    }


    public static void blockDownloaded(InfoHook progress){
        peerGroup().addBlocksDownloadedEventListener((peer, block, filteredBlock, blocksLeft)-> {
            if(blocksLeft==0){
                progress.event("Block downloaded:");
            }
        });
    }


    public void walletUpdated(InfoHook progress){
        wallets.keySet().forEach(k -> walletUpdated(k, progress));
    }
    public static void walletUpdated(String walletName, InfoHook progress){
        wallets.get(walletName).addChangeEventListener((wallet) -> {
            progress.event(walletName+" updated");
        });
    }

    public static void btcSent(InfoHook progress){
        wallets.keySet().forEach(k -> btcSent(k, progress));
    }

    public static void btcSent(String walletName, InfoHook progress){
        Wallet wallet = getWallet(walletName);
        if (wallet == null) return;
        wallet.addCoinsSentEventListener((w, txn, prevBalance, newBalance) -> {
            TxnInfo txnInfo = TxnInfo.get(txn, w);
            progress.event(walletName+" "+txnInfo.type()+" "+txnInfo.amount()+" + fee: "+txnInfo.fee());
        });
    }


    public static void btcReceived(InfoHook progress){
        wallets.keySet().forEach(k -> btcReceived(k, progress));
    }

    public static void btcReceived(String walletName, InfoHook progress){
        Wallet wallet = getWallet(walletName);
        if (wallet == null) return;
        wallet.addCoinsReceivedEventListener((w, txn, prevBalance, newBalance) -> {
            TxnInfo txnInfo = TxnInfo.get(txn, w);
            if(txnInfo.isNotChange()){
                progress.event(walletName+" "+txnInfo.type()+" "+txnInfo.value());
            }
        });
    }

}