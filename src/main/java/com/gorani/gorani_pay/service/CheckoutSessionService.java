package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.dto.CheckoutPageView;
import com.gorani.gorani_pay.dto.CheckoutProcessResult;
import com.gorani.gorani_pay.dto.CheckoutSessionResponse;
import com.gorani.gorani_pay.dto.CreateCheckoutByCodeRequest;
import com.gorani.gorani_pay.dto.CreateCheckoutSessionRequest;
import com.gorani.gorani_pay.dto.CreatePaymentRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayCheckoutChannel;
import com.gorani.gorani_pay.entity.PayCheckoutEntryMode;
import com.gorani.gorani_pay.entity.PayCheckoutSession;
import com.gorani.gorani_pay.entity.PayCheckoutSessionStatus;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayCheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionService {

    private static final long SESSION_EXPIRE_MINUTES = 15L;
    private static final long CODE_TOKEN_EXPIRE_MINUTES = 3L;

    private final PayCheckoutSessionRepository payCheckoutSessionRepository;
    private final PayAccountRepository payAccountRepository;
    private final PaymentService paymentService;
    private final WalletService walletService;

    @Value("${app.checkout.base-url:http://localhost:8083}")
    private String checkoutBaseUrl;

    @Transactional
    public CheckoutSessionResponse createSession(CreateCheckoutSessionRequest request) {
        // 결제 세션은 반드시 유효한 지갑(계좌)과 연결되어야 한다.
        PayAccount account = payAccountRepository.findByPayUserId(request.getPayUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        PayCheckoutEntryMode resolvedEntryMode = resolveEntryMode(request.getEntryMode());

        // 코드 제시용 세션(IN_APP_CODE)은 금액 0 허용, 즉시결제 세션은 1원 이상만 허용한다.
        if (resolvedEntryMode != PayCheckoutEntryMode.IN_APP_CODE && (request.getAmount() == null || request.getAmount() < 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero for merchant checkout");
        }

        PayCheckoutSession session = new PayCheckoutSession();
        session.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        session.setMerchantCode(request.getMerchantCode());
        session.setPayUserId(request.getPayUserId());
        session.setPayAccountId(account.getId());
        session.setExternalOrderId(request.getExternalOrderId());
        session.setTitle(request.getTitle());
        session.setAmount(request.getAmount());
        session.setPointAmount(resolveNonNegativeAmount(request.getPointAmount()));
        session.setCouponDiscountAmount(resolveNonNegativeAmount(request.getCouponDiscountAmount()));
        session.setPayProductId(request.getPayProductId());
        session.setSuccessUrl(request.getSuccessUrl());
        session.setFailUrl(request.getFailUrl());
        session.setEntryMode(resolvedEntryMode);
        session.setChannel(resolveChannel(request.getChannel(), session.getEntryMode()));
        session.setStatus(PayCheckoutSessionStatus.CREATED);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(SESSION_EXPIRE_MINUTES));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        // 코드 결제 모드에서는 스캔용 1회성 토큰을 함께 발급한다.
        if (session.getEntryMode() == PayCheckoutEntryMode.IN_APP_CODE) {
            session.setOneTimeToken("GP-CODE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            session.setTokenExpiresAt(LocalDateTime.now().plusMinutes(CODE_TOKEN_EXPIRE_MINUTES));
        }

        payCheckoutSessionRepository.save(session);
        log.info("[Pay] checkout session 생성 완료. token={}, merchant={}, payUserId={}, amount={}",
                session.getSessionToken(), session.getMerchantCode(), session.getPayUserId(), session.getAmount());

        return CheckoutSessionResponse.builder()
                .sessionToken(session.getSessionToken())
                .checkoutUrl(checkoutBaseUrl + "/pay/checkout/" + session.getSessionToken())
                .status(session.getStatus().name())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    @Transactional
    public CheckoutSessionResponse createSessionByCode(CreateCheckoutByCodeRequest request) {
        PayCheckoutSession codeSession = payCheckoutSessionRepository.findByOneTimeToken(request.getCodeToken())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Code token not found"));

        if (codeSession.getEntryMode() != PayCheckoutEntryMode.IN_APP_CODE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token is not issued from IN_APP_CODE session");
        }
        if (codeSession.getStatus() != PayCheckoutSessionStatus.CREATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code session is not active");
        }
        if (codeSession.getTokenExpiresAt() == null || LocalDateTime.now().isAfter(codeSession.getTokenExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token has expired");
        }

        PayCheckoutSession merchantSession = new PayCheckoutSession();
        merchantSession.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        merchantSession.setMerchantCode(request.getMerchantCode());
        merchantSession.setPayUserId(codeSession.getPayUserId());
        merchantSession.setPayAccountId(codeSession.getPayAccountId());
        merchantSession.setExternalOrderId(request.getExternalOrderId());
        merchantSession.setTitle(request.getTitle());
        merchantSession.setAmount(request.getAmount());
        merchantSession.setPointAmount(0);
        merchantSession.setCouponDiscountAmount(0);
        merchantSession.setPayProductId(null);
        merchantSession.setSuccessUrl(request.getSuccessUrl());
        merchantSession.setFailUrl(request.getFailUrl());
        merchantSession.setEntryMode(PayCheckoutEntryMode.MERCHANT_REDIRECT);
        merchantSession.setChannel(resolveChannel(request.getChannel(), PayCheckoutEntryMode.MERCHANT_REDIRECT));
        merchantSession.setStatus(PayCheckoutSessionStatus.CREATED);
        merchantSession.setExpiresAt(LocalDateTime.now().plusMinutes(SESSION_EXPIRE_MINUTES));
        merchantSession.setCreatedAt(LocalDateTime.now());
        merchantSession.setUpdatedAt(LocalDateTime.now());
        payCheckoutSessionRepository.save(merchantSession);

        // 사용자 결제코드는 매장 결제요청 생성 즉시 소모해 재사용을 방지한다.
        codeSession.consumeOneTimeToken();
        log.info("[Pay] 코드 기반 결제 세션 생성 완료. merchantSessionToken={}, merchantCode={}, amount={}, payUserId={}",
                merchantSession.getSessionToken(), merchantSession.getMerchantCode(), merchantSession.getAmount(), merchantSession.getPayUserId());

        // 바코드/QR 결제는 즉시 승인형으로 처리한다.
        // 매장 스캐너가 codeToken을 읽으면 사용자 추가 조작 없이 결제를 완료한다.
        CheckoutProcessResult processResult = processCheckout(merchantSession.getSessionToken(), true, null);
        log.info("[Pay] 코드 기반 즉시 결제 처리 완료. merchantSessionToken={}, status={}, redirectUrl={}",
                merchantSession.getSessionToken(), processResult.status(), processResult.redirectUrl());

        return CheckoutSessionResponse.builder()
                .sessionToken(merchantSession.getSessionToken())
                .checkoutUrl(processResult.redirectUrl())
                .status(processResult.status())
                .expiresAt(merchantSession.getExpiresAt())
                .build();
    }

    @Transactional
    public CheckoutPageView getPageView(String sessionToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);

        // 페이지 진입 시 만료된 세션은 즉시 EXPIRED로 전환한다.
        if (session.getStatus() == PayCheckoutSessionStatus.CREATED && session.isExpired(LocalDateTime.now())) {
            session.markExpired();
        }

        PayAccount account = payAccountRepository.findById(session.getPayAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        int finalPayableAmount = Math.max(session.getAmount() - session.getPointAmount() - session.getCouponDiscountAmount(), 0);
        // 보유 머니가 부족하면 submit 시 자동충전이 일어날 예상 금액을 미리 계산해 화면에 노출한다.
        int expectedAutoChargeAmount = Math.max(finalPayableAmount - account.getBalance(), 0);

        return CheckoutPageView.builder()
                .sessionToken(session.getSessionToken())
                .merchantCode(session.getMerchantCode())
                .title(session.getTitle())
                .amount(session.getAmount())
                .pointAmount(session.getPointAmount())
                .finalPayableAmount(finalPayableAmount)
                .walletBalance(account.getBalance())
                .expectedAutoChargeAmount(expectedAutoChargeAmount)
                .accountNumber(account.getAccountNumber())
                .bankCode(account.getBankCode())
                .ownerName(account.getOwnerName())
                .entryMode(session.getEntryMode().name())
                .channel(session.getChannel().name())
                .oneTimeToken(session.getOneTimeToken())
                .qrPayload(buildQrPayload(session))
                .status(session.getStatus().name())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    @Transactional
    public CheckoutProcessResult processCheckout(String sessionToken, boolean autoChargeIfInsufficient, String codeToken) {
        PayCheckoutSession session = getSessionByToken(sessionToken);
        validateProcessable(session);
        validateCodeTokenIfNeeded(session, codeToken);

        try {
            // 결제 엔진 기본 흐름: READY 생성 -> COMPLETE 확정
            PayPayment payment = paymentService.createPayment(toCreatePaymentRequest(session), "checkout-create-" + sessionToken);
            boolean autoCharged = false;

            try {
                paymentService.completePayment(payment.getId(), "checkout-complete-" + sessionToken);
            } catch (ApiException ex) {
                // 잔액 부족 + 자동충전 허용이면 부족분 충전 후 1회 재시도
                if (ex.getStatus() == HttpStatus.BAD_REQUEST
                        && "Insufficient balance".equalsIgnoreCase(ex.getMessage())
                        && autoChargeIfInsufficient) {
                    log.info("[Pay] checkout 잔액 부족 감지. token={}, paymentId={}, autoChargeIfInsufficient=true",
                            sessionToken, payment.getId());
                    PayAccount account = payAccountRepository.findById(session.getPayAccountId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
                    int shortage = Math.max(session.getAmount() - account.getBalance(), 0);
                    if (shortage > 0) {
                        log.info("[Pay] checkout 자동충전 실행. token={}, payUserId={}, shortage={}",
                                sessionToken, session.getPayUserId(), shortage);
                        walletService.charge(session.getPayUserId(), shortage);
                        autoCharged = true;
                    }
                    log.info("[Pay] checkout 자동충전 후 결제 재시도. token={}, paymentId={}",
                            sessionToken, payment.getId());
                    paymentService.completePayment(payment.getId(), "checkout-complete-retry-" + sessionToken);
                } else {
                    throw ex;
                }
            }

            session.markCompleted(payment.getId(), autoCharged);
            log.info("[Pay] checkout 결제 완료. token={}, paymentId={}, autoChargeUsed={}",
                    sessionToken, payment.getId(), autoCharged);

            String redirectUrl = UriComponentsBuilder.fromUriString(session.getSuccessUrl())
                    .queryParam("orderId", session.getExternalOrderId())
                    .queryParam("paymentId", payment.getId())
                    .queryParam("amount", session.getAmount())
                    .queryParam("status", "COMPLETED")
                    .build(true)
                    .toUriString();

            return CheckoutProcessResult.builder()
                    .redirectUrl(redirectUrl)
                    .status("COMPLETED")
                    .build();
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null ? "결제 처리 중 오류가 발생했습니다." : ex.getMessage();
            session.markFailed(errorMessage);
            log.error("[Pay] checkout 결제 실패. token={}, message={}", sessionToken, errorMessage, ex);

            String redirectUrl = UriComponentsBuilder.fromUriString(session.getFailUrl())
                    .queryParam("orderId", session.getExternalOrderId())
                    .queryParam("status", "FAILED")
                    .queryParam("message", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                    .build(true)
                    .toUriString();

            return CheckoutProcessResult.builder()
                    .redirectUrl(redirectUrl)
                    .status("FAILED")
                    .build();
        }
    }

    private void validateProcessable(PayCheckoutSession session) {
        if (session.getStatus() != PayCheckoutSessionStatus.CREATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Checkout session is not processable");
        }
        if (session.isExpired(LocalDateTime.now())) {
            session.markExpired();
            throw new ApiException(HttpStatus.BAD_REQUEST, "Checkout session has expired");
        }
    }

    private void validateCodeTokenIfNeeded(PayCheckoutSession session, String codeToken) {
        if (session.getEntryMode() != PayCheckoutEntryMode.IN_APP_CODE) {
            return;
        }

        if (session.getOneTimeToken() == null || session.getTokenExpiresAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token is not available");
        }
        if (LocalDateTime.now().isAfter(session.getTokenExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code token has expired");
        }
        if (codeToken == null || !session.getOneTimeToken().equals(codeToken)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid code token");
        }

        // 1회성 토큰은 서버 검증 직후 바로 소모한다.
        session.consumeOneTimeToken();
        log.info("[Pay] 코드 토큰 검증 완료. sessionToken={}", session.getSessionToken());
    }

    private PayCheckoutSession getSessionByToken(String sessionToken) {
        return payCheckoutSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Checkout session not found"));
    }

    private CreatePaymentRequest toCreatePaymentRequest(PayCheckoutSession session) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setPayUserId(session.getPayUserId());
        request.setPayAccountId(session.getPayAccountId());
        request.setExternalOrderId(session.getExternalOrderId());
        request.setPaymentType("WALLET");
        request.setPayProductId(session.getPayProductId());
        request.setTitle(session.getTitle());
        request.setAmount(session.getAmount());
        request.setPointAmount(resolveNonNegativeAmount(session.getPointAmount()));
        request.setCouponDiscountAmount(resolveNonNegativeAmount(session.getCouponDiscountAmount()));
        return request;
    }

    private int resolveNonNegativeAmount(Integer amount) {
        if (amount == null || amount < 0) {
            return 0;
        }
        return amount;
    }

    private PayCheckoutEntryMode resolveEntryMode(String rawEntryMode) {
        if (rawEntryMode == null || rawEntryMode.isBlank()) {
            return PayCheckoutEntryMode.MERCHANT_REDIRECT;
        }
        try {
            return PayCheckoutEntryMode.valueOf(rawEntryMode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported entryMode");
        }
    }

    private PayCheckoutChannel resolveChannel(String rawChannel, PayCheckoutEntryMode entryMode) {
        if (rawChannel == null || rawChannel.isBlank()) {
            return entryMode == PayCheckoutEntryMode.IN_APP_CODE ? PayCheckoutChannel.QR : PayCheckoutChannel.REDIRECT;
        }
        try {
            return PayCheckoutChannel.valueOf(rawChannel.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported channel");
        }
    }

    private String buildQrPayload(PayCheckoutSession session) {
        // QR에는 결제 세션 URL만 담아 서버에서 상세 데이터를 조회한다.
        return checkoutBaseUrl + "/pay/checkout/" + session.getSessionToken();
    }
}
