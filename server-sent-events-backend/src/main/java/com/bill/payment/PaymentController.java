package com.bill.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允許跨域請求
@Slf4j
public class PaymentController {
    
    private final PaymentService paymentService;
    
    // 存儲訂單狀態（實際系統應該用資料庫）
    private final Map<String, String> orderStatus = new ConcurrentHashMap<>();
    
    // 初始化支付
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializePayment(@RequestBody Map<String, Object> paymentRequest) {
        // 模擬創建訂單
        String orderId = UUID.randomUUID().toString();
        Double amount = (Double) paymentRequest.getOrDefault("amount", 100.0);
        
        // 儲存訂單狀態
        orderStatus.put(orderId, "PENDING");
        
        log.info("初始化支付訂單: {}, 金額: {}", orderId, amount);
        
        // 生成付款頁面網址（實際上應為第三方支付網址）
        String paymentUrl = "/third-party-payment.html?orderId=" + orderId + "&amount=" + amount;
        
        // 回傳訂單資訊和付款頁面網址
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "amount", amount,
                "status", "PENDING",
                "paymentUrl", paymentUrl
        ));
    }
    
    // 第三方支付回調接口（模擬）
    @PostMapping("/callback")
    public ResponseEntity<String> paymentCallback(@RequestBody Map<String, String> callbackData) {
        String orderId = callbackData.get("orderId");
        String status = callbackData.get("status");
        
        if (orderId == null || !orderStatus.containsKey(orderId)) {
            return ResponseEntity.badRequest().body("無效的訂單");
        }
        
        // 更新訂單狀態
        orderStatus.put(orderId, status);
        
        // 發布事件通知前端
        if ("SUCCESS".equals(status)) {
            paymentService.notifyPaymentSuccess(orderId);
            log.info("付款成功: {}", orderId);
        } else {
            String reason = callbackData.getOrDefault("reason", "未知原因");
            paymentService.notifyPaymentFailure(orderId, reason);
            log.info("付款失敗: {}, 原因: {}", orderId, reason);
        }
        
        return ResponseEntity.ok("回調處理成功");
    }
    
    // 檢查訂單狀態
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> checkOrderStatus(@PathVariable String orderId) {
        if (!orderStatus.containsKey(orderId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "NOT_FOUND"));
        }
        
        return ResponseEntity.ok(Map.of("status", orderStatus.get(orderId)));
    }
    
    // 模擬支付成功（測試用）
    @PostMapping("/{orderId}/simulate-success")
    public ResponseEntity<String> simulateSuccess(@PathVariable String orderId) {
        if (!orderStatus.containsKey(orderId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("訂單不存在");
        }
        
        // 更新狀態並發送通知
        orderStatus.put(orderId, "SUCCESS");
        paymentService.notifyPaymentSuccess(orderId);
        
        return ResponseEntity.ok("已模擬付款成功");
    }
    
    // 模擬支付失敗（測試用）
    @PostMapping("/{orderId}/simulate-failure")
    public ResponseEntity<String> simulateFailure(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> payload) {
        
        if (!orderStatus.containsKey(orderId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("訂單不存在");
        }
        
        // 取得失敗原因或使用預設原因
        String reason = (payload != null && payload.containsKey("reason")) 
                ? payload.get("reason") 
                : "使用者取消付款";
        
        // 更新狀態並發送通知
        orderStatus.put(orderId, "FAILURE");
        paymentService.notifyPaymentFailure(orderId, reason);
        
        return ResponseEntity.ok("已模擬付款失敗");
    }
}
