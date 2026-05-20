package com.camerarental.dto.response;

import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderCode;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private String note;
    private LocalDateTime createdAt;

    public static OrderResponse fromEntity(RentalOrder order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .cameraId(item.getCamera().getId())
                        .cameraName(item.getCamera().getName())
                        .quantity(item.getQuantity())
                        .rentalDays(item.getRentalDays())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .depositAmount(order.getDepositAmount())
                .startDate(order.getStartDate())
                .endDate(order.getEndDate())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long cameraId;
        private String cameraName;
        private Integer quantity;
        private Integer rentalDays;
        private BigDecimal subtotal;
    }
}
