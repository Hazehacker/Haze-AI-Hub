<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useDark, useToggle } from '@vueuse/core'
import { SunIcon, MoonIcon } from '@heroicons/vue/24/outline'
import { useRouter } from 'vue-router'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import LoginDialog from '@/components/common/LoginDialog.vue'
import RegisterDialog from '@/components/common/RegisterDialog.vue'
import { useUserStore } from '@/stores/user'

const isDark = useDark()
const toggleDark = useToggle(isDark)
const router = useRouter()
const userStore = useUserStore()

// 添加全局状态来跟踪当前路由
const currentRoute = ref(router.currentRoute.value.path)
const loginDialogRef = ref<any>()
const registerDialogRef = ref<any>()

const avatarUrl = computed(() => {
  const avatar = (userStore.userInfo as any)?.avatar
  return typeof avatar === 'string' && avatar ? avatar : ''
})

const openLogin = () => {
  if (userStore.token) {
    // 已登录，跳转到个人信息页面
    router.push('/profile').catch(err => {
      console.error('路由跳转失败:', err)
    })
  } else {
    // 未登录，打开登录对话框
    window.dispatchEvent(new CustomEvent('open-login-dialog'))
  }
}

const handleOpenLoginDialog = () => {
  if (loginDialogRef.value) loginDialogRef.value.open()
}

const handleOpenRegisterDialog = () => {
  if (registerDialogRef.value) registerDialogRef.value.open()
}

onMounted(async () => {
  window.addEventListener('open-login-dialog', handleOpenLoginDialog)
  window.addEventListener('openRegisterDialog', handleOpenRegisterDialog)
  
  // 监听路由变化
  router.afterEach((to, from) => {
    // 如果是从 ChatPDF 页面离开
    if (from.path === '/chat-pdf') {
      window.dispatchEvent(new CustomEvent('cleanupChatPDF'))
    }
    currentRoute.value = to.path
  })
  
  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    } catch {
      await userStore.logout()
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('open-login-dialog', handleOpenLoginDialog)
  window.removeEventListener('openRegisterDialog', handleOpenRegisterDialog)
})
</script>

<template>
  <div class="app" :class="{ 'dark': isDark }">
    <nav class="navbar">
      <router-link to="/" class="logo">Haze AI Hub</router-link>
      <div class="nav-right">
        <button class="avatar-btn" @click="openLogin" :title="userStore.token ? '已登录' : '登录'">
          <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" alt="avatar" />
          <span v-else class="avatar-fallback">AI</span>
        </button>
        <button @click="toggleDark()" class="theme-toggle">
          <SunIcon v-if="isDark" class="icon" />
          <MoonIcon v-else class="icon" />
        </button>
      </div>
    </nav>
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <!-- 全局登录弹窗（复刻 vue-blog） -->
    <LoginDialog ref="loginDialogRef" />
    <!-- 全局注册弹窗（复刻 vue-blog） -->
    <RegisterDialog ref="registerDialogRef" />
  </div>
</template>

<style lang="scss">
:root {
  --bg-color: #f5f5f5;
  --text-color: #333;
}

.dark {
  --bg-color: #1a1a1a;
  --text-color: #fff;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen,
    Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
  color: var(--text-color);
  background: var(--bg-color);
  min-height: 100vh;
}

.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  .logo {
    font-size: 1.5rem;
    font-weight: bold;
    text-decoration: none;
    color: inherit;
    background: linear-gradient(45deg, #007CF0, #00DFD8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .theme-toggle {
    background: none;
    border: none;
    cursor: pointer;
    padding: 0.5rem;
    border-radius: 50%;
    transition: background-color 0.3s;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
    }

    .icon {
      width: 24px;
      height: 24px;
      color: var(--text-color);
    }
  }

  .dark & {
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  }
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.avatar-btn {
  width: 40px;
  height: 40px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.08);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: background-color 0.2s ease, border-color 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
    border-color: rgba(255, 255, 255, 0.3);
  }
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 0.5px;
  background: linear-gradient(45deg, #007cf0, #00dfd8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .navbar {
    padding: 1rem;
  }
}
</style>
