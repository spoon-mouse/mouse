package com.mouse.listener;

import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.listeners.DownloadProgressTracker;

import javax.annotation.Nullable;
import java.time.Instant;

public class DownloadProgTracker extends DownloadProgressTracker {

    private TextTerminal terminal;

    public DownloadProgTracker(TextTerminal terminal) {
        this.terminal=terminal;

    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        terminal.println("Downloading the block chain size "+blocksLeft+" This will take time");
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
        terminal.println("blocks to go: "+blocksLeft);
    }

    @Override
    protected void progress(double pct, int blocksSoFar, Instant time) {
        terminal.println("downloaded "+pct+"%"+" of the block chain: "+blocksSoFar+"downloaded so far");
    }

    @Override
    protected void startDownload(int blocks) {
        terminal.println("Downloading the block chain size "+blocks+" This will take time");
    }

    @Override
    protected void doneDownload() {
        terminal.println("Download complete");
        this.notifyAll();
    }
}
