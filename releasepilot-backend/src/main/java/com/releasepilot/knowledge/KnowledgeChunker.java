package com.releasepilot.knowledge;

import java.util.ArrayList;
import java.util.List;

/** Splits a markdown file into one chunk per "## " section. Pure and deterministic. */
final class KnowledgeChunker {

    record Chunk(String source, String title, String heading, int index, String text) {
    }

    private KnowledgeChunker() {
    }

    static List<Chunk> split(String source, String rawText, int maxChars) {
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n');

        String title = source;
        boolean titleSeen = false;
        String heading = "Overview";
        StringBuilder body = new StringBuilder();
        List<String[]> sections = new ArrayList<>();   // {heading, body}

        for (String line : text.split("\n", -1)) {
            if (!titleSeen && line.startsWith("# ")) {
                title = line.substring(2).strip();
                titleSeen = true;
                continue;
            }
            if (line.startsWith("## ")) {
                addSection(sections, heading, body);
                heading = line.substring(3).strip();
                body.setLength(0);
                continue;
            }
            body.append(line).append('\n');
        }
        addSection(sections, heading, body);

        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (String[] section : sections) {
            String prefix = title + " - " + section[0] + "\n\n";
            for (String part : pack(prefix, section[1], maxChars)) {
                chunks.add(new Chunk(source, title, section[0], index++, prefix + part));
            }
        }
        return chunks;
    }

    private static void addSection(List<String[]> sections, String heading, StringBuilder body) {
        String content = body.toString().strip();
        if (!content.isEmpty()) {
            sections.add(new String[]{heading, content});
        }
    }

    /** Packs paragraphs into parts that fit the budget; a paragraph longer than the budget is cut on a newline or space. */
    private static List<String> pack(String prefix, String body, int maxChars) {
        int budget = Math.max(200, maxChars - prefix.length());
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : body.split("\n\\s*\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + 2 + p.length() > budget) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (p.length() > budget) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                parts.addAll(hardSplit(p, budget));
                continue;
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(p);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static List<String> hardSplit(String text, int budget) {
        List<String> pieces = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > budget) {
            int cut = remaining.lastIndexOf('\n', budget);
            if (cut < budget / 2) {
                cut = remaining.lastIndexOf(' ', budget);
            }
            if (cut <= 0) {
                cut = budget;
            }
            pieces.add(remaining.substring(0, cut).strip());
            remaining = remaining.substring(cut).strip();
        }
        if (!remaining.isEmpty()) {
            pieces.add(remaining);
        }
        return pieces;
    }
}