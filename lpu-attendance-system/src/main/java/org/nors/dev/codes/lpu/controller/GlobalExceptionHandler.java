package org.nors.dev.codes.lpu.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return json(
                response,
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                message,
                request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(
            ResponseStatusException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return json(
                response,
                status,
                status.getReasonPhrase(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingResource(
            NoResourceFoundException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return json(response, HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (isClientDisconnect(ex) || response.isCommitted()) {
            log.debug("Client disconnected during {}", request.getRequestURI());
            return null;
        }
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return json(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                ex.getMessage() != null ? ex.getMessage() : "Unexpected error",
                request
        );
    }

    /**
     * Video and other media handlers set Content-Type before streaming. A later
     * error must not try to write {@link ApiError} as {@code video/mp4}.
     */
    private ResponseEntity<ApiError> json(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request
    ) {
        if (response.isCommitted() || !reset(response)) {
            return null;
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(
                        Instant.now(),
                        status.value(),
                        error,
                        message,
                        request.getRequestURI()
                ));
    }

    private static boolean reset(HttpServletResponse response) {
        try {
            response.reset();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private static boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            String type = current.getClass().getName();
            if (type.endsWith("ClientAbortException") || type.endsWith("AsyncRequestNotUsableException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("broken pipe") || normalized.contains("connection reset")) {
                    return true;
                }
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }
}
