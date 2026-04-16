package com.gorani.gorani_pay.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private final RestTemplate restTemplate;

    @Value("${app.toss.secret-key}")
    private String secretKey;

    @Value("${app.toss.url}")
    private String tossUrl;

    public void confirmPayment(String paymentKey, String orderId, Integer amount) {
        String authString = secretKey + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedAuth);

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tossUrl, requestEntity, String.class);
            log.info("토스 결제 승인 성공. orderId={}, amount={}, responseBody={}", orderId, amount, response.getBody());
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("토스 결제 승인 실패(응답). orderId={}, amount={}, statusCode={}, responseBody={}",
                    orderId, amount, e.getStatusCode(), responseBody, e);
            throw new RuntimeException("토스 결제 승인 실패: status=" + e.getStatusCode() + ", body=" + responseBody);
        } catch (ResourceAccessException e) {
            log.error("토스 결제 승인 실패(네트워크). orderId={}, amount={}, message={}",
                    orderId, amount, e.getMessage(), e);
            throw new RuntimeException("토스 결제 승인 실패: 네트워크 오류 - " + e.getMessage());
        } catch (RestClientException e) {
            log.error("토스 결제 승인 실패(클라이언트). orderId={}, amount={}, message={}",
                    orderId, amount, e.getMessage(), e);
            throw new RuntimeException("토스 결제 승인 실패: 클라이언트 오류 - " + e.getMessage());
        }
    }
}
