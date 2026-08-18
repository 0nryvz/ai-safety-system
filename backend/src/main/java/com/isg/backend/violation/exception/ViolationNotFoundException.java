package com.isg.backend.violation.exception;

import java.util.UUID;

public class ViolationNotFoundException
        extends RuntimeException {

    public ViolationNotFoundException(
            UUID violationId
    ) {
        super(
                "Violation not found: "
                        + violationId
        );
    }
}