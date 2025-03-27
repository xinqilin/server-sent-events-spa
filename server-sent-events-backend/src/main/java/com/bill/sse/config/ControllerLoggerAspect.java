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

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class ControllerLoggerAspect {

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {
    }

    @Around("restControllerMethods()")
    public Object logControllerMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();

        String controllerName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        String requestMapping = getRequestMapping(method);

        String[] paramNames = methodSignature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();

        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            if (!(paramValues[i] instanceof ServerWebExchange) &&
                    !isSensitiveOrBinaryData(paramValues[i])) {
                params.put(paramNames[i], formatParamValue(paramValues[i]));
            }
        }

        log.info("[controller] {}.{}, mapping: {}, params: {}", controllerName, methodName, requestMapping, params);

        Instant startTime = Instant.now();

        try {
            Object result = joinPoint.proceed();

            if (result instanceof Mono) {
                return ((Mono<?>) result).doOnSuccess(value -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("[controller], {}.{} | 耗時: {}ms | 返回類型: Mono<{}>", controllerName, methodName, duration.toMillis(),
                            value != null ? value.getClass().getSimpleName() : "Void");
                }).doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("[controller], {}.{} | 耗時: {}ms | 錯誤: {}", controllerName, methodName, duration.toMillis(), error.getMessage());
                });
            } else if (result instanceof Flux) {
                return ((Flux<?>) result).doOnComplete(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("[controller], {}.{} | 耗時: {}ms | 返回類型: Flux<?> [流式數據]", controllerName, methodName, duration.toMillis());
                }).doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("[controller], {}.{} | 耗時: {}ms | 錯誤: {}", controllerName, methodName, duration.toMillis(), error.getMessage());
                });
            } else {
                Duration duration = Duration.between(startTime, Instant.now());
                log.info("[controller], {}.{} | 耗時: {}ms | 返回類型: {}", controllerName, methodName, duration.toMillis(), result != null ? result.getClass().getSimpleName() : "void");
                return result;
            }
        } catch (Throwable e) {
            Duration duration = Duration.between(startTime, Instant.now());
            log.error("endpoint異常, {}.{} | 耗時: {}ms | 異常: {}", controllerName, methodName, duration.toMillis(), e.getMessage());
            throw e;
        }
    }

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
            return "無法解析";
        }
    }

    private Object formatParamValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof byte[] || value instanceof Byte[]) {
            return "[Binary data: " + ((byte[]) value).length + " bytes]";
        }

        // 只顯示集合和Map的大小，不顯示具體內容
        if (value instanceof Map) {
            return "{Map: " + ((Map<?, ?>) value).size() + " entries}";
        }

        // 處理大型集合和陣列
        if (value instanceof Iterable) {
            return "[Collection: size unknown]";
        }

        if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            return "[Array: " + ((Object[]) value).length + " elements]";
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

        if (value instanceof byte[] || value instanceof Byte[]) {
            return true;
        }

        String typeName = value.getClass().getSimpleName().toLowerCase();
        return typeName.contains("password") ||
                typeName.contains("credential") ||
                typeName.contains("secret");
    }
}
