package com.releasepilot.knowledge;

import com.releasepilot.knowledge.KnowledgeChunker.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    private static final String DOC = "# Guide\n\nIntro text.\n\n## One\nAlpha text.\n\n## Two\nBeta text.\n";

    @Test
    void splitsOnSecondLevelHeadings() {
        List<Chunk> chunks = KnowledgeChunker.split("guide.md", DOC, 900);

        assertThat(chunks).extracting(Chunk::heading).containsExactly("Overview", "One", "Two");
        assertThat(chunks).extracting(Chunk::index).containsExactly(0, 1, 2);
    }

    @Test
    void prefixesEachChunkWithTitleAndHeading() {
        Chunk one = KnowledgeChunker.split("guide.md", DOC, 900).get(1);

        assertThat(one.text()).startsWith("Guide - One\n\n").contains("Alpha text.");
        assertThat(one.title()).isEqualTo("Guide");
        assertThat(one.source()).isEqualTo("guide.md");
    }

    @Test
    void windowsLineEndingsGiveIdenticalChunks() {
        assertThat(KnowledgeChunker.split("guide.md", DOC.replace("\n", "\r\n"), 900))
                .isEqualTo(KnowledgeChunker.split("guide.md", DOC, 900));
    }

    @Test
    void longSectionIsSplitAndEveryChunkStaysUnderTheLimit() {
        String paragraph = "word ".repeat(60).strip();   // about 300 characters
        String doc = "# Long\n\n## Big\n" + String.join("\n\n", java.util.Collections.nCopies(8, paragraph)) + "\n";

        List<Chunk> chunks = KnowledgeChunker.split("long.md", doc, 900);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(900));
        assertThat(chunks).extracting(Chunk::heading).containsOnly("Big");
    }

    @Test
    void emptySectionsAreSkipped() {
        List<Chunk> chunks = KnowledgeChunker.split("t.md", "# T\n\n## Empty\n\n## Full\ntext\n", 900);

        assertThat(chunks).extracting(Chunk::heading).containsExactly("Full");
    }

    @Test
    void documentWithoutATitleUsesTheFileName() {
        List<Chunk> chunks = KnowledgeChunker.split("notes.md", "## Only\nbody\n", 900);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).title()).isEqualTo("notes.md");
    }
}