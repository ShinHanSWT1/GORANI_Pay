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

        // Hosted Checkout 페이지/submit은 사용자 브라우저에서 직접 접근하므로 내부 토큰 검증에서 제외한다.
        // 단, 세션 생성 API(/pay/checkout/sessions)는 가맹점 백엔드 경로이므로 계속 내부 토큰 검증을 적용한다.
        if (requestUri.startsWith("/pay/checkout/") && !requestUri.startsWith("/pay/checkout/sessions")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 예외 처리 포워드 경로(/error)는 내부 토큰 검증 없이 통과시켜 실제 오류 응답을 반환한다.
        if (requestUri.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (requestUri.startsWith("/actuator") || requestUri.startsWith("/pay/webhooks")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);

        if (validToken.equals(token)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid Internal Token");
        }
    }
}
