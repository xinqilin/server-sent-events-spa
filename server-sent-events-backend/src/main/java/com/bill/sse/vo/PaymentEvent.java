package com.bill.sse.vo;

public record PaymentEvent(
        String eventType,
        String orderId,
        String status,
        String message,
        Long timestamp
) {

    public static PaymentEvent createSuccessEvent(String orderId, String message) {
        return new PaymentEvent(
                "PAYMENT_STATUS",
                orderId,
                "SUCCESS",
                message != null ? message : "付款已成功完成",
                System.currentTimeMillis()
        );
    }

    public static PaymentEvent createFailureEvent(String orderId, String reason) {
        return new PaymentEvent(
                "PAYMENT_STATUS",
                orderId,
                "FAILURE",
                "付款失敗: " + (reason != null ? reason : "未知原因"),
                System.currentTimeMillis()
        );
    }
    
    public static PaymentEvent createHeartbeatEvent() {
        return new PaymentEvent(
                "HEARTBEAT",
                null,
                null,
                "heartbeat",
                System.currentTimeMillis()
        );
    }
}
