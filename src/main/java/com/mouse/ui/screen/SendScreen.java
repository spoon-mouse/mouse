package com.mouse.ui.screen;

import static com.mouse.backend.util.Config.NETWORK;

import com.mouse.backend.Kit;
import com.mouse.ui.input.AddressAmountFee;
import com.mouse.ui.input.Input;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;

import java.util.concurrent.ExecutionException;

import static com.mouse.backend.txn.TxnUtil.*;
import static com.mouse.ui.input.Input.*;


public class SendScreen {

    public enum Choice {SEND, CSV, SWEEP, UTXO, BACK, EXIT;}
    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    private String walletName;
    private Wallet wallet;
    private PeerGroup pg;

    public SendScreen(String name){
        walletName=name;
        wallet=Kit.wallet(walletName);
        pg=Kit.peerGroup();
    }

    public void show() {
        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+" Transactions");
            try {
                switch (choice) {
                    case SEND:
                        AddressAmountFee aaf = AddressAmountFee.get();
                        if(aaf!=null){
                            sendTxn(aaf, wallet, pg, Input::getPassword, terminal::println);
                        }
                        break;
                    case CSV:
                        aaf = AddressAmountFee.get();
                        if(aaf!=null) {
                            long conf = getConfirmation();
                            checkSeqVerifyTxn(aaf, wallet, pg, conf, Input::getPassword, terminal::println);
                        }
                        break;
                    case SWEEP:
                        sweepTxn(wallet, pg, Input::getPassword, terminal::println);
                        break;
                    case UTXO:
                        break;
                    case BACK:
                        return;
                    case EXIT:
                        System.exit(0);
                }
            } catch (AddressFormatException e) {
                terminal.println("invalid address: " + e.getMessage());
            } catch (IllegalArgumentException | InsufficientMoneyException | Wallet.TransactionCompletionException e) {
                terminal.println(e.getMessage());
            } catch (VerificationException e) {
                terminal.println(e.getMessage());
                System.out.println(e);
            } catch (ExecutionException | InterruptedException | IllegalMonitorStateException e) {
                System.out.println(e);
            }
        }
    }

}
