package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayLedger;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayLedgerRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    private final PayAccountRepository accountRepository;
    private final PayTransactionRepository transactionRepository;
    private final PayLedgerRepository ledgerRepository;
    private final LedgerService ledgerService;

    public PayAccount getAccount(Long payUserId) {
        PayAccount account = accountRepository.findByPayUserId(payUserId)
                .orElseGet(() -> {
                    PayAccount newAccount = new PayAccount();
                    newAccount.setPayUserId(payUserId);
                    newAccount.setBalance(0);
                    newAccount.setPoints(0L);

                    // DB에 저장하고 바로 반환
                    return accountRepository.save(newAccount);
                });

        // 이후 로직은 동일
        Long monthlyUsage = getMonthlyUsage(account.getId());
        account.setMonthUsage(monthlyUsage);
        return account;
    }

    public List<PayTransaction> getTransactions(Long userId) {
        PayAccount account = getAccount(userId);
        return transactionRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    public List<PayLedger> getLedgerEntries(Long userId) {
        PayAccount account = getAccount(userId);
        return ledgerRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    public PayAccount charge(Long userId, Integer amount) {
        PayAccount account = getAccount(userId);

        try {
            account.addBalance(amount);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setTransactionType("CHARGE");
        tx.setDirection("CREDIT");
        tx.setAmount(amount);
        tx.setOccurredAt(LocalDateTime.now());
        transactionRepository.save(tx);

        ledgerService.record(
                tx.getId(),
                account.getId(),
                "CREDIT",
                amount,
                account.getBalance(),
                "CHARGE",
                account.getId()
        );

        return account;
    }

    public PayAccount withdraw(Long userId, Integer amount) {
        PayAccount account = getAccount(userId);

        try {
            account.deductBalance(amount);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        PayTransaction tx = new PayTransaction();
        tx.setPayAccountId(account.getId());
        tx.setTransactionType("WITHDRAW");
        tx.setDirection("DEBIT");
        tx.setAmount(amount);
        tx.setOccurredAt(LocalDateTime.now());
        transactionRepository.save(tx);

        ledgerService.record(
                tx.getId(),
                account.getId(),
                "DEBIT",
                amount,
                account.getBalance(),
                "WITHDRAW",
                account.getId()
        );

        return account;
    }

    public Long getMonthlyUsage(Long payAccountId) {
        LocalDateTime startOfMonth = java.time.YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = java.time.YearMonth.now().atEndOfMonth().atTime(java.time.LocalTime.MAX);

        return transactionRepository.sumUsageByPeriod(payAccountId, startOfMonth, endOfMonth);
    }
}
