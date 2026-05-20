package com.camerarental.service;

import com.camerarental.dto.request.ReviewRequest;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.dto.response.ReviewResponse;

public interface ReviewService {

    ReviewResponse createReview(String email, ReviewRequest request);

    ReviewResponse updateReview(Long id, String email, ReviewRequest request);

    void deleteReview(Long id, String email);

    PagedResponse<ReviewResponse> getReviewsByCamera(Long cameraId, int page, int size);
}
