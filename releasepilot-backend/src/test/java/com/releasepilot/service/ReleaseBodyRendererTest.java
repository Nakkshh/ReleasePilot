package com.releasepilot.service;

import com.releasepilot.dto.ReleaseNotes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseBodyRendererTest {

    private static String bodyWithFeature(String item) {
        return ReleaseBodyRenderer.render(
                new ReleaseNotes("t", "summary", List.of(item), List.of(), List.of(), List.of()), 7);
    }

    @Test
    void mentionsAreNeutralised() {
        String body = bodyWithFeature("#1 thanks @everyone and @org/team (@dev)");
        assertThat(body).doesNotContain("@everyone", "@org", "@dev");
        assertThat(body).contains("@\u200Beveryone", "@\u200Borg/team");
    }

    @Test
    void linksImagesAndHtmlAreDefused() {
        String body = bodyWithFeature("#1 [click](http://evil.example) ![x](http://evil.example/a.png) <script>x</script>");
        assertThat(body).doesNotContain("[click]", "![", "<script>");
        assertThat(body).contains("&lt;script>");
    }

    @Test
    void backslashCannotCancelTheEscape() {
        String body = bodyWithFeature("\\[x](http://e)");
        assertThat(body).contains("- \\\\\\[x\\](http://e)\n");
    }

    @Test
    void newlinesCannotInjectHeadingsOrItems() {
        String body = bodyWithFeature("ok\n## Fake heading\n- injected");
        assertThat(body).doesNotContain("\n## Fake").doesNotContain("\n- injected");
        assertThat(body).contains("- ok ## Fake heading - injected\n");
    }
}