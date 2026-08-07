package com.mouse.ui.screen;

import static com.mouse.backend.util.Config.NETWORK;

import com.mouse.backend.Kit;
import com.mouse.backend.util.AddressAmountFee;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;

import java.util.concurrent.ExecutionException;

import static com.mouse.backend.csv.CsvUtil.validateConfimationCsvSequenceNumber;
import static com.mouse.backend.txn.TxnUtil.*;


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
            Choice choice = textIO.newEnumInputReader(Choice.class).read("Transactions");
            try {
                switch (choice) {
                    case SEND:
                    case CSV:
                        AddressAmountFee addressAmountFee = get_address_amount_fee_from_gui_or_null();
                        if (addressAmountFee == null) {
                            return;
                        }
                        if (choice == choice.SEND) {
                            sendTxn(addressAmountFee, wallet, pg, NETWORK, PasswordScreen::get_password_from_gui, terminal::println);
                        } else {
                            long confimations = get_chain_depth_lock_gui();
                            checkSeqVerifyTxn(addressAmountFee, wallet, pg, confimations, NETWORK, PasswordScreen::get_password_from_gui, terminal::println);
                        }
                        break;
                    case SWEEP:
                        sweepTxn(wallet, pg, NETWORK, PasswordScreen::get_password_from_gui, terminal::println);
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


    private AddressAmountFee get_address_amount_fee_from_gui_or_null() throws AddressFormatException {
        String address = getAddress();
        if(address==null || address.isEmpty()){
            return null;
        }
        wallet.parseAddress(address);

        long amount = getAmount();
        final long fee = getFee_from_gui();

        return AddressAmountFee.get(address, amount, fee, wallet);
    }

    private static Long getAmount() {
        return textIO.newLongInputReader()
                .withMinVal(1L)
                .withInputTrimming(true)
                .read("amount (sats):");
    }

    private static String getAddress() {
        return textIO.newStringInputReader()
                .withMinLength(0)
                .withMaxLength(62)
                .withInputTrimming(true)
                .withIgnoreCase()
                .read("address to:");
    }

    public static long getFee_from_gui() {
        long fee = textIO.newLongInputReader()
                .withDefaultValue(AddressAmountFee.MIN_FEE)
                .withMinVal(AddressAmountFee.MIN_FEE)
                .withMaxVal(AddressAmountFee.MAX_FEE)
                .withInputTrimming(true)
                .read("fee (sats per vbyte):");
        return fee;
    }

    private static long get_chain_depth_lock_gui() {

        long l = textIO.newLongInputReader()
                .withDefaultValue(1l)
                .withMinVal(1l)
                .withMaxVal(1000l)
                .withInputTrimming(true)
                .read("chain depth lock:");

        validateConfimationCsvSequenceNumber(l);

        return l;
    }

}
