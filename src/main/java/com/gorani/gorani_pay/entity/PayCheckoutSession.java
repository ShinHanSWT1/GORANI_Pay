package com.gorani.gorani_pay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pay_checkout_sessions")
@Getter
@Setter
@NoArgsConstructor
public class PayCheckoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_token", nullable = false, unique = true, length = 80)
    private String sessionToken;

    @Column(name = "merchant_code", nullable = false, length = 50)
    private String merchantCode;

    @Column(name = "pay_user_id")
    private Long payUserId;

    @Column(name = "pay_account_id")
    private Long payAccountId;

    @Column(name = "external_order_id", nullable = false, length = 80)
    private String externalOrderId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "point_amount", nullable = false)
    private Integer pointAmount = 0;

    @Column(name = "coupon_discount_amount", nullable = false)
    private Integer couponDiscountAmount = 0;

    @Column(name = "pay_product_id")
    private Long payProductId;

    @Column(name = "success_url", nullable = false, length = 500)
    private String successUrl;

    @Column(name = "fail_url", nullable = false, length = 500)
    private String failUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_mode", nullable = false, length = 30)
    private PayCheckoutEntryMode entryMode = PayCheckoutEntryMode.MERCHANT_REDIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private PayCheckoutChannel channel = PayCheckoutChannel.REDIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 30)
    private PayCheckoutIntegrationType integrationType = PayCheckoutIntegrationType.INTERNAL_TOKEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayCheckoutSessionStatus status = PayCheckoutSessionStatus.CREATED;

    @Column(name = "auto_charge_used", nullable = false)
    private boolean autoChargeUsed = false;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "one_time_token", length = 120)
    private String oneTimeToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public void markCompleted(Long paymentId, boolean autoChargeUsed) {
        this.status = PayCheckoutSessionStatus.COMPLETED;
        this.paymentId = paymentId;
        this.autoChargeUsed = autoChargeUsed;
        this.errorMessage = null;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = PayCheckoutSessionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    // 코드 결제 토큰 일회성 처리
    public void consumeOneTimeToken() {
        this.oneTimeToken = null;
        this.tokenExpiresAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markExpired() {
        this.status = PayCheckoutSessionStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }
}
