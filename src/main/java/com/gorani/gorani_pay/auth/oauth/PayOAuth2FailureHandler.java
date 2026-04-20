package com.gorani.gorani_pay.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class PayOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        log.error("Pay OAuth login failed. requestURI={}, message={}", request.getRequestURI(), exception.getMessage());
        String redirectUrl = UriComponentsBuilder.fromPath("/pay/auth/fail")
                .queryParam("error", "oauth_login_failed")
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
