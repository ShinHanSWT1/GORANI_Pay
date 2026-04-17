package com.gorani.gorani_pay.dto;

import lombok.Builder;

@Builder
public record CheckoutProcessResult(
        String redirectUrl,
        String status
) {
}
