package com.gorani.gorani_pay.auth.oauth;

import java.util.Map;

public record PayOAuthAttributes(
        String provider,
        String providerUserId,
        String email,
        String nickname
) {
    @SuppressWarnings("unchecked")
    public static PayOAuthAttributes ofKakao(Map<String, Object> attributes) {
        String providerUserId = String.valueOf(attributes.get("id"));
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("kakao user id is required");
        }

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");

        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        String nickname = properties != null ? (String) properties.get("nickname") : "kakao-user";

        return new PayOAuthAttributes("KAKAO", providerUserId, email, nickname);
    }
}
