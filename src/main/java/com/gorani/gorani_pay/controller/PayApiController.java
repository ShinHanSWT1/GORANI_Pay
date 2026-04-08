package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayPayment;
import com.gorani.gorani_pay.service.PaymentService;
import com.gorani.gorani_pay.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayApiController {

    private final WalletService walletService;
    private final PaymentService paymentService;

    // Wallet
    // 충전
    @PostMapping("/charge")
    public PayAccount charge(@RequestBody Map<String, Object> request) {
        Long payUserId = Long.valueOf(request.get("payUserId").toString());
        Integer amount = Integer.valueOf(request.get("amount").toString());
        log.info("[Pay] 충전 요청 - payUserId={}, amount={}", payUserId, amount);

        return walletService.charge(payUserId, amount);
    }

    // 출금
    @PostMapping("/withdraw")
    public PayAccount withdraw(@RequestBody Map<String, Object> request) {
        Long payUserId = Long.valueOf(request.get("payUserId").toString());
        Integer amount = Integer.valueOf(request.get("amount").toString());
        log.info("[Pay] 출금 요청 - payUserId={}, amount={}", payUserId, amount);

        return walletService.withdraw(payUserId, amount);
    }

    // 계좌 조회
    @GetMapping("/account/{payUserId}")
    public PayAccount getAccount(@PathVariable Long payUserId) {
        log.info("[Pay] 계좌 조회 - payUserId={}", payUserId);

        return walletService.getAccount(payUserId);
    }

    // Payment
    // 결제 생성
    @PostMapping("/payments")
    public PayPayment createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PayPayment request
    ) {

        log.info("[Pay] 결제 생성 - key={}, payUserId={}, amount={}",
                idempotencyKey,
                request.getPayUserId(),
                request.getAmount());

        return paymentService.createPayment(request, idempotencyKey);
    }

    // 결제 완료
    @PostMapping("/payments/{paymentId}/complete")
    public PayPayment completePayment(
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable Long paymentId
    ) {
        log.info("[Pay] 결제 완료 - key={}, paymentId={}", key, paymentId);

        return paymentService.completePayment(paymentId, key);
    }

    // 결제 취소
    @PostMapping("/payments/{paymentId}/cancel")
    public PayPayment cancelPayment(@PathVariable Long paymentId) {
        log.info("[Pay] 결제 취소 - paymentId={}", paymentId);

        return paymentService.cancelPayment(paymentId);
    }

    @PostMapping("/payments/{paymentId}/refund")
    public PayPayment refund(
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable Long paymentId
    ) {
        log.info("[Pay] 환불 요청 - key={}, paymentId={}", key, paymentId);

        return paymentService.refund(paymentId, key);
    }
}