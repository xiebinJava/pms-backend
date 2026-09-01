package com.brad.pms.storage;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Object-storage FileStorageService. Used only when pms.storage.type=s3. */
public class S3FileStorageService implements FileStorageService {

    private final ObjectStore store;

    public S3FileStorageService(ObjectStore store) {
        this.store = store;
    }

    @Override
    public StoredFile store(MultipartFile file) {
        return persist(file, UploadFiles.requireImage(file), "图片保存失败，请稍后重试");
    }

    @Override
    public StoredFile storeAttachment(MultipartFile file) {
        return persist(file, UploadFiles.requireAttachment(file), "附件保存失败，请稍后重试");
    }

    private StoredFile persist(MultipartFile file, String contentType, String saveError) {
        String key = UploadFiles.newKey(contentType);
        try {
            store.put(key, file.getBytes(), contentType);
            return new StoredFile(key, file.getOriginalFilename(), contentType, file.getSize());
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw BusinessException.error(saveError);
        }
    }

    @Override
    public Resource load(String key) {
        if (!UploadFiles.isSafeStorageKey(key)) {
            throw BusinessException.error("文件不存在");
        }
        return store.load(key);
    }

    @Override
    public void delete(String key) {
        if (!UploadFiles.isSafeStorageKey(key)) return;
        store.delete(key);
    }
}
