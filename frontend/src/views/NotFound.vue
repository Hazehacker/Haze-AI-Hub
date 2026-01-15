<template>
  <section class="not-found">
    <div class="not-found-card">
      <p class="not-found-label">Oops…</p>
      <h1 class="not-found-title">404</h1>
      <p class="not-found-subtitle">抱歉，您访问的页面不存在</p>
      <p class="not-found-description">
        可能是链接过期、地址输入错误，或者该页面已被移动。
      </p>
      <el-button type="primary" size="large" class="not-found-button" @click="goHome">
        返回主页
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push({ path: '/' })
}
</script>

<style scoped lang="scss">
.not-found {
  min-height: calc(100vh - 158px);
  padding: 3rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-color);
  transition: background-color 0.3s ease;
}

.not-found-card {
  width: 100%;
  max-width: 520px;
  padding: 3rem 2.5rem 2.5rem;
  text-align: center;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.12);
}

.not-found-label {
  font-size: 0.9rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #9ca3af;
  margin-bottom: 0.75rem;
}

.not-found-title {
  font-size: 4.5rem;
  font-weight: 800;
  margin-bottom: 0.5rem;
  background: linear-gradient(45deg, #007cf0, #00dfd8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.not-found-subtitle {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--text-color);
}

.not-found-description {
  font-size: 0.95rem;
  color: #6b7280;
  margin-bottom: 2rem;
}

.not-found-button {
  padding: 0 2.5rem;
}

/* 暗色模式适配，跟整体主题保持一致 */
.dark .not-found {
  background: #111827;
}

.dark .not-found-card {
  background: rgba(15, 23, 42, 0.9);
  border-color: rgba(148, 163, 184, 0.35);
  box-shadow: 0 22px 55px rgba(15, 23, 42, 0.9);
}

.dark .not-found-label {
  color: #6b7280;
}

.dark .not-found-description {
  color: #9ca3af;
}

@media (max-width: 768px) {
  .not-found {
    padding: 2rem 1.25rem;
  }

  .not-found-card {
    padding: 2.25rem 1.75rem 2rem;
  }

  .not-found-title {
    font-size: 3.25rem;
  }
}
</style>

