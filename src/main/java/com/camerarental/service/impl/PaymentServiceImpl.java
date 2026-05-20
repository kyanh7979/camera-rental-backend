package com.camerarental.service.impl;

import com.camerarental.dto.request.PaymentRequest;
import com.camerarental.dto.response.PaymentResponse;
import com.camerarental.entity.Payment;
import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.entity.enums.PaymentMethod;
import com.camerarental.entity.enums.PaymentStatus;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.PaymentRepository;
import com.camerarental.repository.RentalOrderRepository;
import com.camerarental.service.PaymentService;
import com.camerarental.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RentalOrderRepository orderRepository;
    private final TelegramBotService telegramBotService;

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        RentalOrder order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));

        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            log.warn("Attempt to create duplicate payment for orderId={}", order.getId());
            throw new BadRequestException("Payment already exists for this order");
        }

        Payment payment = Payment.builder()
                .order(order)
                .method(request.getMethod())
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .build();

        if (request.getMethod() == PaymentMethod.MOMO) {
            // Mock: generate a fake Momo transaction URL / ID
            payment.setTransactionId("MOMO-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            payment.setStatus(PaymentStatus.PROCESSING);
            payment.setPaymentData("{\"payUrl\": \"https://test-payment.momo.vn/pay/mock\"}");
        } else {
            payment.setTransactionId("BANK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        }

        Payment saved = paymentRepository.save(payment);
        log.info("Payment created: id={}, orderId={}, method={}, amount={}",
                saved.getId(), order.getId(), saved.getMethod(), saved.getAmount());
        return PaymentResponse.fromEntity(saved);
    }

    @Override
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional
    public PaymentResponse confirmBankTransfer(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            log.warn("confirmBankTransfer called for non-bank payment id={}, method={}",
                    payment.getId(), payment.getMethod());
            throw new BadRequestException("This payment is not a bank transfer");
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());

        RentalOrder order = payment.getOrder();
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        telegramBotService.sendPaymentSuccessNotification(order);

        Payment saved = paymentRepository.save(payment);
        log.info("Bank transfer confirmed: paymentId={}, orderCode={}, amount={}",
                saved.getId(), order.getOrderCode(), saved.getAmount());
        return PaymentResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public PaymentResponse processMomoCallback(String orderId, String transactionId, int resultCode) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "transactionId", transactionId));

        if (resultCode == 0) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());

            RentalOrder order = payment.getOrder();
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            telegramBotService.notifyPaymentCompleted(
                    order.getOrderCode(), payment.getMethod().name(), payment.getAmount().toPlainString());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Momo callback failed: transactionId={}, resultCode={}", transactionId, resultCode);
        }

        Payment saved = paymentRepository.save(payment);
        log.info("Momo callback processed: paymentId={}, status={}", saved.getId(), saved.getStatus());
        return PaymentResponse.fromEntity(saved);
    }
}
