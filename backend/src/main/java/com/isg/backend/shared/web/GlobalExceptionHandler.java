package com.isg.backend.shared.web;

import com.isg.backend.violation.exception.InvalidViolationQueryException;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
                "UNSUPPORTED_DETECTION_LABEL",
                ex.getMessage(),
                req,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        Map<String, String> fieldErrors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        error -> error.getField(),
                                        error -> error.getDefaultMessage() == null
                                                ? "Invalid value."
                                                : error.getDefaultMessage(),
                                        (first, ignored) -> first,
                                        LinkedHashMap::new
                                )
                        );

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed.",
                req,
                fieldErrors
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
                "INVALID_PARAMETER",
                message,
                req,
                Map.of()
        );
    }

    @ExceptionHandler(InvalidViolationQueryException.class)
    public ResponseEntity<ApiErrorResponse> invalidViolationQuery(
            InvalidViolationQueryException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                ex.getMessage(),
                req,
                Map.of()
        );
    }

    @ExceptionHandler(ViolationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> violationNotFound(
            ViolationNotFoundException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                "VIOLATION_NOT_FOUND",
                "Violation not found.",
                req,
                Map.of()
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
                "REQUEST_ERROR",
                message,
                req,
                Map.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(
            NoResourceFoundException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Resource not found.",
                req,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(
            Exception ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                req,
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest req,
            Map<String, String> fieldErrors
    ) {
        ApiErrorResponse body =
                new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        code,
                        message,
                        req.getRequestURI(),
                        fieldErrors
                );

        return ResponseEntity
                .status(status)
                .body(body);
    }
}