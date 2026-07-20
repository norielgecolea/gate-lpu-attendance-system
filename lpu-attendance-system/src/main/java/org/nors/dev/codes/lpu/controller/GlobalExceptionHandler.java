package org.nors.dev.codes.lpu.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.nors.dev.codes.lpu.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                message,
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage() != null ? ex.getMessage() : "Unexpected error",
                request.getRequestURI()
        ));
    }
}
