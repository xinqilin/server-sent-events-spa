<template>
  <header>
    <div class="wrapper">
      <h1>第三方金流 SSE POC</h1>
      <nav>
        <RouterLink to="/">首頁</RouterLink>
      </nav>
    </div>
  </header>
  <RouterView />
</template>

<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<style>
@import '@/assets/main.css';

header {
  line-height: 1.5;
  max-height: 100vh;
}

.wrapper {
  margin: 0 auto;
  padding: 2rem;
  max-width: 1000px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

nav {
  width: 100%;
  font-size: 1rem;
  text-align: center;
  margin: 1rem 0;
}

nav a {
  display: inline-block;
  padding: 0 1rem;
  margin: 0 0.5rem;
  border-radius: 4px;
  background-color: #f0f0f0;
  border: 1px solid #ddd;
  text-decoration: none;
  color: #333;
  transition: all 0.3s;
}

nav a:hover {
  background-color: #e0e0e0;
}

nav a.router-link-exact-active {
  color: white;
  background-color: #4caf50;
  border-color: #4caf50;
}

h1 {
  font-weight: 500;
  font-size: 2.2rem;
  margin-bottom: 0.5rem;
  color: #333;
}

@media (min-width: 1024px) {
  header {
    display: flex;
    place-items: center;
  }

  .wrapper {
    flex-direction: row;
    justify-content: space-between;
  }

  nav {
    text-align: right;
    margin: 0;
    padding: 1rem 0;
  }
}
</style>
