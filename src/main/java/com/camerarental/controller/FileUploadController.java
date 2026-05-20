package com.camerarental.controller;

import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.S3UploadResponse;
import com.camerarental.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<S3UploadResponse>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "cameraId", required = false) Long cameraId) {

        log.info("[FileUpload] uploadImage - fileName: {}, size: {}, cameraId: {}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                cameraId);

        try {
            S3UploadResponse response = fileStorageService.uploadImage(file, cameraId);
            log.info("[FileUpload] Upload successful - storage: {}, key: {}", 
                    fileStorageService.getStorageType(), response.getKey());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Image uploaded successfully", response));
        } catch (Exception e) {
            log.error("[FileUpload] Upload failed - error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
