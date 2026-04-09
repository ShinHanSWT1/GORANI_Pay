package com.gorani.gorani_pay.service;

import com.gorani.gorani_pay.entity.PayIdempotency;
import com.gorani.gorani_pay.repository.PayIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final PayIdempotencyRepository idempotencyRepository;

    // 기존 요청 확인
    public Optional<PayIdempotency> findByKey(String key) {
        return idempotencyRepository.findByIdempotencyKey(key);
    }

    // 저장
    public void save(
            String key,
            String externalOrderId,
            String responsePayload,
            String status
    ) {
        PayIdempotency entity = new PayIdempotency();
        entity.setIdempotencyKey(key);
        entity.setExternalOrderId(externalOrderId);
        entity.setResponsePayload(responsePayload);
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());

        idempotencyRepository.save(entity);
    }
}