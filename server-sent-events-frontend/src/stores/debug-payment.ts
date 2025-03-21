import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useDebugPaymentStore = defineStore('debug-payment', () => {
  // 狀態
  const currentOrderId = ref<string | null>(null);
  const paymentStatus = ref<'PENDING' | 'SUCCESS' | 'FAILURE' | null>(null);
  const paymentMessage = ref<string>('');
  const isProcessing = ref<boolean>(false);
  
  // 調試用狀態
  const receivedEvents = ref<any[]>([]);
  const lastError = ref<string | null>(null);
  
  // EventSource 實例
  let eventSource: EventSource | null = null;
  
  // 計算屬性
  const isPaymentSuccess = computed(() => paymentStatus.value === 'SUCCESS');
  const isPaymentFailure = computed(() => paymentStatus.value === 'FAILURE');
  const hasActiveOrder = computed(() => !!currentOrderId.value);
  
  // 方法
  // 初始化付款
  async function initializePayment(amount: number): Promise<string | null> {
    try {
      isProcessing.value = true;
      
      // 清空之前的事件記錄
      receivedEvents.value = [];
      lastError.value = null;
      
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
        const errorText = await response.text();
        throw new Error(`初始化付款失敗: ${response.status} - ${errorText}`);
      }
      
      const data = await response.json();
      currentOrderId.value = data.orderId;
      paymentStatus.value = 'PENDING';
      
      console.log('%c [DEBUG] 初始化付款成功', 'background: #009688; color: white; padding: 2px 5px; border-radius: 2px;', {
        orderId: data.orderId,
        paymentUrl: data.paymentUrl
      });
      
      // 立即開始監聽付款事件
      startListeningForPaymentEvents();
      
      return data.paymentUrl;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '未知錯誤';
      console.error('%c [DEBUG] 初始化付款出錯', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', errorMessage);
      lastError.value = errorMessage;
      return null;
    } finally {
      isProcessing.value = false;
    }
  }
  
  // 開始監聽付款事件
  function startListeningForPaymentEvents() {
    console.log('%c [DEBUG] 啟動 SSE 連接', 'background: #009688; color: white; padding: 2px 5px; border-radius: 2px;', {
      currentOrderId: currentOrderId.value,
      paymentStatus: paymentStatus.value
    });
    
    if (eventSource) {
      // 如果已經有連接，先關閉
      eventSource.close();
    }
    
    // 建立新的 SSE 連接，添加時間戳避免緩存
    const timestamp = new Date().getTime();
    eventSource = new EventSource(`http://localhost:8080/api/sse/payment-events?_=${timestamp}`);
    
    // 連接建立時的處理
    eventSource.onopen = (event) => {
      console.log('%c [DEBUG] SSE 連接已建立', 'background: #4CAF50; color: white; padding: 2px 5px; border-radius: 2px;');
      receivedEvents.value.push({
        type: 'OPEN',
        timestamp: new Date().toISOString(),
        details: '連接已建立'
      });
    };
    
    // 添加通用事件監聽器
    eventSource.onmessage = (event) => {
      try {
        console.log('%c [DEBUG] 收到通用事件', 'background: #2196F3; color: white; padding: 2px 5px; border-radius: 2px;', event.data);
        
        receivedEvents.value.push({
          type: 'MESSAGE',
          timestamp: new Date().toISOString(),
          data: event.data
        });
        
        const data = JSON.parse(event.data);
        
        // 處理心跳事件
        if (data.eventType === 'HEARTBEAT') {
          console.log('%c [DEBUG] 收到心跳事件', 'background: #9C27B0; color: white; padding: 2px 5px; border-radius: 2px;', data);
          return;
        }
        
        // 檢查是否為當前訂單的事件
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log('%c [DEBUG] 收到訂單狀態更新', 'background: #FF9800; color: white; padding: 2px 5px; border-radius: 2px;', {
            orderId: data.orderId,
            status: data.status,
            message: data.message
          });
          
          // 如果付款已完成（成功或失敗）
          if (data.status === 'SUCCESS' || data.status === 'FAILURE') {
            console.log('%c [DEBUG] 付款已完成', 'background: #4CAF50; color: white; padding: 2px 5px; border-radius: 2px;', {
              status: data.status
            });
          }
        }
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : '未知錯誤';
        console.error('%c [DEBUG] 處理事件出錯', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', errorMessage);
        lastError.value = errorMessage;
      }
    };
    
    // 專門的付款狀態事件監聽器
    eventSource.addEventListener('PAYMENT_STATUS', (event: MessageEvent) => {
      try {
        console.log('%c [DEBUG] 收到 PAYMENT_STATUS 事件', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', event.data);
        
        receivedEvents.value.push({
          type: 'PAYMENT_STATUS',
          timestamp: new Date().toISOString(),
          data: event.data
        });
        
        const data = JSON.parse(event.data);
        
        if (data.orderId === currentOrderId.value) {
          paymentStatus.value = data.status as any;
          paymentMessage.value = data.message || '';
          
          console.log('%c [DEBUG] 付款狀態更新 (專用監聽器)', 'background: #E91E63; color: white; padding: 2px 5px; border-radius: 2px;', {
            orderId: data.orderId,
            status: data.status
          });
        }
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : '未知錯誤';
        console.error('%c [DEBUG] 處理 PAYMENT_STATUS 事件出錯', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', errorMessage);
        lastError.value = errorMessage;
      }
    });
    
    // 心跳事件監聽器
    eventSource.addEventListener('heartbeat', (event: MessageEvent) => {
      console.log('%c [DEBUG] 收到心跳事件 (專用監聽器)', 'background: #673AB7; color: white; padding: 2px 5px; border-radius: 2px;', event.data);
      
      receivedEvents.value.push({
        type: 'HEARTBEAT',
        timestamp: new Date().toISOString(),
        data: event.data
      });
    });
    
    // 錯誤處理
    eventSource.onerror = (error) => {
      console.error('%c [DEBUG] SSE 連接錯誤', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', error);
      
      receivedEvents.value.push({
        type: 'ERROR',
        timestamp: new Date().toISOString(),
        details: 'SSE 連接錯誤'
      });
      
      lastError.value = 'SSE 連接錯誤';
      
      // 檢查連接狀態
      if (eventSource && eventSource.readyState === EventSource.CLOSED) {
        console.warn('%c [DEBUG] SSE 連接已關閉', 'background: #FF9800; color: white; padding: 2px 5px; border-radius: 2px;');
        
        receivedEvents.value.push({
          type: 'CLOSED',
          timestamp: new Date().toISOString(),
          details: 'SSE 連接已關閉'
        });
      }
    };
  }
  
  // 停止監聽付款事件
  function stopListeningForPaymentEvents() {
    if (eventSource) {
      console.log('%c [DEBUG] 關閉 SSE 連接', 'background: #607D8B; color: white; padding: 2px 5px; border-radius: 2px;');
      eventSource.close();
      eventSource = null;
      
      receivedEvents.value.push({
        type: 'CLOSED_MANUALLY',
        timestamp: new Date().toISOString(),
        details: '手動關閉 SSE 連接'
      });
    }
  }
  
  // 重置付款狀態
  function resetPayment() {
    stopListeningForPaymentEvents();
    currentOrderId.value = null;
    paymentStatus.value = null;
    paymentMessage.value = '';
    isProcessing.value = false;
    receivedEvents.value = [];
    lastError.value = null;
  }
  
  // 手動模擬付款成功
  async function simulatePaymentSuccess() {
    if (!currentOrderId.value) {
      console.error('%c [DEBUG] 無法模擬付款：沒有活躍的訂單', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;');
      return false;
    }
    
    try {
      console.log('%c [DEBUG] 模擬付款成功請求', 'background: #4CAF50; color: white; padding: 2px 5px; border-radius: 2px;', {
        orderId: currentOrderId.value
      });
      
      const response = await fetch(`http://localhost:8080/api/payment/${currentOrderId.value}/simulate-success`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`模擬付款成功請求失敗: ${response.status} - ${errorText}`);
      }
      
      return true;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '未知錯誤';
      console.error('%c [DEBUG] 模擬付款成功出錯', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', errorMessage);
      lastError.value = errorMessage;
      return false;
    }
  }
  
  // 手動模擬付款失敗
  async function simulatePaymentFailure(reason: string = '使用者取消付款') {
    if (!currentOrderId.value) {
      console.error('%c [DEBUG] 無法模擬付款失敗：沒有活躍的訂單', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;');
      return false;
    }
    
    try {
      console.log('%c [DEBUG] 模擬付款失敗請求', 'background: #FF5722; color: white; padding: 2px 5px; border-radius: 2px;', {
        orderId: currentOrderId.value,
        reason
      });
      
      const response = await fetch(`http://localhost:8080/api/payment/${currentOrderId.value}/simulate-failure`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason })
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`模擬付款失敗請求失敗: ${response.status} - ${errorText}`);
      }
      
      return true;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '未知錯誤';
      console.error('%c [DEBUG] 模擬付款失敗出錯', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;', errorMessage);
      lastError.value = errorMessage;
      return false;
    }
  }
  
  return {
    // 狀態
    currentOrderId,
    paymentStatus,
    paymentMessage,
    isProcessing,
    receivedEvents,
    lastError,
    
    // 計算屬性
    isPaymentSuccess,
    isPaymentFailure,
    hasActiveOrder,
    
    // 方法
    initializePayment,
    startListeningForPaymentEvents,
    stopListeningForPaymentEvents,
    resetPayment,
    simulatePaymentSuccess,
    simulatePaymentFailure
  };
});
