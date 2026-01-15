import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { getBlogApiBaseURL } from '@/utils/apiConfig'

const request = axios.create({
  baseURL: getBlogApiBaseURL(),
  timeout: 30000,
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      // vue-blog 后端 JWT 拦截器读取的是 authentication 请求头
      config.headers = config.headers || {}
      config.headers['authentication'] = token
    }

    // 默认 JSON；上传 FormData 时让 axios 自动处理 multipart
    const hasContentType = !!(config.headers && (config.headers as any)['Content-Type'])
    if (!hasContentType && !(config.data instanceof FormData)) {
      config.headers = config.headers || {}
      config.headers['Content-Type'] = 'application/json;charset=utf-8'
    }

    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    let res: any = response.data
    if (typeof res === 'string') {
      res = res ? JSON.parse(res) : res
    }
    return res
  },
  (error) => {
    if (error?.response) {
      const status = error.response.status
      const errorData = error.response.data
      if (status === 500) {
        ElMessage.error('服务端内部错误，请稍后重试')
      } else if (status === 401) {
        ElMessage.error('未授权，请重新登录')
      } else if (status === 403) {
        ElMessage.error('权限不足')
      } else {
        ElMessage.error(errorData?.message || errorData?.error || `请求失败 (${status})`)
      }
    } else if (error?.code === 'ERR_NETWORK') {
      ElMessage.error('网络请求失败，请检查网络连接')
    } else if (error?.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络连接')
    } else {
      ElMessage.error('网络请求失败，请检查网络连接')
    }
    return Promise.reject(error)
  },
)

export default request


