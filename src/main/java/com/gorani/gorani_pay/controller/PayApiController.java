package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class PayApiController {

    private final WalletService walletService;

    @PostMapping("/charge")
    public PayAccount charge(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Integer amount = Integer.valueOf(request.get("amount").toString());
        log.info("[EcoDrivePay] 충전 요청 ID: {}, 금액: {}", userId, amount);

        return walletService.charge(userId, amount);
    }

    @PostMapping("/withdraw")
    public PayAccount withdraw(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Integer amount = Integer.valueOf(request.get("amount").toString());
        log.info("[EcoDrivePay] 출금 요청 ID: {}, 금액: {}", userId, amount);

        return walletService.withdraw(userId, amount);
    }
}