package com.bill.sse.controller;

import com.bill.sse.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Map;

import static org.mockito.Mockito.*;

@WebFluxTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void testInitializePayment() {
        // 請求體
        Map<String, Object> requestBody = Map.of("amount", 299.99);

        // 發送初始化付款請求
        webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestBody))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isNotEmpty()
                .jsonPath("$.amount").isEqualTo(299.99)
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.paymentUrl").isNotEmpty();
    }

    @Test
    void testPaymentCallbackSuccess() {
        // 先初始化付款以建立訂單
        Map<String, Object> initRequest = Map.of("amount", 100.0);

        String orderId = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("orderId").toString();

        // 付款成功回調請求
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
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("回調處理成功");

        // 驗證 PaymentService 被調用
        verify(paymentService, times(1)).notifyPaymentSuccess(orderId);
    }

    @Test
    void testPaymentCallbackFailure() {
        // 先初始化付款以建立訂單
        Map<String, Object> initRequest = Map.of("amount", 100.0);

        String orderId = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("orderId").toString();

        // 付款失敗回調請求
        String failureReason = "信用卡被拒";
        Map<String, String> callbackData = Map.of(
                "orderId", orderId,
                "status", "FAILURE",
                "reason", failureReason
        );

        // 發送回調請求
        webTestClient.post()
                .uri("/api/payment/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(callbackData))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("回調處理成功");

        // 驗證 PaymentService 被調用
        verify(paymentService, times(1)).notifyPaymentFailure(orderId, failureReason);
    }

    @Test
    void testCheckOrderStatus() {
        // 先初始化付款以建立訂單
        Map<String, Object> initRequest = Map.of("amount", 100.0);

        String orderId = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("orderId").toString();

        // 檢查訂單狀態
        webTestClient.get()
                .uri("/api/payment/{orderId}/status", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void testSimulateSuccess() {
        // 先初始化付款以建立訂單
        Map<String, Object> initRequest = Map.of("amount", 100.0);

        String orderId = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("orderId").toString();

        // 模擬付款成功
        webTestClient.post()
                .uri("/api/payment/{orderId}/simulate-success", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("已模擬付款成功");

        // 驗證 PaymentService 被調用
        verify(paymentService, times(1)).notifyPaymentSuccess(orderId);

        // 檢查訂單狀態已更新
        webTestClient.get()
                .uri("/api/payment/{orderId}/status", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS");
    }

    @Test
    void testSimulateFailure() {
        // 先初始化付款以建立訂單
        Map<String, Object> initRequest = Map.of("amount", 100.0);

        String orderId = webTestClient.post()
                .uri("/api/payment/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(initRequest))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("orderId").toString();

        // 模擬付款失敗
        String reason = "客戶取消交易";
        webTestClient.post()
                .uri("/api/payment/{orderId}/simulate-failure", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of("reason", reason)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("已模擬付款失敗");

        // 驗證 PaymentService 被調用
        verify(paymentService, times(1)).notifyPaymentFailure(eq(orderId), eq(reason));

        // 檢查訂單狀態已更新
        webTestClient.get()
                .uri("/api/payment/{orderId}/status", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILURE");
    }
}
