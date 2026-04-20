package com.gorani.gorani_pay.repository;

import com.gorani.gorani_pay.entity.PayUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayUserRepository extends JpaRepository<PayUser, Long> {

    Optional<PayUser> findByExternalUserId(Long externalUserId);

    Optional<PayUser> findByOauthProviderAndOauthUserId(String oauthProvider, String oauthUserId);

    Optional<PayUser> findByEmailIgnoreCase(String email);
}
