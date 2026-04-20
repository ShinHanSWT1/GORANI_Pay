package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.dto.CheckoutPageView;
import com.gorani.gorani_pay.dto.CheckoutProcessResult;
import com.gorani.gorani_pay.dto.CheckoutSessionResponse;
import com.gorani.gorani_pay.dto.CreateCheckoutByCodeRequest;
import com.gorani.gorani_pay.dto.CreateCheckoutSessionRequest;
import com.gorani.gorani_pay.dto.CreateMyCodeCheckoutRequest;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.service.CheckoutSessionService;
import com.gorani.gorani_pay.service.PayLoginTokenService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/pay/checkout")
@RequiredArgsConstructor
public class PayCheckoutController {

    private final CheckoutSessionService checkoutSessionService;
    private final PayLoginTokenService payLoginTokenService;

    @GetMapping
    public ResponseEntity<String> checkoutHome() {
        String html = """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="UTF-8"><title>GORANI PAY</title></head>
                <body style="font-family:Arial,sans-serif;padding:24px;">
                  <h2>GORANI PAY 결제 페이지</h2>
                  <p>가맹점 결제 요청 URL로 접속해 주세요.</p>
                </body>
                </html>
                """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/sessions")
    public CheckoutSessionResponse createSession(@Valid @RequestBody CreateCheckoutSessionRequest request) {
        log.info("[Pay] 체크아웃 세션 생성 요청. merchant={}, payUserId={}, amount={}, entryMode={}, integrationType={}",
                request.getMerchantCode(), request.getPayUserId(), request.getAmount(), request.getEntryMode(), request.getIntegrationType());
        return checkoutSessionService.createSession(request);
    }

    @PostMapping("/sessions/by-code")
    public CheckoutSessionResponse createSessionByCode(@Valid @RequestBody CreateCheckoutByCodeRequest request) {
        log.info("[Pay] 코드 기반 세션 생성 요청. merchant={}, amount={}, externalOrderId={}",
                request.getMerchantCode(), request.getAmount(), request.getExternalOrderId());
        return checkoutSessionService.createSessionByCode(request);
    }

    @PostMapping("/me/code-sessions")
    public CheckoutSessionResponse createMyCodeSession(
            @Valid @RequestBody CreateMyCodeCheckoutRequest request,
            HttpServletRequest servletRequest
    ) {
        Long loginPayUserId = payLoginTokenService.resolvePayUserId(servletRequest);
        if (loginPayUserId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Login required");
        }

        CreateCheckoutSessionRequest checkoutRequest = new CreateCheckoutSessionRequest();
        checkoutRequest.setMerchantCode(request.getMerchantCode());
        checkoutRequest.setPayUserId(loginPayUserId);
        checkoutRequest.setExternalOrderId("PAY-CODE-" + UUID.randomUUID().toString().replace("-", ""));
        checkoutRequest.setTitle(request.getTitle());
        checkoutRequest.setAmount(0);
        checkoutRequest.setSuccessUrl(request.getSuccessUrl());
        checkoutRequest.setFailUrl(request.getFailUrl());
        checkoutRequest.setEntryMode("IN_APP_CODE");
        checkoutRequest.setChannel(request.getChannel() == null || request.getChannel().isBlank() ? "QR" : request.getChannel());
        checkoutRequest.setIntegrationType("PAY_LOGIN");

        return checkoutSessionService.createSession(checkoutRequest);
    }

    @GetMapping("/{sessionToken}")
    public ResponseEntity<String> checkoutPage(@PathVariable String sessionToken, HttpServletRequest request) {
        Long loginPayUserId = payLoginTokenService.resolvePayUserId(request);
        boolean loginRequired = checkoutSessionService.requiresPayLogin(sessionToken);

        if (loginPayUserId == null && loginRequired) {
            String returnUrl = "/pay/checkout/" + sessionToken;
            String redirectUrl = "/pay/login?returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .build();
        }

        if (loginPayUserId != null) {
            checkoutSessionService.bindSessionOwner(sessionToken, loginPayUserId);
        }
        CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);

        String html = "IN_APP_CODE".equalsIgnoreCase(view.entryMode())
                ? renderInAppCodePage(view)
                : renderMerchantRedirectPage(view);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/{sessionToken}/submit")
    public ResponseEntity<Void> submitCheckout(
            @PathVariable String sessionToken,
            @RequestParam(name = "autoChargeIfInsufficient", defaultValue = "true") boolean autoChargeIfInsufficient,
            @RequestParam(name = "codeToken", required = false) String codeToken,
            HttpServletRequest request
    ) {
        Long loginPayUserId = payLoginTokenService.resolvePayUserId(request);
        boolean loginRequired = checkoutSessionService.requiresPayLogin(sessionToken);

        if (loginPayUserId == null && loginRequired) {
            String returnUrl = "/pay/checkout/" + sessionToken;
            String redirectUrl = "/pay/login?returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .build();
        }

        if (loginPayUserId != null) {
            checkoutSessionService.bindSessionOwner(sessionToken, loginPayUserId);
        }
        CheckoutProcessResult result = checkoutSessionService.processCheckout(sessionToken, autoChargeIfInsufficient, codeToken);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.redirectUrl())
                .build();
    }

    @GetMapping(value = "/{sessionToken}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> barcodeImage(@PathVariable String sessionToken) {
        try {
            CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);
            String token = view.oneTimeToken();
            if (token == null || token.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            BitMatrix matrix = new MultiFormatWriter().encode(token, BarcodeFormat.CODE_128, 260, 70);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok(output.toByteArray());
        } catch (Exception ex) {
            log.error("[Pay] 바코드 생성 실패. sessionToken={}", sessionToken, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/{sessionToken}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrImage(@PathVariable String sessionToken) {
        try {
            CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);
            String qrPayload = view.qrPayload();
            if (qrPayload == null || qrPayload.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            BitMatrix matrix = new MultiFormatWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok(output.toByteArray());
        } catch (Exception ex) {
            log.error("[Pay] QR 생성 실패. sessionToken={}", sessionToken, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String renderMerchantRedirectPage(CheckoutPageView view) {
        String title = HtmlUtils.htmlEscape(view.title());
        String merchant = HtmlUtils.htmlEscape(view.merchantCode());
        String token = HtmlUtils.htmlEscape(view.sessionToken());

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY CHECKOUT</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div id="loadingOverlay" style="display:none;position:fixed;inset:0;background:rgba(15,23,42,0.45);z-index:999;align-items:center;justify-content:center;">
                    <div style="width:100%%;max-width:340px;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;text-align:center;box-shadow:0 8px 28px rgba(18,52,86,0.15);">
                      <div style="width:34px;height:34px;border:4px solid #d1ddff;border-top-color:#1f6feb;border-radius:50%%;animation:pay-spin 0.8s linear infinite;margin:0 auto 12px;"></div>
                      <p style="margin:0;font-size:16px;font-weight:700;color:#111827;">결제 처리 중입니다</p>
                      <p style="margin:8px 0 0;font-size:13px;color:#6b7280;">창을 닫지 말고 잠시만 기다려 주세요.</p>
                    </div>
                  </div>
                  <div style="max-width:480px;margin:0 auto;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2>GORANI PAY 결제</h2>
                    <p>가맹점: %s</p>
                    <p>주문명: %s</p>
                    <p>결제금액: %s원</p>
                    <p>보유머니: %s원</p>
                    <form id="checkoutForm" method="post" action="/pay/checkout/%s/submit">
                      <label><input type="checkbox" name="autoChargeIfInsufficient" value="true" checked /> 잔액 부족 시 자동충전 후 결제</label>
                      <button id="submitButton" type="submit" style="display:block;margin-top:12px;width:100%%;padding:12px;border:none;border-radius:8px;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">결제하기</button>
                    </form>
                  </div>
                  <style>
                    @keyframes pay-spin { to { transform: rotate(360deg); } }
                  </style>
                  <script>
                    const checkoutForm = document.getElementById('checkoutForm');
                    const submitButton = document.getElementById('submitButton');
                    const loadingOverlay = document.getElementById('loadingOverlay');
                    checkoutForm.addEventListener('submit', function () {
                      submitButton.disabled = true;
                      submitButton.style.opacity = '0.7';
                      submitButton.style.cursor = 'not-allowed';
                      loadingOverlay.style.display = 'flex';
                    });
                  </script>
                </body>
                </html>
                """.formatted(merchant, title, view.amount(), view.walletBalance(), token);
    }

    private String renderInAppCodePage(CheckoutPageView view) {
        String title = HtmlUtils.htmlEscape(view.title());
        String token = HtmlUtils.htmlEscape(view.sessionToken());

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY QR 결제</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#fff;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2>GORANI PAY QR 결제</h2>
                    <p>주문명: %s</p>
                    <div style="display:flex;flex-direction:column;align-items:center;gap:10px;">
                      <img src="/pay/checkout/%s/barcode" alt="barcode" style="width:260px;height:70px;border:1px solid #e5e7eb;border-radius:6px;object-fit:contain;" />
                      <img src="/pay/checkout/%s/qr" alt="qr" style="width:200px;height:200px;border:1px solid #e5e7eb;border-radius:8px;" />
                    </div>
                    <p style="margin-top:10px;font-size:12px;color:#6b7280;">세션 토큰: %s</p>
                  </div>
                </body>
                </html>
                """.formatted(title, token, token, token);
    }
}