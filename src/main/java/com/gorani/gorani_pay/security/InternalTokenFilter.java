package com.gorani.gorani_pay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${app.auth.internal-token:local-dev-token}")
    private String validToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 사용자 브라우저 직접 접근 경로 예외 처리
        if ((requestUri.equals("/pay/checkout") || requestUri.startsWith("/pay/checkout/"))
                && !requestUri.startsWith("/pay/checkout/sessions")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 로그인/OAuth 경로 예외 처리
        if (requestUri.startsWith("/pay/login")
                || requestUri.startsWith("/pay/pay-login")
                || requestUri.startsWith("/pay/oauth2")
                || requestUri.startsWith("/pay/login/oauth2")
                || requestUri.startsWith("/pay/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 시스템 경로 예외 처리
        if (requestUri.startsWith("/error")
                || requestUri.startsWith("/actuator")
                || requestUri.startsWith("/pay/webhooks")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (validToken.equals(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Invalid Internal Token");
    }
}
