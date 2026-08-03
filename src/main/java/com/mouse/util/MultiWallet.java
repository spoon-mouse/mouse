package com.mouse.util;

import com.mouse.listener.DownloadTracker;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.mouse.cmd.txtio.LaunchScreen.*;


public class MultiWallet {

    public static void go() {
        TextIO textIO = TextIoFactory.getTextIO();
        TextTerminal terminal = textIO.getTextTerminal();

        try {
                BlockStore blockStore = new SPVBlockStore(NETWORK_PARAMETERS, new File(WALLET_DIR_PATH+"/w1"+SPVCHAIN_FILE_POST_FIX));

                BlockChain chain = new BlockChain(NETWORK, blockStore);
                PeerGroup peerGroup = new PeerGroup(NETWORK, chain);
                peerGroup.addPeerDiscovery(new DnsDiscovery(NETWORK));

                DownloadTracker listener = new DownloadTracker(terminal);
                peerGroup.start();

                try {
                    peerGroup.waitForPeers( 3 ).get();
                    terminal.println("network of 3 peers");
                } catch (ExecutionException e) {}

                peerGroup.startBlockChainDownload(listener);
                listener.await();



                List<WalletNameId> wallets = WalletTable.listOfWallets();
                wallets.forEach(w -> {
                    chain.addWallet(w.wallet());
                    peerGroup.addWallet(w.wallet());
                });



                wallets.forEach(s -> {
                    wallets.forEach(t -> {

                        Coin amount = Coin.ofSat(1000);
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
                                terminal.println("broadcast progress done:");

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

            wallets.forEach(w -> {
                try {
                    w.wallet().saveToFile(new File(WALLET_DIR_PATH+"/"+w.name()+ WALLET_FILE_POST_FIX) );
                } catch (IOException e) {
                    terminal.println(e.getMessage());
                }
            });


        }catch (BlockStoreException | InterruptedException e ){
                e.printStackTrace();
            }
    }

}
