package com.brad.pms.storage;

import com.brad.pms.common.exception.BusinessException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 / MinIO adapter. The bucket and credentials come from environment config. */
public class S3ObjectStore implements ObjectStore {

    private final S3Client client;
    private final String bucket;

    public S3ObjectStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (RuntimeException ex) {
            throw BusinessException.error("附件保存失败，请稍后重试");
        }
    }

    @Override
    public Resource load(String key) {
        try {
            byte[] bytes = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            return new ByteArrayResource(bytes);
        } catch (NoSuchKeyException ex) {
            throw BusinessException.error("文件不存在");
        } catch (RuntimeException ex) {
            throw BusinessException.error("文件不存在");
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ignored) {
            // Soft-delete in the database still hides the file from the API.
        }
    }
}
