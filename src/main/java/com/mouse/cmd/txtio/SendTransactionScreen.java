package com.mouse.cmd.txtio;

import com.mouse.listener.TerminalTxnCastListener;
import com.mouse.util.AmountType;
import com.mouse.util.SendTransaction;
import com.mouse.util.SendTxnInfo;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;




public class SendTransactionScreen {

    private static TextIO textIO;
    private static TextTerminal terminal;


    public static void show(String walletName,  WalletAppKit kit){
        textIO = TextIoFactory.getTextIO();
        terminal = textIO.getTextTerminal();


        terminal.println(walletName+" balance: "+kit.wallet().getBalance().toFriendlyString());

        SendTxnInfo info = get_SendTxnInfo_GuI();
        if(info==null){
            return;
        }

        SendTransaction sendTransaction = new SendTransaction(kit);
        try{
            sendTransaction.init(info);

            Transaction txn = sendTransaction.complete_txn();
            String id = txn.getTxId().toString();

            long value = txn.getValue(kit.wallet()).getValue();
            long fee = txn.getFee().getValue();
            long fromMe=txn.getValueSentFromMe(kit.wallet()).getValue();
            long toMe=txn.getValueSentToMe(kit.wallet()).getValue();

            AmountType txInfo = AmountType.get(fromMe, toMe, fee, value);

            terminal.println("transaction: "+id+" "+txInfo.type()+" "+txInfo.amount() +" fee: "+fee+" total: "+txInfo.total());

            terminal.println("broadcasting...");
            final PeerGroup peerGroup = kit.peerGroup();
            int min = peerGroup.getMinBroadcastConnections();
            int max = peerGroup.getMaxConnections();
            int now = peerGroup.numConnectedPeers();

            terminal.println("active peer connections: "+now);

            TerminalTxnCastListener listener = new TerminalTxnCastListener(terminal, txn);
            peerGroup.addOnTransactionBroadcastListener(listener);
            sendTransaction.broadCast();
            sendTransaction.awaitBroadCasted();
            terminal.println("done: ");
            peerGroup.removeOnTransactionBroadcastListener(listener);

            //sendTransaction.awaitRelayed();
            //terminal.println("transaction relayed: ");
        }catch (Exception e){
            System.out.println(e);
            terminal.println(e.getMessage());
        }
    }



    private static SendTxnInfo get_SendTxnInfo_GuI() {
        String address = textIO.newStringInputReader()
                .withMinLength(0)
                .withMaxLength(62)
                .withInputTrimming(true)
                .withIgnoreCase()
                .read("address to:");

        if(address==null || address.isEmpty()){
            return null;
        }
        if(address.length()<26){
            terminal.println("address length less that 26");
            return null;
        }

        long amount = textIO.newLongInputReader()
                .withMinVal(1L)
                .withInputTrimming(true)
                .read("amount (sats):");

        long fee = textIO.newLongInputReader()
                .withDefaultValue(1L)
                .withMinVal(1L)
                .withMaxVal(99L)
                .withInputTrimming(true)
                .read("fee (sats per vbyte):");

        return new SendTxnInfo(address, amount, fee);
    }


}
