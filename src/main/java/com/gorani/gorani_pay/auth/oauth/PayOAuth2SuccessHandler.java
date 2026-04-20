package com.gorani.gorani_pay.auth.oauth;

import com.gorani.gorani_pay.entity.PayUser;
import com.gorani.gorani_pay.service.PayLoginTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PayOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final PayOAuthLoginService payOAuthLoginService;
    private final PayLoginTokenService payLoginTokenService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = Objects.requireNonNull(oAuth2User).getAttributes();
        PayOAuthAttributes payOAuthAttributes = PayOAuthAttributes.ofKakao(attributes);
        PayUser payUser = payOAuthLoginService.loginOrRegister(payOAuthAttributes);

        payLoginTokenService.issueLoginToken(response, payUser.getId());
        String returnUrl = payLoginTokenService.readReturnUrl(request);
        payLoginTokenService.clearReturnUrlCookie(response);
        response.sendRedirect(returnUrl == null || returnUrl.isBlank() ? "/pay/checkout" : returnUrl);
    }
}
