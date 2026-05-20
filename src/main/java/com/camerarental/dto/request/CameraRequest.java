package com.camerarental.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CameraRequest {

    @NotBlank(message = "Camera name is required")
    private String name;

    private String brand;
    private String model;
    private String description;

    @NotNull(message = "Daily price is required")
    @Positive(message = "Daily price must be positive")
    private BigDecimal dailyPrice;

    @Positive(message = "Deposit must be positive")
    private BigDecimal deposit;

    private List<String> images;

    private List<SampleImageRequest> sampleImages;

    @Positive(message = "Stock must be positive")
    private Integer stock;

    private String specifications;

    private Long categoryId;

    private String category;
}
