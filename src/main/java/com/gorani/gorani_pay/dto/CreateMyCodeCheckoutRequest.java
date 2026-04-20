package com.gorani.gorani_pay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMyCodeCheckoutRequest {

    @NotBlank
    private String merchantCode;

    @NotBlank
    private String title;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String failUrl;

    private String channel;
}
