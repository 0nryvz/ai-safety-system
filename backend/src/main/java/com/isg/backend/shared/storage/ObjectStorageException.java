package com.isg.backend.shared.storage;

public class ObjectStorageException
        extends RuntimeException {

    public ObjectStorageException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}