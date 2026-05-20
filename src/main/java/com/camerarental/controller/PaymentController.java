package com.camerarental.controller;

import com.camerarental.dto.request.PaymentRequest;
import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.PaymentResponse;
import com.camerarental.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment created", paymentService.createPayment(request)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByOrderId(orderId)));
    }

    @PutMapping("/{id}/confirm-bank-transfer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmBankTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Bank transfer confirmed",
                paymentService.confirmBankTransfer(id)));
    }

    @PostMapping("/momo/callback")
    public ResponseEntity<ApiResponse<Void>> momoCallback(@RequestBody Map<String, Object> body) {
        String orderId = (String) body.get("orderId");
        String transactionId = (String) body.get("transId");
        int resultCode = (int) body.get("resultCode");
        paymentService.processMomoCallback(orderId, transactionId, resultCode);
        return ResponseEntity.ok(ApiResponse.success("Callback processed", null));
    }
}
