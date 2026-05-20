package com.camerarental.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayOSPaymentData {
    
    private Long orderCode;
    private int amount;
    private String description;
    private String paymentLinkId;
    private String paymentLinkUrl;
    private String qrCode;
    private String status;
    private String checkoutUrl;
}
