package com.isg.backend.shared.web;

import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedDetectionLabelException.class)
    public ResponseEntity<ApiErrorResponse> unsupported(
            UnsupportedDetectionLabelException ex,
            HttpServletRequest req) {

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                422,
                "Unprocessable Entity",
                ex.getMessage(),
                req.getRequestURI()
        );

        return ResponseEntity.unprocessableEntity().body(body);
    }
}