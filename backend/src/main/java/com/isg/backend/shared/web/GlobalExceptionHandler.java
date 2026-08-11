package com.isg.backend.shared.web;

import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedDetectionLabelException.class)
    public ResponseEntity<ApiErrorResponse> unsupported(
            UnsupportedDetectionLabelException ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "UNSUPPORTED_DETECTION_LABEL",
                ex.getMessage(),
                req,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(
            MethodArgumentNotValidException ex,
            HttpServletRequest req) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fieldErrors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed.",
                req,
                fieldErrors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> typeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.BAD_REQUEST,
                "TYPE_MISMATCH",
                "Invalid value for parameter '" + ex.getName() + "'.",
                req,
                Map.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(
            NoResourceFoundException ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Resource not found.",
                req,
                Map.of()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> responseStatus(
            ResponseStatusException ex,
            HttpServletRequest req) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String code = switch (status) {
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_ENTITY -> "UNPROCESSABLE_CONTENT";
            default -> "HTTP_ERROR";
        };

        return build(
                status,
                code,
                ex.getReason() != null
                        ? ex.getReason()
                        : status.getReasonPhrase(),
                req,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(
            Exception ex,
            HttpServletRequest req) {

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
            Map<String, String> fieldErrors) {

        ApiErrorResponse body = new ApiErrorResponse(
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