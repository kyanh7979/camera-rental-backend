package com.camerarental.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    private long totalOrders;
    private long pendingOrders;
    private long activeRentals;
    private long completedOrders;
    private BigDecimal totalRevenue;
}
