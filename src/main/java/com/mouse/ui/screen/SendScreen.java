package com.mouse.ui.screen;

import com.mouse.backend.Kit;
import com.mouse.backend.txn.TxnUtil;
import com.mouse.backend.util.CoinSelectOption;
import com.mouse.ui.input.AddressAmountFee;
import com.mouse.ui.input.Input;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import static com.mouse.ui.input.Input.*;


public class SendScreen {

    private static Logger log = LoggerFactory.getLogger(WalletScreen.class);

    public enum Choice {SEND, CSV, SWEEP, SELECTOR, BACK, EXIT;}

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();

    private String walletName;
    private Wallet wallet;
    private PeerGroup pg;

    private TxnUtil txn;

    public SendScreen(String name){
        walletName=name;
        wallet=Kit.wallet(walletName);
        pg=Kit.peerGroup();
        txn = new TxnUtil(walletName);
    }

    public void show() {
        while(true) {
            Choice choice = textIO.newEnumInputReader(Choice.class).read(walletName+" Transactions");
            try {
                switch (choice) {
                    case SEND:
                        AddressAmountFee aaf = AddressAmountFee.get();
                        if(aaf!=null){
                            txn.sendTxn(aaf, Input::getPassword, terminal::println);
                        }
                        break;
                    case CSV:
                        aaf = AddressAmountFee.get();
                        if(aaf!=null) {
                            long conf = getConfirmation();
                            txn.checkSeqVerifyTxn(aaf, conf, Input::getPassword, terminal::println);
                        }
                        break;
                    case SWEEP:
                        txn.sweepTxn(Input::getPassword, terminal::println);
                        break;
                    case SELECTOR:
                        CoinSelectOption option = textIO.newEnumInputReader(CoinSelectOption.class).read("Coin Selector:");
                        txn.setCoinSelector(option);
                        terminal.println("set coin selection by "+option);
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
            } catch (ExecutionException | InterruptedException | IllegalMonitorStateException e) {
                log.error(" ", e);
            }
        }
    }

}
