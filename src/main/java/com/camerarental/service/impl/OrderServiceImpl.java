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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final RentalOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final TelegramBotService telegramBotService;

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
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<RentalOrder> orders;
        if (status != null) {
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

        if (status == OrderStatus.RETURNED || status == OrderStatus.COMPLETED) {
            restoreStock(order);
        }

        order.setStatus(status);
        RentalOrder saved = orderRepository.save(order);
        log.info("Order status updated: id={}, code={}, newStatus={}", saved.getId(), saved.getOrderCode(),
                saved.getStatus());

        if (status == OrderStatus.CONFIRMED && previousStatus == OrderStatus.PENDING) {
            log.info("Order {} status changing from PENDING to CONFIRMED, triggering Telegram notification...", saved.getId());
            try {
                telegramBotService.sendOrderConfirmedNotification(saved);
                log.debug("Telegram message sent for orderId = {}", saved.getId());
            } catch (Exception e) {
                log.error("Failed to send Telegram notification for order {}: {}", saved.getId(), e.getMessage());
            }
        }

        return OrderResponse.fromEntity(saved);
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

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        RentalOrder saved = orderRepository.save(order);
        log.info("Order cancelled: id={}, code={}, userId={}", saved.getId(), saved.getOrderCode(),
                saved.getUser().getId());
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
}
