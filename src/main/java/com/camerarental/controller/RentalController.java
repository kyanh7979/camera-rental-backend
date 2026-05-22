package com.camerarental.controller;

import com.camerarental.dto.request.UpdateRentalStatusRequest;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.OrderResponse;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.service.OrderService;
import com.camerarental.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rentals")
@RequiredArgsConstructor
@Slf4j
public class RentalController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getAllRentals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size) {

        OrderStatus orderStatus = convertStatus(status);

        log.info("[ADMIN_ORDERS_SEARCH] status={}, keyword={}, page={}, size={}", status, keyword, page, size);

        PagedResponse<OrderResponse> response = orderService.getAllOrders(orderStatus, page, size, keyword);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getRentalById(@PathVariable Long id) {
        OrderResponse rental = orderService.getOrderById(id, null);
        return ResponseEntity.ok(ApiResponse.success(rental));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateRentalStatus(
            @PathVariable Long id,
            @RequestBody UpdateRentalStatusRequest request) {

        OrderStatus orderStatus = convertStatus(request.getStatus());
        if (orderStatus == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid status: " + request.getStatus()));
        }

        OrderResponse updated = orderService.updateOrderStatus(id, orderStatus);
        return ResponseEntity.ok(ApiResponse.success("Rental status updated", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteRental(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Rental deleted successfully", null));
    }

    private OrderStatus convertStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        String upper = status.toUpperCase();

        // Map frontend status to backend status
        if ("PICKED_UP".equals(upper)) {
            return OrderStatus.RENTING;
        }

        try {
            return OrderStatus.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
