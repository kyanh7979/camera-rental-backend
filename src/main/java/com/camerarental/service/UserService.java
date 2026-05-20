package com.camerarental.service;

import com.camerarental.dto.request.CreateUserRequest;
import com.camerarental.dto.request.UpdateProfileRequest;
import com.camerarental.dto.request.UpdateUserRequest;
import com.camerarental.dto.response.PagedResponse;
import com.camerarental.dto.response.UserResponse;

public interface UserService {

    UserResponse getProfile(String email);

    UserResponse updateProfile(String email, UpdateProfileRequest request);

    PagedResponse<UserResponse> getAllUsers(int page, int size, String search);

    UserResponse getUserById(Long id);

    UserResponse toggleUserStatus(Long id);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);
}
