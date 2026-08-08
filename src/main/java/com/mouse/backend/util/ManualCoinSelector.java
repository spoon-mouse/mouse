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

public class ManualCoinSelector implements CoinSelector {

    @Override
    public org.bitcoinj.wallet.CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        List<TransactionOutput> selected = new ArrayList<>();

        while(true){

            String id = getTxId("select UTXO by id");
            if(id==null || id.isEmpty()){break;}

            Sha256Hash hash = Sha256Hash.wrap(id);
            final TransactionOutput select = candidates.stream().filter(utxo -> utxo.getParentTransactionHash().equals(hash)).findFirst().get();

            selected.add(select);

        }

        return new CoinSelection(selected);
    }


}