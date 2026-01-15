import request from '@/utils/request'

export function login(data: { email: string; password: string }) {
  return request({
    url: '/user/user/login',
    method: 'post',
    data,
  })
}

export function register(data: { userName?: string; email: string; password: string }) {
  return request({
    url: '/user/user/register',
    method: 'post',
    data,
  })
}

export function getUserInfo() {
  return request({
    url: '/user/user/userinfo',
    method: 'get',
  })
}

// 前端直登（Google id_token 交换应用端 token）
export function loginWithGoogleIdToken(idToken: string) {
  return request({
    url: '/user/user/google/idtoken-login',
    method: 'post',
    data: { idToken },
  })
}

// Google 第三方授权相关接口
export function getGoogleAuthUrl() {
  return request({
    url: '/user/user/google/url',
    method: 'get',
  })
}

export function googleAuthCallback(code: string) {
  return request({
    url: '/user/user/google/callback',
    method: 'get',
    params: { code },
  })
}

// GitHub 第三方授权相关接口
export function getGithubAuthUrl() {
  return request({
    url: '/user/user/github/url',
    method: 'get',
  })
}

export function githubAuthCallback(code: string) {
  return request({
    url: '/user/user/github/callback',
    method: 'get',
    params: { code },
  })
}

export function logout() {
  return request({
    url: '/user/user/logout',
    method: 'post',
  })
}


