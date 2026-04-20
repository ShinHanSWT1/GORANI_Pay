package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.config.CheckoutMerchantPolicyProperties;
import com.gorani.gorani_pay.entity.PayCheckoutIntegrationType;
import com.gorani.gorani_pay.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckoutMerchantPolicyService {

    private final CheckoutMerchantPolicyProperties policyProperties;

    public void validate(String merchantCode, PayCheckoutIntegrationType integrationType) {
        CheckoutMerchantPolicyProperties.MerchantPolicy merchantPolicy = policyProperties.resolve(merchantCode);
        List<PayCheckoutIntegrationType> allowed = merchantPolicy == null
                ? policyProperties.getDefaultAllowedIntegrations()
                : merchantPolicy.getAllowedIntegrations();

        if (allowed == null || allowed.isEmpty() || !allowed.contains(integrationType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported integrationType for merchant");
        }
    }
}

