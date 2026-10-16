package com.releasepilot.agent;

/**
 * Recognises transient provider/model failures that are worth retrying (Groq + gpt-oss quirks):
 * leaked channel tokens in tool names, tool-call validation errors (including calls to a non-existent
 * tool such as "json"), malformed or empty structured output, and unknown tool names caught locally.
 * Auth errors, rate limits and guardrail trips are deliberately NOT matched.
 */
public final class ProviderGlitch {

    private static final String[] MARKERS = {
            "<|channel|>",
            "Tool call validation failed",
            "Parsing failed",
            "No content to map due to end-of-input",
            "No ToolCallback found for tool name"
    };
    private static final int MAX_DEPTH = 10;
    private static final int MAX_MESSAGE_CHARS = 400;

    private ProviderGlitch() {
    }

    /** True if the exception, or any of its causes, carries a known glitch marker. */
    public static boolean matches(Throwable t) {
        if (LlmRateLimit.isRateLimited(t)) {
            return false;
        }
        int depth = 0;
        while (t != null && depth++ < MAX_DEPTH) {
            String m = t.getMessage();
            if (m != null) {
                for (String marker : MARKERS) {
                    if (m.contains(marker)) {
                        return true;
                    }
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /** Short single-line description of an exception chain for log lines. */
    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (int depth = 0; t != null && depth < 4; depth++, t = t.getCause()) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            String m = t.getMessage() == null ? "" : t.getMessage().replaceAll("\\s+", " ");
            sb.append(t.getClass().getSimpleName()).append(": ")
                    .append(m.length() > MAX_MESSAGE_CHARS ? m.substring(0, MAX_MESSAGE_CHARS) + "..." : m);
        }
        return sb.toString();
    }
}