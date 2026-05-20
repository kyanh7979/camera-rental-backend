package com.camerarental.controller;

import com.camerarental.service.PayOSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PayOSWebhookController {

    private final PayOSService payOSService;

    /**
     * Primary webhook endpoint - PayOS dashboard URL
     * POST /api/payment/payos/webhook
     */
    @PostMapping("/payos/webhook")
    public ResponseEntity<Map<String, Object>> handlePayOSWebhook(@RequestBody Map<String, Object> payload) {
        log.info("📥 PayOS webhook received at /api/payment/payos/webhook: {}", payload);
        return processWebhook(payload);
    }

    /**
     * Alias webhook endpoint - some PayOS integrations call this
     * POST /api/payment/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhookAlias(@RequestBody Map<String, Object> payload) {
        log.info("📥 PayOS webhook received at /api/payment/webhook: {}", payload);
        return processWebhook(payload);
    }

    /**
     * Get payment status for frontend polling
     * GET /api/payment/payos/status/{orderCode}
     */
    @GetMapping("/payos/status/{orderCode}")
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

    /**
     * Test endpoint to verify webhook controller is working
     * GET /api/payment/payos/test
     */
    @GetMapping("/payos/test")
    public ResponseEntity<Map<String, String>> testEndpoint() {
        log.info("✅ PayOSWebhookController test endpoint called!");
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "PayOSWebhookController is working!"
        ));
    }

    /**
     * Shared webhook processing logic for both endpoints
     */
    private ResponseEntity<Map<String, Object>> processWebhook(Map<String, Object> payload) {
        try {
            log.info("📥 Full webhook payload: {}", payload);

            String code = payload.get("code") != null ? String.valueOf(payload.get("code")) : null;
            String desc = payload.get("desc") != null ? String.valueOf(payload.get("desc")) : null;

            // Extract orderCode - PayOS sends orderCode nested inside data
            Long orderCode = null;

            // Try payload.data.orderCode first (PayOS format)
            Object dataObj = payload.get("data");
            if (dataObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) dataObj;
                Object rawOrderCode = data.get("orderCode");
                if (rawOrderCode != null) {
                    try {
                        orderCode = Long.parseLong(rawOrderCode.toString());
                        log.info("📋 Extracted orderCode from data.orderCode: {}", orderCode);
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse orderCode from data.orderCode: {}", rawOrderCode);
                    }
                }
            }

            // Fallback: try payload.orderCode
            if (orderCode == null && payload.get("orderCode") != null) {
                try {
                    orderCode = Long.parseLong(String.valueOf(payload.get("orderCode")));
                    log.info("📋 Extracted orderCode from root: {}", orderCode);
                } catch (NumberFormatException e) {
                    log.warn("Cannot parse orderCode from root: {}", payload.get("orderCode"));
                }
            }

            log.info("📋 Webhook parsed - code: {}, desc: {}, extracted orderCode: {}", code, desc, orderCode);

            // If missing orderCode, return 400 error
            if (orderCode == null) {
                log.warn("⚠️ Missing orderCode in webhook payload");
                return ResponseEntity.badRequest().body(Map.of(
                    "error", 1,
                    "message", "missing orderCode"
                ));
            }

            // Check for successful payment
            boolean paymentSuccess = "00".equals(code) || Boolean.TRUE.equals(payload.get("success"));

            if (paymentSuccess) {
                log.info("✅ Payment SUCCESS detected for orderCode: {}", orderCode);
                boolean success = payOSService.handlePaymentSuccess(orderCode);

                if (success) {
                    log.info("🎉 Payment processed successfully for order: {}", orderCode);
                } else {
                    log.info("ℹ️ Payment already processed or order not found for orderCode: {}", orderCode);
                }

                // Always return success to PayOS - no complex response
                return ResponseEntity.ok(Map.of(
                    "error", 0,
                    "message", "success"
                ));
            }

            // Payment not successful (e.g., cancelled, failed)
            log.info("ℹ️ Payment status update: {} - {}", code, desc);
            return ResponseEntity.ok(Map.of(
                "error", 0,
                "message", "success"
            ));

        } catch (Exception e) {
            log.error("❌ Error processing payment webhook: {}", e.getMessage(), e);
            // Return success to prevent PayOS from retrying
            return ResponseEntity.ok(Map.of(
                "error", 0,
                "message", "success"
            ));
        }
    }
}
