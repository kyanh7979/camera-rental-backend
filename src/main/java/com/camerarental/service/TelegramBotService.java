package com.camerarental.service;

import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.RentalOrderItem;
import com.camerarental.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

/**
 * Telegram Bot Service for LensRent Admin Notifications
 * 
 * FIXED:
 * - UTF-8 encoding throughout
 * - Message splitting for long messages
 * - Centralized sendReply method
 * - Full error handling
 */
@Slf4j
@Service
public class TelegramBotService {

    private static final int MAX_MESSAGE_LENGTH = 3500;
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter D_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.chat-id:}")
    private String chatId;

    private final RestTemplate restTemplate;

    public TelegramBotService() {
        this.restTemplate = new RestTemplate();
        // Configure UTF-8 encoding
        this.restTemplate.getMessageConverters().removeIf(c -> c instanceof StringHttpMessageConverter);
        this.restTemplate.getMessageConverters().add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    // ==================== MAIN SEND METHODS ====================

    /**
     * Send reply to specific chat - auto splits if too long
     */
    public void sendReply(String targetChatId, String message) {
        if (targetChatId == null || targetChatId.isBlank()) {
            log.warn("[TELEGRAM] Cannot send: targetChatId is null/blank");
            return;
        }
        if (message == null || message.isBlank()) {
            log.warn("[TELEGRAM] Cannot send: message is null/blank");
            return;
        }
        if (botToken == null || botToken.isBlank()) {
            log.error("[TELEGRAM] Cannot send: botToken not configured");
            return;
        }

        if (message.length() <= MAX_MESSAGE_LENGTH) {
            sendSingleMessage(targetChatId, message);
        } else {
            sendLongMessage(targetChatId, message);
        }
    }

    /**
     * Send message to admin chat
     */
    public void sendMessage(String message) {
        sendReply(chatId, message);
    }

    /**
     * Send long message - splits into multiple parts
     */
    private void sendLongMessage(String targetChatId, String message) {
        log.info("[TELEGRAM] Message length {} exceeds {}, splitting...", message.length(), MAX_MESSAGE_LENGTH);
        
        String[] parts = splitMessage(message);
        log.info("[TELEGRAM] Split into {} parts", parts.length);

        for (int i = 0; i < parts.length; i++) {
            String header = parts.length > 1 ? "(" + (i + 1) + "/" + parts.length + ")\n" : "";
            sendSingleMessage(targetChatId, header + parts[i]);
            log.info("[TELEGRAM] Sent part {}/{}", i + 1, parts.length);

            if (i < parts.length - 1) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Split message at safe boundaries
     */
    private String[] splitMessage(String message) {
        int msgLen = message.length();
        int partCount = (int) Math.ceil((double) msgLen / MAX_MESSAGE_LENGTH);
        String[] parts = new String[partCount];

        int start = 0;
        for (int i = 0; i < partCount; i++) {
            int end = start + MAX_MESSAGE_LENGTH;

            if (end >= msgLen) {
                parts[i] = message.substring(start);
                break;
            }

            // Find last newline before end
            int splitPoint = message.lastIndexOf('\n', end);
            if (splitPoint <= start) {
                splitPoint = findSafeSplitPoint(message, start, end);
            }

            parts[i] = message.substring(start, splitPoint);
            start = splitPoint;
        }

        return parts;
    }

    /**
     * Find safe split point (not in middle of emoji/unicode)
     */
    private int findSafeSplitPoint(String text, int start, int end) {
        for (int i = end; i > start + 50; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r') {
                return i;
            }
        }
        return Math.min(end, text.length());
    }

    /**
     * Send single message to Telegram API with HTML format
     */
    private void sendSingleMessage(String targetChatId, String message) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", targetChatId);
            body.put("text", message);
            body.put("parse_mode", "HTML");  // Enable HTML formatting

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, request, String.class);
            log.info("[TELEGRAM] Message sent to chat {}", targetChatId);

        } catch (Exception e) {
            log.error("[TELEGRAM] Failed to send message to {}: {}", targetChatId, e.getMessage(), e);
        }
    }

    // ==================== NOTIFICATION METHODS ====================

    /**
     * New user registration notification
     */
    public void sendNewUserNotification(User user) {
        if (user == null) {
            log.warn("[TELEGRAM] Cannot send new user notification: user is null");
            return;
        }

        String msg = String.format("""
            
            <b>Người dùng mới đăng ký</b>
            
            👤 <b>Tên:</b> %s
            📧 <b>Email:</b> %s
            📱 <b>SDT:</b> %s
            🕒 <b>Thời gian:</b> %s
            
            """,
            safeStr(user.getFullName()),
            safeStr(user.getEmail()),
            safeStr(user.getPhone()),
            now()
        );

        sendMessage(msg);
    }

    /**
     * New order notification
     */
    public void sendNewOrderNotification(RentalOrder order) {
        if (order == null) {
            log.warn("[TELEGRAM] Cannot send new order notification: order is null");
            return;
        }

        User user = order.getUser();
        String customerName = user != null ? safeStr(user.getFullName()) : "N/A";
        String customerEmail = user != null ? safeStr(user.getEmail()) : "N/A";
        String customerPhone = user != null ? safeStr(user.getPhone()) : "N/A";

        StringBuilder products = new StringBuilder();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (RentalOrderItem item : order.getItems()) {
                String cameraName = "N/A";
                if (item.getCamera() != null) {
                    cameraName = safeStr(item.getCamera().getName());
                }
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                products.append("  • ").append(cameraName).append(" x").append(qty).append("\n");
            }
        } else {
            products.append("  (không có sản phẩm)\n");
        }

        String startDate = order.getStartDate() != null ? order.getStartDate().format(D_FORMAT) : "N/A";
        String endDate = order.getEndDate() != null ? order.getEndDate().format(D_FORMAT) : "N/A";

        int rentalDays = 0;
        if (order.getStartDate() != null && order.getEndDate() != null) {
            rentalDays = (int) java.time.temporal.ChronoUnit.DAYS.between(order.getStartDate(), order.getEndDate());
        }

        String totalAmount = formatCurrency(order.getTotalAmount());
        String paymentStatus = order.getPaymentStatus() != null ? order.getPaymentStatus().toString() : "N/A";
        String orderStatus = order.getStatus() != null ? order.getStatus().toString() : "N/A";

        String msg = String.format("""
            
            🆕 <b>ĐƠN THUÊ MỚI</b>
            
            🆔 <b>Mã đơn:</b> %s
            👤 <b>Khách hàng:</b> %s
            📱 <b>SDT:</b> %s
            📧 <b>Email:</b> %s
            
            📷 <b>Sản phẩm:</b>
            %s
            📅 <b>Ngày nhận:</b> %s
            📅 <b>Ngày trả:</b> %s
            📆 <b>Số ngày thuê:</b> %d ngày
            💰 <b>Tổng tiền:</b> %s
            💳 <b>Thanh toán:</b> %s
            📌 <b>Trạng thái:</b> %s
            
            """,
            safeStr(order.getOrderCode()),
            customerName,
            customerPhone,
            customerEmail,
            products.toString().trim(),
            startDate,
            endDate,
            rentalDays,
            totalAmount,
            paymentStatus,
            orderStatus
        );

        sendMessage(msg);
    }

    /**
     * Payment success notification
     */
    public void sendPaymentSuccessNotification(RentalOrder order) {
        if (order == null) {
            log.warn("[TELEGRAM] Cannot send payment notification: order is null");
            return;
        }

        User user = order.getUser();
        String customerName = user != null ? safeStr(user.getFullName()) : "N/A";
        String totalAmount = formatCurrency(order.getTotalAmount());

        String msg = String.format("""
            
            ✅ <b>THANH TOÁN THÀNH CÔNG</b>
            
            🆔 <b>Mã đơn:</b> %s
            👤 <b>Khách hàng:</b> %s
            💰 <b>Số tiền:</b> %s
            💳 <b>Phương thức:</b> PayOS
            🕒 <b>Thời gian:</b> %s
            
            """,
            safeStr(order.getOrderCode()),
            customerName,
            totalAmount,
            now()
        );

        sendMessage(msg);
    }

    /**
     * Payment completed notification (backward compatible)
     */
    public void notifyPaymentCompleted(String orderCode, String paymentMethod, String amount) {
        String formattedAmount = formatCurrencyAmount(amount);
        String methodDisplay = paymentMethod != null ? paymentMethod : "PayOS";

        String msg = String.format("""
            
            ✅ <b>THANH TOÁN THÀNH CÔNG</b>
            
            🆔 <b>Mã đơn:</b> %s
            💰 <b>Số tiền:</b> %s
            💳 <b>Phương thức:</b> %s
            🕒 <b>Thời gian:</b> %s
            
            """,
            safeStr(orderCode),
            formattedAmount,
            methodDisplay,
            now()
        );

        sendMessage(msg);
    }

    /**
     * Payment failed notification
     */
    public void sendPaymentFailedNotification(RentalOrder order, String reason) {
        if (order == null) {
            log.warn("[TELEGRAM] Cannot send payment failed notification: order is null");
            return;
        }

        User user = order.getUser();
        String customerName = user != null ? safeStr(user.getFullName()) : "N/A";
        String totalAmount = formatCurrency(order.getTotalAmount());
        String reasonText = reason != null && !reason.isBlank() ? reason : "Không xác định";

        String msg = String.format("""
            
            ❌ <b>THANH TOÁN THẤT BẠI</b>
            
            🆔 <b>Mã đơn:</b> %s
            👤 <b>Khách hàng:</b> %s
            💰 <b>Số tiền:</b> %s
            🕒 <b>Thời gian:</b> %s
            ⚠️ <b>Lý do:</b> %s
            
            """,
            safeStr(order.getOrderCode()),
            customerName,
            totalAmount,
            now(),
            reasonText
        );

        sendMessage(msg);
    }

    /**
     * Order due soon notification
     */
    public void sendOrderDueSoonNotification(RentalOrder order, int hoursRemaining) {
        if (order == null) {
            log.warn("[TELEGRAM] Cannot send due soon notification: order is null");
            return;
        }

        User user = order.getUser();
        String customerName = user != null ? safeStr(user.getFullName()) : "N/A";
        String endDate = order.getEndDate() != null ? order.getEndDate().format(D_FORMAT) : "N/A";

        StringBuilder products = new StringBuilder();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (RentalOrderItem item : order.getItems()) {
                String cameraName = "N/A";
                if (item.getCamera() != null) {
                    cameraName = safeStr(item.getCamera().getName());
                }
                products.append("  • ").append(cameraName).append("\n");
            }
        } else {
            products.append("  (không có sản phẩm)\n");
        }

        String icon = hoursRemaining <= 1 ? "🚨" : "⚠️";

        String msg = String.format("""
            
            %s <b>ĐƠN THUÊ SẮP ĐẾN HẠN TRẢ</b>
            
            🆔 <b>Mã đơn:</b> %s
            👤 <b>Khách hàng:</b> %s
            📷 <b>Sản phẩm:</b>
            %s
            📅 <b>Ngày trả:</b> %s
            ⏰ <b>Còn lại:</b> %d giờ
            
            """,
            icon,
            safeStr(order.getOrderCode()),
            customerName,
            products.toString().trim(),
            endDate,
            hoursRemaining
        );

        sendMessage(msg);
    }

    /**
     * Order overdue notification
     */
    public void sendOrderOverdueNotification(RentalOrder order, long daysOverdue, long hoursOverdue) {
        if (order == null) {
            log.warn("[TELEGRAM] Cannot send overdue notification: order is null");
            return;
        }

        User user = order.getUser();
        String customerName = user != null ? safeStr(user.getFullName()) : "N/A";
        String customerPhone = user != null ? safeStr(user.getPhone()) : "N/A";
        String endDate = order.getEndDate() != null ? order.getEndDate().format(D_FORMAT) : "N/A";

        StringBuilder products = new StringBuilder();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (RentalOrderItem item : order.getItems()) {
                String cameraName = "N/A";
                if (item.getCamera() != null) {
                    cameraName = safeStr(item.getCamera().getName());
                }
                products.append("  • ").append(cameraName).append("\n");
            }
        } else {
            products.append("  (không có sản phẩm)\n");
        }

        String overdueText = daysOverdue > 0 ? daysOverdue + " ngày" : hoursOverdue + " giờ";

        String msg = String.format("""
            
            🚨 <b>ĐƠN THUÊ QUÁ HẠN</b>
            
            🆔 <b>Mã đơn:</b> %s
            👤 <b>Khách hàng:</b> %s
            📱 <b>SDT:</b> %s
            📷 <b>Sản phẩm:</b>
            %s
            📅 <b>Ngày trả:</b> %s
            ⚠️ <b>Quá hạn:</b> %s
            
            """,
            safeStr(order.getOrderCode()),
            customerName,
            customerPhone,
            products.toString().trim(),
            endDate,
            overdueText
        );

        sendMessage(msg);
    }

    /**
     * Order confirmed notification
     */
    public void sendOrderConfirmedNotification(RentalOrder order) {
        if (order == null) {
            log.warn("[TELEGRAM] Cannot send confirmed notification: order is null");
            return;
        }

        User user = order.getUser();
        String customerName = user != null ? safeStr(user.getFullName()) : "N/A";
        String startDate = order.getStartDate() != null ? order.getStartDate().format(D_FORMAT) : "N/A";
        String endDate = order.getEndDate() != null ? order.getEndDate().format(D_FORMAT) : "N/A";
        String totalAmount = formatCurrency(order.getTotalAmount());

        String msg = String.format("""
            
            ✅ <b>ĐƠN THUÊ ĐÃ XÁC NHẬN</b>
            
            🆔 <b>Mã đơn:</b> %s
            👤 <b>Khách hàng:</b> %s
            📅 <b>Ngày nhận:</b> %s
            📅 <b>Ngày trả:</b> %s
            💰 <b>Tổng tiền:</b> %s
            
            """,
            safeStr(order.getOrderCode()),
            customerName,
            startDate,
            endDate,
            totalAmount
        );

        sendMessage(msg);
    }

    /**
     * Low stock warning
     */
    public void sendLowStockNotification(String cameraName, int available) {
        String msg = String.format("""
            
            ⚠️ <b>CẢNH BÁO TỒN KHO THẤP</b>
            
            📷 <b>Sản phẩm:</b> %s
            📦 <b>Còn lại:</b> %d cái
            
            """,
            safeStr(cameraName),
            available
        );

        sendMessage(msg);
    }

    // ==================== UTILITY METHODS ====================

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
            && chatId != null && !chatId.isBlank();
    }

    public String getAdminChatId() {
        return chatId;
    }

    private String safeStr(String value) {
        return (value != null && !value.isBlank()) ? value : "N/A";
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 VND";
        try {
            return String.format("%,d VND", amount.longValue()).replace(",", ".");
        } catch (Exception e) {
            return amount.toPlainString() + " VND";
        }
    }

    private String formatCurrencyAmount(String amount) {
        if (amount == null) return "0 VND";
        try {
            long value = Long.parseLong(amount.replace(".", "").replace(",", ""));
            return String.format("%,d VND", value).replace(",", ".");
        } catch (Exception e) {
            return amount + " VND";
        }
    }

    private String now() {
        return LocalDateTime.now().format(DT_FORMAT);
    }
}
