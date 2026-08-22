package com.mouse.backend.util;

import java.util.Arrays;

public final class CharArrayCharSequence implements CharSequence {
    private final char[] chars;

    public static CharArrayCharSequence of(char[] chars){
        return new CharArrayCharSequence(chars);
    }

    public CharArrayCharSequence(char[] chars) {
        this.chars = chars;
    }

    @Override
    public int length() {
        return chars.length;
    }

    @Override
    public char charAt(int index) {
        return chars[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new CharArrayCharSequence(Arrays.copyOfRange(chars, start, end));
    }

    public void wipe() {
        Arrays.fill(chars, '\0');
    }

    @Override
    public String toString() {
        return null;
    }
}