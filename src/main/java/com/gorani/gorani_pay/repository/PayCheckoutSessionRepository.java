package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayCheckoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayCheckoutSessionRepository extends JpaRepository<PayCheckoutSession, Long> {

    Optional<PayCheckoutSession> findBySessionToken(String sessionToken);

    Optional<PayCheckoutSession> findByOneTimeToken(String oneTimeToken);

    Optional<PayCheckoutSession> findByMerchantCodeAndExternalOrderId(String merchantCode, String externalOrderId);
}
