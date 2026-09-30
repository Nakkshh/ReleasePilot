package com.releasepilot.exception;

import com.releasepilot.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR",
                details,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(GeminiServiceException.class)
    public ResponseEntity<ErrorResponse> handleGeminiFailure(GeminiServiceException ex) {
        log.error("AI service failure: {}", ex.getMessage(), ex);

        ErrorResponse body = new ErrorResponse(
                "AI_SERVICE_ERROR",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        ErrorResponse body = new ErrorResponse(
                "INTERNAL_ERROR",
                "Something went wrong. Please try again.",
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(AgentBudgetExceededException.class)
    public ResponseEntity<ErrorResponse> handleAgentBudget(AgentBudgetExceededException ex) {
        log.warn("Agent guardrail tripped: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("AGENT_BUDGET_EXCEEDED", ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ErrorResponse> handleGitHub(GitHubApiException ex) {
        log.warn("GitHub API failure: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("GITHUB_API_ERROR", ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}