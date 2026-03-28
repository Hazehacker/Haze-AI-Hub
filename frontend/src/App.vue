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
const comfortDialogVisible = ref(false)

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

const openComfortDialog = () => {
  comfortDialogVisible.value = true
}

const closeComfortDialog = () => {
  comfortDialogVisible.value = false
}

const enterComfortSimulator = () => {
  comfortDialogVisible.value = false
  router.push('/game')
}

onMounted(async () => {
  window.addEventListener('open-login-dialog', handleOpenLoginDialog)
  window.addEventListener('openRegisterDialog', handleOpenRegisterDialog)
  
  // 监听路由变化
  router.afterEach((to, from) => {
    // 如果是从 Astra 页面离开
    if (from.path === '/astra') {
      window.dispatchEvent(new CustomEvent('cleanupAstra'))
    }
    currentRoute.value = to.path
  })
  
  // 页面刷新时，如果 token 存在但用户信息不存在，尝试获取用户信息
  // 如果获取失败，不清除 token，让用户保持登录状态（可能是网络问题）
  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    } catch (error) {
      // 获取失败时不清除 token，可能是网络波动或后端暂时不可用
      // 用户仍然可以访问不需要登录的页面
      console.warn('获取用户信息失败，但保留 token:', error)
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
    <!-- 左上角返回主页按钮 -->
    <button 
      v-if="currentRoute !== '/'" 
      @click="router.push('/')" 
      class="home-btn-fixed"
      title="返回主页"
    >
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="icon">
        <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 12 8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
      </svg>
      <span>主页</span>
    </button>

    <nav class="navbar">
      <div class="logo" @click="openComfortDialog" style="cursor: pointer;">Haze AI Hub</div>
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

    <!-- 哄哄模拟器彩蛋对话框 -->
    <Transition name="dialog-fade">
      <div v-if="comfortDialogVisible" class="dialog-overlay" @click="closeComfortDialog">
        <div class="comfort-dialog" @click.stop>
          <button class="close-btn" @click="closeComfortDialog">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          
          <div class="comfort-content">
            <div class="heart-icon">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
              </svg>
            </div>
            
            <h2 class="comfort-title">哄哄模拟器</h2>
            <p class="comfort-subtitle">一个帮助你练习哄女朋友开心的小游戏</p>
            
            <button class="enter-btn" @click="enterComfortSimulator">
              <span>进入游戏</span>
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </Transition>
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

.home-btn-fixed {
  position: fixed;
  top: 1rem;
  left: 1rem;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  background: rgba(0, 124, 240, 0.95);
  border: none;
  border-radius: 2rem;
  color: white;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 124, 240, 0.3);
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);

  &:hover {
    background: rgba(0, 124, 240, 1);
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 124, 240, 0.4);
  }

  &:active {
    transform: translateY(0);
  }

  .icon {
    width: 1.25rem;
    height: 1.25rem;
  }

  span {
    font-weight: 600;
  }
}

.dark .home-btn-fixed {
  background: rgba(0, 124, 240, 0.9);
  box-shadow: 0 4px 12px rgba(0, 124, 240, 0.4);

  &:hover {
    background: rgba(0, 124, 240, 1);
    box-shadow: 0 6px 16px rgba(0, 124, 240, 0.5);
  }
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
    transition: opacity 0.2s;

    &:hover {
      opacity: 0.8;
    }
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
  .home-btn-fixed {
    padding: 0.625rem 1rem;
    font-size: 0.875rem;

    .icon {
      width: 1.125rem;
      height: 1.125rem;
    }

    span {
      display: none;
    }
  }

  .navbar {
    padding: 1rem;

    .logo {
      font-size: 1.25rem;
    }
  }
}

/* 哄哄模拟器彩蛋对话框样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 1rem;
}

.comfort-dialog {
  position: relative;
  background: white;
  border-radius: 24px;
  padding: 3rem 2.5rem;
  max-width: 480px;
  width: 100%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: dialog-bounce 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.dark .comfort-dialog {
  background: #2a2a2a;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6);
}

@keyframes dialog-bounce {
  0% {
    transform: scale(0.3);
    opacity: 0;
  }
  50% {
    transform: scale(1.05);
  }
  70% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.close-btn {
  position: absolute;
  top: 1rem;
  right: 1rem;
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  svg {
    width: 20px;
    height: 20px;
    color: #666;
  }

  &:hover {
    background: rgba(0, 0, 0, 0.1);
    transform: rotate(90deg);
  }
}

.dark .close-btn {
  background: rgba(255, 255, 255, 0.1);

  svg {
    color: #ccc;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.15);
  }
}

.comfort-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.heart-icon {
  width: 80px;
  height: 80px;
  margin-bottom: 1.5rem;
  animation: heart-beat 1.5s ease-in-out infinite;

  svg {
    width: 100%;
    height: 100%;
    stroke: #007CF0;
    filter: drop-shadow(0 4px 12px rgba(0, 124, 240, 0.3));
  }
}

@keyframes heart-beat {
  0%, 100% {
    transform: scale(1);
  }
  10%, 30% {
    transform: scale(1.1);
  }
  20%, 40% {
    transform: scale(1);
  }
}

.comfort-title {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.75rem;
  background: linear-gradient(45deg, #007CF0, #00DFD8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.comfort-subtitle {
  font-size: 1rem;
  color: #666;
  margin-bottom: 2rem;
  line-height: 1.6;
}

.dark .comfort-subtitle {
  color: #aaa;
}

.enter-btn {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 2.5rem;
  background: linear-gradient(135deg, #007CF0, #00DFD8);
  border: none;
  border-radius: 50px;
  color: white;
  font-size: 1.125rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(0, 124, 240, 0.4);
  transition: all 0.3s ease;

  svg {
    width: 20px;
    height: 20px;
    transition: transform 0.3s ease;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 32px rgba(0, 124, 240, 0.5);

    svg {
      transform: translateX(4px);
    }
  }

  &:active {
    transform: translateY(0);
  }
}

.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: opacity 0.3s ease;
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}

@media (max-width: 640px) {
  .comfort-dialog {
    padding: 2.5rem 1.5rem;
  }

  .comfort-title {
    font-size: 1.75rem;
  }

  .comfort-subtitle {
    font-size: 0.9375rem;
  }

  .enter-btn {
    padding: 0.875rem 2rem;
    font-size: 1rem;
  }

  .heart-icon {
    width: 64px;
    height: 64px;
  }
}
</style>
