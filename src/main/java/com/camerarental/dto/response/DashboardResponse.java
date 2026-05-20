package com.camerarental.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private long totalUsers;
    private long totalCameras;
    private long totalOrders;
    private BigDecimal totalRevenue;
    private long pendingOrders;
    private long activeRentals;
    private List<TopCameraDto> topCameras;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopCameraDto {
        private Long cameraId;
        private String cameraName;
        private long rentalCount;
    }
}
