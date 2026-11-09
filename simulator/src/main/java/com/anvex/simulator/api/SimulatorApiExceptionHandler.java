package com.anvex.simulator.api;

import com.anvex.common.ApiError;
import com.anvex.simulator.run.RunManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.UUID;

@RestControllerAdvice
public class SimulatorApiExceptionHandler {

    private final Clock clock;

    public SimulatorApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(RunManager.RunInProgressException.class)
    public ResponseEntity<ApiError> handleRunInProgress(RunManager.RunInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("run_in_progress", ex.getMessage(), UUID.randomUUID().toString(), clock.instant()));
    }

    @ExceptionHandler(RunManager.RunNotFoundException.class)
    public ResponseEntity<ApiError> handleRunNotFound(RunManager.RunNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("run_not_found", ex.getMessage(), UUID.randomUUID().toString(), clock.instant()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("invalid_request", ex.getMessage(), UUID.randomUUID().toString(), clock.instant()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest()
                .body(ApiError.of("invalid_request", message, UUID.randomUUID().toString(), clock.instant()));
    }
}
