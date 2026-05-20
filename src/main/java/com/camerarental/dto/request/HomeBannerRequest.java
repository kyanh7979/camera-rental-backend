package com.camerarental.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HomeBannerRequest {

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private Boolean active;

    private Integer displayOrder;
}
