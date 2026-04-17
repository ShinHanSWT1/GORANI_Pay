package com.gorani.gorani_pay.controller;

import com.gorani.gorani_pay.dto.CheckoutPageView;
import com.gorani.gorani_pay.dto.CheckoutProcessResult;
import com.gorani.gorani_pay.dto.CheckoutSessionResponse;
import com.gorani.gorani_pay.dto.CreateCheckoutByCodeRequest;
import com.gorani.gorani_pay.dto.CreateCheckoutSessionRequest;
import com.gorani.gorani_pay.service.CheckoutSessionService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;

@Slf4j
@RestController
@RequestMapping("/pay/checkout")
@RequiredArgsConstructor
public class PayCheckoutController {

    private final CheckoutSessionService checkoutSessionService;

    @PostMapping("/sessions")
    public CheckoutSessionResponse createSession(@Valid @RequestBody CreateCheckoutSessionRequest request) {
        // 가맹점 백엔드가 호출하는 결제 세션 생성 API
        log.info("[Pay] checkout session 생성 요청. merchant={}, payUserId={}, amount={}, entryMode={}",
                request.getMerchantCode(), request.getPayUserId(), request.getAmount(), request.getEntryMode());
        return checkoutSessionService.createSession(request);
    }

    @PostMapping("/sessions/by-code")
    public CheckoutSessionResponse createSessionByCode(@Valid @RequestBody CreateCheckoutByCodeRequest request) {
        // 가맹점이 사용자 결제코드 스캔 후 결제요청 세션을 생성하는 API
        log.info("[Pay] 코드 기반 checkout session 생성 요청. merchant={}, amount={}, externalOrderId={}",
                request.getMerchantCode(), request.getAmount(), request.getExternalOrderId());
        return checkoutSessionService.createSessionByCode(request);
    }

    @GetMapping("/{sessionToken}")
    public ResponseEntity<String> checkoutPage(@PathVariable String sessionToken) {
        CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);

        // 진입 모드에 따라 렌더링 페이지를 분리한다.
        String html = "IN_APP_CODE".equalsIgnoreCase(view.entryMode())
                ? renderInAppCodePage(view)
                : renderMerchantRedirectPage(view);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @PostMapping("/{sessionToken}/submit")
    public ResponseEntity<Void> submitCheckout(
            @PathVariable String sessionToken,
            @RequestParam(name = "autoChargeIfInsufficient", defaultValue = "true") boolean autoChargeIfInsufficient,
            @RequestParam(name = "codeToken", required = false) String codeToken
    ) {
        // 결제 처리 후 success/fail URL로 302 리다이렉트한다.
        CheckoutProcessResult result = checkoutSessionService.processCheckout(sessionToken, autoChargeIfInsufficient, codeToken);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.redirectUrl())
                .build();
    }

    @GetMapping(value = "/{sessionToken}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> barcodeImage(@PathVariable String sessionToken) {
        try {
            CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);
            String token = view.oneTimeToken();
            if (token == null || token.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            // 바코드 크기를 작게 조정해 결제창에서 과하게 보이지 않도록 한다.
            BitMatrix matrix = new MultiFormatWriter().encode(token, BarcodeFormat.CODE_128, 260, 70);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok(output.toByteArray());
        } catch (Exception ex) {
            log.error("[Pay] 바코드 생성 실패. sessionToken={}", sessionToken, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/{sessionToken}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrImage(@PathVariable String sessionToken) {
        try {
            CheckoutPageView view = checkoutSessionService.getPageView(sessionToken);
            String qrPayload = view.qrPayload();
            if (qrPayload == null || qrPayload.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            BitMatrix matrix = new MultiFormatWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return ResponseEntity.ok(output.toByteArray());
        } catch (Exception ex) {
            log.error("[Pay] QR 생성 실패. sessionToken={}", sessionToken, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String renderMerchantRedirectPage(CheckoutPageView view) {
        String title = HtmlUtils.htmlEscape(view.title());
        String merchant = HtmlUtils.htmlEscape(view.merchantCode());
        String token = HtmlUtils.htmlEscape(view.sessionToken());
        String status = HtmlUtils.htmlEscape(view.status());
        String expiresAt = HtmlUtils.htmlEscape(String.valueOf(view.expiresAt()));

        boolean processable = "CREATED".equals(view.status());
        String actionButton = processable
                ? """
                    <button type="submit" style="width:100%;padding:12px;border:none;border-radius:8px;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">
                      결제하기
                    </button>
                  """
                : """
                    <button type="button" disabled style="width:100%;padding:12px;border:none;border-radius:8px;background:#c5c9d3;color:#fff;font-weight:700;">
                      처리 불가 상태
                    </button>
                  """;

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY CHECKOUT</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:480px;margin:0 auto;background:white;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2 style="margin:0 0 12px 0;">GORANI PAY 결제</h2>
                    <p style="margin:0 0 12px 0;color:#6b7280;">가맹점: %s</p>
                    <div style="padding:12px;border:1px solid #e5e7eb;border-radius:8px;margin-bottom:12px;">
                      <div style="margin-bottom:8px;">주문명: <strong>%s</strong></div>
                      <div style="margin-bottom:8px;">결제금액: <strong>%s원</strong></div>
                      <div style="margin-bottom:8px;">보유 머니: <strong>%s원</strong></div>
                      <div style="margin-bottom:8px;">자동 충전 예정 금액: <strong>%s원</strong></div>
                      <div style="font-size:12px;color:#6b7280;">상태: %s / 만료시각: %s</div>
                    </div>
                    <form method="post" action="/pay/checkout/%s/submit">
                      <label style="display:block;margin-bottom:12px;font-size:14px;">
                        <input type="checkbox" name="autoChargeIfInsufficient" value="true" checked />
                        잔액 부족 시 자동충전 후 결제
                      </label>
                      %s
                    </form>
                  </div>
                </body>
                </html>
                """.formatted(
                merchant,
                title,
                view.amount(),
                view.walletBalance(),
                view.expectedAutoChargeAmount(),
                status,
                expiresAt,
                token,
                actionButton
        );
    }

    private String renderInAppCodePage(CheckoutPageView view) {
        String title = HtmlUtils.htmlEscape(view.title());
        String merchant = HtmlUtils.htmlEscape(view.merchantCode());
        String token = HtmlUtils.htmlEscape(view.sessionToken());
        String status = HtmlUtils.htmlEscape(view.status());
        String rawCodeToken = view.oneTimeToken();
        String rawQrPayload = view.qrPayload();
        String accountNumber = HtmlUtils.htmlEscape(view.accountNumber() == null ? "-" : view.accountNumber());
        String bankCode = HtmlUtils.htmlEscape(view.bankCode() == null ? "-" : view.bankCode());
        String ownerName = HtmlUtils.htmlEscape(view.ownerName() == null ? "-" : view.ownerName());
        String codeToken = HtmlUtils.htmlEscape(String.valueOf(rawCodeToken));
        String qrPayload = HtmlUtils.htmlEscape(String.valueOf(rawQrPayload));

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>GORANI PAY 코드결제</title>
                </head>
                <body style="font-family:Arial,sans-serif;background:#f6f8fb;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:white;border:1px solid #d9deea;border-radius:12px;padding:20px;">
                    <h2 style="margin:0 0 10px 0;">GORANI PAY 코드결제</h2>
                    <p style="margin:0 0 14px 0;color:#6b7280;">가맹점: %s / 주문: %s / 금액: %s원</p>

                    <div style="display:flex;gap:8px;margin-bottom:12px;">
                      <button id="tabCode" style="flex:1;padding:10px;border-radius:8px;border:1px solid #1f6feb;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">바코드 · QR</button>
                      <button id="tabScan" style="flex:1;padding:10px;border-radius:8px;border:1px solid #c5c9d3;background:#fff;color:#111;font-weight:700;cursor:pointer;">QR 스캔</button>
                    </div>

                    <div id="panelCode" style="padding:12px;border:1px solid #e5e7eb;border-radius:8px;display:block;">
                      <div style="font-size:13px;color:#6b7280;margin-bottom:8px;">매장 스캐너가 읽을 수 있는 바코드/QR 입니다. 매장 단말이 코드를 읽으면 즉시 결제가 승인됩니다.</div>
                      <div style="display:flex;flex-direction:column;align-items:center;gap:10px;background:#ffffff;padding:10px;border-radius:8px;">
                        <img src="/pay/checkout/%s/barcode" alt="barcode" style="width:260px;height:70px;border:1px solid #e5e7eb;border-radius:6px;object-fit:contain;" />
                        <div style="font-size:12px;color:#6b7280;">%s</div>
                        <img src="/pay/checkout/%s/qr" alt="qr" style="width:200px;height:200px;border:1px solid #e5e7eb;border-radius:8px;" />
                        <div style="font-size:12px;color:#6b7280;">QR: %s</div>
                      </div>
                      <div style="margin-top:12px;padding:14px;border:1px solid #dbeafe;border-radius:12px;background:linear-gradient(135deg,#eff6ff,#f8fafc);">
                        <div style="font-size:12px;color:#475569;margin-bottom:8px;">결제 예정 출금 계좌</div>
                        <div style="padding:14px;border-radius:12px;background:linear-gradient(135deg,#0f172a,#1e293b);color:#fff;">
                          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                            <span style="font-size:11px;opacity:0.75;">GORANI PAY ACCOUNT</span>
                            <span style="font-size:11px;opacity:0.75;">BANK %s</span>
                          </div>
                          <div style="font-size:18px;letter-spacing:1px;font-weight:700;margin-bottom:8px;">%s</div>
                          <div style="display:flex;justify-content:space-between;align-items:center;font-size:12px;opacity:0.9;">
                            <span>예금주 %s</span>
                            <span>잔액 %s원</span>
                          </div>
                          <div style="display:flex;justify-content:space-between;align-items:center;font-size:12px;opacity:0.9;margin-top:6px;">
                            <span>자동 충전 예정</span>
                            <span>%s원</span>
                          </div>
                        </div>
                        <div style="margin-top:10px;font-size:12px;color:#64748b;">
                          스캔 즉시 위 계좌에서 결제가 진행됩니다.
                        </div>
                      </div>
                    </div>

                    <div id="panelScan" style="padding:12px;border:1px solid #e5e7eb;border-radius:8px;display:none;">
                      <div style="font-size:13px;color:#6b7280;margin-bottom:8px;">매장/외부 서비스의 결제 요청 QR을 스캔하거나 링크/토큰을 직접 입력하세요.</div>
                      <video id="scanVideo" style="width:100%%;height:220px;background:#111;border-radius:8px;object-fit:cover;"></video>
                      <div style="display:flex;gap:8px;margin-top:8px;">
                        <button id="btnStartCamera" type="button" style="flex:1;padding:10px;border-radius:8px;border:1px solid #1f6feb;background:#1f6feb;color:#fff;font-weight:700;cursor:pointer;">카메라 시작</button>
                        <button id="btnStopCamera" type="button" style="flex:1;padding:10px;border-radius:8px;border:1px solid #c5c9d3;background:#fff;color:#111;font-weight:700;cursor:pointer;">카메라 중지</button>
                      </div>
                      <input id="scanInput" placeholder="스캔 결과(링크 또는 세션토큰)" style="margin-top:8px;width:100%%;padding:10px;border:1px solid #d1d5db;border-radius:8px;" />
                      <button id="btnGoScanned" type="button" style="margin-top:8px;width:100%%;padding:10px;border:none;border-radius:8px;background:#059669;color:#fff;font-weight:700;cursor:pointer;">스캔 결과로 이동</button>
                    </div>

                    <div style="margin-top:12px;font-size:12px;color:#6b7280;">세션 상태: %s / 세션 토큰: %s</div>
                  </div>

                  <script>
                    const tabCode = document.getElementById('tabCode');
                    const tabScan = document.getElementById('tabScan');
                    const panelCode = document.getElementById('panelCode');
                    const panelScan = document.getElementById('panelScan');
                    const video = document.getElementById('scanVideo');
                    const scanInput = document.getElementById('scanInput');
                    const btnStartCamera = document.getElementById('btnStartCamera');
                    const btnStopCamera = document.getElementById('btnStopCamera');
                    const btnGoScanned = document.getElementById('btnGoScanned');
                    let stream = null;
                    let detector = null;
                    let timer = null;

                    tabCode.onclick = () => {
                      panelCode.style.display = 'block';
                      panelScan.style.display = 'none';
                      tabCode.style.background = '#1f6feb'; tabCode.style.color = '#fff';
                      tabScan.style.background = '#fff'; tabScan.style.color = '#111';
                    };

                    tabScan.onclick = () => {
                      panelCode.style.display = 'none';
                      panelScan.style.display = 'block';
                      tabScan.style.background = '#1f6feb'; tabScan.style.color = '#fff';
                      tabCode.style.background = '#fff'; tabCode.style.color = '#111';
                    };

                    btnStartCamera.onclick = async () => {
                      try {
                        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
                        video.srcObject = stream;
                        await video.play();

                        if ('BarcodeDetector' in window) {
                          detector = new BarcodeDetector({ formats: ['qr_code'] });
                          timer = setInterval(async () => {
                            if (!detector || video.readyState < 2) return;
                            const codes = await detector.detect(video);
                            if (codes && codes.length > 0) {
                              scanInput.value = codes[0].rawValue || '';
                              clearInterval(timer);
                            }
                          }, 800);
                        }
                      } catch (e) {
                        alert('카메라를 시작할 수 없습니다. 브라우저 권한을 확인해 주세요.');
                      }
                    };

                    btnStopCamera.onclick = () => {
                      if (timer) clearInterval(timer);
                      if (stream) {
                        stream.getTracks().forEach(track => track.stop());
                        stream = null;
                      }
                    };

                    btnGoScanned.onclick = () => {
                      const value = (scanInput.value || '').trim();
                      if (!value) {
                        alert('스캔 결과를 입력해 주세요.');
                        return;
                      }

                      if (value.startsWith('http://') || value.startsWith('https://')) {
                        window.location.href = value;
                        return;
                      }

                      window.location.href = '/pay/checkout/' + encodeURIComponent(value);
                    };
                  </script>
                </body>
                </html>
                """.formatted(
                merchant,
                title,
                view.amount(),
                token,
                codeToken,
                token,
                qrPayload,
                bankCode,
                accountNumber,
                ownerName,
                view.walletBalance(),
                view.expectedAutoChargeAmount(),
                status,
                token
        );
    }
}
