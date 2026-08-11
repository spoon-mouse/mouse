package com.mouse.backend.util;

import org.bitcoinj.base.Coin;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a fixed, already-resolved set of UTXOs. Lives in the UI package (not
 * backend.util) because deciding *which* UTXOs to use is a person's choice —
 * the prompting happens in SendScreen, before this is even constructed.
 * select() itself never blocks on input, same as every other CoinSelector.
 */
public class ManualCoinSelector2 implements CoinSelector {

    private final List<UtxoId> targets;

    public ManualCoinSelector2(List<UtxoId> targets) {
        this.targets = targets;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        List<TransactionOutput> selected = new ArrayList<>();

        for (UtxoId id : targets) {
            TransactionOutput match = candidates.stream()
                    .filter(utxo -> utxo.getParentTransactionHash().equals(id.txIdHash())
                            && utxo.getIndex() == id.outputIdx())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No candidate UTXO matches " + id.txId() + ":" + id.outputIdx()));
            selected.add(match);
        }

        return new CoinSelection(selected);
    }
}
