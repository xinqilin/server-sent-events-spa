package com.bill.sse.service;

import com.bill.sse.vo.PaymentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class PaymentService {
    
    // 使用 Sinks.Many 來實現事件發布
    private final Sinks.Many<PaymentEvent> paymentEventSink;
    private final Flux<PaymentEvent> paymentEventFlux;
    
    public PaymentService() {
        // 創建多播 sink
        this.paymentEventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.paymentEventFlux = paymentEventSink.asFlux();
    }
    
    // 取得事件流
    public Flux<PaymentEvent> getPaymentEvents() {
        return paymentEventFlux;
    }
    
    // 發布付款事件
    public void publishPaymentEvent(PaymentEvent event) {
        paymentEventSink.tryEmitNext(event);
    }
    
    // 通知付款成功
    public void notifyPaymentSuccess(String orderId) {
        PaymentEvent event = PaymentEvent.createSuccessEvent(orderId, null);
        publishPaymentEvent(event);
    }
    
    // 通知付款失敗
    public void notifyPaymentFailure(String orderId, String reason) {
        PaymentEvent event = PaymentEvent.createFailureEvent(orderId, reason);
        publishPaymentEvent(event);
    }
}
