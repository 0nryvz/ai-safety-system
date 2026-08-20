package com.isg.backend.shared.storage;

import java.io.InputStream;

public interface ObjectStoragePort {

    void putObject(
            String objectKey,
            InputStream inputStream,
            long sizeBytes,
            String contentType
    );

    PresignedObjectUrl createGetUrl(
            String objectKey
    );
}