package com.camerarental.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateOrderRequest {

    private BigDecimal totalAmount;

    private LocalDate startDate;
    private LocalDate endDate;

    private String note;

    private List<Item> items;

    @Data
    public static class Item {
        private Long cameraId;
        private Integer rentalDays;
        private Integer quantity;
    }
}