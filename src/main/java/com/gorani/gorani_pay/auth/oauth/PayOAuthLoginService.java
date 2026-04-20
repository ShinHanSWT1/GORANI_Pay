package com.gorani.gorani_pay.auth.oauth;

import com.gorani.gorani_pay.dto.CreateAccountRequest;
import com.gorani.gorani_pay.entity.PayUser;
import com.gorani.gorani_pay.repository.PayUserRepository;
import com.gorani.gorani_pay.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOAuthLoginService {

    private final PayUserRepository payUserRepository;
    private final WalletService walletService;

    @Transactional
    public PayUser loginOrRegister(PayOAuthAttributes attributes) {
        PayUser oauthLinkedUser = payUserRepository.findByOauthProviderAndOauthUserId(
                attributes.provider(),
                attributes.providerUserId()
        ).orElse(null);
        if (oauthLinkedUser != null) {
            updateProfile(oauthLinkedUser, attributes);
            return oauthLinkedUser;
        }

        // 기존 지갑 사용자와 이메일 연결 우선 처리
        if (attributes.email() != null && !attributes.email().isBlank()) {
            PayUser emailLinkedUser = payUserRepository.findByEmailIgnoreCase(attributes.email()).orElse(null);
            if (emailLinkedUser != null) {
                emailLinkedUser.setOauthProvider(attributes.provider());
                emailLinkedUser.setOauthUserId(attributes.providerUserId());
                updateProfile(emailLinkedUser, attributes);
                log.info("[Pay] OAuth 계정 이메일 연결 완료. payUserId={}, email={}", emailLinkedUser.getId(), attributes.email());
                return emailLinkedUser;
            }
        }

        PayUser payUser = new PayUser();
        payUser.setOauthProvider(attributes.provider());
        payUser.setOauthUserId(attributes.providerUserId());
        payUser.setExternalUserId(generateStandaloneExternalUserId(attributes.providerUserId()));
        payUser.setUserName(resolveUserName(attributes));
        payUser.setEmail(resolveEmail(attributes));
        payUser.setStatus("ACTIVE");
        payUser.setCreatedAt(LocalDateTime.now());
        payUser.setUpdatedAt(LocalDateTime.now());
        PayUser saved = payUserRepository.save(payUser);

        CreateAccountRequest createAccountRequest = new CreateAccountRequest();
        createAccountRequest.setExternalUserId(saved.getExternalUserId());
        createAccountRequest.setUserName(saved.getUserName());
        createAccountRequest.setEmail(saved.getEmail());
        createAccountRequest.setOwnerName(saved.getUserName());
        walletService.createAccount(createAccountRequest);
        log.info("[Pay] OAuth 신규 사용자 및 지갑 생성 완료. payUserId={}, externalUserId={}", saved.getId(), saved.getExternalUserId());
        return saved;
    }

    private void updateProfile(PayUser payUser, PayOAuthAttributes attributes) {
        if (attributes.email() != null && !attributes.email().isBlank()) {
            payUser.setEmail(attributes.email());
        }
        payUser.setUserName(resolveUserName(attributes));
        payUser.setUpdatedAt(LocalDateTime.now());
    }

    private String resolveUserName(PayOAuthAttributes attributes) {
        if (attributes.nickname() == null || attributes.nickname().isBlank()) {
            return "kakao-user";
        }
        return attributes.nickname();
    }

    private String resolveEmail(PayOAuthAttributes attributes) {
        if (attributes.email() == null || attributes.email().isBlank()) {
            return "kakao-user@local.pay";
        }
        return attributes.email();
    }

    private Long generateStandaloneExternalUserId(String providerUserId) {
        long hash = Math.abs(providerUserId.hashCode());
        // 내부 사용자 ID와 충돌 확률 완화 목적 오프셋 처리
        return 9_000_000_000L + hash;
    }
}
