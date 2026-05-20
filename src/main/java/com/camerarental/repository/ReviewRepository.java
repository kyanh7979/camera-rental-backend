package com.camerarental.repository;

import com.camerarental.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByCameraId(Long cameraId, Pageable pageable);

    Optional<Review> findByUserIdAndCameraId(Long userId, Long cameraId);

    boolean existsByUserIdAndCameraId(Long userId, Long cameraId);
}
