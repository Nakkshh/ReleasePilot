package com.releasepilot.security;

import java.util.regex.Pattern;

/** Helpers for text written by other people (PR titles, issue titles, commit messages, CI logs). */
public final class UntrustedText {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Zl}\\p{Zp}]+");
    /** Control characters and invisible format characters (zero-width, bidi overrides). */
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private UntrustedText() {
    }

    /** One line: whitespace runs become one space, invisible characters are removed. */
    public static String singleLine(String s) {
        if (s == null) {
            return "";
        }
        String t = WHITESPACE.matcher(s).replaceAll(" ");
        return INVISIBLE.matcher(t).replaceAll("").strip();
    }

    /** Makes the prompt delimiters (&lt;&lt;&lt; and &gt;&gt;&gt;) unusable inside the text. */
    public static String defang(String s) {
        return s == null ? "" : s.replace("<<<", "< < <").replace(">>>", "> > >");
    }

    /** Single line, delimiters defanged, capped at max characters. For prompts and tool output. */
    public static String line(String s, int max) {
        String t = defang(singleLine(s));
        if (t.length() > max) {
            int end = max;
            if (end > 0 && Character.isHighSurrogate(t.charAt(end - 1))) {
                end--;   // never cut a surrogate pair in half
            }
            t = t.substring(0, end) + "...";
        }
        return t;
    }
}