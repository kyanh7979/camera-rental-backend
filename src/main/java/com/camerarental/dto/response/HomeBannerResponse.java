package com.camerarental.dto.response;

import com.camerarental.entity.HomeBanner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBannerResponse {

    private Long id;
    private String imageUrl;
    private Boolean active;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static HomeBannerResponse fromEntity(HomeBanner banner) {
        if (banner == null) {
            return null;
        }
        return HomeBannerResponse.builder()
                .id(banner.getId())
                .imageUrl(banner.getImageUrl())
                .active(banner.getActive())
                .displayOrder(banner.getDisplayOrder())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }
}
