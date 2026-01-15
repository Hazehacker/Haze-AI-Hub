import { defineStore } from 'pinia'
import { getToken, removeToken, setToken } from '@/utils/auth'
import {
  getUserInfo as getUserInfoApi,
  githubAuthCallback,
  login as loginApi,
  loginWithGoogleIdToken,
  logout as logoutApi,
  register as registerApi,
} from '@/api/auth'

type AnyUser = Record<string, any> | null

function normalizeUserData(userData: AnyUser) {
  if (!userData) return null

  // 先拷贝一份，避免直接修改原对象
  const normalized: Record<string, any> = { ...userData }

  // 兼容博客系统：userName / nickName 映射为 username，方便前端统一展示
  if (!Object.prototype.hasOwnProperty.call(normalized, 'username')) {
    if (Object.prototype.hasOwnProperty.call(normalized, 'userName')) {
      normalized.username = normalized.userName
    } else if (Object.prototype.hasOwnProperty.call(normalized, 'nickName')) {
      normalized.username = normalized.nickName
    }
  }

  // 已经有 isAdmin 字段，直接返回
  if (Object.prototype.hasOwnProperty.call(normalized, 'isAdmin')) return normalized

  // 根据 role 衍生 isAdmin（与博客系统保持一致）
  if (Object.prototype.hasOwnProperty.call(normalized, 'role')) {
    normalized.isAdmin = normalized.role === 0
    return normalized
  }

  // 兜底逻辑：id 为 1 认为是管理员（兼容博客旧数据）
  if ((normalized as any).id === 1) {
    normalized.isAdmin = true
    return normalized
  }

  return normalized
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null as AnyUser,
  }),

  actions: {
    async login(loginForm: { email: string; password: string }) {
      const res = await loginApi(loginForm)
      if (res.code === 200) {
        this.token = res.data.token
        setToken(res.data.token)
        const userData = res.data.user || res.data
        this.userInfo = normalizeUserData(userData)
      } else {
        throw new Error(res.msg || '登录失败')
      }
    },

    async register(registerForm: { userName?: string; email: string; password: string }) {
      const res = await registerApi(registerForm)
      if (res.code === 200) {
        this.token = res.data.token
        setToken(res.data.token)
        const userData = res.data.user || res.data
        this.userInfo = normalizeUserData(userData)
      } else {
        throw new Error(res.msg || '注册失败')
      }
    },

    async getUserInfo() {
      const res = await getUserInfoApi()
      if (res.code === 200) {
        this.userInfo = normalizeUserData(res.data)
      } else {
        throw new Error(res.msg || '获取用户信息失败')
      }
    },

    async googleLoginByIdToken(idToken: string) {
      const res = await loginWithGoogleIdToken(idToken)
      if (res.code === 200) {
        this.token = res.data.token
        setToken(res.data.token)
        const userData = res.data.user || res.data
        this.userInfo = normalizeUserData(userData)
      } else {
        throw new Error(res.msg || 'Google 登录失败')
      }
    },

    async githubLogin(code: string) {
      const res = await githubAuthCallback(code)
      if (res.code === 200) {
        this.token = res.data.token
        setToken(res.data.token)
        // data 中 token 与用户字段混在一起：拆掉 token
        const { token, ...userData } = res.data
        this.userInfo = normalizeUserData(userData)
      } else {
        throw new Error(res.msg || 'GitHub 登录失败')
      }
    },

    async logout() {
      try {
        await logoutApi()
      } catch {
        // ignore
      } finally {
        this.token = ''
        this.userInfo = null
        removeToken()
      }
    },
  },
})


