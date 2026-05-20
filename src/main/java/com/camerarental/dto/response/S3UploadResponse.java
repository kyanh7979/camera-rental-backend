package com.camerarental.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3UploadResponse {
    private String key;
    private String url;
    private String contentType;
    private long size;
}
