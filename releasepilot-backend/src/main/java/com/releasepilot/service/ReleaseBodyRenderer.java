package com.releasepilot.service;

import com.releasepilot.dto.ReleaseNotes;
import com.releasepilot.security.UntrustedText;
import java.util.regex.Pattern;

import java.util.List;

/** Renders stored notes to the GitHub release body. Deterministic: no model call at approval time. */
final class ReleaseBodyRenderer {

    private ReleaseBodyRenderer() {
    }

    static String render(ReleaseNotes n, long draftId) {
        StringBuilder sb = new StringBuilder();
        if (n != null && n.summary() != null && !n.summary().isBlank()) {
            sb.append(esc(n.summary().strip())).append("\n\n");
        }
        if (n != null) {
            section(sb, "Breaking changes", n.breakingChanges());
            section(sb, "Features", n.features());
            section(sb, "Fixes", n.fixes());
            section(sb, "Chores", n.chores());
        }
        sb.append("---\n_Approved via ReleasePilot (draft #").append(draftId).append(")._\n");
        return sb.toString();
    }

    private static void section(StringBuilder sb, String heading, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("## ").append(heading).append("\n\n");
        for (String item : items) {
            sb.append("- ").append(esc(item)).append("\n");
        }
        sb.append("\n");
    }

    private static final Pattern MENTION = Pattern.compile("@(?=[A-Za-z0-9])");

    /**
     * Titles are untrusted and the body lands on a public GitHub page. One line only (no fake headings),
     * no raw HTML, no markdown links or images, no live @mentions.
     */
    private static String esc(String s) {
        String t = UntrustedText.singleLine(s)
                .replace("\\", "\\\\")        // first, so the escapes below cannot be cancelled
                .replace("<", "&lt;")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`");
        return MENTION.matcher(t).replaceAll("@\u200B");   // zero-width space: still reads "@dev", no longer a mention
    }
}