package com.dong.dongrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.minio")
public class MinioStorageProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucketName;
}
