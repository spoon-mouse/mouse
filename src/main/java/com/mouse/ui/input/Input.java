package com.mouse.ui.input;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;

import static com.mouse.backend.csv.CsvUtil.validateConfimationCsvSequenceNumber;
import static com.mouse.ui.screen.LaunchScreen.DEFAULT_WALLET_NAME;
import static com.mouse.ui.screen.PasswordScreen.DEFAULT_PASSWORD;

public class Input {

    public static final String REGEX_12_WORDS = "^[A-Za-z]+(?:\\s+[A-Za-z]+){11}$";

    private static TextIO textIO = TextIoFactory.getTextIO();


    public static Long getAmount() {
        return textIO.newLongInputReader()
                .withMinVal(1L)
                .withInputTrimming(true)
                .read("amount (sats):");
    }

    public static String getAddress() {
        return textIO.newStringInputReader()
                .withMinLength(0)
                .withMaxLength(62)
                .withInputTrimming(true)
                .withIgnoreCase()
                .read("address to:");
    }

    public static long getFee() {
        long fee = textIO.newLongInputReader()
                .withDefaultValue(AddressAmountFee.MIN_FEE)
                .withMinVal(AddressAmountFee.MIN_FEE)
                .withMaxVal(AddressAmountFee.MAX_FEE)
                .withInputTrimming(true)
                .read("fee (sats per vbyte):");
        return fee;
    }

    public static long getConfirmation() {

        long l = textIO.newLongInputReader()
                .withDefaultValue(1l)
                .withMinVal(1l)
                .withMaxVal(1000l)
                .withInputTrimming(true)
                .read("chain depth lock:");

        validateConfimationCsvSequenceNumber(l);

        return l;
    }

    public static String getTxId() {
        return textIO.newStringInputReader().withMinLength(0).withInputTrimming(true).read("transaction id: ");
    }

    public static long getEpochSeconds(){
        return textIO.newLongInputReader().withMinVal(0l).withDefaultValue(0l).read("creation epoch seconds (optionally speeds up restoration):");
    }

    public static String getSeed() {
        return textIO.newStringInputReader().withInputTrimming(true).withPattern(REGEX_12_WORDS).read("12 word seed phrase:");
    }

    public static String getWalletName() {
        return textIO.newStringInputReader().withDefaultValue(DEFAULT_WALLET_NAME).withInputTrimming(true).read("wallet name");
    }

    public static CharSequence getPassword() {
        return textIO.newStringInputReader()
                .withDefaultValue(DEFAULT_PASSWORD)
                .withInputMasking(true)
                .read("password");
    }



}
