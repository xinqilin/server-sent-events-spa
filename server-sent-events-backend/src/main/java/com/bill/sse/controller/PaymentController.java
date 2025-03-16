package com.bill.sse.controller;

import com.bill.sse.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允許跨域請求
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    // 儲存訂單狀態（實際系統應該用資料庫）
    private final Map<String, String> orderStatus = new ConcurrentHashMap<>();

    // 初始化付款
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializePayment(
            @RequestBody Map<String, Object> paymentRequest,
            ServerWebExchange exchange) {

        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        log.info("收到初始化付款請求, 客戶端: {}, User-Agent: {}, 請求內容: {}",
                clientIp, userAgent, paymentRequest);

        // 模擬建立訂單
        String orderId = UUID.randomUUID().toString();

        // 安全地處理金額，支援多種數字類型轉換為 BigDecimal
        BigDecimal amount;
        Object amountObj = paymentRequest.getOrDefault("amount", new BigDecimal("100.00"));

        if (amountObj instanceof Number) {
            amount = new BigDecimal(amountObj.toString());
            log.debug("金額來源為數字類型: {}, 轉換為 BigDecimal: {}", amountObj.getClass().getSimpleName(), amount);
        } else if (amountObj instanceof String) {
            try {
                amount = new BigDecimal((String) amountObj);
                log.debug("金額來源為字串: {}, 轉換為 BigDecimal: {}", amountObj, amount);
            } catch (NumberFormatException e) {
                log.warn("金額格式無效: {}, 使用預設值 100.00", amountObj);
                amount = new BigDecimal("100.00");
            }
        } else {
            log.warn("金額類型未知: {}, 使用預設值 100.00",
                    amountObj != null ? amountObj.getClass().getName() : "null");
            amount = new BigDecimal("100.00");
        }

        // 儲存訂單狀態
        orderStatus.put(orderId, "PENDING");

        log.info("初始化付款訂單, 訂單ID: {}, 金額: {}, 狀態: PENDING", orderId, amount);

        // 生成付款頁面網址
        String paymentUrl = "/third-party-payment.html?orderId=" + orderId + "&amount=" + amount;

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("amount", amount);
        responseData.put("status", "PENDING");
        responseData.put("paymentUrl", paymentUrl);

        log.debug("回應初始化付款請求, 訂單ID: {}, 付款頁面URL: {}", orderId, paymentUrl);

        // 回傳訂單資訊和付款頁面網址
        return ResponseEntity.ok(responseData);
    }

    // 第三方付款回調介面（模擬）
    @PostMapping("/callback")
    public ResponseEntity<String> paymentCallback(
            @RequestBody Map<String, String> callbackData,
            ServerWebExchange exchange) {

        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";

        log.info("收到第三方付款 callback, 客戶端: {}, 回調資料: {}", clientIp, callbackData);

        String orderId = callbackData.get("orderId");
        String status = callbackData.get("status");

        if (orderId == null || !orderStatus.containsKey(orderId)) {
            log.warn("付款 callback 失敗, 無效的訂單ID: {}", orderId);
            return ResponseEntity.badRequest().body("無效的訂單");
        }

        // 更新訂單狀態
        String previousStatus = orderStatus.put(orderId, status);
        log.info("訂單狀態更新, 訂單ID: {}, 舊狀態: {}, 新狀態: {}", orderId, previousStatus, status);

        // 發布事件通知前端
        if ("SUCCESS".equals(status)) {
            log.info("準備發送付款成功事件, 訂單ID: {}", orderId);
            paymentService.notifyPaymentSuccess(orderId);
            log.info("付款成功事件已發送, 訂單ID: {}", orderId);
        } else {
            String reason = callbackData.getOrDefault("reason", "未知原因");
            log.info("準備發送付款失敗事件, 訂單ID: {}, 原因: {}", orderId, reason);
            paymentService.notifyPaymentFailure(orderId, reason);
            log.info("付款失敗事件已發送, 訂單ID: {}, 原因: {}", orderId, reason);
        }

        return ResponseEntity.ok("回調處理成功");
    }

    // 檢查訂單狀態
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> checkOrderStatus(
            @PathVariable String orderId,
            ServerWebExchange exchange) {

        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";

        log.info("檢查訂單狀態, 訂單ID: {}, 客戶端: {}", orderId, clientIp);

        if (!orderStatus.containsKey(orderId)) {
            log.warn("訂單不存在, 訂單ID: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "NOT_FOUND"));
        }

        String status = orderStatus.get(orderId);
        log.info("訂單狀態查詢成功, 訂單ID: {}, 狀態: {}", orderId, status);

        return ResponseEntity.ok(Map.of("status", status));
    }

    // 模擬付款成功（測試用）
    @PostMapping("/{orderId}/simulate-success")
    public ResponseEntity<String> simulateSuccess(
            @PathVariable String orderId,
            ServerWebExchange exchange) {

        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";

        log.info("收到模擬付款成功請求, 訂單ID: {}, 客戶端: {}", orderId, clientIp);

        if (!orderStatus.containsKey(orderId)) {
            log.warn("模擬付款失敗, 訂單不存在, 訂單ID: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("訂單不存在");
        }

        // 更新狀態並發送通知
        String previousStatus = orderStatus.put(orderId, "SUCCESS");
        log.info("訂單狀態更新, 訂單ID: {}, 舊狀態: {}, 新狀態: SUCCESS", orderId, previousStatus);

        log.info("準備發送模擬付款成功事件, 訂單ID: {}", orderId);
        paymentService.notifyPaymentSuccess(orderId);
        log.info("模擬付款成功事件已發送, 訂單ID: {}", orderId);

        return ResponseEntity.ok("已模擬付款成功");
    }

    // 模擬付款失敗（測試用）
    @PostMapping("/{orderId}/simulate-failure")
    public ResponseEntity<String> simulateFailure(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> payload,
            ServerWebExchange exchange) {

        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";

        log.info("收到模擬付款失敗請求, 訂單ID: {}, 客戶端: {}, 請求內容: {}",
                orderId, clientIp, payload);

        if (!orderStatus.containsKey(orderId)) {
            log.warn("模擬付款失敗, 訂單不存在, 訂單ID: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("訂單不存在");
        }

        // 取得失敗原因或使用預設原因
        String reason = (payload != null && payload.containsKey("reason"))
                ? payload.get("reason")
                : "使用者取消付款";

        // 更新狀態並發送通知
        String previousStatus = orderStatus.put(orderId, "FAILURE");
        log.info("訂單狀態更新, 訂單ID: {}, 舊狀態: {}, 新狀態: FAILURE, 原因: {}",
                orderId, previousStatus, reason);

        log.info("準備發送模擬付款失敗事件, 訂單ID: {}, 原因: {}", orderId, reason);
        paymentService.notifyPaymentFailure(orderId, reason);
        log.info("模擬付款失敗事件已發送, 訂單ID: {}, 原因: {}", orderId, reason);

        return ResponseEntity.ok("已模擬付款失敗");
    }
}
