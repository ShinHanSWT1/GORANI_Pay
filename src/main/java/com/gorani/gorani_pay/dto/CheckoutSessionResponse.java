package com.gorani.gorani_pay.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CheckoutSessionResponse(
        String sessionToken,
        String checkoutUrl,
        String status,
        LocalDateTime expiresAt
) {
}
