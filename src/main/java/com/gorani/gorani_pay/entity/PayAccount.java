package com.gorani.gorani_pay.entity;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(name = "pay_accounts")
@Getter @Setter @NoArgsConstructor
public class PayAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_user_id")
    private Long payUserId;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "owner_name")
    private String ownerName;

    private Integer balance = 0; // V1__init.sql에 맞춤
    private String status = "ACTIVE";

    public void addBalance(Integer amount) {
        this.balance += amount;
    }

    public void deductBalance(Integer amount) {
        if (this.balance < amount) throw new RuntimeException("잔액 부족");
        this.balance -= amount;
    }
}