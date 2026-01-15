<template>
  <div class="profile">
    <div class="profile-container">
      <el-card class="profile-card">
        <template #header>
          <h1 class="profile-title">个人中心</h1>
        </template>

        <div v-if="loading" class="loading-wrapper">
          <LoadingSpinner />
        </div>

        <div v-else class="profile-content">
          <!-- 用户信息 -->
          <div class="section">
            <h2 class="section-title">个人信息</h2>
            <div class="info-grid">
              <div class="info-item">
                <label class="info-label">用户名</label>
                <el-input :value="userInfo.username" disabled />
              </div>
              
              <div class="info-item">
                <label class="info-label">邮箱</label>
                <el-input :value="userInfo.email" disabled />
              </div>

              <div class="info-item">
                <label class="info-label">性别</label>
                <el-input
                  :value="userInfo.gender === 0 ? '保密' : userInfo.gender === 1 ? '男' : userInfo.gender === 2 ? '女' : '未知'"
                  disabled
                />
              </div>
            </div>
          </div>

          <!-- 统计信息 -->
          <div class="section">
            <h2 class="section-title">统计信息</h2>
            <div class="stats-grid">
              <div class="stat-card stat-card-blue">
                <div class="stat-icon-wrapper stat-icon-blue">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="stat-content">
                  <p class="stat-label">我的收藏</p>
                  <p class="stat-value">{{ userStats.favoriteCount || 0 }}</p>
                </div>
              </div>

              <div class="stat-card stat-card-green">
                <div class="stat-icon-wrapper stat-icon-green">
                  <el-icon><ChatDotRound /></el-icon>
                </div>
                <div class="stat-content">
                  <p class="stat-label">我的评论</p>
                  <p class="stat-value">{{ userStats.commentsCount || 0 }}</p>
                </div>
              </div>

              <div class="stat-card stat-card-purple">
                <div class="stat-icon-wrapper stat-icon-purple">
                  <el-icon><Star /></el-icon>
                </div>
                <div class="stat-content">
                  <p class="stat-label">获赞数</p>
                  <p class="stat-value">{{ userStats.likeCount || 0 }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="actions">
            <el-button type="primary" @click="$router.push('/profile/edit')">
              编辑资料
            </el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { getUserStats } from '@/api/user'
import { Document, ChatDotRound, Star } from '@element-plus/icons-vue'

const userStore = useUserStore()

// 响应式数据
const loading = ref(true)
const userInfo = ref<any>({})
const userStats = ref({
  favoriteCount: 0,
  commentsCount: 0,
  likeCount: 0
})

// 方法
const fetchUserInfo = async () => {
  try {
    console.log('开始获取用户信息...')
    await userStore.getUserInfo()
    console.log('用户信息获取成功:', userStore.userInfo)
    userInfo.value = userStore.userInfo || {}
    
    // 获取用户统计信息
    try {
      console.log('开始获取用户统计信息...')
      const statsResponse = await getUserStats()
      console.log('统计信息响应:', statsResponse)
      userStats.value = statsResponse.data || {}
    } catch (e) {
      console.warn('获取用户统计信息失败:', e)
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  } finally {
    loading.value = false
    console.log('加载完成, loading:', loading.value)
    console.log('最终 userInfo:', userInfo.value)
    console.log('最终 userStats:', userStats.value)
  }
}

// 生命周期
onMounted(() => {
  fetchUserInfo()
})
</script>

<style scoped>
.profile {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 2rem 1rem;
}

.dark .profile {
  background-color: #1a202c;
}

.profile-container {
  max-width: 1200px;
  margin: 0 auto;
}

.profile-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.dark .profile-card {
  background: #2a2a2a;
}

.profile-title {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  margin: 0;
}

.dark .profile-title {
  color: #fff;
}

.loading-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 3rem 0;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.dark .section-title {
  color: #fff;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.info-label {
  font-size: 14px;
  font-weight: 500;
  color: #666;
}

.dark .info-label {
  color: #ccc;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1.5rem;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 1rem;
  border-radius: 8px;
  gap: 1rem;
}

.stat-card-blue {
  background-color: #eff6ff;
}

.dark .stat-card-blue {
  background-color: rgba(59, 130, 246, 0.2);
}

.stat-card-green {
  background-color: #f0fdf4;
}

.dark .stat-card-green {
  background-color: rgba(34, 197, 94, 0.2);
}

.stat-card-purple {
  background-color: #faf5ff;
}

.dark .stat-card-purple {
  background-color: rgba(168, 85, 247, 0.2);
}

.stat-icon-wrapper {
  padding: 0.75rem;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
}

.stat-icon-blue {
  background-color: #dbeafe;
  color: #2563eb;
}

.dark .stat-icon-blue {
  background-color: rgba(59, 130, 246, 0.3);
  color: #60a5fa;
}

.stat-icon-green {
  background-color: #dcfce7;
  color: #16a34a;
}

.dark .stat-icon-green {
  background-color: rgba(34, 197, 94, 0.3);
  color: #4ade80;
}

.stat-icon-purple {
  background-color: #f3e8ff;
  color: #9333ea;
}

.dark .stat-icon-purple {
  background-color: rgba(168, 85, 247, 0.3);
  color: #a78bfa;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.stat-label {
  font-size: 14px;
  font-weight: 500;
  margin: 0;
}

.stat-card-blue .stat-label {
  color: #2563eb;
}

.dark .stat-card-blue .stat-label {
  color: #60a5fa;
}

.stat-card-green .stat-label {
  color: #16a34a;
}

.dark .stat-card-green .stat-label {
  color: #4ade80;
}

.stat-card-purple .stat-label {
  color: #9333ea;
}

.dark .stat-card-purple .stat-label {
  color: #a78bfa;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin: 0;
}

.stat-card-blue .stat-value {
  color: #1e40af;
}

.dark .stat-card-blue .stat-value {
  color: #93c5fd;
}

.stat-card-green .stat-value {
  color: #15803d;
}

.dark .stat-card-green .stat-value {
  color: #86efac;
}

.stat-card-purple .stat-value {
  color: #7e22ce;
}

.dark .stat-card-purple .stat-value {
  color: #c084fc;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
}

@media (max-width: 768px) {
  .profile {
    padding: 1rem 0.5rem;
  }

  .info-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
