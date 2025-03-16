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
  
  // 計算屬性
  const isPaymentSuccess = computed(() => paymentStatus.value === 'SUCCESS');
  const isPaymentFailure = computed(() => paymentStatus.value === 'FAILURE');
  const hasActiveOrder = computed(() => !!currentOrderId.value);
  
  // 方法
  // 初始化支付
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
        throw new Error('初始化支付失敗');
      }
      
      const data = await response.json();
      currentOrderId.value = data.orderId;
      paymentStatus.value = 'PENDING';
      
      return data.paymentUrl;
    } catch (error) {
      console.error('初始化支付出錯:', error);
      return null;
    } finally {
      isProcessing.value = false;
    }
  }
  
  // 開始監聽支付事件
  function startListeningForPaymentEvents() {
    if (eventSource) {
      // 如果已經有連接，先關閉
      eventSource.close();
    }
    
    // 建立新的 SSE 連接
    eventSource = new EventSource('http://localhost:8080/api/sse/payment-events');
    
    // 添加事件監聽器
    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        // 檢查是否為當前訂單的事件
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log(`收到支付狀態更新: ${data.status}`);
          
          // 如果支付已完成（成功或失敗），關閉連接
          if (data.status === 'SUCCESS' || data.status === 'FAILURE') {
            stopListeningForPaymentEvents();
          }
        }
      } catch (error) {
        console.error('處理支付事件出錯:', error);
      }
    };
    
    // 錯誤處理
    eventSource.onerror = (error) => {
      console.error('SSE 連接錯誤:', error);
      stopListeningForPaymentEvents();
    };
  }
  
  // 停止監聽支付事件
  function stopListeningForPaymentEvents() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
  }
  
  // 重置支付狀態
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
