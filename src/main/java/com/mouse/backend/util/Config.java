package com.mouse.backend.util;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.core.NetworkParameters;

import java.io.File;
import java.nio.file.Path;

/**
 * App-wide configuration: network selection and wallet/chain file locations.
 * Pure config, no UI dependency — both backend and UI code depend on this,
 * never the other way around.
 */
public class Config {


    public static final double MIN_FEE=0.01;
    public static final double MAX_FEE=100.0;
    public static final double DEFAULT_FEE = 1.0;


    public static final String  DEFAULT_WALLET_NAME = "wallet";

    public static final BitcoinNetwork NETWORK = BitcoinNetwork.SIGNET;
    public static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.of(NETWORK);

    //public static final String walletDirStr = "wallet";
    public static Path WALLET_DIR_PATH;

    //Path.of(walletDirStr);
    //public static final File walletDir = new File(walletDirStr);

    public static final String WALLET_FILE_POST_FIX = ".wallet";
    public static final String SPVCHAIN_FILE_POST_FIX = ".spvchain";
    public static final String REDEEM_SCRIPT_HEX_KEY = "redeemScriptHex";
    public static final String CREATION_TIME_KEY = "creationTime";

    public static final int MIN_PEERS_TO_CAST_TXN = 3;

    private Config() {}
}
