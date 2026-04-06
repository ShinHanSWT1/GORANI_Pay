package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface PayAccountRepository extends JpaRepository<PayAccount, Long> {
    // 비관적 락(Pessimistic Lock)으로 동시성 제어 (기존 getWalletForUpdate 대체)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayAccount> findByPayUserId(Long payUserId);
}