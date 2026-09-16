package com.mouse.backend.util;

public record BalanceInfo(
        long confirmed,
        long pendingIncoming,
        long pendingOutgoing,
        long pendingChange,
        long locked,
        long spendable
) {
    public long totalPending() {
        return pendingIncoming + pendingOutgoing + pendingChange;
    }

    public long totalAvailable() {
        return spendable;
    }
}
