package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayPaymentRepository extends JpaRepository<PayPayment, Long> {
}