package com.bill.sse.vo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentEventTest {

    @Test
    void testCreateSuccessEvent() {
        String orderId = "order-123";
        
        // 使用工廠方法建立付款成功事件
        PaymentEvent event = PaymentEvent.createSuccessEvent(orderId, null);
        
        // 驗證欄位值
        assertEquals("PAYMENT_STATUS", event.eventType());
        assertEquals(orderId, event.orderId());
        assertEquals("SUCCESS", event.status());
        assertEquals("付款已成功完成", event.message());
        assertNotNull(event.timestamp());
    }
    
    @Test
    void testCreateSuccessEventWithCustomMessage() {
        String orderId = "order-456";
        String customMessage = "VIP 客戶付款成功";
        
        // 使用工廠方法建立自訂訊息的付款成功事件
        PaymentEvent event = PaymentEvent.createSuccessEvent(orderId, customMessage);
        
        // 驗證欄位值
        assertEquals("PAYMENT_STATUS", event.eventType());
        assertEquals(orderId, event.orderId());
        assertEquals("SUCCESS", event.status());
        assertEquals(customMessage, event.message());
        assertNotNull(event.timestamp());
    }
    
    @Test
    void testCreateFailureEvent() {
        String orderId = "order-789";
        String reason = "餘額不足";
        
        // 使用工廠方法建立付款失敗事件
        PaymentEvent event = PaymentEvent.createFailureEvent(orderId, reason);
        
        // 驗證欄位值
        assertEquals("PAYMENT_STATUS", event.eventType());
        assertEquals(orderId, event.orderId());
        assertEquals("FAILURE", event.status());
        assertEquals("付款失敗: 餘額不足", event.message());
        assertNotNull(event.timestamp());
    }
    
    @Test
    void testCreateFailureEventWithNullReason() {
        String orderId = "order-101";
        
        // 使用工廠方法建立無原因的付款失敗事件
        PaymentEvent event = PaymentEvent.createFailureEvent(orderId, null);
        
        // 驗證欄位值
        assertEquals("PAYMENT_STATUS", event.eventType());
        assertEquals(orderId, event.orderId());
        assertEquals("FAILURE", event.status());
        assertEquals("付款失敗: 未知原因", event.message());
        assertNotNull(event.timestamp());
    }
    
    @Test
    void testRecordEquality() {
        // 建立兩個相同內容的事件
        PaymentEvent event1 = new PaymentEvent("TEST", "123", "PENDING", "測試", 1000L);
        PaymentEvent event2 = new PaymentEvent("TEST", "123", "PENDING", "測試", 1000L);
        
        // 驗證 record 的 equals 方法
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }
}
