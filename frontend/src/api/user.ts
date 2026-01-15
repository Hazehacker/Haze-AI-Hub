import request from '@/utils/request'

// 与 blog 保持一致的用户相关 API（供 AiHub 复用博客系统用户能力）
export const userApi = {
  // 获取用户收藏文章
  getFavoriteArticles(params: Record<string, any> = {}) {
    return request({
      url: '/user/user/favorite',
      method: 'get',
      params: {
        sortBy: 'createTime',
        sortOrder: 'desc',
        ...params, // { page, pageSize }
      },
    })
  },

  // 获取当前登录用户统计信息
  getUserStats() {
    return request({
      url: '/user/user/stats',
      method: 'get',
    })
  },

  // 上传图片（用户端）
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request({
      url: '/common/upload',
      method: 'post',
      data: formData,
      // 不设置 Content-Type，让 axios 自动处理 multipart/form-data（含 boundary）
    })
  },

  // 更新用户资料
  updateProfile(data: Record<string, any>) {
    return request({
      url: '/user/user/profile',
      method: 'put',
      data,
    })
  },

  // 修改密码
  updatePassword(data: Record<string, any>) {
    return request({
      url: '/user/user/password',
      method: 'put',
      data,
    })
  },
}

// 兼容 vue-blog 的函数式导出
export function getFavoriteArticles(params: Record<string, any> = {}) {
  return userApi.getFavoriteArticles(params)
}

export function getUserStats() {
  return userApi.getUserStats()
}

export function uploadImage(file: File) {
  return userApi.uploadImage(file)
}

export function updateProfile(data: Record<string, any>) {
  return userApi.updateProfile(data)
}

export function updatePassword(data: Record<string, any>) {
  return userApi.updatePassword(data)
}


