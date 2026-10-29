package com.bhavesh.urlshortner.util;

public final class Text {
    private Text() {}

    public static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
