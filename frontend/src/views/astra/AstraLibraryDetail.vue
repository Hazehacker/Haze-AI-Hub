<template>
  <div class="library-detail">
    <!-- 顶部导航 -->
    <header class="header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
        <div class="library-info">
          <h1 class="library-name">{{ library?.name }}</h1>
          <p class="library-desc">{{ library?.description || '暂无描述' }}</p>
        </div>
      </div>
      <div class="header-right">
        <button class="btn-secondary" @click="goToUpload">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          上传文档
        </button>
        <button class="btn-primary" @click="goToChat">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
          </svg>
          开始问答
        </button>
      </div>
    </header>

    <!-- 统计信息 -->
    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-value">{{ library?.mediaCount || 0 }}</span>
        <span class="stat-label">文档数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ library?.chunkCount || 0 }}</span>
        <span class="stat-label">分片数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ parsedCount }}</span>
        <span class="stat-label">已解析</span>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="tabs">
      <button
        :class="['tab', { active: activeTab === 'all' }]"
        @click="activeTab = 'all'"
      >
        全部文档
      </button>
      <button
        :class="['tab', { active: activeTab === 'parsing' }]"
        @click="activeTab = 'parsing'"
      >
        解析中
      </button>
      <button
        :class="['tab', { active: activeTab === 'failed' }]"
        @click="activeTab = 'failed'"
      >
        解析失败
      </button>
    </div>

    <!-- 文件列表 -->
    <div class="file-list" v-if="filteredFiles.length > 0">
      <div
        v-for="file in filteredFiles"
        :key="file.id"
        class="file-item"
      >
        <div class="file-icon" :class="getFileType(file.mimeType)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
        </div>
        <div class="file-info">
          <div class="file-name">{{ file.fileName }}</div>
          <div class="file-meta">
            <span class="file-size">{{ formatSize(file.fileSize) }}</span>
            <span class="file-time">{{ formatTime(file.createdAt) }}</span>
          </div>
        </div>
        <div class="file-status">
          <span :class="['status-badge', file.status]">
            {{ getStatusText(file.status) }}
          </span>
          <div class="progress-bar" v-if="file.status === 'PARSING'">
            <div class="progress" :style="{ width: getProgress(file) + '%' }"></div>
          </div>
        </div>
        <div class="file-actions">
          <button
            class="action-btn"
            v-if="file.status === 'PARSED'"
            @click="goToChatWithFile(file)"
            title="基于此文档问答"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
          </button>
          <button class="action-btn delete" @click="confirmDeleteFile(file)" title="删除">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
      </div>
      <h3>暂无文档</h3>
      <p>点击上方按钮上传文档</p>
    </div>

    <!-- 删除确认弹窗 -->
    <div class="modal-overlay" v-if="showDeleteModal" @click.self="showDeleteModal = false">
      <div class="modal modal-small">
        <div class="modal-header">
          <h2>确认删除</h2>
        </div>
        <div class="modal-body">
          <p>确定要删除「{{ fileToDelete?.fileName }}」吗？</p>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="showDeleteModal = false">取消</button>
          <button class="btn-danger" @click="deleteFile">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { astraAPI } from '@/services/api'

const router = useRouter()
const route = useRoute()

const libraryId = route.params.id
const library = ref(null)
const files = ref([])
const activeTab = ref('all')
const showDeleteModal = ref(false)
const fileToDelete = ref(null)
let statusPolling = null

const filteredFiles = computed(() => {
  if (activeTab.value === 'all') return files.value
  if (activeTab.value === 'parsing') return files.value.filter(f => f.status === 'PARSING')
  if (activeTab.value === 'failed') return files.value.filter(f => f.status === 'FAILED')
  return files.value
})

const parsedCount = computed(() => {
  return files.value.filter(f => f.status === 'PARSED').length
})

async function loadLibrary() {
  library.value = await astraAPI.getLibrary(libraryId)
}

async function loadFiles() {
  files.value = await astraAPI.getMediaList(libraryId)
}

function goBack() {
  router.push('/astra')
}

function goToUpload() {
  router.push(`/astra/library/${libraryId}/upload`)
}

function goToChat() {
  router.push(`/astra/chat/${libraryId}`)
}

function goToChatWithFile(file) {
  router.push(`/astra/chat/${libraryId}?mediaId=${file.id}`)
}

function getFileType(mimeType) {
  if (!mimeType) return 'unknown'
  if (mimeType.includes('pdf')) return 'pdf'
  if (mimeType.includes('word') || mimeType.includes('document')) return 'word'
  if (mimeType.includes('image')) return 'image'
  if (mimeType.includes('audio')) return 'audio'
  return 'unknown'
}

function getStatusText(status) {
  const map = {
    'PENDING': '等待中',
    'PARSING': '解析中',
    'PARSED': '已解析',
    'FAILED': '解析失败'
  }
  return map[status] || status
}

function getProgress(file) {
  if (!file.totalChunks || file.totalChunks === 0) return 0
  return Math.round((file.parsedChunks / file.totalChunks) * 100)
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) {
    bytes /= 1024
    i++
  }
  return `${bytes.toFixed(1)} ${units[i]}`
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

function confirmDeleteFile(file) {
  fileToDelete.value = file
  showDeleteModal.value = true
}

async function deleteFile() {
  if (!fileToDelete.value) return
  const success = await astraAPI.deleteMedia(fileToDelete.value.id)
  if (success) {
    showDeleteModal.value = false
    fileToDelete.value = null
    loadFiles()
    loadLibrary()
  }
}

function pollStatus() {
  statusPolling = setInterval(() => {
    if (files.value.some(f => f.status === 'PARSING' || f.status === 'PENDING')) {
      loadFiles()
    } else {
      clearInterval(statusPolling)
    }
  }, 3000)
}

onMounted(() => {
  loadLibrary()
  loadFiles()
  pollStatus()
})

onUnmounted(() => {
  if (statusPolling) clearInterval(statusPolling)
})
</script>

<style scoped>
.library-detail {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.back-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #e0e0e0;
}

.back-btn svg {
  width: 20px;
  height: 20px;
}

.library-name {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.library-desc {
  font-size: 14px;
  color: #999;
}

.header-right {
  display: flex;
  gap: 12px;
}

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-primary {
  background: linear-gradient(135deg, #e85d04 0%, #ff7b00 100%);
  color: white;
  border: none;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(232, 93, 4, 0.3);
}

.btn-secondary {
  background: white;
  color: #666;
  border: 1px solid #e0e0e0;
}

.btn-secondary:hover {
  background: #f5f5f5;
}

.btn-secondary svg,
.btn-primary svg {
  width: 18px;
  height: 18px;
}

.stats-bar {
  display: flex;
  gap: 32px;
  padding: 20px 24px;
  background: #f8f9fa;
  border-radius: 12px;
  margin-bottom: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.stat-label {
  font-size: 13px;
  color: #999;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  border-bottom: 1px solid #e0e0e0;
  padding-bottom: 12px;
}

.tab {
  padding: 8px 16px;
  background: none;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tab:hover {
  background: #f5f5f5;
}

.tab.active {
  background: #e85d04;
  color: white;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
}

.file-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.file-icon {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
  color: #666;
}

.file-icon.pdf {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
}

.file-icon.word {
  background: rgba(52, 152, 219, 0.1);
  color: #3498db;
}

.file-icon.image {
  background: rgba(155, 89, 182, 0.1);
  color: #9b59b6;
}

.file-icon.audio {
  background: rgba(46, 204, 113, 0.1);
  color: #2ecc71;
}

.file-icon svg {
  width: 24px;
  height: 24px;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #999;
}

.file-status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  min-width: 80px;
}

.status-badge {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;
}

.status-badge.PENDING {
  background: #f0f0f0;
  color: #999;
}

.status-badge.PARSING {
  background: rgba(232, 93, 4, 0.1);
  color: #e85d04;
}

.status-badge.PARSED {
  background: rgba(46, 204, 113, 0.1);
  color: #2ecc71;
}

.status-badge.FAILED {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
}

.progress-bar {
  width: 80px;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
  overflow: hidden;
}

.progress {
  height: 100%;
  background: linear-gradient(90deg, #e85d04, #ff7b00);
  transition: width 0.3s;
}

.file-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #e0e0e0;
}

.action-btn.delete:hover {
  background: #ffe6e6;
  color: #dc3545;
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: #f5f5f5;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ccc;
}

.empty-icon svg {
  width: 40px;
  height: 40px;
}

.empty-state h3 {
  font-size: 18px;
  color: #333;
  margin-bottom: 8px;
}

.empty-state p {
  font-size: 14px;
  color: #999;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: white;
  border-radius: 16px;
  width: 90%;
  max-width: 400px;
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h2 {
  font-size: 18px;
  font-weight: 600;
}

.modal-body {
  padding: 24px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}

.btn-danger {
  padding: 10px 20px;
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
}
</style>
