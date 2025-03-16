package com.bill.sse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class WebResourceConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 為所有 /**路徑設置靜態資源位置
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
    
    @Bean
    public RouterFunction<ServerResponse> htmlRouter() {
        // 特別處理第三方付款頁面
        return RouterFunctions.resources("/third-party-payment.html", new ClassPathResource("static/"));
    }
}
