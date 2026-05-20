package com.camerarental.controller;

import com.camerarental.dto.request.CreateOrderFromCartRequest;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.PayOSResponse;
import com.camerarental.service.PayOSService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOSController {

    private final PayOSService payOSService;

    @GetMapping("/create-payment")
    public ResponseEntity<ApiResponse<PayOSResponse>> createPayment(
            @RequestParam(defaultValue = "10000") int amount,
            @RequestParam(defaultValue = "Thanh toan don hang") String description) {
        try {
            log.info("=== CREATE PAYMENT REQUEST ===");
            log.info("Amount: {} VND", amount);
            log.info("Description: {}", description);

            PayOSResponse response = payOSService.createPayment(amount, description);

            log.info("=== CREATE PAYMENT SUCCESS ===");
            log.info("QRCode: {}", response.getQrCode());
            log.info("CheckoutUrl: {}", response.getCheckoutUrl());

            return ResponseEntity.ok(ApiResponse.success("Payment created successfully", response));
        } catch (Exception e) {
            log.error("=== CREATE PAYMENT ERROR ===");
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create payment: " + e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("PayOS webhook received: {}", payload);

            String code = String.valueOf(payload.get("code"));
            String desc = String.valueOf(payload.get("desc"));
            Long orderCode = payload.get("orderCode") != null
                ? Long.parseLong(String.valueOf(payload.get("orderCode")))
                : null;

            if ("00".equals(code) && orderCode != null) {
                log.info("Payment SUCCESS for orderCode: {}", orderCode);
                payOSService.handlePaymentSuccess(orderCode);
                return ResponseEntity.ok(Map.of("status", "success", "message", "Payment confirmed"));
            }

            log.info("Payment status: {} - {}", code, desc);
            return ResponseEntity.ok(Map.of("status", "received", "code", code));

        } catch (Exception e) {
            log.error("Error processing PayOS webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/create-from-cart")
    public ResponseEntity<ApiResponse<PayOSResponse>> createFromCart(
            @Valid @RequestBody CreateOrderFromCartRequest request) {
        try {
            log.info("=== CREATE PAYMENT FROM CART ===");
            log.info("Total Amount: {} VND", request.getTotalAmount());
            log.info("Items count: {}", request.getItems() != null ? request.getItems().size() : 0);

            PayOSResponse response = payOSService.createPaymentFromCart(request);

            log.info("=== CREATE PAYMENT SUCCESS ===");
            log.info("QRCode: {}", response.getQrCode());
            log.info("QRUrl: {}", response.getQrUrl());
            log.info("CheckoutUrl: {}", response.getCheckoutUrl());

            return ResponseEntity.ok(ApiResponse.success("Tạo mã QR thanh toán thành công", response));
        } catch (Exception e) {
            log.error("=== CREATE PAYMENT ERROR ===");
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi tạo mã QR: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{orderCode}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable Long orderCode) {
        try {
            log.info("=== GET PAYMENT STATUS ===");
            log.info("OrderCode: {}", orderCode);
            var result = payOSService.getPaymentStatusForFrontend(orderCode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting payment status: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "orderCode", orderCode,
                "paymentStatus", "UNKNOWN",
                "orderStatus", "UNKNOWN"
            ));
        }
    }

    @PostMapping("/confirm-payment/{orderCode}")
    public ResponseEntity<ApiResponse<PayOSResponse>> confirmPayment(@PathVariable Long orderCode) {
        try {
            log.info("=== CONFIRM PAYMENT REQUEST ===");
            log.info("OrderCode: {}", orderCode);

            PayOSResponse response = payOSService.confirmPayment(orderCode);

            return ResponseEntity.ok(ApiResponse.success("Xác nhận thanh toán thành công!", response));
        } catch (Exception e) {
            log.error("Error confirming payment: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi xác nhận: " + e.getMessage()));
        }
    }
}
