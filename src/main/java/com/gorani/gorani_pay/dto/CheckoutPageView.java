package com.gorani.gorani_pay.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CheckoutPageView(
        String sessionToken,
        String merchantCode,
        String title,
        Integer amount,
        Integer pointAmount,
        Integer finalPayableAmount,
        Integer walletBalance,
        Integer expectedAutoChargeAmount,
        String accountNumber,
        String bankCode,
        String ownerName,
        String entryMode,
        String channel,
        String oneTimeToken,
        String qrPayload,
        String status,
        LocalDateTime expiresAt
) {
}
