package com.mouse.util;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class SendTransaction {

    private WalletAppKit kit;
    private Wallet wallet;

    private Coin amount;
    private Coin fee;
    private Address address;

    private SendRequest sendRequest;

    private TransactionBroadcast transactionBroadcast;

    public SendTransaction(WalletAppKit appkit){
        kit=appkit;
        wallet=kit.wallet();
    }

    public void init(SendTxnInfo info) {
        if(info.fee() < 1 || info.fee() > 99){
            throw new IllegalArgumentException("fee in sats per vbyte out of range 1-200 fee set was: "+ info.fee());
        }

        amount = Coin.ofSat(info.amount());
        fee = Coin.ofSat( info.fee() * 1000l );
        address = wallet.parseAddress(info.address());
    }

    public Transaction complete_txn(CharSequence password) throws Exception {
        sendRequest = SendRequest.to(address, amount);
        sendRequest.feePerKb = fee;

        try {
            wallet.decrypt(password);
            wallet.completeTx(sendRequest);
            if(!wallet.isEncrypted()){
                wallet.encrypt(password);
            }
        } catch (InsufficientMoneyException | Wallet.DustySendRequested e) {

            if( e instanceof Wallet.DustySendRequested){
                throw new Exception("Dusty transaction not allowed");
            }
            if( e instanceof InsufficientMoneyException){
                throw new Exception(e.getMessage());
            }

            throw new Exception(e);

        }finally {
            if(!wallet.isEncrypted()){
                wallet.encrypt(password);
            }
        }

        return sendRequest.tx;
    }


    public TransactionBroadcast broadCast(int minConnections){
        PeerGroup peerGroup = kit.peerGroup();
        transactionBroadcast = peerGroup.broadcastTransaction(sendRequest.tx, minConnections, true);
        return transactionBroadcast;
    }

    public void awaitBroadCasted() throws ExecutionException, InterruptedException {
        CompletableFuture<TransactionBroadcast> cast = transactionBroadcast.broadcastOnly();
        cast.get();
    }

    public void awaitRelayed() throws ExecutionException, InterruptedException {
        CompletableFuture<TransactionBroadcast> relay = transactionBroadcast.awaitRelayed();
        relay.get();
    }

}
