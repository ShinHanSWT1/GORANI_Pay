package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.client.TossPaymentClient;
import com.gorani.gorani_pay.dto.ChargeConfirmRequest;
import com.gorani.gorani_pay.dto.CreateAccountRequest;
import com.gorani.gorani_pay.entity.PayAccount;
import com.gorani.gorani_pay.entity.PayLedger;
import com.gorani.gorani_pay.entity.PayTransaction;
import com.gorani.gorani_pay.entity.PayUser;
import com.gorani.gorani_pay.exception.ApiException;
import com.gorani.gorani_pay.repository.PayAccountRepository;
import com.gorani.gorani_pay.repository.PayLedgerRepository;
import com.gorani.gorani_pay.repository.PayTransactionRepository;
import com.gorani.gorani_pay.repository.PayUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// 지갑 도메인 서비스
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    // 계좌 저장소
    private final PayAccountRepository accountRepository;
    // 거래 저장소
    private final PayTransactionRepository transactionRepository;
    // 원장 저장소
    private final PayLedgerRepository ledgerRepository;
    // 원장 기록 서비스
    private final LedgerService ledgerService;
    // 결제 사용자 저장소
    private final PayUserRepository payUserRepository;
    // Toss PG 연동
    private final TossPaymentClient tossPaymentClient;

    // Redis 분산 락을 위한 클라이언트
    private final RedissonClient redissonClient;
    private static final String LOCK_KEY_PREFIX = "lock:wallet:";

    // 계좌 생성
    public PayAccount createAccount(CreateAccountRequest request) {
        PayUser payUser = payUserRepository.findByExternalUserId(request.getExternalUserId())
                .orElseGet(() -> createPayUser(request));

        PayAccount existing = accountRepository.findByPayUserId(payUser.getId()).orElse(null);
        if (existing != null) {
            existing.setMonthUsage(getMonthlyUsage(existing.getId()));
            return existing;
        }

        PayAccount account = new PayAccount();
        account.setPayUserId(payUser.getId());
        account.setOwnerName(request.getOwnerName().trim());
        account.setBankCode(normalizeBlankToNull(request.getBankCode()));
        account.setAccountNumber(resolveAccountNumber(request.getAccountNumber()));
        account.setBalance(0);
        account.setPoints(0L);
        account.setStatus("ACTIVE");
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        PayAccount saved = accountRepository.save(account);
        saved.setMonthUsage(0L);
        return saved;
    }

    // 계좌 조회
    public PayAccount getAccount(Long payUserId) {
        PayAccount account = findAccountOrThrow(payUserId);
        account.setMonthUsage(getMonthlyUsage(account.getId()));
        return account;
    }

    // 외부 사용자 연계키 기반 계좌 조회
    public PayAccount getAccountByExternalUserId(Long externalUserId) {
        PayUser payUser = payUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pay user not found"));
        return getAccount(payUser.getId());
    }

    // 거래내역 조회
    public List<PayTransaction> getTransactions(Long payUserId) {
        PayAccount account = findAccountOrThrow(payUserId);
        return transactionRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    // 원장내역 조회
    public List<PayLedger> getLedgerEntries(Long payUserId) {
        PayAccount account = findAccountOrThrow(payUserId);
        return ledgerRepository.findByPayAccountIdOrderByIdDesc(account.getId());
    }

    // 충전
    @Transactional
    public PayAccount charge(Long payUserId, Integer amount) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + payUserId);
        try {
            // 5초 동안 락 획득 시도, 락 획득 후 10초간 유지
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "결제가 진행 중입니다. 잠시 후 다시 시도해 주세요.");
            }

            PayAccount account = findAccountOrThrow(payUserId);
            account.addBalance(amount);

            PayTransaction tx = new PayTransaction();
            tx.setPayAccountId(account.getId());
            tx.setTransactionType("CHARGE");
            tx.setDirection("CREDIT");
            tx.setAmount(amount);
            tx.setOccurredAt(LocalDateTime.now());
            transactionRepository.save(tx);

            ledgerService.record(tx.getId(), account.getId(), "CREDIT", amount,
                    account.getBalance(), "CHARGE", account.getId());

            account.setMonthUsage(getMonthlyUsage(account.getId()));
            return account;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "작업 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock(); // 작업 완료 후 반드시 락 해제
            }
        }
    }

    // 출금
    @Transactional
    public PayAccount withdraw(Long payUserId, Integer amount) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + payUserId);
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "다른 요청이 처리 중입니다.");
            }

            PayAccount account = findAccountOrThrow(payUserId);
            account.deductBalance(amount);

            PayTransaction tx = new PayTransaction();
            tx.setPayAccountId(account.getId());
            tx.setTransactionType("WITHDRAW");
            tx.setDirection("DEBIT");
            tx.setAmount(amount);
            tx.setOccurredAt(LocalDateTime.now());
            transactionRepository.save(tx);

            ledgerService.record(tx.getId(), account.getId(), "DEBIT", amount,
                    account.getBalance(), "WITHDRAW", account.getId());

            account.setMonthUsage(getMonthlyUsage(account.getId()));
            return account;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "작업 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // 월 사용액 조회 (PAYMENT 거래만 집계)
    public Long getMonthlyUsage(Long payAccountId) {
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = YearMonth.now().atEndOfMonth().atTime(LocalTime.MAX);

        Long usage = transactionRepository.sumUsageByPeriod(payAccountId, startOfMonth, endOfMonth);
        return usage == null ? 0L : usage;
    }

    // 계좌 조회 공통
    private PayAccount findAccountOrThrow(Long payUserId) {
        return accountRepository.findByPayUserId(payUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // 결제 사용자 생성
    private PayUser createPayUser(CreateAccountRequest request) {
        PayUser payUser = new PayUser();
        payUser.setExternalUserId(request.getExternalUserId());
        payUser.setUserName(request.getUserName().trim());
        payUser.setEmail(request.getEmail().trim());
        payUser.setStatus("ACTIVE");
        payUser.setCreatedAt(LocalDateTime.now());
        payUser.setUpdatedAt(LocalDateTime.now());
        return payUserRepository.save(payUser);
    }

    // 계좌번호 결정
    private String resolveAccountNumber(String accountNumber) {
        String normalized = normalizeBlankToNull(accountNumber);
        if (normalized != null) {
            return normalized;
        }
        return "GP-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    // 공백 문자열 정리
    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

//    @Transactional
//    public PayAccount confirmCharge(ChargeConfirmRequest request) {
//        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + request.payUserId());
//        try {
//            if (!lock.tryLock(10, 20, TimeUnit.SECONDS)) { // 충전은 중요하므로 좀 더 여유있게 대기
//                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "충전 처리가 이미 진행 중입니다.");
//            }
//
//            // 1. 토스 서버 승인 요청
//            tossPaymentClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());
//
//            // 2. 지갑 조회 및 잔액 증가
//            PayAccount account = findAccountOrThrow(request.payUserId());
//            account.addBalance(request.amount());
//
//            // 3. 내역 저장
//            PayTransaction tx = new PayTransaction();
//            tx.setPayAccountId(account.getId());
//            tx.setTransactionType("CHARGE");
//            tx.setDirection("CREDIT");
//            tx.setAmount(request.amount());
//            tx.setOccurredAt(LocalDateTime.now());
//            transactionRepository.save(tx);
//
//            ledgerService.record(tx.getId(), account.getId(), "CREDIT", request.amount(),
//                    account.getBalance(), "TOSS_CHARGE", account.getId());
//
//            return account;
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "충전 확인 중 오류가 발생했습니다.");
//        } finally {
//            if (lock.isHeldByCurrentThread()) {
//                lock.unlock();
//            }
//        }
//    }

    @Transactional
    public PayAccount confirmCharge(ChargeConfirmRequest request) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + request.payUserId());
        try {
            if (!lock.tryLock(10, 20, TimeUnit.SECONDS)) { // 충전은 중요하므로 좀 더 여유있게 대기
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "충전 처리가 이미 진행 중입니다.");
            }

            // 1. 토스 서버 승인 요청
            tossPaymentClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());

            // 2. 지갑 조회 및 잔액 증가
            PayAccount account = findAccountOrThrow(request.payUserId());
            account.addBalance(request.amount());

            // 3. 내역 저장
            PayTransaction tx = new PayTransaction();
            tx.setPayAccountId(account.getId());
            tx.setTransactionType("CHARGE");
            tx.setDirection("CREDIT");
            tx.setAmount(request.amount());
            tx.setOccurredAt(LocalDateTime.now());
            transactionRepository.save(tx);

            ledgerService.record(tx.getId(), account.getId(), "CREDIT", request.amount(),
                    account.getBalance(), "TOSS_CHARGE", account.getId());

            return account;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "충전 확인 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
