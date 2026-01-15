<template>
  <div class="profile-edit">
    <div class="profile-edit-container">
      <el-card class="profile-edit-card">
        <template #header>
          <h1 class="profile-edit-title">编辑资料</h1>
        </template>

        <div v-if="loading" class="loading-wrapper">
          <LoadingSpinner />
        </div>

        <el-form v-else @submit.prevent="handleSubmit" class="profile-edit-form">
          <!-- 基本信息 -->
          <div class="section">
            <h2 class="section-title">基本信息</h2>
            <div class="form-grid">
              <el-form-item label="昵称">
                <el-input
                  v-model="form.username"
                  placeholder="请输入昵称"
                />
              </el-form-item>

              <el-form-item label="邮箱">
                <el-input
                  v-model="form.email"
                  type="email"
                  disabled
                  placeholder="请输入邮箱"
                />
              </el-form-item>

              <el-form-item label="性别">
                <el-select v-model="form.gender" placeholder="请选择性别">
                  <el-option label="保密" :value="0" />
                  <el-option label="男" :value="1" />
                  <el-option label="女" :value="2" />
                </el-select>
              </el-form-item>

              <el-form-item label="头像">
                <div class="avatar-upload">
                  <div class="avatar-preview">
                    <img
                      v-if="avatarPreview || form.avatar"
                      :src="avatarPreview || form.avatar"
                      alt=""
                      class="avatar-img"
                    />
                    <div v-else class="avatar-placeholder">
                      <i class="fas fa-user"></i>
                    </div>
                  </div>
                  <div class="avatar-actions">
                    <input
                      ref="fileInputRef"
                      type="file"
                      accept="image/*"
                      @change="handleFileChange"
                      class="file-input"
                    />
                    <el-button
                      type="default"
                      @click="triggerFileUpload"
                      :loading="uploading"
                    >
                      {{ uploading ? '上传中...' : '选择头像' }}
                    </el-button>
                    <p class="avatar-hint">
                      支持 JPG、PNG 格式，建议尺寸 200x200 像素
                    </p>
                  </div>
                </div>
              </el-form-item>
            </div>
          </div>

          <!-- 密码修改 -->
          <div class="section">
            <h2 class="section-title">密码修改</h2>
            <div class="form-grid">
              <el-form-item label="当前密码">
                <el-input
                  v-model="form.currentPassword"
                  type="password"
                  placeholder="请输入当前密码"
                  show-password
                />
              </el-form-item>

              <el-form-item label="新密码">
                <el-input
                  v-model="form.newPassword"
                  type="password"
                  placeholder="请输入新密码"
                  show-password
                />
              </el-form-item>

              <el-form-item label="确认新密码">
                <el-input
                  v-model="form.confirmPassword"
                  type="password"
                  placeholder="请确认新密码"
                  show-password
                />
              </el-form-item>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="actions">
            <el-button @click="$router.go(-1)">取消</el-button>
            <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
              {{ submitLoading ? '保存中...' : '保存修改' }}
            </el-button>
          </div>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { ElMessage } from 'element-plus'
import { uploadImage, updateProfile, updatePassword } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

// 响应式数据
const loading = ref(true)
const submitLoading = ref(false)
const uploading = ref(false)
const avatarPreview = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

// 表单数据
const form = reactive({
  username: '',
  email: '',
  gender: 0,
  avatar: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 方法
const fetchUserInfo = async () => {
  try {
    await userStore.getUserInfo()
    const userInfo = userStore.userInfo || {}
    
    form.username = userInfo.username || ''
    form.email = userInfo.email || ''
    form.gender = userInfo.gender ?? 0
    form.avatar = userInfo.avatar || ''
    avatarPreview.value = ''
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  } finally {
    loading.value = false
  }
}

// 触发文件选择
const triggerFileUpload = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return
  }

  // 验证文件大小（限制为 5MB）
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }

  // 显示预览
  const reader = new FileReader()
  reader.onload = (e) => {
    avatarPreview.value = e.target?.result as string
  }
  reader.readAsDataURL(file)

  // 上传文件
  uploading.value = true
  try {
    const response = await uploadImage(file)
    if (response.code === 200) {
      form.avatar = response.data
      ElMessage.success('头像上传成功')
    } else {
      ElMessage.error(response.msg || '头像上传失败')
      avatarPreview.value = ''
    }
  } catch (error: any) {
    console.error('头像上传失败:', error)
    ElMessage.error('头像上传失败，请重试')
    avatarPreview.value = ''
  } finally {
    uploading.value = false
    // 清空input，以便可以再次选择同一文件
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

const validateForm = () => {
  // 如果要修改密码，验证密码
  if (form.currentPassword || form.newPassword || form.confirmPassword) {
    if (!form.currentPassword) {
      ElMessage.error('请输入当前密码')
      return false
    }
    if (!form.newPassword) {
      ElMessage.error('请输入新密码')
      return false
    }
    if (form.newPassword.length < 6) {
      ElMessage.error('新密码长度不能少于6位')
      return false
    }
    if (form.newPassword !== form.confirmPassword) {
      ElMessage.error('两次输入的密码不一致')
      return false
    }
  }

  return true
}

const handleSubmit = async () => {
  if (!validateForm()) return

  submitLoading.value = true
  try {
    // 更新基本信息（不包含邮箱，邮箱不能修改）
    const updateData = {
      username: form.username,
      gender: form.gender,
      avatar: form.avatar
    }

    await updateProfile(updateData)

    // 如果修改了密码，单独调用密码修改接口
    if (form.currentPassword && form.newPassword) {
      await updatePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword
      })
    }

    // 更新用户store中的信息
    await userStore.getUserInfo()

    ElMessage.success('资料更新成功')
    router.push('/profile')
  } catch (error: any) {
    console.error('更新资料失败:', error)
    ElMessage.error(error.response?.data?.msg || error.message || '更新资料失败，请重试')
  } finally {
    submitLoading.value = false
  }
}

// 生命周期
onMounted(() => {
  fetchUserInfo()
})
</script>

<style scoped>
.profile-edit {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 2rem 1rem;
}

.dark .profile-edit {
  background-color: #1a202c;
}

.profile-edit-container {
  max-width: 1200px;
  margin: 0 auto;
}

.profile-edit-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.dark .profile-edit-card {
  background: #2a2a2a;
}

.profile-edit-title {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  margin: 0;
}

.dark .profile-edit-title {
  color: #fff;
}

.loading-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 3rem 0;
}

.profile-edit-form {
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}

.avatar-upload {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid #e0e0e0;
  background-color: #f5f5f5;
  flex-shrink: 0;
}

.dark .avatar-preview {
  border-color: #444;
  background-color: #333;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 32px;
}

.dark .avatar-placeholder {
  color: #666;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.file-input {
  display: none;
}

.avatar-hint {
  font-size: 12px;
  color: #999;
  margin: 0;
}

.dark .avatar-hint {
  color: #666;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
}

@media (max-width: 768px) {
  .profile-edit {
    padding: 1rem 0.5rem;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .avatar-upload {
    flex-direction: column;
  }
}
</style>
