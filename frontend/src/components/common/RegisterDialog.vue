<template>
  <div v-if="visible" class="register-dialog-overlay" @click="closeDialog">
    <div class="register-dialog-container" @click.stop>
      <div class="register-card">
        <!-- 关闭按钮 -->
        <button @click="closeDialog" class="close-btn">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>

        <!-- 标题 -->
        <div class="register-header">
          <h1 class="register-title">注册到 Hazenix's Blog</h1>
          <p class="register-subtitle">创建您的账号，开始使用</p>
        </div>

        <!-- 注册表单 -->
        <el-form :model="registerForm" :rules="rules" ref="formRef" class="register-form">
          <el-form-item prop="username">
            <label class="form-label">用户名</label>
            <el-input v-model="registerForm.username" placeholder="请输入用户名" class="form-input" />
          </el-form-item>

          <el-form-item prop="email">
            <label class="form-label">邮箱</label>
            <el-input v-model="registerForm.email" placeholder="your@email.com" class="form-input" />
          </el-form-item>

          <el-form-item prop="password">
            <label class="form-label">密码</label>
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="........"
              class="form-input"
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <label class="form-label">确认密码</label>
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="........"
              class="form-input"
            />
          </el-form-item>

          <!-- 注册按钮 -->
          <el-form-item>
            <el-button type="primary" class="register-submit-btn" @click="handleRegister" :loading="loading">
              注册
            </el-button>
          </el-form-item>

          <!-- 社交注册按钮 -->
          <div class="social-register-section">
            <div class="divider">
              <span class="divider-text">或通过社交账号注册</span>
            </div>

            <div class="social-register-buttons">
              <button class="github-register-btn" @click="handleGitHubLogin" :disabled="githubLoading">
                <svg class="github-icon" viewBox="0 0 24 24" width="20" height="20">
                  <path
                    fill="currentColor"
                    d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"
                  />
                </svg>
                GitHub
              </button>

              <button class="google-register-btn" @click="handleGoogleLogin" :disabled="googleLoading">
                <svg class="google-icon" viewBox="0 0 24 24" width="20" height="20">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                  />
                </svg>
                Google
              </button>
            </div>
          </div>

          <!-- 登录链接 -->
          <div class="login-link">
            <span class="login-link-text">已有账号？</span>
            <a href="#" class="login-link-btn" @click.prevent="goToLogin">立即登录</a>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGithubAuthUrl } from '@/api/auth'
import { getGoogleIdToken } from '@/utils/googleAuth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<any>()
const loading = ref(false)
const googleLoading = ref(false)
const githubLoading = ref(false)
const visible = ref(false)

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (rule: any, value: string, callback: any) => {
  if (value !== registerForm.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
} as const

const setMessageZIndex = () => {
  const messageContainer = document.getElementById('el-message-container')
  if (messageContainer) {
    messageContainer.style.zIndex = '10000000'
  }

  const messages = document.querySelectorAll('.el-message')
  messages.forEach((msg) => {
    if (msg instanceof HTMLElement) {
      msg.style.zIndex = '10000000'
    }
  })

  nextTick(() => {
    const container = document.getElementById('el-message-container')
    if (container) container.style.zIndex = '10000000'

    const allMessages = document.querySelectorAll('.el-message')
    allMessages.forEach((msg) => {
      if (msg instanceof HTMLElement) {
        msg.style.zIndex = '10000000'
      }
    })
  })

  if (!(window as any).__messageObserver) {
    const observer = new MutationObserver(() => {
      const newMessages = document.querySelectorAll('.el-message')
      newMessages.forEach((msg) => {
        if (msg instanceof HTMLElement) {
          msg.style.zIndex = '10000000'
        }
      })
      const container = document.getElementById('el-message-container')
      if (container) container.style.zIndex = '10000000'
    })

    observer.observe(document.body, { childList: true, subtree: true })
    ;(window as any).__messageObserver = observer
  }
}

const openDialog = () => {
  visible.value = true
  document.body.style.overflow = 'hidden'
  setMessageZIndex()
}

const closeDialog = () => {
  visible.value = false
  document.body.style.overflow = ''

  if ((window as any).__messageObserver) {
    ;(window as any).__messageObserver.disconnect()
    delete (window as any).__messageObserver
  }

  registerForm.value = { username: '', email: '', password: '', confirmPassword: '' }
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

const goToLogin = () => {
  closeDialog()
  // 触发登录对话框打开事件
  const event = new CustomEvent('openLoginDialog')
  window.dispatchEvent(event)
}

const handleRegister = async () => {
  try {
    await formRef.value.validate()

    loading.value = true
    await userStore.register({
      userName: registerForm.value.username,
      email: registerForm.value.email,
      password: registerForm.value.password,
    })

    try {
      await userStore.getUserInfo()
    } catch {
      // ignore
    }

    ElMessage.success('注册成功')
    closeDialog()

    const redirectTarget = (route?.query as any)?.redirect
    if (typeof redirectTarget === 'string' && redirectTarget) {
      router.replace(redirectTarget)
    } else {
      router.replace('/')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '注册失败')
    setTimeout(() => setMessageZIndex(), 10)
  } finally {
    loading.value = false
  }
}

const handleGitHubLogin = async () => {
  if (githubLoading.value) return
  try {
    githubLoading.value = true
    sessionStorage.setItem('oauth_source', 'github')

    const res = await getGithubAuthUrl()
    let authUrl = res.data

    try {
      const url = new URL(authUrl)
      const redirectUri = url.searchParams.get('redirect_uri')
      if (redirectUri) {
        const decodedUri = decodeURIComponent(redirectUri)
        const redirectUrl = new URL(decodedUri)
        redirectUrl.searchParams.set('source', 'github')
        url.searchParams.set('redirect_uri', redirectUrl.toString())
        authUrl = url.toString()
      } else {
        url.searchParams.set('redirect_uri', `${window.location.origin}/?source=github`)
        authUrl = url.toString()
      }
    } catch {
      // ignore
    }

    window.location.href = authUrl
  } catch (error: any) {
    ElMessage.error('获取GitHub授权链接失败: ' + (error?.message || '未知错误'))
    githubLoading.value = false
    sessionStorage.removeItem('oauth_source')
  }
}

const handleGoogleLogin = async () => {
  if (googleLoading.value) return
  try {
    googleLoading.value = true
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string
    const idToken = await getGoogleIdToken({ clientId, useOneTap: false })
    await userStore.googleLoginByIdToken(idToken)
    try {
      await userStore.getUserInfo()
    } catch {
      // ignore
    }
    ElMessage.success('注册成功')
    closeDialog()
    const redirectTarget = (route?.query as any)?.redirect
    if (typeof redirectTarget === 'string' && redirectTarget) {
      router.replace(redirectTarget)
    } else {
      router.replace('/')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || 'Google 注册失败')
    setTimeout(() => setMessageZIndex(), 10)
  } finally {
    googleLoading.value = false
  }
}

onMounted(() => {
  // 监听打开注册对话框事件
  window.addEventListener('openRegisterDialog', openDialog)
})

onUnmounted(() => {
  window.removeEventListener('openRegisterDialog', openDialog)
})

defineExpose({
  open: openDialog,
  close: closeDialog,
})
</script>

<style scoped>
.register-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 999999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  animation: fadeIn 0.3s ease-out;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.register-dialog-container {
  position: relative;
  animation: slideIn 0.3s ease-out;
  width: 100%;
  max-width: 420px;
  margin: auto;
}

.register-card {
  background: white;
  border-radius: 20px;
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.25);
  padding: 40px;
  width: 100%;
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.close-btn {
  position: absolute;
  top: 20px;
  right: 20px;
  background: none;
  border: none;
  cursor: pointer;
  color: #666;
  transition: color 0.2s ease;
  z-index: 10;
}

.close-btn:hover {
  color: #333;
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.register-title {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin: 0 0 8px 0;
  line-height: 1.2;
}

.register-subtitle {
  font-size: 16px;
  color: #666;
  margin: 0;
}

.register-form {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
}

.form-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  box-shadow: none;
  transition: border-color 0.2s ease;
}

.form-input :deep(.el-input__wrapper:hover) {
  border-color: #409eff;
}

.form-input :deep(.el-input__wrapper.is-focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}

.register-submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  background: #409eff;
  border: none;
  transition: all 0.2s ease;
}

.register-submit-btn:hover {
  background: #66b1ff;
  transform: translateY(-1px);
}

.social-register-section {
  margin-top: 24px;
}

.divider {
  position: relative;
  text-align: center;
  margin: 24px 0;
}

.divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: #e0e0e0;
}

.divider-text {
  background: white;
  padding: 0 16px;
  color: #999;
  font-size: 14px;
}

.social-register-buttons {
  display: flex;
  gap: 12px;
}

.github-register-btn,
.google-register-btn {
  flex: 1;
  height: 48px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  background: white;
  border: 1px solid #e0e0e0;
  color: #333;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
}

.github-register-btn:hover,
.google-register-btn:hover {
  background: #f5f5f5;
  border-color: #d0d0d0;
  transform: translateY(-1px);
}

.github-register-btn:disabled,
.google-register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.github-icon,
.google-icon {
  flex-shrink: 0;
}

.login-link {
  text-align: center;
  margin-top: 24px;
}

.login-link-text {
  color: #666;
  font-size: 14px;
}

.login-link-btn {
  color: #409eff;
  text-decoration: none;
  font-size: 14px;
  margin-left: 4px;
  cursor: pointer;
}

.login-link-btn:hover {
  color: #66b1ff;
  text-decoration: underline;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@media (max-width: 768px) {
  .register-card {
    margin: 20px;
    padding: 32px;
    max-width: none;
    border-radius: 16px;
  }

  .register-title {
    font-size: 24px;
  }
}

@media (max-width: 480px) {
  .register-card {
    margin: 16px;
    padding: 24px;
    border-radius: 12px;
  }

  .register-title {
    font-size: 22px;
  }

  .register-subtitle {
    font-size: 14px;
  }

  .form-label {
    font-size: 13px;
  }

  .register-submit-btn {
    height: 44px;
    font-size: 15px;
  }

  .social-register-buttons {
    flex-direction: column;
    align-items: stretch;
  }

  .github-register-btn,
  .google-register-btn {
    width: 100%;
    height: 44px;
    font-size: 15px;
  }
}

@media (prefers-color-scheme: dark) {
  .register-card {
    background: #1a1a1a;
    color: #fff;
  }

  .register-title {
    color: #fff;
  }

  .register-subtitle {
    color: #ccc;
  }

  .form-label {
    color: #fff;
  }

  .divider-text {
    background: #1a1a1a;
    color: #ccc;
  }

  .github-register-btn,
  .google-register-btn {
    background: #2a2a2a;
    border-color: #444;
    color: #fff;
  }

  .github-register-btn:hover,
  .google-register-btn:hover {
    background: #333;
    border-color: #555;
  }

  .close-btn {
    color: #ccc;
  }

  .close-btn:hover {
    color: #fff;
  }

  .login-link-text {
    color: #ccc;
  }
}
</style>

<style>
#el-message-container,
.el-message-container,
.el-message {
  z-index: 10000000 !important;
}

.el-message__wrapper,
.el-message__content {
  z-index: 10000000 !important;
}
</style>

