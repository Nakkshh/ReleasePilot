package com.releasepilot.agent;

import com.releasepilot.dto.TraceStep;
import com.releasepilot.exception.AgentBudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-request guardrail + trace advisor. Must be registered per call (it holds run state).
 * Order is inside ToolCallingAdvisor so it is invoked on every loop iteration.
 */
public class AgentRunAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AgentRunAdvisor.class);
    private static final int MAX_ARG_CHARS = 200;

    private static final class Round {
        final List<AssistantMessage.ToolCall> calls;
        final long modelMs;
        Long toolMs;

        Round(List<AssistantMessage.ToolCall> calls, long modelMs) {
            this.calls = calls;
            this.modelMs = modelMs;
        }
    }

    private final int maxToolRounds;
    private final long startNanos;
    private final long budgetNanos;
    private final long budgetSeconds;
    private final List<Round> rounds = new ArrayList<>();

    private long lastIterationEndNanos;
    private int modelCalls;
    private int promptTokens;
    private int completionTokens;

    public AgentRunAdvisor(int maxToolRounds, long timeBudgetSeconds) {
        this.maxToolRounds = maxToolRounds;
        this.budgetSeconds = timeBudgetSeconds;
        this.budgetNanos = timeBudgetSeconds * 1_000_000_000L;
        this.startNanos = System.nanoTime();
    }

    @Override
    public String getName() {
        return "AgentRunAdvisor";
    }

    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 200;   // inside the tool loop, after ReasoningStripAdvisor
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long callStart = System.nanoTime();

        // Time since the previous model response = tool execution of that round.
        if (!rounds.isEmpty()) {
            Round last = rounds.get(rounds.size() - 1);
            if (last.toolMs == null) {
                last.toolMs = nanosToMs(callStart - lastIterationEndNanos);
            }
        }
        checkDeadline("before model call");

        ChatClientResponse response = chain.nextCall(request);

        long callEnd = System.nanoTime();
        lastIterationEndNanos = callEnd;
        modelCalls++;

        ChatResponse chatResponse = response.chatResponse();
        accumulateUsage(chatResponse);

        if (chatResponse != null && chatResponse.hasToolCalls() && chatResponse.getResult() != null) {
            // We are about to let Spring AI execute these tools; refuse if over budget.
            if (rounds.size() >= maxToolRounds) {
                throw new AgentBudgetExceededException(
                        "Agent stopped: exceeded the maximum of " + maxToolRounds + " tool-call rounds.");
            }
            checkDeadline("before executing tools");

            List<AssistantMessage.ToolCall> calls = chatResponse.getResult().getOutput().getToolCalls();
            rounds.add(new Round(calls, nanosToMs(callEnd - callStart)));
            calls.forEach(c -> log.info("[AGENT] round {} -> {}({})",
                    rounds.size(), c.name(), truncate(c.arguments())));
        }
        return response;
    }

    private void checkDeadline(String where) {
        if (System.nanoTime() - startNanos > budgetNanos) {
            throw new AgentBudgetExceededException(
                    "Agent stopped: exceeded the time budget of " + budgetSeconds + "s (" + where + ").");
        }
    }

    private void accumulateUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            promptTokens += usage.getPromptTokens();
        }
        if (usage.getCompletionTokens() != null) {
            completionTokens += usage.getCompletionTokens();
        }
    }

    public List<TraceStep> trace() {
        List<TraceStep> steps = new ArrayList<>();
        for (int i = 0; i < rounds.size(); i++) {
            Round r = rounds.get(i);
            for (AssistantMessage.ToolCall c : r.calls) {
                steps.add(new TraceStep(i + 1, c.name(), truncate(c.arguments()), r.modelMs, r.toolMs));
            }
        }
        return steps;
    }

    public int modelCalls() {
        return modelCalls;
    }

    public int toolRounds() {
        return rounds.size();
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public long elapsedMs() {
        return nanosToMs(System.nanoTime() - startNanos);
    }

    private static long nanosToMs(long nanos) {
        return nanos / 1_000_000L;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_ARG_CHARS ? s : s.substring(0, MAX_ARG_CHARS) + "...";
    }
}