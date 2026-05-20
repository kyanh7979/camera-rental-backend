package com.camerarental.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String fullName;
    private String phone;
    private String address;
    private String avatar;
}
