package com.releasepilot.exception;

import com.releasepilot.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import com.releasepilot.agent.LlmRateLimit;
import org.springframework.http.HttpHeaders;

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

        if (LlmRateLimit.isRateLimited(ex)) {
            return rateLimited(ex);
        }

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
        if (LlmRateLimit.isRateLimited(ex)) {
            return rateLimited(ex);
        }
        log.error("Unhandled exception", ex);
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

    @ExceptionHandler(DraftConflictException.class)
    public ResponseEntity<ErrorResponse> handleDraftConflict(DraftConflictException ex) {
        log.warn("Draft conflict [{}]: {}", ex.getCode(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ErrorResponse body = new ErrorResponse(
                "BAD_REQUEST", "Request body is missing or is not valid JSON.", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDraftNotFound(DraftNotFoundException ex) {
        ErrorResponse body = new ErrorResponse("DRAFT_NOT_FOUND", ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentModification(OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("CONCURRENT_MODIFICATION",
                "The draft was changed by another request. Reload it and try again.", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleBadParam(MethodArgumentTypeMismatchException ex) {
        ErrorResponse body = new ErrorResponse("BAD_REQUEST",
                "Invalid value for parameter '" + ex.getName() + "'.", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        ErrorResponse body = new ErrorResponse("BAD_REQUEST",
                "Missing required parameter '" + ex.getParameterName() + "'.", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ClientRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleClientRateLimited(ClientRateLimitedException ex) {
        log.warn("Request limited [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(new ErrorResponse(ex.getCode(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        log.warn("Auth request rejected: {}", ex.getCode());
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage(), Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    private ResponseEntity<ErrorResponse> rateLimited(Exception ex) {
        var retryAfter = LlmRateLimit.retryAfterSeconds(ex);
        log.warn("LLM provider rate limit (retryAfter={}s)", retryAfter.isPresent() ? retryAfter.getAsLong() : "n/a");
        String message = retryAfter.isPresent()
                ? "The AI provider is rate limiting requests. Try again in " + retryAfter.getAsLong() + " seconds."
                : "The AI provider is rate limiting requests. Try again shortly.";
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        retryAfter.ifPresent(s -> response.header(HttpHeaders.RETRY_AFTER, Long.toString(s)));
        return response.body(new ErrorResponse("LLM_RATE_LIMITED", message, Instant.now()));
    }
}