package com.camerarental.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderFromCartRequest {
    
    @NotNull(message = "Total amount is required")
    private BigDecimal totalAmount;
    
    @NotNull(message = "Start date is required")
    private String startDate;
    
    @NotNull(message = "End date is required")
    private String endDate;
    
    private String note;
    
    @NotEmpty(message = "Items cannot be empty")
    private List<CartItemRequest> items;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemRequest {
        
        @NotNull(message = "Camera ID is required")
        private Long cameraId;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        @NotNull(message = "Rental days is required")
        private Integer rentalDays;
        
        private BigDecimal pricePerDay;
        private BigDecimal subtotal;
        private String cameraName;
    }
}
