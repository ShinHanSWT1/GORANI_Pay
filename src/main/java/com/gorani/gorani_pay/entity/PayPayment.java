package com.gorani.gorani_pay.entity;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(name = "pay_payments")
@Getter @Setter @NoArgsConstructor
public class PayPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_user_id")
    private Long payUserId;

    @Column(name = "pay_account_id")
    private Long payAccountId;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "payment_type")
    private String paymentType;

    private String title;
    private Integer amount;
    private String status = "READY"; // READY(INIT), PENDING, COMPLETED, CANCELED
}