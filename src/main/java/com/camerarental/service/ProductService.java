package com.camerarental.service;

import com.camerarental.dto.response.CameraResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface ProductService {

    CameraResponse createProduct(String ten, String hang, BigDecimal gia, MultipartFile anh);
}
