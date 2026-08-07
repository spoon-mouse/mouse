package com.mouse.backend.hook;

/**
 * Backend-side hook for reporting broadcast progress messages, without the
 * backend needing to know how those messages are displayed (terminal, log,
 * GUI status bar, etc). The UI layer supplies the implementation.
 */
@FunctionalInterface
public interface BroadcastProgressListener {
    void onEvent(String message);
}
