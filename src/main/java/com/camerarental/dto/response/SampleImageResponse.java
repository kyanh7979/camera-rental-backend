package com.camerarental.dto.response;

import com.camerarental.entity.CameraSampleImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleImageResponse {

    private Long id;
    private String imageUrl;
    private String title;
    private Integer displayOrder;

    public static SampleImageResponse fromEntity(CameraSampleImage entity) {
        if (entity == null) {
            return null;
        }
        return SampleImageResponse.builder()
                .id(entity.getId())
                .imageUrl(entity.getImageUrl())
                .title(entity.getTitle())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }
}
