package com.camerarental.service.impl;

import com.camerarental.dto.request.OrderRequest;
import com.camerarental.dto.response.OrderResponse;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.entity.Camera;
import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.RentalOrderItem;
import com.camerarental.entity.User;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.CameraRepository;
import com.camerarental.repository.RentalOrderRepository;
import com.camerarental.repository.UserRepository;
import com.camerarental.service.OrderService;
import com.camerarental.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final RentalOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final TelegramBotService telegramBotService;

    /**
     * Valid status transitions for order lifecycle.
     *
     * Flow: PENDING -> CONFIRMED -> PAID -> RENTING -> RETURNED -> COMPLETED
     * Any non-terminal status can transition to CANCELLED.
     *
     * Note: The PAID status is set automatically by payment services (PayOS, bank transfer, MoMo).
     * Admin can also manually confirm payment and move CONFIRMED -> PAID.
     * Admin directly moves PAID -> RENTING (when customer picks up equipment).
     */
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.CONFIRMED, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PAID, Set.of(OrderStatus.RENTING, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.RENTING, Set.of(OrderStatus.RETURNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.RETURNED, Set.of(OrderStatus.COMPLETED)),
            Map.entry(OrderStatus.COMPLETED, Set.of()),
            Map.entry(OrderStatus.CANCELLED, Set.of())
    );

    /**
     * Terminal statuses that cannot transition to any other status.
     */
    private static final Set<OrderStatus> TERMINAL_STATUSES = Set.of(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED
    );

    @Override
    @Transactional
    public OrderResponse createOrder(String email, OrderRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        int rentalDays = (int) ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());

        RentalOrder order = RentalOrder.builder()
                .orderCode("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .note(request.getNote())
                .items(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal depositTotal = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Camera camera = cameraRepository.findById(itemReq.getCameraId())
                    .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", itemReq.getCameraId()));

            if (camera.getAvailable() < itemReq.getQuantity()) {
                log.warn("Insufficient stock for camera id={}, name={}, requested={}, available={}",
                        camera.getId(), camera.getName(), itemReq.getQuantity(), camera.getAvailable());
                throw new BadRequestException("Camera '" + camera.getName()
                        + "' does not have enough stock. Available: " + camera.getAvailable());
            }

            BigDecimal subtotal = camera.getDailyPrice()
                    .multiply(BigDecimal.valueOf(rentalDays))
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            RentalOrderItem item = RentalOrderItem.builder()
                    .order(order)
                    .camera(camera)
                    .quantity(itemReq.getQuantity())
                    .rentalDays(rentalDays)
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(item);
            total = total.add(subtotal);

            if (camera.getDeposit() != null) {
                depositTotal = depositTotal
                        .add(camera.getDeposit().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            }

            camera.setAvailable(camera.getAvailable() - itemReq.getQuantity());
            cameraRepository.save(camera);
        }

        order.setTotalAmount(total);
        order.setDepositAmount(depositTotal);

        RentalOrder saved = orderRepository.save(order);
        log.info("Order created: id={}, code={}, userId={}, totalAmount={}",
                saved.getId(), saved.getOrderCode(), user.getId(), total);

        // Gửi thông báo Telegram cho admin - đơn thuê mới
        try {
            telegramBotService.sendNewOrderNotification(saved);
        } catch (Exception e) {
            log.warn("Failed to send Telegram notification for new order: {}", e.getMessage());
        }

        // Kiểm tra tồn kho thấp
        for (RentalOrderItem item : saved.getItems()) {
            if (item.getCamera().getAvailable() <= 1) {
                try {
                    telegramBotService.sendLowStockNotification(item.getCamera().getName(), item.getCamera().getAvailable());
                } catch (Exception e) {
                    log.warn("Failed to send low stock notification: {}", e.getMessage());
                }
            }
        }

        return OrderResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, String email) {
        RentalOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (email != null && !order.getUser().getEmail().equals(email)) {
            throw new BadRequestException("You can only view your own orders");
        }

        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getMyOrders(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        // Use optimized query to avoid N+1 problem
        Page<RentalOrder> orders = orderRepository.findByUserIdWithItemsAndCameras(user.getId(), pageable);

        return buildPagedResponse(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size) {
        return getAllOrders(status, page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<RentalOrder> orders;

        if (keyword != null && !keyword.isBlank()) {
            log.info("[ADMIN_ORDERS_SEARCH] keyword={}, page={}, size={}", keyword, page, size);
            orders = orderRepository.searchOrders(keyword, pageable);

            // Log debug info
            log.info("[ADMIN_ORDERS_SEARCH] totalOrders={}, matchedOrders={}, sampleOrderCodes={}",
                    orders.getTotalElements(),
                    orders.getNumberOfElements(),
                    orders.getContent().stream().limit(5).map(RentalOrder::getOrderCode).toList());

            // Apply status filter post-search if needed
            if (status != null) {
                orders = new org.springframework.data.domain.PageImpl<>(
                        orders.getContent().stream()
                                .filter(o -> o.getStatus() == status)
                                .toList(),
                        pageable,
                        orders.getTotalElements()
                );
            }
        } else if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return buildPagedResponse(orders);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus status) {
        RentalOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        OrderStatus previousStatus = order.getStatus();

        // Validate status transition
        if (!isValidTransition(previousStatus, status)) {
            throw new BadRequestException(
                    String.format("Không thể chuyển từ '%s' sang '%s'. Luồng hợp lệ: PENDING -> CONFIRMED -> PAID -> RENTING -> RETURNED -> COMPLETED (hoặc hủy tại bất kỳ bước nào)",
                            previousStatus.name(), status.name()));
        }

        if (status == OrderStatus.RETURNED || status == OrderStatus.COMPLETED) {
            restoreStock(order);
        }

        order.setStatus(status);
        RentalOrder saved = orderRepository.save(order);

        log.info("[ORDER_STATUS] id={}, code={}, oldStatus={}, newStatus={}",
                saved.getId(), saved.getOrderCode(), previousStatus, saved.getStatus());

        // Fire Telegram notification for each transition — NEVER let Telegram failure affect status update
        sendOrderStatusNotification(saved, previousStatus, status);

        return OrderResponse.fromEntity(saved);
    }

    /**
     * Send Telegram notification for status transition.
     * This method is fire-and-forget: Telegram failures are logged but never thrown.
     */
    private void sendOrderStatusNotification(RentalOrder order, OrderStatus oldStatus, OrderStatus newStatus) {
        try {
            switch (newStatus) {
                case CONFIRMED -> {
                    if (oldStatus == OrderStatus.PENDING) {
                        log.debug("[ORDER_STATUS] Telegram: sending CONFIRMED notification for order {}", order.getId());
                        telegramBotService.sendOrderConfirmedNotification(order);
                        log.info("[ORDER_STATUS] Telegram: CONFIRMED notification sent for order {}", order.getId());
                    }
                }
                case RENTING -> {
                    log.debug("[ORDER_STATUS] Telegram: sending RENTING notification for order {}", order.getId());
                    telegramBotService.sendOrderRentingNotification(order);
                    log.info("[ORDER_STATUS] Telegram: RENTING notification sent for order {}", order.getId());
                }
                case RETURNED -> {
                    log.debug("[ORDER_STATUS] Telegram: sending RETURNED notification for order {}", order.getId());
                    telegramBotService.sendOrderReturnedNotification(order);
                    log.info("[ORDER_STATUS] Telegram: RETURNED notification sent for order {}", order.getId());
                }
                case COMPLETED -> {
                    log.debug("[ORDER_STATUS] Telegram: sending COMPLETED notification for order {}", order.getId());
                    telegramBotService.sendOrderCompletedNotification(order);
                    log.info("[ORDER_STATUS] Telegram: COMPLETED notification sent for order {}", order.getId());
                }
                case CANCELLED -> {
                    log.debug("[ORDER_STATUS] Telegram: sending CANCELLED notification for order {}", order.getId());
                    telegramBotService.sendOrderCancelledNotification(order, null);
                    log.info("[ORDER_STATUS] Telegram: CANCELLED notification sent for order {}", order.getId());
                }
                default -> {
                    log.debug("[ORDER_STATUS] Telegram: no notification configured for transition {} -> {}",
                            oldStatus, newStatus);
                }
            }
        } catch (Exception e) {
            log.warn("[ORDER_STATUS] Telegram notification failed for order {} (transition {} -> {}): {}",
                    order.getId(), oldStatus, newStatus, e.getMessage());
        }
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, String email) {
        RentalOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (!order.getUser().getEmail().equals(email)) {
            throw new BadRequestException("You can only cancel your own orders");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only pending orders can be cancelled");
        }

        OrderStatus previousStatus = order.getStatus();
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        RentalOrder saved = orderRepository.save(order);

        log.info("[ORDER_STATUS] id={}, code={}, oldStatus={}, newStatus={} (cancelled by user)",
                saved.getId(), saved.getOrderCode(), previousStatus, saved.getStatus());

        // Fire Telegram notification — NEVER let Telegram failure affect cancellation
        try {
            telegramBotService.sendOrderCancelledNotification(saved, "Hủy bởi khách hàng");
            log.info("[ORDER_STATUS] Telegram: CANCELLED notification sent for order {}", saved.getId());
        } catch (Exception e) {
            log.warn("[ORDER_STATUS] Telegram notification failed for order {} (cancelled): {}",
                    saved.getId(), e.getMessage());
        }

        return OrderResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        RentalOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        orderRepository.delete(order);
        log.info("Order deleted: id={}, code={}", order.getId(), order.getOrderCode());
    }

    private void restoreStock(RentalOrder order) {
        for (RentalOrderItem item : order.getItems()) {
            Camera camera = item.getCamera();
            camera.setAvailable(camera.getAvailable() + item.getQuantity());
            cameraRepository.save(camera);
        }
    }

    private PagedResponse<OrderResponse> buildPagedResponse(Page<RentalOrder> orders) {
        return PagedResponse.<OrderResponse>builder()
                .content(orders.getContent().stream().map(OrderResponse::fromEntity).toList())
                .page(orders.getNumber())
                .size(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .last(orders.isLast())
                .build();
    }

    /**
     * Check if a status transition is valid.
     *
     * @param from Current status
     * @param to   Target status
     * @return true if the transition is allowed
     */
    private boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == to) {
            return false; // No-op transitions are not allowed
        }
        Set<OrderStatus> allowedTargets = VALID_TRANSITIONS.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }
}
