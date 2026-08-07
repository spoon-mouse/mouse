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

    public static final BitcoinNetwork NETWORK = BitcoinNetwork.TESTNET;
    public static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.of(NETWORK);

    public static final String walletDirStr = "wallet";
    public static final Path WALLET_DIR_PATH = Path.of(walletDirStr);
    public static final File walletDir = new File(walletDirStr);

    public static final String WALLET_FILE_POST_FIX = ".wallet";
    public static final String SPVCHAIN_FILE_POST_FIX = ".spvchain";

    private Config() {}
}
