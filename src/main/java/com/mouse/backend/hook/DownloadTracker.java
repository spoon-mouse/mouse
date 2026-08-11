package com.mouse.backend.hook;


import org.bitcoinj.core.Block;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.listeners.DownloadProgressTracker;

import javax.annotation.Nullable;

public class DownloadTracker extends DownloadProgressTracker {

    private long count=0;
    private long chainSize = Long.MAX_VALUE;
    private boolean first=true;

    private InfoHook progress;

    public DownloadTracker(InfoHook progress) {
        this.progress=progress;
    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        super.onChainDownloadStarted(peer, blocksLeft);
        if(first){
            progress.event("Downloading chain: "+blocksLeft+" blocks...");
            chainSize=blocksLeft;
            first=false;
        }

        if(blocksLeft==0){
            this.notifyAll();
        }
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
        super.onBlocksDownloaded(peer, block, filteredBlock, blocksLeft);
        count++;
        if(count%100000==0){
            double pct = ((double) count /chainSize) * 100;
            progress.event("blocks downloaded: "+count+" "+String.format("%.1f", pct)+"%");
        }
    }

    @Override
    public void doneDownload() {
        progress.event("Blockchain download complete");
    }
};