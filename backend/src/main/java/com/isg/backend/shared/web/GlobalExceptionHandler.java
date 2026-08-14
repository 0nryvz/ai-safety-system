package com.isg.backend.shared.web;

import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedDetectionLabelException.class)
    public ResponseEntity<ApiErrorResponse> unsupported(
            UnsupportedDetectionLabelException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                req
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        String message =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(
                                error ->
                                        error.getField()
                                                + ": "
                                                + error.getDefaultMessage()
                        )
                        .collect(
                                Collectors.joining(
                                        ", "
                                )
                        );

        return build(
                HttpStatus.BAD_REQUEST,
                message,
                req
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> typeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest req
    ) {
        String message =
                "Invalid value for parameter '"
                        + ex.getName()
                        + "'.";

        return build(
                HttpStatus.BAD_REQUEST,
                message,
                req
        );
    }

    @ExceptionHandler(ViolationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> violationNotFound(
            ViolationNotFoundException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                "Violation not found.",
                req
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> responseStatus(
            ResponseStatusException ex,
            HttpServletRequest req
    ) {
        HttpStatus status =
                HttpStatus.valueOf(
                        ex.getStatusCode().value()
                );

        String message =
                ex.getReason() == null
                        ? status.getReasonPhrase()
                        : ex.getReason();

        return build(
                status,
                message,
                req
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(
            NoResourceFoundException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                "Resource not found.",
                req
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(
            Exception ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                req
        );
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest req
    ) {
        ApiErrorResponse body =
                new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        req.getRequestURI()
                );

        return ResponseEntity
                .status(
                        status
                )
                .body(
                        body
                );
    }
}