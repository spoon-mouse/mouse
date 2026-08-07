package com.mouse.ui.listener;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.listeners.DownloadProgressTracker;

import javax.annotation.Nullable;

public class DownloadTracker extends DownloadProgressTracker {

    private long count=0;
    private long chainSize = Long.MAX_VALUE;
    private boolean first=true;

    private  TextIO textIO = TextIoFactory.getTextIO();
    private  TextTerminal terminal = textIO.getTextTerminal();


    public DownloadTracker() { }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        super.onChainDownloadStarted(peer, blocksLeft);
        if(first){
            terminal.println("Downloading chain: "+blocksLeft+" blocks...");
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
            terminal.println("blocks downloaded: "+count+" "+String.format("%.1f", pct)+"%");
        }
    }

    @Override
    public void doneDownload() {
        terminal.println("Blockchain download complete");
    }
};