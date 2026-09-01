package com.brad.pms.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Storage abstraction so local disk can later be replaced by object storage. */
public interface FileStorageService {

    StoredFile store(MultipartFile file);

    StoredFile storeAttachment(MultipartFile file);

    Resource load(String key);

    void delete(String key);

    record StoredFile(String key, String originalFilename, String contentType, long size) {
    }
}
