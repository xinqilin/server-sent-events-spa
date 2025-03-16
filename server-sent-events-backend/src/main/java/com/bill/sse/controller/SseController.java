package com.bill.sse.controller;

import com.bill.sse.vo.PaymentEvent;
import com.bill.sse.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允許跨域請求
public class SseController {
    
    private final PaymentService paymentService;
    
    @GetMapping(value = "/payment-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PaymentEvent> streamEvents() {
        // 回傳事件流
        return paymentService.getPaymentEvents();
    }
}
