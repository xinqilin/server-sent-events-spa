package com.bill.sse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy // 啟用 AspectJ 自動代理
public class ServerSentEventsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerSentEventsBackendApplication.class, args);
    }

}
