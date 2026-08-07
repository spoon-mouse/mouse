package com.mouse.backend.hook;

/**
 * Backend-side hook for obtaining a wallet decryption password, without the
 * backend needing to know how that password is actually collected (TextIO
 * terminal, GUI dialog, etc). The UI layer supplies the implementation.
 */
@FunctionalInterface
public interface PasswordPrompt {
    CharSequence getPassword();
}
