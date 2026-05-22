package com.camerarental.service;

import com.camerarental.dto.request.OrderRequest;
import com.camerarental.dto.response.OrderResponse;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.entity.enums.OrderStatus;

public interface OrderService {

    OrderResponse createOrder(String email, OrderRequest request);

    OrderResponse getOrderById(Long id, String email);

    PagedResponse<OrderResponse> getMyOrders(String email, int page, int size);

    PagedResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size);

    PagedResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size, String keyword);

    OrderResponse updateOrderStatus(Long id, OrderStatus status);

    OrderResponse cancelOrder(Long id, String email);

    void deleteOrder(Long id);
}
