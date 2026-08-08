package com.mouse.backend.util;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.util.ArrayList;
import java.util.List;

import static com.mouse.backend.csv.CsvUtil.extractCsvSequenceFromScript;
import static com.mouse.ui.input.Input.getTxId;
import static com.mouse.ui.input.Input.getUtxoId;

public class ManualCoinSelector implements CoinSelector {

    @Override
    public org.bitcoinj.wallet.CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        List<TransactionOutput> selected = new ArrayList<>();

        while(true){

            String utxlUrl = getUtxoId("select UTXO by parent txn id : output idx");
            if(utxlUrl==null || utxlUrl.isEmpty()){break;}

            UtxoId id = UtxoId.get(utxlUrl);

            final TransactionOutput select = candidates.stream().filter(
                    utxo -> utxo.getParentTransactionHash().equals(id.txIdHash()) && utxo.getIndex()==id.outputIdx()).findFirst().get();

            selected.add(select);

        }

        return new CoinSelection(selected);
    }


}