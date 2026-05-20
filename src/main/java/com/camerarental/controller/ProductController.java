package com.camerarental.controller;

import com.camerarental.dto.response.ApiResponse;
import com.camerarental.dto.response.CameraResponse;
import com.camerarental.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CameraResponse>> createProduct(
            @RequestParam("ten") String ten,
            @RequestParam("hang") String hang,
            @RequestParam("gia") java.math.BigDecimal gia,
            @RequestParam(value = "anh", required = false) MultipartFile anh) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created", productService.createProduct(ten, hang, gia, anh)));
    }
}
