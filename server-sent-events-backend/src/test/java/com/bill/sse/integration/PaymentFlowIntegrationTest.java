package com.bill.sse.integration;

import com.bill.sse.vo.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    /**
     * 測試完整的付款流程，包括:
     * 1. 初始化付款
     * 2. 連接 SSE 事件流
     * 3. 模擬付款成功
     * 4. 接收付款成功事件
     */
    @Test
    void testSuccessfulPaymentFlow() throws Exception {
        // 步驟 1: 初始化付款
        Map<String, Object> initRequest = Map.of("amount", 888.0);
        
        // 發送初始化請求取得訂單ID
        Map<String, Object> response = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();
        
        assertNotNull(response);
        String orderId = (String) response.get("orderId");
        assertNotNull(orderId);
        
        // 步驟 2: 連接 SSE 事件流
        WebClient client = WebClient.create("http://localhost:" + port);
        
        // 使用 CountDownLatch 等待事件接收
        CountDownLatch latch = new CountDownLatch(1);
        
        // 儲存接收到的事件
        AtomicReference<PaymentEvent> receivedEvent = new AtomicReference<>();
        
        // 開始監聽 SSE 事件
        Flux<PaymentEvent> eventFlux = client.get()
                .uri("/api/sse/payment-events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(PaymentEvent.class)
                .filter(event -> orderId.equals(event.orderId()))
                .take(1)  // 只取第一個匹配的事件
                .doOnNext(event -> {
                    // 保存事件並釋放鎖
                    receivedEvent.set(event);
                    latch.countDown();
                });
        
        // 訂閱事件流但不阻塞測試執行緒
        eventFlux.subscribe();
        
        // 等待片刻確保 SSE 連接已建立
        Thread.sleep(500);
        
        // 步驟 3: 模擬付款成功
        webTestClient.post()
                .uri("/api/payment/{orderId}/simulate-success", orderId)
                .exchange()
                .expectStatus().isOk();
        
        // 步驟 4: 等待並驗證是否收到付款成功事件
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "未在期望的時間內收到付款事件");
        
        // 驗證事件內容
        PaymentEvent event = receivedEvent.get();
        assertNotNull(event);
        assertEquals(orderId, event.orderId());
        assertEquals("SUCCESS", event.status());
        assertEquals("付款已成功完成", event.message());
    }
    
    /**
     * 測試付款失敗流程
     */
    @Test
    void testFailedPaymentFlow() throws Exception {
        // 初始化付款
        Map<String, Object> initRequest = Map.of("amount", 999.0);
        
        // 發送初始化請求取得訂單ID
        Map<String, Object> response = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();
        
        assertNotNull(response);
        String orderId = (String) response.get("orderId");
        assertNotNull(orderId);
        
        // 連接 SSE 事件流
        WebClient client = WebClient.create("http://localhost:" + port);
        
        // 使用 StepVerifier 測試事件流
        Flux<PaymentEvent> eventFlux = client.get()
                .uri("/api/sse/payment-events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(PaymentEvent.class)
                .filter(event -> orderId.equals(event.orderId()));
        
        // 設置 StepVerifier
        StepVerifier.FirstStep<PaymentEvent> verifier = StepVerifier.create(eventFlux.take(1));
        
        // 等待片刻確保 SSE 連接已建立
        Thread.sleep(500);
        
        // 模擬付款失敗，並指定失敗原因
        String failureReason = "交易被拒絕";
        webTestClient.post()
                .uri("/api/payment/{orderId}/simulate-failure", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of("reason", failureReason)))
                .exchange()
                .expectStatus().isOk();
        
        // 驗證收到的事件
        verifier.assertNext(event -> {
            assertEquals(orderId, event.orderId());
            assertEquals("FAILURE", event.status());
            assertEquals("付款失敗: " + failureReason, event.message());
        })
        .expectComplete()
        .verify(Duration.ofSeconds(5));
    }
    
    /**
     * 測試第三方付款回調
     */
    @Test
    void testPaymentCallbackFlow() throws Exception {
        // 初始化付款
        Map<String, Object> initRequest = Map.of("amount", 777.0);
        
        // 發送初始化請求取得訂單ID
        Map<String, Object> response = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();
        
        assertNotNull(response);
        String orderId = (String) response.get("orderId");
        assertNotNull(orderId);
        
        // 連接 SSE 事件流
        WebClient client = WebClient.create("http://localhost:" + port);
        
        // 使用 CountDownLatch 等待事件接收
        CountDownLatch latch = new CountDownLatch(1);
        
        // 儲存接收到的事件
        AtomicReference<PaymentEvent> receivedEvent = new AtomicReference<>();
        
        // 開始監聽 SSE 事件
        Flux<PaymentEvent> eventFlux = client.get()
                .uri("/api/sse/payment-events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(PaymentEvent.class)
                .filter(event -> orderId.equals(event.orderId()))
                .take(1)
                .doOnNext(event -> {
                    receivedEvent.set(event);
                    latch.countDown();
                });
        
        // 訂閱事件流
        eventFlux.subscribe();
        
        // 等待片刻確保 SSE 連接已建立
        Thread.sleep(500);
        
        // 模擬第三方付款回調
        Map<String, String> callbackData = Map.of(
                "orderId", orderId,
                "status", "SUCCESS"
        );
        
        // 發送回調請求
        webTestClient.post()
                .uri("/api/payment/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(callbackData))
                .exchange()
                .expectStatus().isOk();
        
        // 等待並驗證是否收到付款事件
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "未在期望的時間內收到付款事件");
        
        // 驗證事件內容
        PaymentEvent event = receivedEvent.get();
        assertNotNull(event);
        assertEquals(orderId, event.orderId());
        assertEquals("SUCCESS", event.status());
    }
    
    /**
     * 測試多個客戶端監聽同一個訂單
     */
    @Test
    void testMultipleClientsListening() throws Exception {
        // 初始化付款
        Map<String, Object> initRequest = Map.of("amount", 555.0);
        
        // 發送初始化請求取得訂單ID
        Map<String, Object> response = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst();
        
        assertNotNull(response);
        String orderId = (String) response.get("orderId");
        assertNotNull(orderId);
        
        // 建立 WebClient
        WebClient client = WebClient.create("http://localhost:" + port);
        
        // 為兩個客戶端創建 CountDownLatch
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        // 創建兩個訂閱
        AtomicReference<PaymentEvent> receivedEvent1 = new AtomicReference<>();
        AtomicReference<PaymentEvent> receivedEvent2 = new AtomicReference<>();
        
        // 客戶端 1
        Flux<PaymentEvent> eventFlux1 = client.get()
                .uri("/api/sse/payment-events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(PaymentEvent.class)
                .filter(event -> orderId.equals(event.orderId()))
                .take(1)
                .doOnNext(event -> {
                    receivedEvent1.set(event);
                    latch1.countDown();
                });
        
        // 客戶端 2
        Flux<PaymentEvent> eventFlux2 = client.get()
                .uri("/api/sse/payment-events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(PaymentEvent.class)
                .filter(event -> orderId.equals(event.orderId()))
                .take(1)
                .doOnNext(event -> {
                    receivedEvent2.set(event);
                    latch2.countDown();
                });
        
        // 訂閱兩個事件流
        eventFlux1.subscribe();
        eventFlux2.subscribe();
        
        // 等待片刻確保 SSE 連接已建立
        Thread.sleep(500);
        
        // 模擬付款成功
        webTestClient.post()
                .uri("/api/payment/{orderId}/simulate-success", orderId)
                .exchange()
                .expectStatus().isOk();
        
        // 等待並驗證兩個客戶端是否都收到事件
        boolean received1 = latch1.await(5, TimeUnit.SECONDS);
        boolean received2 = latch2.await(5, TimeUnit.SECONDS);
        
        assertTrue(received1, "客戶端 1 未在期望的時間內收到付款事件");
        assertTrue(received2, "客戶端 2 未在期望的時間內收到付款事件");
        
        // 驗證兩個客戶端收到的事件內容相同
        PaymentEvent event1 = receivedEvent1.get();
        PaymentEvent event2 = receivedEvent2.get();
        
        assertNotNull(event1);
        assertNotNull(event2);
        assertEquals(event1.orderId(), event2.orderId());
        assertEquals(event1.status(), event2.status());
        assertEquals(event1.message(), event2.message());
    }
}
