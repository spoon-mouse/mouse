package com.mouse.util;

import com.mouse.cmd.txtio.WalletScreen;
import com.mouse.listener.DownloadTracker;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestWord;
import org.beryx.textio.TerminalProperties;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.*;
import org.bitcoinj.core.TransactionBroadcast.ProgressCallback;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.mouse.cmd.txtio.LaunchScreen.*;
import static java.util.stream.Collectors.toList;


public class MultiWallet {


    public static void main(String[] args){
        go();
    }

    private static void go() {
        TextIO textIO = TextIoFactory.getTextIO();
        TextTerminal terminal = textIO.getTextTerminal();

        try {
                BlockStore blockStore = new SPVBlockStore(netParams, new File(WALLET_DIR_PATH+"/common"+SPVCHAIN_FILE_POST_FIX));

                BlockChain chain = new BlockChain(network, blockStore);
                PeerGroup peerGroup = new PeerGroup(network, chain);
                peerGroup.addPeerDiscovery(new DnsDiscovery(network));

                List<WalletNameId> wallets = listOfWallets();
                wallets.forEach(w -> {
                    chain.addWallet(w.wallet());
                    peerGroup.addWallet(w.wallet());
                });

                DownloadTracker listener = new DownloadTracker(terminal);
                peerGroup.start();
                peerGroup.startBlockChainDownload(listener);
                listener.await();


                wallets.forEach(s -> {
                    wallets.forEach(t -> {

                        Coin amount = Coin.ofSat(1001);
                        Coin fee = Coin.ofSat(1000);

                        SendRequest sendRequest = SendRequest.to(t.wallet().currentReceiveAddress(), amount);
                        sendRequest.feePerKb = fee;

                        try {
                            s.wallet().completeTx(sendRequest);
                            Transaction txn = sendRequest.tx;
                            TransactionBroadcast caster = peerGroup.broadcastTransaction(txn);

                            caster.setProgressCallback( progress -> terminal.println("wallet: "+s.name()+" txn: "+txn.getTxId()+"broadcast progress: "+progress));

                            try {
                                CompletableFuture<TransactionBroadcast> cast = caster.broadcastOnly();
                                cast.get();
                                terminal.println("cast:");

                                s.wallet().commitTx(txn);

                            } catch (InterruptedException | ExecutionException e) {
                                terminal.println(s.name()+" "+e.getMessage());
                            }
                        } catch (InsufficientMoneyException | Wallet.DustySendRequested e) {
                            terminal.println(s.name()+" "+e.getMessage());
                        }
                    });
                });

                peerGroup.stop();
                blockStore.close();

            }catch (BlockStoreException | InterruptedException e ){
                e.printStackTrace();
            }
    }

}
