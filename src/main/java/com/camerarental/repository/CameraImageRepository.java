package com.camerarental.repository;

import com.camerarental.entity.CameraImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CameraImageRepository extends JpaRepository<CameraImage, Long> {

    /**
     * Find all images for a camera
     */
    List<CameraImage> findByCameraIdOrderByDisplayOrderAsc(Long cameraId);

    /**
     * Delete all images for a camera
     */
    @Modifying
    @Query("DELETE FROM CameraImage ci WHERE ci.camera.id = :cameraId")
    void deleteByCameraId(@Param("cameraId") Long cameraId);

    /**
     * Count images for a camera
     */
    long countByCameraId(Long cameraId);
}
