package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCheckoutByCodeRequest {

    @NotBlank
    private String merchantCode;

    @NotBlank
    private String codeToken;

    @NotBlank
    private String externalOrderId;

    @NotBlank
    private String title;

    @NotNull
    @Min(1)
    private Integer amount;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String failUrl;

    // 결제 채널 힌트: QR / REDIRECT
    private String channel;
}
