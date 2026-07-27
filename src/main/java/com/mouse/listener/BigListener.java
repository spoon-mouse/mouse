package com.mouse.listener;

import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.GetDataEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;

import javax.annotation.Nullable;
import java.util.List;

public class BigListener {

    private TextTerminal terminal;
    private WalletAppKit kit;
    private Wallet wallet;
    private BlockChain chain;
    private PeerGroup peerGroup;

    MyNewBestBlockListener blockListener = new MyNewBestBlockListener();
    MyWalletCoinsSentEventListener coinsSentEventListener = new MyWalletCoinsSentEventListener();
    MyWalletCoinsReceivedEventListener coinsReceivedEventListener = new MyWalletCoinsReceivedEventListener();
    MyWalletChangeEventListener walletChangeEventListener = new MyWalletChangeEventListener();
    MyTransactionReceivedInBlockListener transactionReceivedInBlockListener = new MyTransactionReceivedInBlockListener();
    MyOnTransactionBroadcastListener onTransactionBroadcastListener = new MyOnTransactionBroadcastListener();

    public BigListener(TextTerminal terminal, WalletAppKit kit){
        this.terminal=terminal;
        this.kit=kit;
        this.wallet = kit.wallet();
        this.chain = kit.chain();
        this.peerGroup = kit.peerGroup();
    }

    public void start(  ) {
        chain.addNewBestBlockListener(blockListener);
        chain.addTransactionReceivedListener(transactionReceivedInBlockListener);

        wallet.addCoinsSentEventListener(coinsSentEventListener);
        wallet.addCoinsReceivedEventListener(coinsReceivedEventListener);
        wallet.addChangeEventListener(walletChangeEventListener);

        peerGroup.addOnTransactionBroadcastListener(onTransactionBroadcastListener);
    }

    public void stop(){
        chain.removeNewBestBlockListener(blockListener);
        chain.removeTransactionReceivedListener(transactionReceivedInBlockListener);

        wallet.removeCoinsSentEventListener(coinsSentEventListener);
        wallet.removeCoinsReceivedEventListener(coinsReceivedEventListener);
        wallet.removeChangeEventListener(walletChangeEventListener);

        peerGroup.removeOnTransactionBroadcastListener(onTransactionBroadcastListener);
    }

    private static class MyWalletChangeEventListener implements WalletChangeEventListener {
        @Override
        public void onWalletChanged(Wallet e) {

        }
    }

    private static class MyTransactionReceivedInBlockListener implements TransactionReceivedInBlockListener {
        @Override
        public void receiveFromBlock(Transaction tx, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {

        }

        @Override
        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            return false;
        }
    }

    private class MyOnTransactionBroadcastListener implements OnTransactionBroadcastListener {
        @Override
        public void onTransaction(Peer peer, Transaction t) {
            terminal.println("[broadcast] peer:"+peer+" txn: "+t);
        }
    }

    private class MyWalletCoinsSentEventListener implements WalletCoinsSentEventListener {
        @Override
        public void onCoinsSent(Wallet eWallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(eWallet);
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }

            terminal.println("[coin sent] id: "+id+" value: "+value+" fee: "+fee+" old balance: "+prevBalance+" new balance: "+newBalance +" "+confidenceType+" blockDepth:"+blockDepth);
        }
    }

    private class MyWalletCoinsReceivedEventListener implements WalletCoinsReceivedEventListener {
        @Override
        public void onCoinsReceived(Wallet eWallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            Sha256Hash id = tx.getTxId();
            int blockDepth = confidence.getDepthInBlocks();
            Coin value = tx.getValue(eWallet);
            Coin fee = tx.getFee();
            if(fee==null){
                fee=Coin.ZERO;
            }

            terminal.println("[coin received] id: "+id+" value: "+value+" fee: "+fee+" old balance: "+prevBalance+" new balance: "+newBalance +" "+confidenceType+" blockDepth:"+blockDepth);
        }
    }

    private class MyNewBestBlockListener implements NewBestBlockListener {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            terminal.println("[block height]: " + block.getHeight());
        }
    }
}
