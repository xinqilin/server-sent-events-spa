<template>
  <main class="home">
    <h1>付款示範</h1>
    <p class="description">
      這是一個使用 Server-Sent Events (SSE) 實現的第三方支付流程模擬示範。點擊「付款」按鈕後，
      系統會打開一個新的瀏覽器窗口模擬第三方支付頁面，當您在該頁面完成或取消付款後，
      原頁面會通過 SSE 接收付款結果通知並更新狀態。
    </p>
    
    <PaymentService />
    
    <div class="how-it-works">
      <h2>工作原理</h2>
      <ol>
        <li>當您點擊「付款」按鈕時，系統會向後端發送初始化支付的請求</li>
        <li>後端創建訂單並回傳支付頁面 URL</li>
        <li>前端打開新窗口顯示第三方支付頁面</li>
        <li>同時，前端通過 SSE 建立與後端的連接，等待付款結果通知</li>
        <li>當您在支付頁面完成或取消付款時，支付頁面會向後端發送回調</li>
        <li>後端接收回調後，通過 SSE 向前端發送支付結果</li>
        <li>前端接收通知並更新頁面狀態</li>
      </ol>
    </div>
  </main>
</template>

<script setup lang="ts">
import PaymentService from '@/components/PaymentService.vue';
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
