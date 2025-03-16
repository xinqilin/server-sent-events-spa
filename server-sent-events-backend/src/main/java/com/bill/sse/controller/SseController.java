package com.bill.sse.controller;

import com.bill.sse.service.PaymentService;
import com.bill.sse.vo.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允許跨域請求
@Slf4j
public class SseController {

    private final PaymentService paymentService;
    private static final AtomicInteger activeConnections = new AtomicInteger(0);

    @GetMapping(value = "/payment-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PaymentEvent> streamEvents(ServerWebExchange exchange) {
        // 生成唯一的連接 ID 用於日誌追蹤
        String connectionId = UUID.randomUUID().toString().substring(0, 8);
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        // 計算並記錄活躍連接數
        int currentConnections = activeConnections.incrementAndGet();

        log.info("SSE 連接已建立, 連接ID: {}, 客戶端: {}, User-Agent: {}, current 連接數: {}",
                connectionId, clientIp, userAgent, currentConnections);

        // 使用 doOnCancel 和 doFinally 來追蹤連接關閉情況
        return paymentService.getPaymentEvents()
                // 記錄每個事件的發送
                .doOnNext(event -> {
                    log.debug("SSE 事件發送, 連接ID: {}, 事件類型: {}, 訂單ID: {}, 狀態: {}",
                            connectionId, event.eventType(), event.orderId(), event.status());
                })
                // 記錄連接取消
                .doOnCancel(() -> {
                    int remaining = activeConnections.decrementAndGet();
                    log.info("SSE 連接被取消, 連接ID: {}, 客戶端: {}, 剩餘活躍連接數: {}",
                            connectionId, clientIp, remaining);
                })
                // 記錄連接異常
                .doOnError(error -> {
                    log.error("SSE 連接發生錯誤, 連接ID: {}, 客戶端: {}, 錯誤: {}",
                            connectionId, clientIp, error.getMessage(), error);
                })
                // 記錄連接終止（無論是完成、錯誤還是取消）
                .doFinally(signalType -> {
                    if (signalType != SignalType.CANCEL) { // 只有在非取消場景下才減少計數（避免重複計算）
                        int remaining = activeConnections.decrementAndGet();
                        log.info("SSE 連接已終止, 連接ID: {}, 終止類型: {}, 客戶端: {}, 剩餘活躍連接數: {}",
                                connectionId, signalType, clientIp, remaining);
                    }
                });
    }

    // 獲取當前活躍的 SSE 連接數量（用於監控與調試）
    @GetMapping("/connections")
    public Map<String, Integer> getActiveConnections() {
        int count = activeConnections.get();
        log.info("current 連接數: {}", count);
        return Map.of("activeConnections", count);
    }
}
