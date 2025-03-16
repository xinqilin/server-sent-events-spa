<template>
  <main class="home">
    <PaymentService />

    <div class="how-it-works">
      <ol>
        <li>點擊「付款」按鈕後 => create payment</li>
        <li>redirect to third party</li>
        <li>前端通過 SSE 建立與後端的連接，等待付款結果通知</li>
        <li>付款完成後 callback 更新資料庫</li>
        <li>通過 SSE 向前端發送付款結果</li>
        <li>前端接收通知並更新頁面狀態</li>
      </ol>
    </div>
  </main>
</template>

<script setup lang="ts">
import PaymentService from '@/components/PaymentService.vue'
</script>

<style scoped>
.home {
  max-width: 800px;
  margin: 0 auto;
  padding: 30px 20px;
}

h1 {
  color: #333;
  text-align: center;
  margin-bottom: 20px;
}

.description {
  margin-bottom: 30px;
  line-height: 1.6;
  color: #555;
  text-align: center;
}

.how-it-works {
  margin-top: 50px;
  padding: 20px;
  background-color: #f5f5f5;
  border-radius: 8px;
}

h2 {
  color: #333;
  margin-bottom: 15px;
}

ol {
  padding-left: 20px;
}

li {
  margin-bottom: 10px;
  line-height: 1.6;
}
</style>
