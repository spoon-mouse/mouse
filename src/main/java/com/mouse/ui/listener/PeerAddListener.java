package com.mouse.ui.listener;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.beryx.textio.TextTerminal;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;

public class PeerAddListener implements PeerConnectedEventListener {

    private static TextIO textIO = TextIoFactory.getTextIO();
    private static TextTerminal terminal = textIO.getTextTerminal();


    @Override
    public void onPeerConnected(Peer peer, int peerCount) {
        terminal.println( "connecting..."  );
    }
}
