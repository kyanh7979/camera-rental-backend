package com.camerarental.service.impl;

import com.camerarental.dto.request.ReviewRequest;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.dto.response.ReviewResponse;
import com.camerarental.entity.Camera;
import com.camerarental.entity.Review;
import com.camerarental.entity.User;
import com.camerarental.exception.BadRequestException;
import com.camerarental.exception.DuplicateResourceException;
import com.camerarental.exception.ResourceNotFoundException;
import com.camerarental.repository.CameraRepository;
import com.camerarental.repository.ReviewRepository;
import com.camerarental.repository.UserRepository;
import com.camerarental.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(String email, ReviewRequest request) {
        log.info("Creating review for camera {} by user {}", request.getCameraId(), email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        Camera camera = cameraRepository.findById(request.getCameraId())
                .orElseThrow(() -> new ResourceNotFoundException("Camera", "id", request.getCameraId()));

        if (reviewRepository.existsByUserIdAndCameraId(user.getId(), camera.getId())) {
            throw new DuplicateResourceException("Review", "camera", camera.getName());
        }

        Review review = Review.builder()
                .user(user)
                .camera(camera)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long id, String email, ReviewRequest request) {
        log.info("Updating review {} by user {}", id, email);
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", id));

        if (!review.getUser().getEmail().equals(email)) {
            throw new BadRequestException("You can only edit your own reviews");
        }

        review.setRating(request.getRating());
        review.setComment(request.getComment());
        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void deleteReview(Long id, String email) {
        log.info("Deleting review {} by user {}", id, email);
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", id));

        if (!review.getUser().getEmail().equals(email)) {
            throw new BadRequestException("You can only delete your own reviews");
        }

        reviewRepository.delete(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getReviewsByCamera(Long cameraId, int page, int size) {
        log.info("Fetching reviews for camera: {}, page: {}, size: {}", cameraId, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Review> reviews = reviewRepository.findByCameraId(cameraId, pageable);
        log.info("Found {} reviews for camera {}", reviews.getTotalElements(), cameraId);

        return PagedResponse.<ReviewResponse>builder()
                .content(reviews.getContent().stream().map(ReviewResponse::fromEntity).toList())
                .page(reviews.getNumber())
                .size(reviews.getSize())
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .last(reviews.isLast())
                .build();
    }
}
