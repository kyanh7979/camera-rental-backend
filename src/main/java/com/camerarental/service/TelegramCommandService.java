package com.camerarental.service;

import com.camerarental.entity.Camera;
import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.RentalOrderItem;
import com.camerarental.entity.User;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.repository.CameraRepository;
import com.camerarental.repository.RentalOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Telegram Command Handler - FIXED FORMAT
 * Only message formatting changed, NOT logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramCommandService {

    private final TelegramBotService telegramBotService;
    private final RentalOrderRepository orderRepository;
    private final CameraRepository cameraRepository;

    @Value("${app.telegram.chat-id:}")
    private String adminChatId;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<OrderStatus> PAID_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.RENTING, OrderStatus.RETURNED, OrderStatus.COMPLETED
    );
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.CONFIRMED, OrderStatus.PAID, OrderStatus.RENTING
    );

    // ==================== MAIN ENTRY POINT ====================

    public void handleCommand(String text, String chatId) {
        if (text == null || text.isBlank()) {
            log.warn("[CMD] Empty command, ignored");
            return;
        }

        log.info("[CMD] Command '{}' from chatId={}", text, chatId);

        if (!isAuthorized(chatId)) {
            log.warn("[CMD] Unauthorized chatId={}", chatId);
            return;
        }

        String cmd = normalizeCommand(text.trim());
        log.info("[CMD] Processing: /{}", cmd);

        try {
            switch (cmd) {
                case "start" -> handleStart(chatId);
                case "help" -> handleHelp(chatId);
                case "status" -> handleStatus(chatId);
                case "orders" -> handleOrders(chatId);
                case "pending" -> handlePending(chatId);
                case "renting" -> handleRenting(chatId);
                case "overdue" -> handleOverdue(chatId);
                case "revenue" -> handleRevenue(chatId);
                case "lowstock" -> handleLowStock(chatId);
                default -> handleUnknown(chatId, cmd);
            }
            log.info("[CMD] /{} OK", cmd);
            
        } catch (Exception e) {
            log.error("[CMD] /{} FAILED: {}", cmd, e.getMessage(), e);
            sendError(chatId, cmd, e.getMessage());
        }
    }

    // ==================== COMMAND HANDLERS ====================

    private void handleStart(String chatId) {
        String msg = """
            
            👋 <b>Chào Admin!</b>
            
            Bot quản lý hệ thống cho thuê máy ảnh LensRent.
            Gửi /help để xem danh sách lệnh.
            
            """;
        telegramBotService.sendReply(chatId, msg);
    }

    private void handleHelp(String chatId) {
        String msg = """
            
            🛠️ <b>DANH SÁCH LỆNH ADMIN</b>
            
            /start    Khởi động bot
            /help     Xem danh sách lệnh
            /status   Trạng thái hệ thống
            /orders   5 đơn mới nhất
            /pending  Đơn chờ xác nhận
            /renting  Đơn đang thuê
            /overdue  Đơn quá hạn
            /revenue  Doanh thu
            /lowstock Tồn kho thấp
            
            """;
        telegramBotService.sendReply(chatId, msg);
    }

    private void handleStatus(String chatId) {
        String time = LocalDateTime.now(TZ).format(DT_FMT);
        boolean dbOk = checkDb();
        boolean tgOk = telegramBotService.isConfigured();

        String dbIcon = dbOk ? "🟢" : "🔴";
        String tgIcon = tgOk ? "🟢" : "🔴";

        String msg = String.format("""
            
            🟢 <b>TRẠNG THÁI HỆ THỐNG</b>
            
            🖥️ Backend:   <b>ONLINE</b>
            💾 Database:  %s %s
            📱 Telegram:  %s %s
            🕒 Thời gian: %s
            
            """,
            dbIcon, dbOk ? "ONLINE" : "OFFLINE",
            tgIcon, tgOk ? "READY" : "NOT_CONFIG",
            time
        );
        telegramBotService.sendReply(chatId, msg);
    }

    @Transactional(readOnly = true)
    public void handleOrders(String chatId) {
        try {
            List<RentalOrder> orders = orderRepository.findTop5OrdersWithDetailsForTelegram();
            log.info("[CMD] /orders: {} orders", listSize(orders));

            if (isEmpty(orders)) {
                telegramBotService.sendReply(chatId, "\n📦 <b>Chưa có đơn nào.</b>\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n📦 <b>ĐƠN MỚI NHẤT</b>\n\n");

            for (int i = 0; i < orders.size(); i++) {
                sb.append(formatOrder(orders.get(i), i + 1));
                sb.append("\n");
            }

            telegramBotService.sendReply(chatId, sb.toString());
            log.info("[CMD] /orders: sent {} chars", sb.length());

        } catch (Exception e) {
            log.error("[CMD] /orders FAILED: {}", e.getMessage(), e);
            sendError(chatId, "orders", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void handlePending(String chatId) {
        try {
            List<RentalOrder> orders = orderRepository.findTop5PendingOrdersForTelegram(OrderStatus.PENDING);
            log.info("[CMD] /pending: {} orders", listSize(orders));

            if (isEmpty(orders)) {
                telegramBotService.sendReply(chatId, "\n⏳ <b>Không có đơn chờ xác nhận.</b>\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n⏳ <b>ĐƠN CHỜ XÁC NHẬN</b> (").append(orders.size()).append(" đơn)\n\n");

            for (int i = 0; i < orders.size(); i++) {
                sb.append(formatOrder(orders.get(i), i + 1));
                sb.append("\n");
            }

            telegramBotService.sendReply(chatId, sb.toString());

        } catch (Exception e) {
            log.error("[CMD] /pending FAILED: {}", e.getMessage(), e);
            sendError(chatId, "pending", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void handleRenting(String chatId) {
        try {
            List<RentalOrder> orders = orderRepository.findTop5OrdersByStatusForTelegram(OrderStatus.RENTING);
            log.info("[CMD] /renting: {} orders", listSize(orders));

            if (isEmpty(orders)) {
                telegramBotService.sendReply(chatId, "\n📷 <b>Không có đơn đang thuê.</b>\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n📷 <b>ĐƠN ĐANG THUÊ</b> (").append(orders.size()).append(" đơn)\n\n");

            for (int i = 0; i < orders.size(); i++) {
                sb.append(formatRenting(orders.get(i), i + 1));
                sb.append("\n");
            }

            telegramBotService.sendReply(chatId, sb.toString());

        } catch (Exception e) {
            log.error("[CMD] /renting FAILED: {}", e.getMessage(), e);
            sendError(chatId, "renting", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void handleOverdue(String chatId) {
        try {
            LocalDate today = LocalDate.now();
            List<RentalOrder> orders = orderRepository.findTop5OverdueOrdersForTelegram(ACTIVE_STATUSES, today);
            log.info("[CMD] /overdue: {} orders", listSize(orders));

            if (isEmpty(orders)) {
                telegramBotService.sendReply(chatId, "\n✅ <b>Không có đơn quá hạn.</b>\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n🚨 <b>ĐƠN QUÁ HẠN</b> (").append(orders.size()).append(" đơn)\n\n");

            for (int i = 0; i < orders.size(); i++) {
                sb.append(formatOverdue(orders.get(i), i + 1));
                sb.append("\n");
            }

            telegramBotService.sendReply(chatId, sb.toString());

        } catch (Exception e) {
            log.error("[CMD] /overdue FAILED: {}", e.getMessage(), e);
            sendError(chatId, "overdue", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void handleRevenue(String chatId) {
        try {
            LocalDateTime now = LocalDateTime.now(TZ);
            LocalDate today = now.toLocalDate();
            LocalDate monthStart = today.withDayOfMonth(1);

            BigDecimal todayRev = getRevenue(PAID_STATUSES, today.atStartOfDay());
            BigDecimal monthRev = getRevenue(PAID_STATUSES, monthStart.atStartOfDay());
            BigDecimal totalRev = getTotalRevenue(PAID_STATUSES);
            long orderCount = getOrderCount(PAID_STATUSES);

            String msg = String.format("""
                
                📊 <b>THỐNG KÊ DOANH THU</b>
                
                💰 Hôm nay:   <b>%s</b>
                📅 Tháng này: <b>%s</b>
                🏆 Tổng doanh thu: <b>%s</b>
                🧾 Đơn đã thanh toán: <b>%d đơn</b>
                
                """,
                fmtCurrency(todayRev),
                fmtCurrency(monthRev),
                fmtCurrency(totalRev),
                orderCount
            );

            telegramBotService.sendReply(chatId, msg);
            log.info("[CMD] /revenue OK");

        } catch (Exception e) {
            log.error("[CMD] /revenue FAILED: {}", e.getMessage(), e);
            sendError(chatId, "revenue", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void handleLowStock(String chatId) {
        try {
            List<Camera> cameras = cameraRepository.findByAvailableLessThanEqual(3);
            log.info("[CMD] /lowstock: {} cameras", listSize(cameras));

            if (isEmpty(cameras)) {
                telegramBotService.sendReply(chatId, "\n✅ <b>Không có sản phẩm tồn kho thấp.</b>\n");
                return;
            }

            List<Camera> lowStock = cameras.stream()
                    .filter(c -> c.getAvailable() != null && c.getAvailable() > 0 && c.getAvailable() <= 3)
                    .collect(Collectors.toList());

            if (lowStock.isEmpty()) {
                telegramBotService.sendReply(chatId, "\n✅ <b>Không có sản phẩm tồn kho thấp.</b>\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n⚠️ <b>TỒN KHO THẤP</b> (").append(lowStock.size()).append(" sản phẩm)\n\n");

            int max = Math.min(lowStock.size(), 10);
            for (int i = 0; i < max; i++) {
                Camera c = lowStock.get(i);
                sb.append("📷 ").append(s(c.getName()));
                sb.append(": ").append("<b>").append(c.getAvailable()).append("</b>").append(" cái\n");
            }

            if (lowStock.size() > 10) {
                sb.append("\n... và ").append(lowStock.size() - 10).append(" sản phẩm khác.");
            }

            telegramBotService.sendReply(chatId, sb.toString());

        } catch (Exception e) {
            log.error("[CMD] /lowstock FAILED: {}", e.getMessage(), e);
            sendError(chatId, "lowstock", e.getMessage());
        }
    }

    private void handleUnknown(String chatId, String cmd) {
        telegramBotService.sendReply(chatId, "\n❓ Lệnh không nhận diện: /" + cmd + "\n\nGửi /help để xem danh sách.\n");
    }

    // ==================== FORMAT METHODS ====================

    private String formatOrder(RentalOrder o, int idx) {
        String customer = getCustomerName(o);
        String products = formatProducts(o);
        String amount = fmtCurrency(o.getTotalAmount());
        String status = o.getStatus() != null ? o.getStatus().toString() : "N/A";
        String created = o.getCreatedAt() != null ? o.getCreatedAt().format(DT_FMT) : "N/A";

        return "━━━━━━━━━━━━━━\n" +
                "#" + idx + " <b>Mã:</b> " + s(o.getOrderCode()) + "\n" +
                "👤 <b>Khách:</b> " + customer + "\n" +
                "📷 <b>Sản phẩm:</b>\n" + products + "\n" +
                "💰 <b>Tổng:</b> " + amount + "\n" +
                "📌 <b>Trạng thái:</b> " + status + "\n" +
                "🕒 <b>Tạo lúc:</b> " + created;
    }

    private String formatRenting(RentalOrder o, int idx) {
        String customer = getCustomerName(o);
        String products = formatProducts(o);
        String endDate = o.getEndDate() != null ? o.getEndDate().format(D_FMT) : "N/A";
        String daysLeft = calcDays(o.getEndDate());

        return "━━━━━━━━━━━━━━\n" +
                "#" + idx + " <b>Mã:</b> " + s(o.getOrderCode()) + "\n" +
                "👤 <b>Khách:</b> " + customer + "\n" +
                "📷 <b>Sản phẩm:</b>\n" + products + "\n" +
                "📅 <b>Ngày trả:</b> " + endDate + "\n" +
                "⏰ <b>Còn lại:</b> " + daysLeft;
    }

    private String formatOverdue(RentalOrder o, int idx) {
        String customer = getCustomerName(o);
        String phone = getCustomerPhone(o);
        String products = formatProducts(o);
        String endDate = o.getEndDate() != null ? o.getEndDate().format(D_FMT) : "N/A";
        String overdue = calcOverdue(o.getEndDate());

        return "━━━━━━━━━━━━━━\n" +
                "#" + idx + " <b>Mã:</b> " + s(o.getOrderCode()) + "\n" +
                "👤 <b>Khách:</b> " + customer + " | 📱 " + phone + "\n" +
                "📷 <b>Sản phẩm:</b>\n" + products + "\n" +
                "📅 <b>Ngày trả:</b> " + endDate + "\n" +
                "⚠️ <b>Quá hạn:</b> " + overdue;
    }

    private String formatProducts(RentalOrder o) {
        if (o.getItems() == null || o.getItems().isEmpty()) {
            return "  (không có sản phẩm)";
        }

        return o.getItems().stream()
                .map(this::formatItem)
                .collect(Collectors.joining("\n"));
    }

    private String formatItem(RentalOrderItem item) {
        String name = "N/A";
        if (item.getCamera() != null && item.getCamera().getName() != null) {
            name = item.getCamera().getName();
        }
        int qty = item.getQuantity() != null ? item.getQuantity() : 1;
        return "  • " + name + " x" + qty;
    }

    // ==================== HELPERS ====================

    private String getCustomerName(RentalOrder o) {
        if (o.getUser() != null && o.getUser().getFullName() != null) {
            return o.getUser().getFullName();
        }
        return "N/A";
    }

    private String getCustomerPhone(RentalOrder o) {
        if (o.getUser() != null && o.getUser().getPhone() != null) {
            return o.getUser().getPhone();
        }
        return "N/A";
    }

    private String calcDays(LocalDate endDate) {
        if (endDate == null) return "N/A";
        LocalDate today = LocalDate.now();
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
        if (days >= 0) return days + " ngày";
        return "⚠️ Quá hạn " + Math.abs(days) + " ngày";
    }

    private String calcOverdue(LocalDate endDate) {
        if (endDate == null) return "N/A";
        LocalDate today = LocalDate.now();
        long days = java.time.temporal.ChronoUnit.DAYS.between(endDate, today);
        if (days > 0) return "⚠️ " + days + " ngày";
        return "N/A";
    }

    private String fmtCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";
        try {
            return String.format("%,d đ", amount.longValue()).replace(",", ".");
        } catch (Exception e) {
            return amount.toPlainString() + " đ";
        }
    }

    private String s(String val) {
        return (val != null && !val.isBlank()) ? val : "N/A";
    }

    private <T> int listSize(List<T> list) {
        return list != null ? list.size() : 0;
    }

    private <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    private boolean checkDb() {
        try {
            orderRepository.count();
            return true;
        } catch (Exception e) {
            log.warn("[CMD] DB check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isAuthorized(String chatId) {
        return chatId != null && chatId.equals(adminChatId);
    }

    private String normalizeCommand(String text) {
        String cmd = text.toLowerCase().trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        return cmd;
    }

    private void sendError(String chatId, String cmd, String reason) {
        String shortReason = truncate(reason);
        String msg = String.format("""
            
            ❌ <b>Lệnh /%s gặp lỗi.</b>
            
            <b>Lý do:</b> %s
            Vui lòng thử lại sau.
            
            """, cmd, shortReason);
        telegramBotService.sendReply(chatId, msg);
    }

    private String truncate(String reason) {
        if (reason == null) return "Lỗi không xác định";
        if (reason.length() <= 50) return reason;
        return reason.substring(0, 50) + "...";
    }

    private BigDecimal getRevenue(List<OrderStatus> statuses, LocalDateTime since) {
        try {
            BigDecimal result = orderRepository.sumRevenueAfter(statuses, since);
            return result != null ? result : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("[CMD] Revenue query failed: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal getTotalRevenue(List<OrderStatus> statuses) {
        try {
            BigDecimal result = orderRepository.sumTotalAmountByStatuses(statuses);
            return result != null ? result : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("[CMD] Total revenue query failed: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private long getOrderCount(List<OrderStatus> statuses) {
        try {
            return orderRepository.countByStatusIn(statuses);
        } catch (Exception e) {
            log.warn("[CMD] Order count query failed: {}", e.getMessage());
            return 0;
        }
    }
}
