package com.camerarental.service;

import com.camerarental.dto.response.S3UploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3 Service Interface
 */
public interface S3Service {
    
    /**
     * Upload image to S3
     * @param file The file to upload
     * @param cameraId Optional camera ID for database linking
     * @return Upload response with URL
     */
    S3UploadResponse uploadImage(MultipartFile file, Long cameraId);
}
