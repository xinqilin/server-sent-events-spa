package com.bill.sse.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 控制器日誌記錄切面，使用AOP技術記錄所有控制器方法的調用詳情
 */
@Aspect
@Component
@Slf4j
public class ControllerLoggerAspect {

    /**
     * 定義切點：所有使用@RestController註解的類中的所有方法
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {
    }

    /**
     * 環繞通知：在控制器方法執行前後添加日誌記錄
     */
    @Around("restControllerMethods()")
    public Object logControllerMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // 取得方法簽名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        
        // 獲取控制器名稱和方法名稱
        String controllerName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        
        // 獲取請求映射信息
        String requestMapping = getRequestMapping(method);
        
        // 獲取參數名稱和值
        String[] paramNames = methodSignature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();
        
        // 過濾掉技術性參數，只記錄業務參數
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            if (!(paramValues[i] instanceof ServerWebExchange) && 
                !isSensitiveOrBinaryData(paramValues[i])) {
                params.put(paramNames[i], formatParamValue(paramValues[i]));
            }
        }
        
        // 記錄方法開始執行
        log.info("控制器調用開始 | {}.{} | 映射: {} | 參數: {}", 
                controllerName, methodName, requestMapping, params);
        
        // 記錄開始時間
        Instant startTime = Instant.now();
        
        try {
            // 執行實際的控制器方法
            Object result = joinPoint.proceed();
            
            // 處理不同類型的返回值
            if (result instanceof Mono) {
                return ((Mono<?>) result).doOnSuccess(value -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("控制器調用成功 | {}.{} | 耗時: {}ms | 返回類型: Mono<{}>", 
                            controllerName, methodName, duration.toMillis(), 
                            value != null ? value.getClass().getSimpleName() : "Void");
                }).doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("控制器調用失敗 | {}.{} | 耗時: {}ms | 錯誤: {}", 
                            controllerName, methodName, duration.toMillis(), error.getMessage());
                });
            } else if (result instanceof Flux) {
                return ((Flux<?>) result).doOnComplete(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("控制器調用成功 | {}.{} | 耗時: {}ms | 返回類型: Flux<?> [流式數據]", 
                            controllerName, methodName, duration.toMillis());
                }).doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("控制器調用失敗 | {}.{} | 耗時: {}ms | 錯誤: {}", 
                            controllerName, methodName, duration.toMillis(), error.getMessage());
                });
            } else {
                // 同步返回值
                Duration duration = Duration.between(startTime, Instant.now());
                log.info("控制器調用成功 | {}.{} | 耗時: {}ms | 返回類型: {}", 
                        controllerName, methodName, duration.toMillis(),
                        result != null ? result.getClass().getSimpleName() : "void");
                return result;
            }
        } catch (Throwable e) {
            // 記錄異常情況
            Duration duration = Duration.between(startTime, Instant.now());
            log.error("控制器調用異常 | {}.{} | 耗時: {}ms | 異常: {}", 
                    controllerName, methodName, duration.toMillis(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 獲取方法的請求映射信息
     */
    private String getRequestMapping(Method method) {
        try {
            StringBuilder mapping = new StringBuilder();
            
            // 檢查方法上的HTTP方法註解
            if (method.isAnnotationPresent(GetMapping.class)) {
                mapping.append("GET ");
                mapping.append(Arrays.toString(method.getAnnotation(GetMapping.class).value()));
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                mapping.append("POST ");
                mapping.append(Arrays.toString(method.getAnnotation(PostMapping.class).value()));
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                mapping.append("PUT ");
                mapping.append(Arrays.toString(method.getAnnotation(PutMapping.class).value()));
            } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                mapping.append("DELETE ");
                mapping.append(Arrays.toString(method.getAnnotation(DeleteMapping.class).value()));
            } else if (method.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping rm = method.getAnnotation(RequestMapping.class);
                RequestMethod[] methods = rm.method();
                mapping.append(methods.length > 0 ? methods[0] : "").append(" ");
                mapping.append(Arrays.toString(rm.value()));
            } else {
                mapping.append("UNKNOWN");
            }
            
            return mapping.toString();
        } catch (Exception e) {
            return "無法獲取映射信息";
        }
    }
    
    /**
     * 格式化參數值，避免敏感信息、二進制數據等
     */
    private Object formatParamValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        if (value instanceof byte[] || value instanceof Byte[]) {
            return "[Binary data: " + ((byte[])value).length + " bytes]";
        }
        
        // 只顯示集合和Map的大小，不顯示具體內容
        if (value instanceof Map) {
            return "{Map: " + ((Map<?,?>)value).size() + " entries}";
        }
        
        // 處理大型集合和陣列
        if (value instanceof Iterable) {
            return "[Collection: size unknown]";
        }
        
        if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            return "[Array: " + ((Object[])value).length + " elements]";
        }
        
        return value;
    }
    
    /**
     * 檢查參數是否為敏感或二進制數據
     */
    private boolean isSensitiveOrBinaryData(Object value) {
        if (value == null) {
            return false;
        }
        
        // 二進制數據
        if (value instanceof byte[] || value instanceof Byte[]) {
            return true;
        }
        
        // 類型名稱包含敏感字詞的參數
        String typeName = value.getClass().getSimpleName().toLowerCase();
        return typeName.contains("password") || 
               typeName.contains("credential") || 
               typeName.contains("secret");
    }
}
