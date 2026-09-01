package com.brad.pms.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pms.storage")
public class StorageProperties {

    /** local = default disk volume; s3 = S3-compatible object store (MinIO or a cloud bucket). */
    private String type = "local";

    private final S3 s3 = new S3();

    @Data
    public static class S3 {
        private String endpoint = "";
        private String region = "us-east-1";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;
    }

    public boolean usesObjectStorage() {
        return "s3".equalsIgnoreCase(trim(type));
    }

    public void validate() {
        String kind = trim(type).toLowerCase();
        if (kind.isEmpty() || "local".equals(kind)) return;
        if (!"s3".equals(kind)) {
            throw new IllegalStateException("pms.storage.type must be local or s3");
        }
        if (trim(s3.endpoint).isEmpty() || trim(s3.bucket).isEmpty()
                || trim(s3.accessKey).isEmpty() || trim(s3.secretKey).isEmpty()) {
            throw new IllegalStateException(
                    "S3 storage requires PMS_S3_ENDPOINT, PMS_S3_BUCKET, PMS_S3_ACCESS_KEY, and PMS_S3_SECRET_KEY");
        }
        String endpoint = trim(s3.endpoint);
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new IllegalStateException("PMS_S3_ENDPOINT must be an http(s) URL");
        }
    }

    static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
