package com.camerarental.service.impl;

import com.camerarental.dto.response.DashboardResponse;
import com.camerarental.dto.response.DashboardStatsDto;
import com.camerarental.entity.enums.OrderStatus;
import com.camerarental.repository.CameraRepository;
import com.camerarental.repository.RentalOrderRepository;
import com.camerarental.repository.UserRepository;
import com.camerarental.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final RentalOrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getStats() {
        // OPTIMIZED: Get order stats in 1 native query instead of 4 separate queries
        Object[] orderStats = orderRepository.getOrderStatsNative();

        // Parse results: [totalOrders, pendingOrders, activeRentals, completedOrders, totalRevenue]
        long totalOrders = 0;
        long pendingOrders = 0;
        long activeRentals = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        if (orderStats != null && orderStats.length >= 5) {
            totalOrders = ((Number) orderStats[0]).longValue();
            pendingOrders = orderStats[1] != null ? ((Number) orderStats[1]).longValue() : 0;
            activeRentals = orderStats[2] != null ? ((Number) orderStats[2]).longValue() : 0;
            // completedOrders not used but parsed
            if (orderStats[4] != null) {
                totalRevenue = new BigDecimal(orderStats[4].toString());
            }
        }

        log.info("[Dashboard] Stats loaded - orders: {}, pending: {}, active: {}, revenue: {}",
                totalOrders, pendingOrders, activeRentals, totalRevenue);

        return DashboardResponse.builder()
                .totalUsers(userRepository.count())
                .totalCameras(cameraRepository.count())
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .pendingOrders(pendingOrders)
                .activeRentals(activeRentals)
                .build();
    }
}
