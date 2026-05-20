package com.camerarental.scheduler;

import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.repository.RentalOrderRepository;
import com.camerarental.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Order Expiration Scheduler
 * Checks for expiring orders and overdue orders
 * Sends notifications to admin via Telegram
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationScheduler {

    private final RentalOrderRepository orderRepository;
    private final TelegramBotService telegramBotService;

    @Value("${app.telegram.warning-enabled:true}")
    private boolean warningEnabled;

    @Value("${app.telegram.overdue-notification-enabled:true}")
    private boolean overdueNotificationEnabled;

    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PAID,
            OrderStatus.RENTING
    );

    /**
     * Check for expiring orders (3h and 1h before)
     * Runs every 5 minutes by default
     */
    @Scheduled(fixedRateString = "${app.telegram.check-interval-ms:300000}")
    @Transactional
    public void checkExpiringOrders() {
        if (!warningEnabled) {
            log.debug("Order expiration warning is disabled");
            return;
        }

        log.info("=== Running expiration check (3h/1h warning) ===");
        
        // Get orders with user loaded (using fetch join)
        List<RentalOrder> activeOrders = orderRepository.findByStatusInWithUser(ACTIVE_STATUSES);

        int checked = 0;
        int warned = 0;
        int failed = 0;

        for (RentalOrder order : activeOrders) {
            checked++;
            try {
                boolean sent = processExpiringOrder(order);
                if (sent) warned++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to process expiration for order {}: {}", order.getOrderCode(), e.getMessage());
            }
        }

        log.info("=== Expiration check completed: checked={}, sent={}, failed={} ===", checked, warned, failed);
    }

    /**
     * Check for overdue orders (past due date)
     * Runs every 1 hour by default
     * Each overdue order is only notified ONCE (prevents spam)
     */
    @Scheduled(fixedRateString = "${app.telegram.overdue-check-interval-ms:3600000}")
    @Transactional
    public void checkOverdueOrders() {
        if (!overdueNotificationEnabled) {
            log.debug("Overdue notification is disabled");
            return;
        }

        log.info("=== Running overdue check ===");
        
        LocalDate today = LocalDate.now();
        
        // Get overdue orders with user AND items+cameras loaded (using fetch join)
        List<RentalOrder> overdueOrders = orderRepository.findOverdueOrdersWithDetails(ACTIVE_STATUSES, today);

        int checked = 0;
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (RentalOrder order : overdueOrders) {
            checked++;
            
            // Check if already notified (prevents spam)
            if (Boolean.TRUE.equals(order.getOverdueNotificationSent())) {
                log.debug("Order {} already notified about overdue, skipping", order.getOrderCode());
                skipped++;
                continue;
            }
            
            try {
                // Calculate overdue duration
                long daysOverdue = ChronoUnit.DAYS.between(order.getEndDate(), today);
                long hoursOverdue = ChronoUnit.HOURS.between(order.getEndDate().atStartOfDay(), today.atStartOfDay());
                
                // Send notification
                telegramBotService.sendOrderOverdueNotification(order, daysOverdue, hoursOverdue);
                
                // Mark as notified (prevents duplicate notifications)
                order.setOverdueNotificationSent(true);
                orderRepository.save(order);
                
                sent++;
                log.info("Overdue notification sent for order: {} (overdue: {} days)", 
                        order.getOrderCode(), daysOverdue);
                
            } catch (Exception e) {
                failed++;
                log.error("Failed to send overdue notification for order {}: {}", 
                        order.getOrderCode(), e.getMessage());
            }
        }

        log.info("=== Overdue check completed: checked={}, sent={}, skipped={}, failed={} ===", 
                checked, sent, skipped, failed);
    }

    /**
     * Process a single order for expiration warnings
     */
    private boolean processExpiringOrder(RentalOrder order) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = order.getEndDate();

        if (endDate == null) {
            return false;
        }

        // Order is past due, let overdue checker handle it
        if (endDate.isBefore(today) || endDate.isEqual(today)) {
            return false;
        }

        long hoursRemaining = ChronoUnit.HOURS.between(
                today.atStartOfDay(),
                endDate.atStartOfDay()
        );

        // Only check within 24 hours
        if (hoursRemaining <= 0 || hoursRemaining > 24) {
            return false;
        }

        // Check 1 hour warning
        if (hoursRemaining <= 1 && !Boolean.TRUE.equals(order.getWarning1hSent())) {
            log.info("Sending 1h warning for order: {} (hours remaining: {})",
                    order.getOrderCode(), hoursRemaining);
            telegramBotService.sendOrderDueSoonNotification(order, (int) hoursRemaining);
            order.setWarning1hSent(true);
            orderRepository.save(order);
            return true;
        }

        // Check 3 hour warning
        if (hoursRemaining <= 3 && !Boolean.TRUE.equals(order.getWarning3hSent())) {
            log.info("Sending 3h warning for order: {} (hours remaining: {})",
                    order.getOrderCode(), hoursRemaining);
            telegramBotService.sendOrderDueSoonNotification(order, (int) hoursRemaining);
            order.setWarning3hSent(true);
            orderRepository.save(order);
            return true;
        }

        return false;
    }
}
