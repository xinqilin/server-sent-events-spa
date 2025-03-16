package com.bill.sse.controller;

import com.bill.sse.service.PaymentService;
import com.bill.sse.vo.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.Mockito.when;

@WebFluxTest(SseController.class)
class SseControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void testStreamEvents() {
        // 建立測試事件
        PaymentEvent event1 = new PaymentEvent("TEST", "order1", "PENDING", "測試事件1", 1000L);
        PaymentEvent event2 = new PaymentEvent("TEST", "order2", "SUCCESS", "測試事件2", 2000L);

        // 模擬 PaymentService 回傳的 Flux
        when(paymentService.getPaymentEvents())
                .thenReturn(Flux.just(event1, event2).delayElements(Duration.ofMillis(100)));

        // 測試 SSE 端點
        Flux<PaymentEvent> responseBody = webTestClient.get()
                .uri("/api/sse/payment-events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(PaymentEvent.class)
                .getResponseBody();

        // 驗證回傳的事件
        StepVerifier.create(responseBody.take(2))
                .expectNext(event1)
                .expectNext(event2)
                .expectComplete()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void testStreamEventsContentType() {
        // 模擬空的事件流
        when(paymentService.getPaymentEvents()).thenReturn(Flux.empty());

        // 測試 SSE 端點確認返回的 Content-Type 是否正確
        webTestClient.get()
                .uri("/api/sse/payment-events")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }
}
