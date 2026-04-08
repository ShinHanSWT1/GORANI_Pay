package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.dto.PaymentResponse;
import com.gorani.gorani_pay.entity.*;
import com.gorani.gorani_pay.repository.*;
import com.gorani.gorani_pay.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PayPaymentRepository paymentRepository;
    private final PayAccountRepository accountRepository;
    private final PayTransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final PayRefundRepository refundRepository;

    // 결제 생성
    @Transactional
    public PayPayment createPayment(PayPayment request, String idempotencyKey) {

        Optional<PayIdempotency> existing = idempotencyService.findByKey(idempotencyKey);

        if (existing.isPresent()) {
            // 기존 응답 반환
            return JsonUtil.fromJson(
                    existing.get().getResponsePayload(),
                    PayPayment.class
            );
        }

        request.setStatus("READY");
        request.setCreatedAt(LocalDateTime.now());

        PayPayment saved = paymentRepository.save(request);

        // 응답 JSON 저장
        idempotencyService.save(
                idempotencyKey,
                saved.getExternalOrderId(),
                JsonUtil.toJson(new PaymentResponse(saved)),
                "COMPLETED"
        );

        return saved;
    }

    // 결제 완료
    @Transactional
    public PayPayment completePayment(Long paymentId, String key) {

        PayPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("결제 없음"));

        if (!payment.getStatus().equals("READY")) {
            throw new RuntimeException("이미 처리된 결제");
        }

        PayAccount account = accountRepository.findByPayUserId(payment.getPayUserId())
                .orElseThrow(() -> new RuntimeException("계좌 없음"));

        if (account.getBalance() < payment.getAmount()) {
            throw new RuntimeException("잔액 부족");
        }

        // Transaction 생성
        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setPayPaymentId(payment.getId());
        tx.setTransactionType("PAYMENT");
        tx.setDirection("DEBIT");
        tx.setAmount(payment.getAmount());
        tx.setOccurredAt(LocalDateTime.now());

        transactionRepository.save(tx);

        // 잔액 차감
        account.deductBalance(payment.getAmount());

        // Ledger 기록
        ledgerService.record(
                tx.getId(),
                account.getId(),
                "DEBIT",
                payment.getAmount(),
                account.getBalance(),
                "PAYMENT",
                payment.getId()
        );

        // 상태 변경
        payment.setStatus("COMPLETED");
        payment.setApprovedAt(LocalDateTime.now());

        return payment;
    }

    // 결제 취소
    @Transactional
    public PayPayment cancelPayment(Long paymentId) {

        PayPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("결제 없음"));

        if (payment.getStatus().equals("COMPLETED")) {
            throw new RuntimeException("완료된 결제 취소 불가");
        }

        payment.setStatus("CANCELED");

        return payment;
    }

    @Transactional
    public PayPayment refund(Long paymentId, String key) {

        Optional<PayIdempotency> existing = idempotencyService.findByKey(key);

        if (existing.isPresent()) {
            return JsonUtil.fromJson(
                    existing.get().getResponsePayload(),
                    PayPayment.class
            );
        }

        PayPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("결제 없음"));

        if (!payment.getStatus().equals("COMPLETED")) {
            throw new RuntimeException("환불 불가능 상태");
        }

        PayAccount account = accountRepository.findByPayUserId(payment.getPayUserId())
                .orElseThrow(() -> new RuntimeException("계좌 없음"));

        // Transaction
        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setPayPaymentId(payment.getId());
        tx.setTransactionType("REFUND");
        tx.setDirection("CREDIT");
        tx.setAmount(payment.getAmount());
        tx.setOccurredAt(LocalDateTime.now());

        transactionRepository.save(tx);

        // balance
        account.addBalance(payment.getAmount());

        // ledger
        ledgerService.record(
                tx.getId(),
                account.getId(),
                "CREDIT",
                payment.getAmount(),
                account.getBalance(),
                "REFUND",
                payment.getId()
        );

        // refund table
        PayRefund refund = new PayRefund();
        refund.setPayPaymentId(payment.getId());
        refund.setRefundAmount(payment.getAmount());
        refund.setStatus("COMPLETED");
        refundRepository.save(refund);

        // 상태 변경
        payment.setStatus("REFUNDED");

        // idempotency 저장
        idempotencyService.save(
                key,
                payment.getExternalOrderId(),
                JsonUtil.toJson(payment),
                "REFUNDED"
        );

        return payment;
    }
}