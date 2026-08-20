package com.isg.backend.shared.web;

import com.isg.backend.violation.exception.InvalidViolationQueryException;
import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.exception.ViolationVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import com.isg.backend.violation.exception.CoverImageNotReadyException;

import org.springframework.dao.DataIntegrityViolationException;
import com.isg.backend.recording.application.RecordingNotFoundForViolationException;
import com.isg.backend.recording.application.RecordingNotReadyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;


import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.MDC;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(
            Clock clock
    ) {
        this.clock =
                clock;
    }

    @ExceptionHandler(UnsupportedDetectionLabelException.class)
    public ResponseEntity<ApiErrorResponse> unsupported(
            UnsupportedDetectionLabelException ex,
            HttpServletRequest req
    ) {
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
            HttpServletRequest req
    ) {
        Map<String, String> fieldErrors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        error ->
                                                error.getField(),
                                        error ->
                                                error.getDefaultMessage() == null
                                                        ? "Invalid value."
                                                        : error.getDefaultMessage(),
                                        (first, ignored) ->
                                                first,
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> messageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Malformed or invalid request body.",
                req,
                Map.of()
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

    @ExceptionHandler(ViolationVersionConflictException.class)
    public ResponseEntity<ApiErrorResponse> violationVersionConflict(
            ViolationVersionConflictException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.CONFLICT,
                "VIOLATION_VERSION_CONFLICT",
                ex.getMessage(),
                req,
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiErrorResponse> dataIntegrityViolation(
        DataIntegrityViolationException ex,
        HttpServletRequest req
) {
    return build(
            HttpStatus.CONFLICT,
            "DATA_INTEGRITY_VIOLATION",
            "Database constraint violation.",
            req,
            Map.of()
    );
}

    @ExceptionHandler(RecordingNotFoundForViolationException.class)
    public ResponseEntity<ApiErrorResponse> recordingNotFound(
            RecordingNotFoundForViolationException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                "RECORDING_NOT_FOUND",
                "Recording not found.",
                req,
                Map.of()
        );
    }

    @ExceptionHandler(RecordingNotReadyException.class)
    public ResponseEntity<ApiErrorResponse> recordingNotReady(
            RecordingNotReadyException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.CONFLICT,
                "RECORDING_NOT_READY",
                "Recording is not ready.",
                req,
                Map.of()
        );
    }

    @ExceptionHandler(CoverImageNotReadyException.class)
    public ResponseEntity<ApiErrorResponse> coverImageNotReady(
            CoverImageNotReadyException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.CONFLICT,
                "COVER_IMAGE_NOT_READY",
                "Cover image is not ready.",
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
                        ex.getStatusCode()
                                .value()
                );

        String message =
                ex.getReason() == null
                        ? status.getReasonPhrase()
                        : ex.getReason();

        return build(
                status,
                codeFor(status),
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
    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_CONTENT -> "UNPROCESSABLE_CONTENT";
            case INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR";
            default -> "REQUEST_ERROR";
        };
    }


    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest req,
            Map<String, String> fieldErrors
    ) {
        String correlationId =
                MDC.get(
                        CorrelationIdFilter.MDC_KEY
                );

        ApiErrorResponse body =
                new ApiErrorResponse(
                        Instant.now(
                                clock
                        ),
                        status.value(),
                        code,
                        message,
                        req.getRequestURI(),
                        correlationId,
                        fieldErrors
                );

        return ResponseEntity
                .status(
                        status
                )
                .body(
                        body
                );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> badCredentials(
            BadCredentialsException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Geçersiz e-posta veya şifre.",
                req,
                Map.of()
        );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> disabledUser(
            DisabledException ex,
            HttpServletRequest req
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Geçersiz e-posta veya şifre.",
                req,
                Map.of()
        );
    }

}