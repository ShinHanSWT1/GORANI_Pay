package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCheckoutSessionRequest {

    @NotBlank
    private String merchantCode;

    private Long payUserId;

    private String merchantUserKey;

    @NotBlank
    private String externalOrderId;

    @NotBlank
    private String title;

    @NotNull
    @Min(0)
    private Integer amount;

    @Min(0)
    private Integer pointAmount;

    @Min(0)
    private Integer couponDiscountAmount;

    private Long payProductId;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String failUrl;

    // 결제 진입 방식
    private String entryMode;

    // 결제 채널 힌트
    private String channel;

    // 가맹점 연동 방식
    private String integrationType;
}
