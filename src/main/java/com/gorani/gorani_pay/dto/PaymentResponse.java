package com.gorani.gorani_pay.dto;

import com.gorani.gorani_pay.entity.PayPayment;
import lombok.Getter;

@Getter
public class PaymentResponse {

    private Long id;
    private Long payUserId;
    private Integer amount;
    private String status;

    public PaymentResponse(PayPayment payment) {
        this.id = payment.getId();
        this.payUserId = payment.getPayUserId();
        this.amount = payment.getAmount();
        this.status = payment.getStatus();
    }
}