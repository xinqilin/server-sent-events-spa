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
      
      // 確保之前的連接已關閉
      stopListeningForPaymentEvents();
      
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
    console.log('%c 啟動 SSE 連接', 'background: #009688; color: white; padding: 2px 5px; border-radius: 2px;', {
      currentOrderId: currentOrderId.value,
      paymentStatus: paymentStatus.value
    });
    
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
      console.log('%c SSE 連接已建立', 'background: #4CAF50; color: white; padding: 2px 5px; border-radius: 2px;');
      reconnectAttempts = 0; // 重置重連計數
    };
    
    // 添加通用事件監聽器
    eventSource.onmessage = (event) => {
      try {
        console.log('%c 原始事件數據', 'background: #2196F3; color: white; padding: 2px 5px; border-radius: 2px;', event.data);
        
        const data = JSON.parse(event.data);
        
        console.log('%c 收到付款事件數據', 'background: #2196F3; color: white; padding: 2px 5px; border-radius: 2px;', data);
        
        // 處理心跳事件
        if (data.eventType === 'HEARTBEAT') {
          console.log('%c 收到心跳事件', 'background: #9C27B0; color: white; padding: 2px 5px; border-radius: 2px;', data);
          return;
        }
        
        // 檢查是否為當前訂單的事件
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log('%c 收到付款狀態更新', 'background: #FF9800; color: white; padding: 2px 5px; border-radius: 2px;', `狀態: ${data.status}, 訂單: ${data.orderId}`);
          console.log('付款訊息:', data.message);
          
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
        console.log('%c 收到 PAYMENT_STATUS 事件', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', event.data);
        
        const data = JSON.parse(event.data);
        
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log('%c 付款狀態更新 (事件監聽器)', 'background: #E91E63; color: white; padding: 2px 5px; border-radius: 2px;', `狀態: ${data.status}, 訂單: ${data.orderId}`);
          console.log('付款訊息 (事件監聽器):', data.message);
          
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
      console.log('%c 收到心跳事件 (心跳監聽器)', 'background: #673AB7; color: white; padding: 2px 5px; border-radius: 2px;', event.data);
      // 重置重連計數並打印日誌
      reconnectAttempts = 0;
      console.log('%c 心跳重置重連計數', 'background: #673AB7; color: white; padding: 2px 5px; border-radius: 2px;', { reconnectAttempts });
    });
    
    // 改進錯誤處理
    eventSource.onerror = (error) => {
      console.error('%c SSE 連接錯誤', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', error);
      
      // 檢查連接狀態
      if (eventSource && eventSource.readyState === EventSource.CLOSED) {
        console.warn('%c SSE 連接已關閉', 'background: #FF9800; color: white; padding: 2px 5px; border-radius: 2px;');
        
        // 檢查是否應該重新連接
        if (currentOrderId.value && paymentStatus.value === 'PENDING' && reconnectAttempts < maxReconnectAttempts) {
          reconnectAttempts++;
          
          console.log('%c 嘗試重新建立 SSE 連接', 'background: #3F51B5; color: white; padding: 2px 5px; border-radius: 2px;', `重連次數: ${reconnectAttempts}/${maxReconnectAttempts}`);
          
          // 延遲一段時間後重新連接
          setTimeout(() => {
            if (currentOrderId.value && paymentStatus.value === 'PENDING') {
              console.log('%c 重新連接 SSE', 'background: #3F51B5; color: white; padding: 2px 5px; border-radius: 2px;', {
                orderId: currentOrderId.value,
                status: paymentStatus.value
              });
              startListeningForPaymentEvents();
            }
          }, reconnectDelay);
        } else if (reconnectAttempts >= maxReconnectAttempts) {
          console.warn('%c 已達到最大重連次數', 'background: #FF5722; color: white; padding: 2px 5px; border-radius: 2px;', `${maxReconnectAttempts} 次`);
          // 可以在這裡加入一些用戶通知或其他處理
        } else {
          console.log('%c 不需要重連 SSE', 'background: #795548; color: white; padding: 2px 5px; border-radius: 2px;', {
            orderId: currentOrderId.value,
            status: paymentStatus.value
          });
        }
      }
    };
  }
  
  // 停止監聽付款事件
  function stopListeningForPaymentEvents() {
    if (eventSource) {
      console.log('%c 關閉 SSE 連接', 'background: #607D8B; color: white; padding: 2px 5px; border-radius: 2px;');
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
