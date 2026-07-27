package com.mouse.cmd.txtio;

import com.mouse.util.SendTransaction;
import com.mouse.util.SendTxnInfo;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;

import static com.mouse.cmd.txtio.LaunchScreen.get_password_from_gui;


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

            Transaction txn = sendTransaction.complete_txn(get_password_from_gui());

            String id = txn.getTxId().toString();
            long absVal=Math.abs( txn.getValue(kit.wallet()).getValue() );
            long fee = txn.getFee().getValue();
            long amount = absVal - fee;
            long total = amount+fee;
            terminal.println("transaction id: "+id+" sending "+amount+" fee: "+fee+" total: "+total);

            sendTransaction.broadCast(3);
            sendTransaction.awaitBroadCasted();
            terminal.println("transaction broadcast: ");
            sendTransaction.awaitRelayed();
            terminal.println("transaction relayed: ");

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
