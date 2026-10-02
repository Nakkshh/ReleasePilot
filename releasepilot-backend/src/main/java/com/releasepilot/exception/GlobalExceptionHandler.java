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

    @ExceptionHandler(AdminAuthException.class)
    public ResponseEntity<ErrorResponse> handleAdminAuth(AdminAuthException ex) {
        log.warn("Admin guard rejected a request: {}", ex.getCode());
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage(), Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}