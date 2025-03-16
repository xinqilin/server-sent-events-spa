import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const usePaymentStore = defineStore('payment', () => {
  // 狀態
  const currentOrderId = ref<string | null>(null);
  const paymentStatus = ref<'PENDING' | 'SUCCESS' | 'FAILURE' | null>(null);
  const paymentMessage = ref<string>('');
  const isProcessing = ref<boolean>(false);
  
  // EventSource 實例
  let eventSource: EventSource | null = null;
  
  // 重連嘗試次數及間隔
  let reconnectAttempts = 0;
  const maxReconnectAttempts = 5;
  const reconnectDelay = 3000; // 3秒
  
  // 計算屬性
  const isPaymentSuccess = computed(() => paymentStatus.value === 'SUCCESS');
  const isPaymentFailure = computed(() => paymentStatus.value === 'FAILURE');
  const hasActiveOrder = computed(() => !!currentOrderId.value);
  
  // 方法
  // 初始化付款
  async function initializePayment(amount: number): Promise<string | null> {
    try {
      isProcessing.value = true;
      
      const response = await fetch('http://localhost:8080/api/payment/initialize', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ amount }),
      });
      
      if (!response.ok) {
        throw new Error('初始化付款失敗');
      }
      
      const data = await response.json();
      currentOrderId.value = data.orderId;
      paymentStatus.value = 'PENDING';
      
      return data.paymentUrl;
    } catch (error) {
      console.error('初始化付款出錯:', error);
      return null;
    } finally {
      isProcessing.value = false;
    }
  }
  
  // 開始監聽付款事件
  function startListeningForPaymentEvents() {
    if (eventSource) {
      // 如果已經有連接，先關閉
      eventSource.close();
    }
    
    // 重置重連計數
    reconnectAttempts = 0;
    
    // 建立新的 SSE 連接
    eventSource = new EventSource('http://localhost:8080/api/sse/payment-events');
    
    // 連接建立時的處理
    eventSource.onopen = (event) => {
      console.log('SSE 連接已建立');
      reconnectAttempts = 0; // 重置重連計數
    };
    
    // 添加通用事件監聽器
    eventSource.onmessage = (event) => {
      try {
        console.log('原始事件數據:', event.data);
        
        const data = JSON.parse(event.data);
        
        console.log(`收到付款事件數據:`, data);
        
        // 處理心跳事件
        if (data.eventType === 'HEARTBEAT') {
          console.log('收到心跳事件');
          return;
        }
        
        // 檢查是否為當前訂單的事件
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log(`收到付款狀態更新: ${data.status}`);
          
          // 如果付款已完成（成功或失敗），設置延遲關閉連接
          if (data.status === 'SUCCESS' || data.status === 'FAILURE') {
            // 延遲1秒後關閉連接，確保所有事件都被接收
            setTimeout(() => {
              stopListeningForPaymentEvents();
            }, 1000);
          }
        }
      } catch (error) {
        console.error('處理付款事件出錯:', error);
      }
    };
    
    // 專門的付款狀態事件監聽器
    eventSource.addEventListener('PAYMENT_STATUS', (event: MessageEvent) => {
      try {
        console.log('收到 PAYMENT_STATUS 事件:', event.data);
        
        const data = JSON.parse(event.data);
        
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log(`收到付款狀態更新: ${data.status}`);
          
          // 如果付款已完成（成功或失敗），設置延遲關閉連接
          if (data.status === 'SUCCESS' || data.status === 'FAILURE') {
            // 延遲1秒後關閉連接，確保所有事件都被接收
            setTimeout(() => {
              stopListeningForPaymentEvents();
            }, 1000);
          }
        }
      } catch (error) {
        console.error('處理付款事件出錯:', error);
      }
    });
    
    // 心跳事件監聽器
    eventSource.addEventListener('heartbeat', (event: MessageEvent) => {
      console.log('收到心跳事件:', event.data);
      // 重置重連嘗試計數
      reconnectAttempts = 0;
    });
    
    // 改進錯誤處理
    eventSource.onerror = (error) => {
      console.error('SSE 連接錯誤:', error);
      
      // 檢查連接狀態
      if (eventSource && eventSource.readyState === EventSource.CLOSED) {
        console.warn('SSE 連接已關閉');
        
        // 檢查是否應該重新連接
        if (currentOrderId.value && paymentStatus.value === 'PENDING' && reconnectAttempts < maxReconnectAttempts) {
          reconnectAttempts++;
          
          console.log(`嘗試重新建立 SSE 連接 (${reconnectAttempts}/${maxReconnectAttempts})`);
          
          // 延遲一段時間後重新連接
          setTimeout(() => {
            if (currentOrderId.value && paymentStatus.value === 'PENDING') {
              console.log('重新連接 SSE');
              startListeningForPaymentEvents();
            }
          }, reconnectDelay);
        } else if (reconnectAttempts >= maxReconnectAttempts) {
          console.warn(`已達到最大重連次數 (${maxReconnectAttempts})，停止重連`);
          // 可以在這裡加入一些用戶通知或其他處理
        } else {
          // 如果不是等待付款狀態，或沒有訂單，則不需要重連
          console.log('不需要重連 SSE');
        }
      }
    };
  }
  
  // 停止監聽付款事件
  function stopListeningForPaymentEvents() {
    if (eventSource) {
      console.log('關閉 SSE 連接');
      eventSource.close();
      eventSource = null;
    }
  }
  
  // 重置付款狀態
  function resetPayment() {
    stopListeningForPaymentEvents();
    currentOrderId.value = null;
    paymentStatus.value = null;
    paymentMessage.value = '';
    isProcessing.value = false;
  }
  
  return {
    // 狀態
    currentOrderId,
    paymentStatus,
    paymentMessage,
    isProcessing,
    
    // 計算屬性
    isPaymentSuccess,
    isPaymentFailure,
    hasActiveOrder,
    
    // 方法
    initializePayment,
    startListeningForPaymentEvents,
    stopListeningForPaymentEvents,
    resetPayment,
  };
});
