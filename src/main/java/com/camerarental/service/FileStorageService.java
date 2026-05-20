package com.camerarental.service;

import com.camerarental.dto.response.S3UploadResponse;
import com.camerarental.service.impl.LocalStorageService;
import com.camerarental.service.impl.S3ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final S3ServiceImpl s3Service;
    private final LocalStorageService localStorageService;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Value("${aws.s3.bucket-name:}")
    private String bucketName;

    public S3UploadResponse uploadImage(MultipartFile file, Long cameraId) {
        if (isS3Configured()) {
            log.info("[FileStorage] Using S3 storage");
            try {
                return s3Service.uploadImage(file, cameraId);
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                log.error("[FileStorage] S3 upload failed: {}", errorMessage);
                
                if (errorMessage != null && errorMessage.toLowerCase().contains("access denied")) {
                    log.error("[FileStorage] S3 Access Denied - possible causes: wrong region, missing IAM permissions, or bucket policy issue");
                    log.error("[FileStorage] Current region should be: ap-southeast-2");
                }
                
                log.warn("[FileStorage] Falling back to local storage");
                return localStorageService.uploadImage(file, cameraId);
            }
        } else {
            log.warn("[FileStorage] S3 not configured, using local storage");
            return localStorageService.uploadImage(file, cameraId);
        }
    }

    public S3UploadResponse uploadImageSimple(MultipartFile file) {
        return uploadImage(file, null);
    }

    public boolean isS3Configured() {
        return StringUtils.hasText(accessKeyId) && 
               StringUtils.hasText(secretAccessKey) && 
               StringUtils.hasText(bucketName);
    }

    public String getStorageType() {
        return isS3Configured() ? "S3" : "LOCAL";
    }
}
