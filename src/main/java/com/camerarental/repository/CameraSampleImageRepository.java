package com.camerarental.repository;

import com.camerarental.entity.CameraSampleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CameraSampleImageRepository extends JpaRepository<CameraSampleImage, Long> {

    /**
     * Find all sample images for a camera ordered by displayOrder
     */
    List<CameraSampleImage> findByCameraIdOrderByDisplayOrderAsc(Long cameraId);

    /**
     * Find all sample images for multiple cameras (for batch loading)
     */
    @Query("SELECT csi FROM CameraSampleImage csi WHERE csi.camera.id IN :cameraIds ORDER BY csi.camera.id, csi.displayOrder")
    List<CameraSampleImage> findByCameraIdIn(@Param("cameraIds") List<Long> cameraIds);

    /**
     * Delete all sample images for a camera
     */
    @Modifying
    @Query("DELETE FROM CameraSampleImage csi WHERE csi.camera.id = :cameraId")
    void deleteByCameraId(@Param("cameraId") Long cameraId);

    /**
     * Count sample images for a camera
     */
    long countByCameraId(Long cameraId);

    /**
     * Check if camera has any sample images
     */
    boolean existsByCameraId(Long cameraId);
}
