package com.brad.pms.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "pms.storage.type", havingValue = "s3")
    S3Client s3Client(StorageProperties properties) {
        properties.validate();
        StorageProperties.S3 s3 = properties.getS3();
        return S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint().trim()))
                .region(Region.of(s3.getRegion() == null || s3.getRegion().isBlank() ? "us-east-1" : s3.getRegion().trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey().trim(), s3.getSecretKey().trim())))
                .forcePathStyle(s3.isPathStyle())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "pms.storage.type", havingValue = "s3")
    ObjectStore objectStore(S3Client s3Client, StorageProperties properties) {
        return new S3ObjectStore(s3Client, properties.getS3().getBucket().trim());
    }

    @Bean
    @ConditionalOnProperty(name = "pms.storage.type", havingValue = "s3")
    FileStorageService s3FileStorageService(ObjectStore objectStore) {
        return new S3FileStorageService(objectStore);
    }
}
