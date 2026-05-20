package com.camerarental.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@Slf4j
public class S3Config {

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Value("${aws.region:}")
    private String region;

    @Value("${aws.s3.bucket-name:}")
    private String bucketName;

    @Bean
    public S3Client s3Client() {
        log.info("[S3Config] Initializing S3 client...");
        log.info("[S3Config] Region: {}", region);
        log.info("[S3Config] Bucket: {}", bucketName);
        log.info("[S3Config] AccessKeyId present: {}", StringUtils.hasText(accessKeyId));
        log.info("[S3Config] SecretAccessKey present: {}", StringUtils.hasText(secretAccessKey));

        String effectiveRegion = StringUtils.hasText(region) ? region : "ap-southeast-2";

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(effectiveRegion));

        if (StringUtils.hasText(accessKeyId) && StringUtils.hasText(secretAccessKey)) {
            log.info("[S3Config] Using explicit AWS credentials");
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(awsCreds));
        } else {
            log.warn("[S3Config] AWS credentials not provided, using default chain");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        S3Client client = builder.build();
        log.info("[S3Config] S3 client created for region: {}", effectiveRegion);
        return client;
    }

    @Bean
    public boolean s3Available() {
        boolean available = StringUtils.hasText(accessKeyId) && 
                           StringUtils.hasText(secretAccessKey) && 
                           StringUtils.hasText(bucketName);
        log.info("[S3Config] S3 available: {}", available);
        return available;
    }
}
