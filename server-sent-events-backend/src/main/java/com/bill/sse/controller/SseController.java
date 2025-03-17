package com.bill.sse.controller;

import com.bill.sse.service.PaymentService;
import com.bill.sse.vo.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.Duration;
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
    public Flux<ServerSentEvent<PaymentEvent>> streamEvents(ServerWebExchange exchange) {
        // 追蹤使用
        String connectionId = UUID.randomUUID().toString().substring(0, 8);
        String clientIp = exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().getHostString() : "unknown";
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        // 計算並記錄活躍連接數
        int currentConnections = activeConnections.incrementAndGet();

        log.info("SSE 連接已建立, 連接ID: {}, 客戶端: {}, User-Agent: {}, current 連接數: {}", connectionId, clientIp, userAgent, currentConnections);

        // 創建心跳事件流，每30秒發送一次心跳以保持連接
        Flux<ServerSentEvent<PaymentEvent>> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<PaymentEvent>builder()
                        .id(String.valueOf(tick))
                        .event("heartbeat")
                        .data(PaymentEvent.createHeartbeatEvent())
                        .build()
                );

        // 轉換事件流為 ServerSentEvent 格式
        Flux<ServerSentEvent<PaymentEvent>> paymentEvents = paymentService.getPaymentEvents()
                .map(event -> ServerSentEvent.<PaymentEvent>builder()
                        .id(event.orderId() != null ? event.orderId() : UUID.randomUUID().toString())
                        .event(event.eventType())
                        .data(event)
                        .build()
                );

        // 合併心跳和支付事件流
        Flux<ServerSentEvent<PaymentEvent>> combinedFlux = Flux.merge(
                paymentEvents,
                heartbeat
        );

        // 使用 doOnCancel 和 doFinally 來追蹤連接關閉情況
        return combinedFlux
                // 記錄每個事件的發送
                .doOnNext(event -> {
                    log.debug("SSE 事件發送, 連接ID: {}, 事件類型: {}, 訂單ID: {}, 狀態: {}",
                            connectionId,
                            event.event(),
                            event.data() != null ? event.data().orderId() : "null",
                            event.data() != null ? event.data().status() : "null");
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
                        log.info("SSE 連接已終止, 連接ID: {}, 終止類型: {}, 客戶端: {}, 剩餘連接數: {}",
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
