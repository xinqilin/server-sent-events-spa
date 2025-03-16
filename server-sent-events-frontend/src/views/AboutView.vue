<template>
  <div class="about">
    <h1>關於此示範</h1>
    <div class="content">
      <h2>技術實現</h2>
      <p>
        此示範應用展示了如何使用 Server-Sent Events (SSE) 實現第三方付款流程的即時通知功能。
      </p>
      
      <h3>使用的技術：</h3>
      <ul>
        <li><strong>前端：</strong> Vue 3, TypeScript, Pinia</li>
        <li><strong>後端：</strong> Spring Boot 3, WebFlux, Server-Sent Events</li>
      </ul>
      
      <h3>核心功能：</h3>
      <ul>
        <li>使用 SSE 建立從伺服器到客戶端的單向即時通訊通道</li>
        <li>跨頁面通訊，允許主頁接收第三方付款頁面的付款結果</li>
        <li>透過 Pinia 管理付款狀態</li>
        <li>支援付款成功和失敗場景</li>
      </ul>
      
      <h2>Server-Sent Events (SSE) 簡介</h2>
      <p>
        Server-Sent Events 是一種基於 HTTP 的技術，允許伺服器向瀏覽器客戶端推送即時更新。
        與 WebSockets 不同，SSE 是單向的（只能從伺服器到客戶端），並且基於標準 HTTP 協議，
        這使得它更容易實現和維護。
      </p>
      
      <p>
        在本示範中，當使用者在第三方付款頁面完成或取消付款後，後端伺服器使用 SSE 將付款結果
        推送到原始頁面，這樣使用者就能即時看到付款結果，而無需手動重新整理頁面。
      </p>
    </div>
  </div>
</template>

<style scoped>
.about {
  max-width: 800px;
  margin: 0 auto;
  padding: 30px 20px;
}

h1 {
  color: #333;
  text-align: center;
  margin-bottom: 30px;
}

.content {
  background-color: #f9f9f9;
  padding: 30px;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

h2 {
  color: #333;
  margin-top: 30px;
  margin-bottom: 15px;
}

h3 {
  color: #444;
  margin-top: 20px;
  margin-bottom: 10px;
}

p {
  line-height: 1.6;
  margin-bottom: 16px;
  color: #555;
}

ul {
  margin-bottom: 20px;
  padding-left: 20px;
}

li {
  margin-bottom: 8px;
  line-height: 1.6;
}
</style>
