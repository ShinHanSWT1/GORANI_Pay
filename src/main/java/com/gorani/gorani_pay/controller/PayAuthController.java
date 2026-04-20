package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.service.PayLoginTokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class PayAuthController {

    private final PayLoginTokenService payLoginTokenService;

    @GetMapping({"/pay/login", "/pay/pay-login"})
    public ResponseEntity<Void> login(@RequestParam(required = false) String returnUrl, HttpServletResponse response) {
        String resolvedReturnUrl = (returnUrl == null || returnUrl.isBlank()) ? "/pay/checkout" : returnUrl;
        payLoginTokenService.writeReturnUrlCookie(response, resolvedReturnUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/pay/oauth2/authorization/kakao")
                .build();
    }

    @GetMapping("/pay/auth/fail")
    public ResponseEntity<String> fail(@RequestParam(required = false) String error) {
        String message = (error == null || error.isBlank()) ? "로그인 실패" : error;
        String retryUrl = UriComponentsBuilder.fromPath("/pay/login")
                .queryParam("returnUrl", "/pay/checkout")
                .build()
                .toUriString();
        String html = """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="UTF-8"><title>GORANI PAY 로그인 실패</title></head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:420px;margin:0 auto;background:white;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2>GORANI PAY 로그인 실패</h2>
                    <p>오류: %s</p>
                    <a href="%s">다시 로그인</a>
                  </div>
                </body>
                </html>
                """.formatted(message, retryUrl);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(html);
    }
}
