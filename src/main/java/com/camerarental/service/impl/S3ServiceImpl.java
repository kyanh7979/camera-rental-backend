package com.camerarental.service.impl;

import com.camerarental.dto.response.S3UploadResponse;
import com.camerarental.entity.Camera;
import com.camerarental.entity.CameraImage;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.CameraImageRepository;
import com.camerarental.repository.CameraRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class S3ServiceImpl {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/avif", "image/gif"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif"
    );

    private static final DateTimeFormatter PATH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final CameraRepository cameraRepository;
    private final CameraImageRepository cameraImageRepository;

    @Value("${aws.s3.bucket-name:}")
    private String bucketName;

    @Value("${aws.s3.public-base-url:}")
    private String publicBaseUrl;

    @Value("${aws.s3.max-size-bytes:10485760}")
    private long maxSizeBytes;

    public S3ServiceImpl(S3Client s3Client, CameraRepository cameraRepository, CameraImageRepository cameraImageRepository) {
        this.s3Client = s3Client;
        this.cameraRepository = cameraRepository;
        this.cameraImageRepository = cameraImageRepository;
    }

    @Transactional
    public S3UploadResponse uploadImage(MultipartFile file, Long cameraId) {
        log.info("[S3Service] uploadImage - fileName: {}, size: {}, cameraId: {}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                cameraId);

        validateImage(file);

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        String key = buildObjectKey(originalFilename);

        try {
            log.info("[S3Service] Uploading to S3 - bucket: {}, key: {}", bucketName, key);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            try (InputStream inputStream = file.getInputStream()) {
                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            }

            log.info("[S3Service] S3 upload successful");

        } catch (IOException e) {
            log.error("[S3Service] Cannot read upload file: {}", e.getMessage());
            throw new BadRequestException("Không thể đọc file upload. Vui lòng thử lại.");
        } catch (S3Exception e) {
            log.error("[S3Service] S3 upload failed: {}", e.awsErrorDetails().errorMessage());
            throw new BadRequestException("Upload lên S3 thất bại: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("[S3Service] Unexpected error: {}", e.getMessage());
            throw new BadRequestException("Upload thất bại: " + e.getMessage());
        }

        String url = buildPublicUrl(key);

        if (cameraId != null) {
            saveImageToDatabase(cameraId, url);
        }

        return S3UploadResponse.builder()
                .key(key)
                .url(url)
                .contentType(contentType)
                .size(file.getSize())
                .build();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File upload không được để trống");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Chỉ chấp nhận file ảnh: JPG, PNG, WEBP, AVIF, GIF");
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename)) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BadRequestException("Đuôi file không hợp lệ. Chỉ chấp nhận: " + String.join(", ", ALLOWED_EXTENSIONS));
            }
        }

        long sizeBytes = file.getSize();
        if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new BadRequestException("Kích thước file vượt quá giới hạn. Tối đa: " + (maxSizeBytes / 1024 / 1024) + "MB");
        }
    }

    private String buildObjectKey(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String datePart = LocalDateTime.now().format(PATH_TIME_FORMAT);
        String uuid = UUID.randomUUID().toString();
        return "images/" + datePart + "/" + uuid + extension;
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) return ".jpg";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return ".jpg";
        return filename.substring(dotIndex).toLowerCase();
    }

    private String buildPublicUrl(String key) {
        String encodedKey = Arrays.stream(key.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b)
                .orElse("");

        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl.replaceAll("/+$", "") + "/" + encodedKey;
        }

        return "https://" + bucketName + ".s3.amazonaws.com/" + encodedKey;
    }

    private void saveImageToDatabase(Long cameraId, String imageUrl) {
        try {
            Camera camera = cameraRepository.findById(cameraId)
                    .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", cameraId));

            long imageCount = cameraImageRepository.countByCameraId(cameraId);

            CameraImage cameraImage = CameraImage.builder()
                    .imageUrl(imageUrl)
                    .displayOrder((int) imageCount)
                    .camera(camera)
                    .build();

            cameraImageRepository.save(cameraImage);
            log.info("[S3Service] Image saved to DB - cameraId: {}", cameraId);
        } catch (Exception e) {
            log.error("[S3Service] Failed to save image to database: {}", e.getMessage());
        }
    }
}
