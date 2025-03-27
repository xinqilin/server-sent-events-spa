package com.bill.sse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

//@Component
//@Order(1)
@Slf4j
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // 取得請求相關資訊
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String uri = request.getURI().toString();
        String path = request.getPath().value();
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getHostString()
                : "unknown";
        String userAgent = request.getHeaders().getFirst("User-Agent");

        // 記錄請求開始時間
        Instant startTime = Instant.now();

        // 記錄請求開始
        log.info("[{}] 請求開始 | {} {} | 客戶端: {} | User-Agent: {}", requestId, method, path, clientIp, userAgent);

        // 使用exchange的attributes保存requestId，以便在其他地方使用
        exchange.getAttributes().put("requestId", requestId);

        // 繼續請求處理，並在完成後記錄結束資訊
        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    int status = exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : 0;

                    log.info("[{}] 請求完成 | {} {} | 狀態: {} | 耗時: {}ms | 客戶端: {}", requestId, method, path, status, duration.toMillis(), clientIp);
                })
                .doOnError(e -> {
                    Duration duration = Duration.between(startTime, Instant.now());

                    log.error("[{}] 請求出錯 | {} {} | 耗時: {}ms | 客戶端: {} | 錯誤: {}",
                            requestId, method, path, duration.toMillis(), clientIp, e.getMessage(), e);
                });
    }
}
