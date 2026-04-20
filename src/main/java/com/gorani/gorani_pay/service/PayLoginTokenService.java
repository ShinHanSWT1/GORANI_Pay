package com.gorani.gorani_pay.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PayLoginTokenService {
    public static final String AUTH_COOKIE_NAME = "GORANI_PAY_AUTH";
    public static final String RETURN_URL_COOKIE_NAME = "GORANI_PAY_RETURN_URL";
    private static final int EXPIRE_SECONDS = 60 * 60;

    private final Map<String, LoginSession> loginSessionStore = new ConcurrentHashMap<>();

    public void issueLoginToken(HttpServletResponse response, Long payUserId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(EXPIRE_SECONDS);
        loginSessionStore.put(token, new LoginSession(payUserId, expiresAt));

        Cookie cookie = new Cookie(AUTH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(EXPIRE_SECONDS);
        response.addCookie(cookie);
    }

    public Long resolvePayUserId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (!AUTH_COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            LoginSession session = loginSessionStore.get(cookie.getValue());
            if (session == null) {
                return null;
            }
            if (LocalDateTime.now().isAfter(session.expiresAt())) {
                loginSessionStore.remove(cookie.getValue());
                return null;
            }
            return session.payUserId();
        }
        return null;
    }

    public void writeReturnUrlCookie(HttpServletResponse response, String returnUrl) {
        Cookie cookie = new Cookie(RETURN_URL_COOKIE_NAME, URLEncoder.encode(returnUrl, StandardCharsets.UTF_8));
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(5 * 60);
        response.addCookie(cookie);
    }

    public String readReturnUrl(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
                if (RETURN_URL_COOKIE_NAME.equals(cookie.getName())) {
                return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
                }
        }
        return null;
    }

    public void clearReturnUrlCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(RETURN_URL_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private record LoginSession(Long payUserId, LocalDateTime expiresAt) {
    }
}
