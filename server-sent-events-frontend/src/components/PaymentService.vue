<template>
  <div class="payment-service">
    <div v-if="paymentStore.hasActiveOrder" class="payment-status">
      <h3>訂單狀態</h3>
      <p><strong>訂單編號：</strong>{{ paymentStore.currentOrderId }}</p>
      <p>
        <strong>付款狀態：</strong>
        <span :class="statusClass">{{ statusText }}</span>
      </p>
      <p v-if="paymentStore.paymentMessage" class="message">
        {{ paymentStore.paymentMessage }}
      </p>

      <div v-if="paymentStore.isPaymentSuccess || paymentStore.isPaymentFailure" class="actions">
        <button @click="resetAndStartNew" class="btn btn-primary">
          開始新的付款
        </button>
      </div>
    </div>

    <div v-else class="payment-form">
      <h3>付款表單</h3>
      <div class="form-group">
        <label for="amount">金額</label>
        <input
          type="number"
          id="amount"
          v-model="amount"
          :disabled="paymentStore.isProcessing"
          min="1"
          step="1"
          class="form-control"
        />
      </div>

      <button
        @click="startPayment"
        class="btn btn-primary"
        :disabled="!isValidAmount || paymentStore.isProcessing"
      >
        {{ paymentStore.isProcessing ? '處理中...' : '付款' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { usePaymentStore } from '@/stores/payment';

const paymentStore = usePaymentStore();
const amount = ref<number>(100);

// 計算屬性
const isValidAmount = computed(() => {
  return amount.value > 0;
});

const statusText = computed(() => {
  switch (paymentStore.paymentStatus) {
    case 'PENDING':
      return '處理中';
    case 'SUCCESS':
      return '付款成功';
    case 'FAILURE':
      return '付款失敗';
    default:
      return '未知狀態';
  }
});

const statusClass = computed(() => {
  switch (paymentStore.paymentStatus) {
    case 'SUCCESS':
      return 'status-success';
    case 'FAILURE':
      return 'status-failure';
    case 'PENDING':
      return 'status-pending';
    default:
      return '';
  }
});

// 方法
const startPayment = async () => {
  if (!isValidAmount.value) return;

  console.log('%c 開始付款流程', 'background: #009688; color: white; padding: 2px 5px; border-radius: 2px;', {
    amount: amount.value
  });

  // 初始化付款
  const paymentUrl = await paymentStore.initializePayment(amount.value);

  if (paymentUrl) {
    console.log('%c 獲得付款 URL', 'background: #8BC34A; color: white; padding: 2px 5px; border-radius: 2px;', {
      paymentUrl
    });
    
    // 開始監聽付款事件
    paymentStore.startListeningForPaymentEvents();

    // 開啟第三方付款頁面
    window.open(`http://localhost:8080${paymentUrl}`, '_blank');
  } else {
    console.error('%c 付款初始化失敗', 'background: #F44336; color: white; padding: 2px 5px; border-radius: 2px;');
  }
};

// 重置付款並開始新付款
const resetAndStartNew = () => {
  console.log('%c 重置付款狀態', 'background: #FF5722; color: white; padding: 2px 5px; border-radius: 2px;', {
    orderId: paymentStore.currentOrderId,
    status: paymentStore.paymentStatus,
    message: paymentStore.paymentMessage
  });
  paymentStore.resetPayment();
  // 不需要立即開始新的付款，只需要清除當前付款狀態
  // 如果用戶想要創建新付款，他們可以填寫表單並點擊付款按鈕
};

// 生命週期鉤子
onMounted(() => {
  // 如果有進行中的訂單，開始監聽付款事件
  if (paymentStore.hasActiveOrder && paymentStore.paymentStatus === 'PENDING') {
    paymentStore.startListeningForPaymentEvents();
  }
});

onUnmounted(() => {
  // 確保在組件卸載時停止監聽
  paymentStore.stopListeningForPaymentEvents();
});
</script>

<style scoped>
.payment-service {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.payment-status,
.payment-form {
  padding: 20px;
}

h3 {
  margin-top: 0;
  color: #333;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 20px;
}

label {
  display: block;
  margin-bottom: 8px;
  font-weight: bold;
}

.form-control {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
}

.btn {
  padding: 10px 16px;
  font-size: 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.btn-primary {
  background-color: #4CAF50;
  color: white;
}

.btn-primary:hover {
  background-color: #45a049;
}

.btn-primary:disabled {
  background-color: #9e9e9e;
  cursor: not-allowed;
}

.status-success {
  color: #4CAF50;
  font-weight: bold;
}

.status-failure {
  color: #f44336;
  font-weight: bold;
}

.status-pending {
  color: #ff9800;
  font-weight: bold;
}

.message {
  margin-top: 10px;
  padding: 10px;
  background-color: #f5f5f5;
  border-left: 4px solid #2196F3;
}

.actions {
  margin-top: 20px;
}
</style>
