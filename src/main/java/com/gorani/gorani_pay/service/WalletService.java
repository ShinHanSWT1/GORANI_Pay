package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final PayAccountRepository accountRepository;

    // 충전
    @Transactional
    public PayAccount charge(Long userId, Integer amount) {
        PayAccount account = accountRepository.findByPayUserId(userId)
                .orElseThrow(() -> new RuntimeException("계좌를 찾을 수 없습니다."));
        account.addBalance(amount);
        // TODO: Transaction 및 Ledger 기록 추가 (V1__init.sql 참조)
        return account;
    }

    // 출금
    @Transactional
    public PayAccount withdraw(Long userId, Integer amount) {
        PayAccount account = accountRepository.findByPayUserId(userId)
                .orElseThrow(() -> new RuntimeException("계좌를 찾을 수 없습니다."));
        account.deductBalance(amount);
        // TODO: Transaction 및 Ledger 기록 추가
        return account;
    }
}