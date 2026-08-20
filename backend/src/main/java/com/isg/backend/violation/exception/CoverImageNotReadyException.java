package com.isg.backend.violation.exception;

import java.util.UUID;

public class CoverImageNotReadyException
        extends RuntimeException {

    public CoverImageNotReadyException(
            UUID violationId
    ) {
        super(
                "Cover image is not ready for violationId="
                        + violationId
        );
    }
}