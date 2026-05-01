package com.dong.dongrag.service.impl;

import com.dong.dongrag.config.MinioStorageProperties;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.service.MinioStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioStorageServiceImpl implements MinioStorageService {

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioStorageProperties minioStorageProperties;

    @PostConstruct
    public void initBucket() {
        try {
            String bucketName = getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 MinIO Bucket 失败: " + e.getMessage());
        }
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(getBucketName())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传 MinIO 失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] download(String objectKey) {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(getBucketName())
                .object(objectKey)
                .build())) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取 MinIO 源文件失败: " + e.getMessage());
        }
    }

    @Override
    public String getBucketName() {
        return minioStorageProperties.getBucketName();
    }
}
