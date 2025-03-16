package com.bill.sse.service;

import com.bill.sse.vo.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class PaymentService {
    
    // 使用 Sinks.Many 事件發布
    private final Sinks.Many<PaymentEvent> paymentEventSink;
    private final Flux<PaymentEvent> paymentEventFlux;
    
    // 計數器用於追蹤已發布的事件總數
    private final AtomicLong totalEventsPublished = new AtomicLong(0);
    
    public PaymentService() {
        // 創建多播 sink
        this.paymentEventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.paymentEventFlux = paymentEventSink.asFlux();
        log.info("PaymentService init done!, create multicast");
    }
    
    // 取得事件流
    public Flux<PaymentEvent> getPaymentEvents() {
        log.debug("提供 PaymentEvent 事件流 | 當前已發布事件總數: {}", totalEventsPublished.get());
        return paymentEventFlux;
    }
    
    // 發布付款事件
    public void publishPaymentEvent(PaymentEvent event) {
        long eventCount = totalEventsPublished.incrementAndGet();
        log.info("發布付款事件 #{} | 類型: {} | 訂單ID: {} | 狀態: {}", 
                eventCount, event.eventType(), event.orderId(), event.status());
        
        Sinks.EmitResult result = paymentEventSink.tryEmitNext(event);
        
        if (result.isSuccess()) {
            log.info("付款事件發布成功 #{} | 訂單ID: {}", eventCount, event.orderId());
        } else {
            log.error("付款事件發布失敗 #{} | 訂單ID: {} | 結果: {}", 
                    eventCount, event.orderId(), result);
        }
    }
    
    // 通知付款成功
    public void notifyPaymentSuccess(String orderId) {
        log.info("建立付款成功事件 | 訂單ID: {}", orderId);
        PaymentEvent event = PaymentEvent.createSuccessEvent(orderId, null);
        publishPaymentEvent(event);
    }
    
    // 通知付款失敗
    public void notifyPaymentFailure(String orderId, String reason) {
        log.info("建立付款失敗事件 | 訂單ID: {} | 原因: {}", orderId, reason);
        PaymentEvent event = PaymentEvent.createFailureEvent(orderId, reason);
        publishPaymentEvent(event);
    }
    
    // 獲取已發布事件總數（用於監控與調試）
    public long getTotalEventsPublished() {
        return totalEventsPublished.get();
    }
}
