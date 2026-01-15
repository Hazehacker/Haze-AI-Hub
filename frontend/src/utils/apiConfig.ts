/**
 * 博客系统 API 基地址（AiHub 复用 vue-blog 登录/用户接口）。
 *
 * 行为与 vue-blog 中的 `getApiBaseURL` 保持一致：
 * - 优先使用环境变量：VITE_BLOG_API_BASE_URL
 * - 生产环境默认：`/api`（通常由 Nginx 反向代理到博客后端）
 * - 开发环境默认：`http://localhost:9090`（与 vue-blog 本地联调保持一致）
 */
export function getBlogApiBaseURL(): string {
  const envVal = import.meta.env.VITE_BLOG_API_BASE_URL as string | undefined
  if (envVal !== undefined) {
    const trimmed = envVal.trim()
    if (!trimmed) {
      // 生产环境下，空字符串一般表示走相对路径，由外层反向代理处理
      if (import.meta.env.PROD) {
        return ''
      }
      // 开发环境允许返回空字符串，兼容相对路径联调
      return ''
    }
    // 去掉结尾多余的斜杠
    return trimmed.replace(/\/+$/, '')
  }

  if (import.meta.env.PROD) {
    // 生产环境默认走 /api 前缀，由前置网关/Nginx 转发到博客后端
    return '/api'
  }

  // 开发环境默认：本地直连博客后端
  return 'https://blog.hazenix.top/api'
}

