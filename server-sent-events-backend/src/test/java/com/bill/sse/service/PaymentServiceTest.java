package com.bill.sse.service;

import com.bill.sse.vo.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    @Test
    void testGetPaymentEvents() {
        // 取得事件流
        Flux<PaymentEvent> eventFlux = paymentService.getPaymentEvents();
        
        // 確認回傳的不是 null
        StepVerifier.create(eventFlux.take(Duration.ofMillis(100)))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(100))
                .thenCancel()
                .verify();
    }

    @Test
    void testPublishPaymentEvent() {
        // 建立測試事件
        PaymentEvent testEvent = new PaymentEvent(
                "TEST_EVENT", "test-order", "TEST", "測試事件", System.currentTimeMillis());
        
        // 取得事件流
        Flux<PaymentEvent> eventFlux = paymentService.getPaymentEvents();
        
        // 發布事件
        paymentService.publishPaymentEvent(testEvent);
        
        // 驗證事件可以收到
        StepVerifier.create(eventFlux.take(1))
                .expectNext(testEvent)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void testNotifyPaymentSuccess() {
        String orderId = "success-order";
        
        // 取得事件流
        Flux<PaymentEvent> eventFlux = paymentService.getPaymentEvents();
        
        // 發送付款成功通知
        paymentService.notifyPaymentSuccess(orderId);
        
        // 驗證事件內容
        StepVerifier.create(eventFlux.take(1))
                .expectNextMatches(event -> 
                    "PAYMENT_STATUS".equals(event.eventType()) &&
                    orderId.equals(event.orderId()) &&
                    "SUCCESS".equals(event.status()) &&
                    "付款已成功完成".equals(event.message())
                )
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void testNotifyPaymentFailure() {
        String orderId = "failure-order";
        String reason = "信用卡驗證失敗";
        
        // 取得事件流
        Flux<PaymentEvent> eventFlux = paymentService.getPaymentEvents();
        
        // 發送付款失敗通知
        paymentService.notifyPaymentFailure(orderId, reason);
        
        // 驗證事件內容
        StepVerifier.create(eventFlux.take(1))
                .expectNextMatches(event -> 
                    "PAYMENT_STATUS".equals(event.eventType()) &&
                    orderId.equals(event.orderId()) &&
                    "FAILURE".equals(event.status()) &&
                    ("付款失敗: " + reason).equals(event.message())
                )
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void testMultipleSubscribers() throws Exception {
        // 建立測試事件
        PaymentEvent testEvent = new PaymentEvent(
                "MULTI_SUB_TEST", "multi-order", "TEST", "多訂閱者測試", System.currentTimeMillis());
        
        // 建立兩個訂閱者
        Flux<PaymentEvent> subscriber1 = paymentService.getPaymentEvents();
        Flux<PaymentEvent> subscriber2 = paymentService.getPaymentEvents();
        
        // 使用 CountDownLatch 來等待兩個訂閱者接收事件
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        AtomicReference<PaymentEvent> received1 = new AtomicReference<>();
        AtomicReference<PaymentEvent> received2 = new AtomicReference<>();
        
        // 訂閱事件流
        subscriber1.take(1).subscribe(event -> {
            received1.set(event);
            latch1.countDown();
        });
        
        subscriber2.take(1).subscribe(event -> {
            received2.set(event);
            latch2.countDown();
        });
        
        // 發布事件
        paymentService.publishPaymentEvent(testEvent);
        
        // 等待兩個訂閱者接收事件（最多等待 3 秒）
        boolean subscriber1Received = latch1.await(3, TimeUnit.SECONDS);
        boolean subscriber2Received = latch2.await(3, TimeUnit.SECONDS);
        
        // 驗證兩個訂閱者都收到了事件
        assert subscriber1Received : "訂閱者 1 未接收到事件";
        assert subscriber2Received : "訂閱者 2 未接收到事件";
        
        // 驗證接收到的事件內容正確
        assert testEvent.equals(received1.get()) : "訂閱者 1 接收到的事件內容不正確";
        assert testEvent.equals(received2.get()) : "訂閱者 2 接收到的事件內容不正確";
    }
}
