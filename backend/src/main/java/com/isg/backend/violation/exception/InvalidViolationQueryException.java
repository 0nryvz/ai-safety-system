package com.isg.backend.violation.exception;

public class InvalidViolationQueryException
        extends IllegalArgumentException {

    public InvalidViolationQueryException(
            String message
    ) {
        super(message);
    }
}