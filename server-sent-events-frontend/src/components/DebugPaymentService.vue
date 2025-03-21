<template>
  <div class="debug-payment-service">
    <h2>調試付款連接</h2>
    
    <div class="debug-panel">
      <div class="status-panel">
        <h3>訂單狀態</h3>
        <div class="status-info">
          <p><strong>訂單編號:</strong> {{ paymentStore.currentOrderId || '無' }}</p>
          <p><strong>付款狀態:</strong> <span :class="statusClass">{{ statusText }}</span></p>
          <p v-if="paymentStore.paymentMessage"><strong>訊息:</strong> {{ paymentStore.paymentMessage }}</p>
          <p><strong>處理中:</strong> {{ paymentStore.isProcessing ? '是' : '否' }}</p>
        </div>
        
        <div class="button-group">
          <button 
            @click="startPayment" 
            :disabled="paymentStore.isProcessing || paymentStore.hasActiveOrder"
            class="btn btn-primary">
            初始化新付款
          </button>
          
          <button 
            @click="paymentStore.simulatePaymentSuccess()" 
            :disabled="!paymentStore.hasActiveOrder || paymentStore.isPaymentSuccess || paymentStore.isPaymentFailure"
            class="btn btn-success">
            模擬付款成功
          </button>
          
          <button 
            @click="paymentStore.simulatePaymentFailure()" 
            :disabled="!paymentStore.hasActiveOrder || paymentStore.isPaymentSuccess || paymentStore.isPaymentFailure"
            class="btn btn-danger">
            模擬付款失敗
          </button>
          
          <button 
            @click="resetPayment" 
            :disabled="!paymentStore.hasActiveOrder"
            class="btn btn-secondary">
            重置付款
          </button>
          
          <button 
            @click="reconnect" 
            :disabled="!paymentStore.hasActiveOrder"
            class="btn btn-info">
            重新連接
          </button>
        </div>
      </div>
      
      <div class="events-panel">
        <h3>收到的事件 ({{ paymentStore.receivedEvents.length }})</h3>
        <div class="events-list">
          <div v-if="paymentStore.receivedEvents.length === 0" class="no-events">
            尚未收到任何事件
          </div>
          <div 
            v-for="(event, index) in paymentStore.receivedEvents" 
            :key="index" 
            class="event-item"
            :class="eventClass(event)">
            <div class="event-time">{{ formatTime(event.timestamp) }}</div>
            <div class="event-type">{{ event.type }}</div>
            <div class="event-data" v-if="event.data">{{ event.data }}</div>
            <div class="event-details" v-if="event.details">{{ event.details }}</div>
          </div>
        </div>
      </div>
    </div>
    
    <div v-if="paymentStore.lastError" class="error-panel">
      <h3>最後錯誤</h3>
      <div class="error-message">{{ paymentStore.lastError }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useDebugPaymentStore } from '@/stores/debug-payment';

const paymentStore = useDebugPaymentStore();
const amount = ref<number>(100);

// 狀態文字
const statusText = computed(() => {
  switch (paymentStore.paymentStatus) {
    case 'PENDING': return '處理中';
    case 'SUCCESS': return '付款成功';
    case 'FAILURE': return '付款失敗';
    default: return '無';
  }
});

// 狀態樣式
const statusClass = computed(() => {
  switch (paymentStore.paymentStatus) {
    case 'PENDING': return 'status-pending';
    case 'SUCCESS': return 'status-success';
    case 'FAILURE': return 'status-failure';
    default: return '';
  }
});

// 開始新付款
const startPayment = async () => {
  const paymentUrl = await paymentStore.initializePayment(amount.value);
  if (paymentUrl) {
    // 不打開新視窗，我們會使用模擬按鈕
    console.log(`%c [DEBUG] 付款URL創建成功`, 'background: #8BC34A; color: white; padding: 2px 5px; border-radius: 2px;', paymentUrl);
  }
};

// 重置付款
const resetPayment = () => {
  paymentStore.resetPayment();
};

// 重新連接
const reconnect = () => {
  paymentStore.stopListeningForPaymentEvents();
  paymentStore.startListeningForPaymentEvents();
};

// 格式化時間
const formatTime = (timestamp: string) => {
  try {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) + 
           '.' + date.getMilliseconds().toString().padStart(3, '0');
  } catch (e) {
    return timestamp;
  }
};

// 事件樣式
const eventClass = (event: any) => {
  switch (event.type) {
    case 'OPEN': return 'event-open';
    case 'HEARTBEAT': return 'event-heartbeat';
    case 'PAYMENT_STATUS': return 'event-payment';
    case 'MESSAGE': return 'event-message';
    case 'ERROR': return 'event-error';
    case 'CLOSED': 
    case 'CLOSED_MANUALLY': return 'event-closed';
    default: return '';
  }
};
</script>

<style scoped>
.debug-payment-service {
  font-family: Arial, sans-serif;
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

h2 {
  color: #333;
  margin-bottom: 20px;
  text-align: center;
}

h3 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #333;
}

.debug-panel {
  display: flex;
  gap: 20px;
}

.status-panel {
  flex: 1;
  background-color: white;
  padding: 20px;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.events-panel {
  flex: 1;
  background-color: white;
  padding: 20px;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  max-height: 500px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.events-list {
  overflow-y: auto;
  flex-grow: 1;
}

.status-info {
  margin-bottom: 20px;
}

.status-info p {
  margin: 8px 0;
}

.status-pending {
  color: #ff9800;
  font-weight: bold;
}

.status-success {
  color: #4caf50;
  font-weight: bold;
}

.status-failure {
  color: #f44336;
  font-weight: bold;
}

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  color: white;
  font-weight: bold;
  cursor: pointer;
  min-width: 120px;
  transition: opacity 0.2s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background-color: #2196f3;
}

.btn-success {
  background-color: #4caf50;
}

.btn-danger {
  background-color: #f44336;
}

.btn-secondary {
  background-color: #607d8b;
}

.btn-info {
  background-color: #00bcd4;
}

.event-item {
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 4px;
  background-color: #f5f5f5;
  font-size: 13px;
  display: flex;
  flex-direction: column;
}

.event-time {
  font-size: 12px;
  color: #666;
  margin-bottom: 3px;
}

.event-type {
  font-weight: bold;
  margin-bottom: 3px;
}

.event-data, .event-details {
  font-family: monospace;
  background-color: #f0f0f0;
  padding: 4px;
  border-radius: 3px;
  margin-top: 4px;
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 12px;
}

.event-open {
  background-color: #e8f5e9;
  border-left: 4px solid #4caf50;
}

.event-heartbeat {
  background-color: #f3e5f5;
  border-left: 4px solid #9c27b0;
}

.event-payment {
  background-color: #fce4ec;
  border-left: 4px solid #e91e63;
}

.event-message {
  background-color: #e3f2fd;
  border-left: 4px solid #2196f3;
}

.event-error {
  background-color: #ffebee;
  border-left: 4px solid #f44336;
}

.event-closed {
  background-color: #eceff1;
  border-left: 4px solid #607d8b;
}

.no-events {
  text-align: center;
  color: #999;
  padding: 20px;
}

.error-panel {
  margin-top: 20px;
  background-color: #ffebee;
  padding: 15px;
  border-radius: 6px;
  border-left: 4px solid #f44336;
}

.error-message {
  color: #d32f2f;
  font-family: monospace;
}
</style>
