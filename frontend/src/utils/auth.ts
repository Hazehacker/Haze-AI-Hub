import Cookies from 'js-cookie'

// 与 vue-blog 保持一致：复用博客系统 token 的 Cookie key
const TOKEN_KEY = 'blog_token'

export function getToken(): string | undefined {
  return Cookies.get(TOKEN_KEY)
}

export function setToken(token: string) {
  return Cookies.set(TOKEN_KEY, token, { expires: 7 })
}

export function removeToken() {
  return Cookies.remove(TOKEN_KEY)
}


