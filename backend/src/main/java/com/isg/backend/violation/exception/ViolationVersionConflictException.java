package com.isg.backend.violation.exception;

public class ViolationVersionConflictException extends RuntimeException {

    public ViolationVersionConflictException(
            String message
    ) {   
        super(message);
    }
}