import request from '@/utils/request'
import { getToken } from '@/utils/auth'

const BASE_URL = 'http://localhost:8080/api/v1'

export interface GroupDTO {
  id?: number
  userId?: number
  name: string
  sort?: number
}

export interface Group {
  id: number
  userId: number
  name: string
  sort: number
  status: boolean
  createdAt: string
}

export const groupAPI = {
  // 创建分组
  async createGroup(groupDTO: GroupDTO): Promise<void> {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/group`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'authentication': token || ''
      },
      body: JSON.stringify(groupDTO)
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const result = await response.json()
    if (result.code !== 200) {
      throw new Error(result.msg || '创建分组失败')
    }
  },

  // 查询所有分组
  async getGroups(): Promise<Group[]> {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/group`, {
      headers: {
        'authentication': token || ''
      }
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const result = await response.json()
    if (result.code === 200) {
      return result.data || []
    } else {
      throw new Error(result.msg || '获取分组失败')
    }
  },

  // 删除分组
  async deleteGroup(id: number): Promise<void> {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/group/${id}`, {
      method: 'DELETE',
      headers: {
        'authentication': token || ''
      }
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const result = await response.json()
    if (result.code !== 200) {
      throw new Error(result.msg || '删除分组失败')
    }
  },

  // 更新分组
  async updateGroup(id: number, groupDTO: GroupDTO): Promise<void> {
    const token = getToken()
    const response = await fetch(`${BASE_URL}/group/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'authentication': token || ''
      },
      body: JSON.stringify(groupDTO)
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const result = await response.json()
    if (result.code !== 200) {
      throw new Error(result.msg || '更新分组失败')
    }
  }
}
