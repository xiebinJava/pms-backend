package com.brad.pms.storage;

import org.springframework.core.io.Resource;

/** Byte-level object store used by the S3-compatible FileStorageService. */
public interface ObjectStore {

    void put(String key, byte[] content, String contentType);

    Resource load(String key);

    void delete(String key);
}
