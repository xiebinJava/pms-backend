package com.brad.pms.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoragePropertiesTest {

    @Test
    void localStorageNeedsNoBucket() {
        StorageProperties properties = new StorageProperties();
        properties.validate();
        assertThat(properties.usesObjectStorage()).isFalse();
    }

    @Test
    void s3RequiresEndpointBucketAndKeys() {
        StorageProperties properties = new StorageProperties();
        properties.setType("s3");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PMS_S3_ENDPOINT");

        properties.getS3().setEndpoint("https://minio.example.com");
        properties.getS3().setBucket("pms-uploads");
        properties.getS3().setAccessKey("pms");
        properties.getS3().setSecretKey("replace_with_s3_secret");
        properties.validate();
        assertThat(properties.usesObjectStorage()).isTrue();
    }

    @Test
    void rejectsUnknownStorageType() {
        StorageProperties properties = new StorageProperties();
        properties.setType("ftp");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local or s3");
    }
}
