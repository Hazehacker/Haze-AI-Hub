<template>
  <div class="astra-library-list">
    <!-- 顶部导航 -->
    <header class="header">
      <div class="header-left">
        <h1 class="title">知识库</h1>
      </div>
      <div class="header-right">
        <button class="btn-primary" @click="showCreateModal = true">
          <span class="icon">+</span>
          新建知识库
        </button>
      </div>
    </header>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <input
        v-model="searchKeyword"
        type="text"
        class="search-input"
        placeholder="搜索知识库..."
        @input="handleSearch"
      />
    </div>

    <!-- 知识库列表 -->
    <div class="library-grid" v-if="libraries.length > 0">
      <div
        v-for="lib in libraries"
        :key="lib.id"
        class="library-card"
        @click="goToLibrary(lib)"
      >
        <div class="card-header">
          <div class="card-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
            </svg>
          </div>
          <span :class="['type-badge', lib.type]">{{ lib.type === 'personal' ? '个人' : '团队' }}</span>
        </div>
        <h3 class="card-title">{{ lib.name }}</h3>
        <p class="card-desc">{{ lib.description || '暂无描述' }}</p>
        <div class="card-stats">
          <span class="stat">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
            </svg>
            {{ lib.mediaCount || 0 }} 文档
          </span>
          <span class="stat">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
            </svg>
            {{ lib.chunkCount || 0 }} 分片
          </span>
        </div>
        <div class="card-footer">
          <span class="updated-time">更新于 {{ formatTime(lib.updatedAt) }}</span>
          <div class="card-actions" @click.stop>
            <button class="action-btn" @click="toggleTop(lib)" title="置顶">
              <svg viewBox="0 0 24 24" fill="none" :stroke="lib.isTop ? '#e85d04' : 'currentColor'" stroke-width="2">
                <path d="M12 2L12 12"></path>
                <path d="M5 12l7-7 7 7"></path>
              </svg>
            </button>
            <button class="action-btn delete" @click="confirmDelete(lib)" title="删除">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"></polyline>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
        </svg>
      </div>
      <h3>还没有知识库</h3>
      <p>点击上方按钮创建第一个知识库</p>
      <button class="btn-primary" @click="showCreateModal = true">新建知识库</button>
    </div>

    <!-- 创建知识库弹窗 -->
    <div class="modal-overlay" v-if="showCreateModal" @click.self="showCreateModal = false">
      <div class="modal">
        <div class="modal-header">
          <h2>新建知识库</h2>
          <button class="close-btn" @click="showCreateModal = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>知识库名称</label>
            <input
              v-model="newLibrary.name"
              type="text"
              placeholder="输入知识库名称"
              maxlength="32"
            />
          </div>
          <div class="form-item">
            <label>描述</label>
            <textarea
              v-model="newLibrary.description"
              placeholder="输入知识库描述（可选）"
              rows="3"
              maxlength="255"
            ></textarea>
          </div>
          <div class="form-item">
            <label>类型</label>
            <div class="type-selector">
              <label class="radio-item">
                <input type="radio" v-model="newLibrary.type" value="personal" />
                <span class="radio-label">
                  <span class="radio-title">个人</span>
                  <span class="radio-desc">仅自己可用</span>
                </span>
              </label>
              <label class="radio-item">
                <input type="radio" v-model="newLibrary.type" value="team" />
                <span class="radio-label">
                  <span class="radio-title">团队</span>
                  <span class="radio-desc">可分享给团队成员</span>
                </span>
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="showCreateModal = false">取消</button>
          <button class="btn-primary" @click="createLibrary" :disabled="!newLibrary.name">创建</button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div class="modal-overlay" v-if="showDeleteModal" @click.self="showDeleteModal = false">
      <div class="modal modal-small">
        <div class="modal-header">
          <h2>确认删除</h2>
        </div>
        <div class="modal-body">
          <p>确定要删除知识库「{{ libraryToDelete?.name }}」吗？</p>
          <p class="warning-text">此操作不可恢复，将删除知识库下所有文档和分片</p>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="showDeleteModal = false">取消</button>
          <button class="btn-danger" @click="deleteLibrary">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { astraAPI } from '@/services/api'

const router = useRouter()

const libraries = ref([])
const searchKeyword = ref('')
const showCreateModal = ref(false)
const showDeleteModal = ref(false)
const libraryToDelete = ref(null)

const newLibrary = ref({
  name: '',
  description: '',
  type: 'personal'
})

// 加载知识库列表
async function loadLibraries() {
  const data = await astraAPI.getLibraries(searchKeyword.value)
  libraries.value = data
}

// 搜索处理
let searchTimer = null
function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadLibraries()
  }, 300)
}

// 创建知识库
async function createLibrary() {
  if (!newLibrary.value.name) return
  try {
    await astraAPI.createLibrary(newLibrary.value)
    showCreateModal.value = false
    newLibrary.value = { name: '', description: '', type: 'personal' }
    loadLibraries()
  } catch (error) {
    alert(error.message)
  }
}

// 置顶/取消置顶
async function toggleTop(lib) {
  const result = await astraAPI.toggleTop(lib.id)
  if (result) {
    lib.isTop = result.isTop
    loadLibraries()
  }
}

// 确认删除
function confirmDelete(lib) {
  libraryToDelete.value = lib
  showDeleteModal.value = true
}

// 删除知识库
async function deleteLibrary() {
  if (!libraryToDelete.value) return
  const success = await astraAPI.deleteLibrary(libraryToDelete.value.id)
  if (success) {
    showDeleteModal.value = false
    libraryToDelete.value = null
    loadLibraries()
  }
}

// 跳转到知识库详情
function goToLibrary(lib) {
  router.push(`/astra/library/${lib.id}`)
}

// 格式化时间
function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`

  return date.toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadLibraries()
})
</script>

<style scoped>
.astra-library-list {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #e85d04 0%, #ff7b00 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(232, 93, 4, 0.3);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 10px 20px;
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
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

.search-bar {
  margin-bottom: 24px;
}

.search-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.search-input:focus {
  border-color: #e85d04;
}

.library-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.library-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s;
}

.library-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #e85d04 0%, #ff7b00 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.card-icon svg {
  width: 24px;
  height: 24px;
}

.type-badge {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.type-badge.personal {
  background: rgba(106, 76, 147, 0.1);
  color: #6b4c93;
}

.type-badge.team {
  background: rgba(52, 152, 219, 0.1);
  color: #3498db;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.card-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 16px;
  line-height: 1.5;
}

.card-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #666;
}

.stat svg {
  width: 16px;
  height: 16px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.updated-time {
  font-size: 12px;
  color: #999;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 28px;
  height: 28px;
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
  margin-bottom: 24px;
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
  max-width: 480px;
  overflow: hidden;
}

.modal-small {
  max-width: 400px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h2 {
  font-size: 18px;
  font-weight: 600;
}

.close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  font-size: 24px;
  color: #999;
  cursor: pointer;
}

.modal-body {
  padding: 24px;
}

.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.form-item input,
.form-item textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.form-item input:focus,
.form-item textarea:focus {
  border-color: #e85d04;
}

.form-item textarea {
  resize: vertical;
}

.type-selector {
  display: flex;
  gap: 12px;
}

.radio-item {
  flex: 1;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.radio-item input {
  display: none;
}

.radio-label {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  transition: all 0.3s;
}

.radio-item input:checked + .radio-label {
  border-color: #e85d04;
  background: rgba(232, 93, 4, 0.05);
}

.radio-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.radio-desc {
  font-size: 12px;
  color: #999;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}

.warning-text {
  font-size: 13px;
  color: #dc3545;
  margin-top: 8px;
}
</style>
