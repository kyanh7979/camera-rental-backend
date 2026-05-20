package com.camerarental.service;

import com.camerarental.dto.request.PaymentRequest;
import com.camerarental.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    PaymentResponse getPaymentByOrderId(Long orderId);

    PaymentResponse confirmBankTransfer(Long paymentId);

    PaymentResponse processMomoCallback(String orderId, String transactionId, int resultCode);
}
