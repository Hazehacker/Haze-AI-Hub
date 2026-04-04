<template>
  <div class="upload-page">
    <!-- 顶部导航 -->
    <header class="header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
        <h1 class="title">上传文档</h1>
      </div>
    </header>

    <!-- 知识库选择 -->
    <div class="library-selector" v-if="!libraryId">
      <label>选择知识库</label>
      <select v-model="selectedLibraryId" class="select">
        <option value="">请选择知识库</option>
        <option v-for="lib in libraries" :key="lib.id" :value="lib.id">
          {{ lib.name }}
        </option>
      </select>
    </div>

    <!-- 拖拽上传区域 -->
    <div
      :class="['drop-zone', { dragging: isDragging }]"
      @dragover.prevent="isDragging = true"
      @dragleave="isDragging = false"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <input
        ref="fileInput"
        type="file"
        multiple
        accept=".pdf,.doc,.docx,.jpg,.jpeg,.png,.gif,.webp,.mp3,.wav,.xmind,.txt"
        @change="handleFileSelect"
        hidden
      />
      <div class="drop-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="17 8 12 3 7 8"/>
          <line x1="12" y1="3" x2="12" y2="15"/>
        </svg>
      </div>
      <p class="drop-text">拖拽文件到此处，或 <span class="link">点击浏览</span></p>
      <p class="drop-hint">支持 PDF、Word、图片、音频、XMind、TXT</p>
    </div>

    <!-- 上传队列 -->
    <div class="upload-queue" v-if="uploadQueue.length > 0">
      <div class="queue-header">
        <span class="queue-title">上传队列 ({{ uploadQueue.length }})</span>
        <button class="clear-btn" @click="clearQueue">清空</button>
      </div>
      <div class="queue-list">
        <div v-for="(item, index) in uploadQueue" :key="index" class="queue-item">
          <div class="file-icon" :class="getFileType(item.file)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
          </div>
          <div class="file-info">
            <div class="file-name">{{ item.file.name }}</div>
            <div class="file-meta">
              <span class="file-size">{{ formatSize(item.file.size) }}</span>
              <span v-if="item.status === 'uploading'" class="status">上传中...</span>
              <span v-else-if="item.status === 'done'" class="status success">已上传</span>
              <span v-else-if="item.status === 'error'" class="status error">{{ item.error }}</span>
            </div>
          </div>
          <div class="progress-bar" v-if="item.status === 'uploading'">
            <div class="progress" :style="{ width: item.progress + '%' }"></div>
          </div>
          <button class="remove-btn" @click="removeFromQueue(index)" v-if="item.status !== 'uploading'">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 安全信息 -->
    <div class="security-info">
      <div class="info-item">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
        </svg>
        <span>SHA256 文件完整性校验</span>
      </div>
      <div class="info-item">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
          <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
        </svg>
        <span>MIME 类型安全验证</span>
      </div>
      <div class="info-item">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
        </svg>
        <span>私有存储，安全可靠</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { astraAPI } from '@/services/api'

const router = useRouter()
const route = useRoute()

const libraryId = route.params.libraryId
const libraries = ref([])
const selectedLibraryId = ref(libraryId || '')
const uploadQueue = ref([])
const isDragging = ref(false)
const fileInput = ref(null)

async function loadLibraries() {
  libraries.value = await astraAPI.getLibraries()
}

function goBack() {
  if (libraryId) {
    router.push(`/astra/library/${libraryId}`)
  } else {
    router.push('/astra')
  }
}

function triggerFileInput() {
  fileInput.value?.click()
}

function handleFileSelect(e) {
  const files = Array.from(e.target.files)
  addFilesToQueue(files)
}

function handleDrop(e) {
  isDragging.value = false
  const files = Array.from(e.dataTransfer.files)
  addFilesToQueue(files)
}

function addFilesToQueue(files) {
  const targetLibraryId = selectedLibraryId.value || libraryId
  if (!targetLibraryId) {
    alert('请先选择知识库')
    return
  }

  files.forEach(file => {
    uploadQueue.value.push({
      file,
      status: 'pending',
      progress: 0,
      error: ''
    })
  })

  // 自动开始上传
  uploadNext()
}

async function uploadNext() {
  const pendingItem = uploadQueue.value.find(item => item.status === 'pending')
  if (!pendingItem) return

  pendingItem.status = 'uploading'

  try {
    // 模拟上传进度
    const progressInterval = setInterval(() => {
      if (pendingItem.progress < 90) {
        pendingItem.progress += 10
      }
    }, 200)

    await astraAPI.uploadFile(pendingItem.file, selectedLibraryId.value || libraryId)

    clearInterval(progressInterval)
    pendingItem.progress = 100
    pendingItem.status = 'done'

    // 继续上传下一个
    uploadNext()
  } catch (error) {
    pendingItem.status = 'error'
    pendingItem.error = error.message
  }
}

function removeFromQueue(index) {
  uploadQueue.value.splice(index, 1)
}

function clearQueue() {
  uploadQueue.value = uploadQueue.value.filter(item => item.status === 'uploading')
}

function getFileType(file) {
  const type = file.type
  if (type.includes('pdf')) return 'pdf'
  if (type.includes('word') || type.includes('document')) return 'word'
  if (type.includes('image')) return 'image'
  if (type.includes('audio')) return 'audio'
  return 'unknown'
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

onMounted(() => {
  if (!libraryId) {
    loadLibraries()
  }
})
</script>

<style scoped>
.upload-page {
  padding: 32px 48px;
  max-width: 100%;
  min-height: calc(100vh - 64px);
}

.header {
  display: flex;
  align-items: center;
  margin-bottom: 32px;
  max-width: 1000px;
  margin-left: auto;
  margin-right: auto;
  margin-bottom: 40px;
  background: white;
  padding: 24px 32px;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.back-btn:hover {
  background: #e8e8e8;
  transform: translateX(-4px);
}

.back-btn svg {
  width: 22px;
  height: 22px;
  color: #666;
}

.title {
  font-size: 26px;
  font-weight: 700;
  color: #1a1a1a;
}

.library-selector {
  margin-bottom: 24px;
  max-width: 1000px;
  margin-left: auto;
  margin-right: auto;
}

.library-selector label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.select {
  width: 100%;
  padding: 14px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 15px;
  outline: none;
  transition: all 0.3s;
}

.select:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.drop-zone {
  border: 2px dashed #667eea;
  border-radius: 20px;
  padding: 60px 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.02), rgba(118, 75, 162, 0.02));
  max-width: 1000px;
  margin-left: auto;
  margin-right: auto;
}

.drop-zone:hover,
.drop-zone.dragging {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.02);
}

.drop-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.drop-icon svg {
  width: 32px;
  height: 32px;
}

.drop-text {
  font-size: 16px;
  color: #333;
  margin-bottom: 8px;
}

.drop-text .link {
  color: #667eea;
}

.drop-hint {
  font-size: 13px;
  color: #999;
}

.upload-queue {
  margin-top: 32px;
  max-width: 1000px;
  margin-left: auto;
  margin-right: auto;
}

.queue-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.queue-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.clear-btn {
  background: none;
  border: none;
  font-size: 13px;
  color: #999;
  cursor: pointer;
}

.clear-btn:hover {
  color: #666;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  transition: all 0.3s;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.queue-item:hover {
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.1);
  transform: translateY(-2px);
}

.file-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.1));
  color: #667eea;
}

.file-icon.pdf { background: linear-gradient(135deg, rgba(220, 53, 69, 0.15), rgba(220, 53, 69, 0.05)); color: #dc3545; }
.file-icon.word { background: linear-gradient(135deg, rgba(52, 152, 219, 0.15), rgba(52, 152, 219, 0.05)); color: #3498db; }
.file-icon.image { background: linear-gradient(135deg, rgba(155, 89, 182, 0.15), rgba(155, 89, 182, 0.05)); color: #9b59b6; }
.file-icon.audio { background: linear-gradient(135deg, rgba(46, 204, 113, 0.15), rgba(46, 204, 113, 0.05)); color: #2ecc71; }

.file-icon svg {
  width: 20px;
  height: 20px;
}

.file-info {
  flex: 1;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.file-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.file-size {
  color: #999;
}

.status {
  color: #667eea;
}

.status.success {
  color: #2ecc71;
}

.status.error {
  color: #dc3545;
}

.progress-bar {
  width: 100px;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
  overflow: hidden;
}

.progress {
  height: 100%;
  background: linear-gradient(90deg, #667eea, #764ba2);
  transition: width 0.3s;
}

.remove-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
}

.remove-btn:hover {
  color: #dc3545;
}

.remove-btn svg {
  width: 16px;
  height: 16px;
}

.security-info {
  margin-top: 40px;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 12px;
  display: flex;
  justify-content: space-around;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}

.info-item svg {
  width: 18px;
  height: 18px;
  color: #2ecc71;
}
</style>
