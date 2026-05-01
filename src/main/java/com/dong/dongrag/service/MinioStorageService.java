package com.dong.dongrag.service;

import java.io.InputStream;

public interface MinioStorageService {

    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    byte[] download(String objectKey);

    String getBucketName();
}
