package com.camerarental.service;

import com.camerarental.config.PayOSConfig;
import com.camerarental.dto.request.CreateOrderFromCartRequest;
import com.camerarental.dto.response.PayOSResponse;
import com.camerarental.entity.Camera;
import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.RentalOrderItem;
import com.camerarental.entity.User;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.entity.enums.PaymentStatus;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.CameraRepository;
import com.camerarental.repository.RentalOrderRepository;
import com.camerarental.repository.UserRepository;
import com.camerarental.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSService {

    private final PayOSConfig payOSConfig;
    private final RentalOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final TelegramBotService telegramBotService;

    private PayOS payOSClient;

    private PayOS getPayOSClient() {
        if (payOSClient == null) {
            payOSClient = new PayOS(
                    payOSConfig.getClientId(),
                    payOSConfig.getApiKey(),
                    payOSConfig.getChecksumKey()
            );
        }
        return payOSClient;
    }

    public PayOSResponse createPayment(int amount, String description) throws Exception {
        if (amount < 1000) {
            throw new IllegalArgumentException("Amount must be >= 1000 VND");
        }

        long orderCode = System.currentTimeMillis();

        log.info("=== PayOS CREATE PAYMENT ===");
        log.info("OrderCode: {}", orderCode);
        log.info("Amount: {} VND", amount);
        log.info("Description: {}", description);
        log.info("Return URL: {}", payOSConfig.getReturnUrl());
        log.info("Cancel URL: {}", payOSConfig.getCancelUrl());

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount((long) amount)
                .description(description)
                .returnUrl(payOSConfig.getReturnUrl())
                .cancelUrl(payOSConfig.getCancelUrl())
                .build();

        log.info("Request: orderCode={}, amount={}, description={}", 
                orderCode, amount, description);

        try {
            CreatePaymentLinkResponse response = getPayOSClient().paymentRequests().create(request);

            log.info("=== PayOS RESPONSE SUCCESS ===");
            log.info("QR Code URL: {}", response.getQrCode());
            log.info("Checkout URL: {}", response.getCheckoutUrl());
            log.info("Payment Link ID: {}", response.getPaymentLinkId());
            log.info("Status: {}", response.getStatus());

            return PayOSResponse.builder()
                    .qrCode(response.getQrCode())
                    .qrUrl(response.getQrCode())
                    .checkoutUrl(response.getCheckoutUrl())
                    .paymentLink(response.getCheckoutUrl())
                    .orderCode(response.getOrderCode())
                    .amount(response.getAmount())
                    .status(response.getStatus() != null ? response.getStatus().toString() : null)
                    .build();

        } catch (PayOSException e) {
            log.error("=== PayOS ERROR ===");
            log.error("Error Message: {}", e.getMessage());
            log.error("Error toString: {}", e.toString());
            log.error("Full error: ", e);
            throw new Exception("PayOS Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("=== UNEXPECTED ERROR ===");
            log.error("Error: {}", e.getMessage(), e);
            throw new Exception("Failed to create payment: " + e.getMessage(), e);
        }
    }

    @Transactional
    public PayOSResponse createPaymentFromCart(CreateOrderFromCartRequest request) throws Exception {
        log.info("=== CREATE PAYMENT FROM CART ===");
        log.info("Total Amount: {} VND", request.getTotalAmount());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        log.info("Current user email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());

        if (!endDate.isAfter(startDate)) {
            throw new BadRequestException("End date must be after start date");
        }

        int rentalDays = (int) ChronoUnit.DAYS.between(startDate, endDate);
        if (rentalDays <= 0) {
            rentalDays = 1;
        }

        long orderCode = System.currentTimeMillis();

        RentalOrder order = RentalOrder.builder()
                .orderCode("ORD-" + orderCode)
                .user(user)
                .startDate(startDate)
                .endDate(endDate)
                .note(request.getNote())
                .items(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderFromCartRequest.CartItemRequest itemReq : request.getItems()) {
            Camera camera = cameraRepository.findById(itemReq.getCameraId())
                    .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", itemReq.getCameraId()));

            if (camera.getAvailable() < itemReq.getQuantity()) {
                log.warn("Insufficient stock for camera id={}, name={}, requested={}, available={}",
                        camera.getId(), camera.getName(), itemReq.getQuantity(), camera.getAvailable());
                throw new BadRequestException("Camera '" + camera.getName()
                        + "' does not have enough stock. Available: " + camera.getAvailable());
            }

            BigDecimal subtotal = camera.getDailyPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getRentalDays()))
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            RentalOrderItem item = RentalOrderItem.builder()
                    .order(order)
                    .camera(camera)
                    .quantity(itemReq.getQuantity())
                    .rentalDays(itemReq.getRentalDays())
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(item);
            total = total.add(subtotal);

            camera.setAvailable(camera.getAvailable() - itemReq.getQuantity());
            cameraRepository.save(camera);
        }

        order.setTotalAmount(total);
        RentalOrder savedOrder = orderRepository.save(order);
        log.info("Order created: id={}, code={}, userId={}, totalAmount={}",
                savedOrder.getId(), savedOrder.getOrderCode(), user.getId(), total);

        String description = "Thue camera"; // PayOS giới hạn 25 ký tự
        int amount = total.intValue();

        CreatePaymentLinkRequest payosRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount((long) amount)
                .description(description)
                .returnUrl(payOSConfig.getReturnUrl())
                .cancelUrl(payOSConfig.getCancelUrl())
                .build();

        log.info("Creating PayOS payment link: orderCode={}, amount={}", orderCode, amount);

        try {
            CreatePaymentLinkResponse response = getPayOSClient().paymentRequests().create(payosRequest);

            log.info("=== PayOS RESPONSE SUCCESS ===");
            log.info("QR Code URL: {}", response.getQrCode());
            log.info("Checkout URL: {}", response.getCheckoutUrl());
            log.info("Status: {}", response.getStatus());

            return PayOSResponse.builder()
                    .id(savedOrder.getId())
                    .qrCode(response.getQrCode())
                    .qrUrl(response.getQrCode())
                    .checkoutUrl(response.getCheckoutUrl())
                    .paymentLink(response.getCheckoutUrl())
                    .orderCode(response.getOrderCode())
                    .amount(response.getAmount())
                    .status(response.getStatus() != null ? response.getStatus().toString() : null)
                    .build();

        } catch (PayOSException e) {
            log.error("=== PayOS ERROR ===");
            log.error("Error Message: {}", e.getMessage());
            throw new Exception("PayOS Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("=== UNEXPECTED ERROR ===");
            log.error("Error: {}", e.getMessage(), e);
            throw new Exception("Failed to create payment: " + e.getMessage(), e);
        }
    }

    public PayOSResponse getPaymentStatus(Long orderCode) throws Exception {
        log.info("=== GET PAYMENT STATUS ===");
        log.info("OrderCode: {}", orderCode);

        try {
            // Find order from database
            RentalOrder order = orderRepository.findByOrderCode("ORD-" + orderCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", "ORD-" + orderCode));

            log.info("Order found: status={}", order.getStatus());

            return PayOSResponse.builder()
                    .orderCode(orderCode)
                    .amount(order.getTotalAmount().longValue())
                    .status(order.getStatus().toString())
                    .build();

        } catch (ResourceNotFoundException e) {
            log.error("Order not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error getting payment status: {}", e.getMessage(), e);
            throw new Exception("Lỗi kiểm tra trạng thái: " + e.getMessage());
        }
    }

    @Transactional
    public PayOSResponse confirmPayment(Long orderCode) {
        log.info("=== CONFIRM PAYMENT ===");
        log.info("OrderCode: ORD-{}", orderCode);

        RentalOrder order = orderRepository.findByOrderCode("ORD-" + orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", "ORD-" + orderCode));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} is not PENDING, current status: {}", order.getOrderCode(), order.getStatus());
            return PayOSResponse.builder()
                    .orderCode(orderCode)
                    .amount(order.getTotalAmount().longValue())
                    .status(order.getStatus().toString())
                    .build();
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        orderRepository.save(order);

        // Gửi thông báo thanh toán thành công cho admin
        try {
            telegramBotService.sendPaymentSuccessNotification(order);
        } catch (Exception e) {
            log.warn("Failed to send payment success notification: {}", e.getMessage());
        }

        log.info("Order {} confirmed as PAID with COMPLETED payment status", order.getOrderCode());

        return PayOSResponse.builder()
                .orderCode(orderCode)
                .amount(order.getTotalAmount().longValue())
                .status("PAID")
                .build();
    }

    @Transactional
    public boolean handlePaymentSuccess(Long orderCode) {
        log.info("=== HANDLING PAYMENT SUCCESS ===");
        log.info("OrderCode: {}", orderCode);

        try {
            RentalOrder order = orderRepository.findByOrderCode("ORD-" + orderCode)
                    .orElse(null);

            if (order == null) {
                log.warn("⚠️ ORDER NOT FOUND: ORD-{}", orderCode);
                return false;
            }

            log.info("✅ Order found: {} - Current status: {}", order.getOrderCode(), order.getStatus());

            if (order.getStatus() == OrderStatus.PAID && order.getPaymentStatus() == PaymentStatus.COMPLETED) {
                log.info("⏭️ Order {} already PAID and COMPLETED, skipping update", order.getOrderCode());
                return false;
            }

            order.setStatus(OrderStatus.PAID);
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            orderRepository.save(order);
            log.info("Order {} updated: status=PAID, paymentStatus=COMPLETED", order.getOrderCode());

            // Gửi thông báo thanh toán thành công cho admin
            try {
                telegramBotService.sendPaymentSuccessNotification(order);
                log.info("Telegram payment success notification sent successfully");
            } catch (Exception e) {
                log.warn("Failed to send Telegram payment success notification: {}", e.getMessage());
            }

            log.info("Payment success handled for order: {}", order.getOrderCode());
            return true;

        } catch (Exception e) {
            log.error("❌ ERROR handling payment success for order {}: {}", orderCode, e.getMessage(), e);
            return false;
        }
    }

    public Map<String, Object> getPaymentStatusForFrontend(Long orderCode) {
        log.info("=== GET PAYMENT STATUS FOR FRONTEND ===");
        log.info("OrderCode: {}", orderCode);

        try {
            // Use JOIN FETCH to avoid LazyInitializationException
            RentalOrder order = orderRepository.findByOrderCodeWithUser("ORD-" + orderCode)
                    .orElse(null);

            if (order == null) {
                log.warn("⚠️ Order not found: ORD-{}", orderCode);
                Map<String, Object> result = new HashMap<>();
                result.put("orderCode", "ORD-" + orderCode);
                result.put("status", "NOT_FOUND");
                result.put("paymentStatus", "NOT_FOUND");
                result.put("success", false);
                return result;
            }

            log.info("✅ Order found: {} - Status: {}", order.getOrderCode(), order.getStatus());

            User user = order.getUser();
            String customerName = (user != null) ? user.getFullName() : "";
            String customerEmail = (user != null) ? user.getEmail() : "";
            String customerPhone = (user != null && user.getPhone() != null) ? user.getPhone() : "";

            LocalDate startDate = order.getStartDate();
            LocalDate endDate = order.getEndDate();

            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("orderCode", order.getOrderCode());
            paymentData.put("customerName", customerName);
            paymentData.put("customerEmail", customerEmail);
            paymentData.put("customerPhone", customerPhone);
            paymentData.put("amount", order.getTotalAmount());
            paymentData.put("status", order.getStatus().toString());
            paymentData.put("paymentStatus", order.getPaymentStatus().toString());
            paymentData.put("startDate", startDate != null ? startDate.toString() : "");
            paymentData.put("endDate", endDate != null ? endDate.toString() : "");
            paymentData.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : "");
            paymentData.put("success", true);

            log.info("📤 Payment status returned: paymentStatus={}, orderStatus={}",
                    order.getPaymentStatus(), order.getStatus());

            return paymentData;

        } catch (Exception e) {
            log.error("❌ Error getting payment status: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("orderCode", "ORD-" + orderCode);
            result.put("status", "ERROR");
            result.put("paymentStatus", "ERROR");
            result.put("success", false);
            return result;
        }
    }
}
