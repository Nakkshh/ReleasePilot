package com.releasepilot.exception;

/** Thrown by the agent guardrails when the tool-round cap or time budget is exceeded. */
public class AgentBudgetExceededException extends RuntimeException {
    public AgentBudgetExceededException(String message) {
        super(message);
    }
}