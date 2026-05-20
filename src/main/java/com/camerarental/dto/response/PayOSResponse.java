package com.camerarental.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOSResponse {

    private Long id;
    private String qrUrl;
    private String checkoutUrl;
    private String paymentLink;
    private String qrCode;
    private Long orderCode;
    private Long amount;
    private String status;

    public static PayOSResponse fromPaymentLinkResponse(CreatePaymentLinkResponse data) {
        return PayOSResponse.builder()
                .qrUrl(data.getQrCode())
                .qrCode(data.getQrCode())
                .checkoutUrl(data.getCheckoutUrl())
                .orderCode(data.getOrderCode())
                .amount(data.getAmount())
                .status(data.getStatus() != null ? data.getStatus().toString() : null)
                .build();
    }

    public static PayOSResponse fromPaymentData(PayOSPaymentData data) {
        return PayOSResponse.builder()
                .qrUrl(data.getQrCode())
                .qrCode(data.getQrCode())
                .checkoutUrl(data.getCheckoutUrl())
                .paymentLink(data.getPaymentLinkUrl())
                .orderCode(data.getOrderCode())
                .amount((long) data.getAmount())
                .status(data.getStatus())
                .build();
    }
}
