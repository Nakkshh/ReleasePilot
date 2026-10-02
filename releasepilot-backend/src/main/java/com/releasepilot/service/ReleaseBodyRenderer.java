package com.releasepilot.service;

import com.releasepilot.dto.ReleaseNotes;

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

    /** PR titles are untrusted input; neutralise raw HTML before it lands on a public page. */
    private static String esc(String s) {
        return s == null ? "" : s.replace("<", "&lt;");
    }
}