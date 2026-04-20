package com.gorani.gorani_pay.config;

import com.gorani.gorani_pay.entity.PayCheckoutIntegrationType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.checkout.merchant-policy")
public class CheckoutMerchantPolicyProperties {

    private List<PayCheckoutIntegrationType> defaultAllowedIntegrations = new ArrayList<>();
    private Map<String, MerchantPolicy> merchants = new HashMap<>();

    public MerchantPolicy resolve(String merchantCode) {
        if (merchantCode == null) {
            return null;
        }
        return merchants.get(merchantCode.toLowerCase(Locale.ROOT));
    }

    @Getter
    @Setter
    public static class MerchantPolicy {
        private List<PayCheckoutIntegrationType> allowedIntegrations = new ArrayList<>();
    }
}

