<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="visible" class="dialog-overlay" @click.self="handleClose">
        <div class="dialog-container" :class="{ 'dark': isDark }">
          <div class="dialog-header">
            <h3>{{ title }}</h3>
            <button class="close-btn" @click="handleClose">
              <XMarkIcon class="icon" />
            </button>
          </div>
          
          <div class="dialog-body">
            <p class="dialog-description">{{ description }}</p>
            
            <div class="input-wrapper">
              <input
                v-model="groupName"
                type="text"
                :placeholder="placeholder"
                maxlength="50"
                @keydown.enter="handleConfirm"
                ref="inputRef"
              />
              <span class="char-count">{{ groupName.length }}/50</span>
            </div>
            
            <div class="icon-selector">
              <button
                v-for="icon in icons"
                :key="icon.name"
                class="icon-btn"
                :class="{ 'active': selectedIcon === icon.name }"
                @click="selectedIcon = icon.name"
                :title="icon.label"
              >
                {{ icon.emoji }}
              </button>
            </div>
          </div>
          
          <div class="dialog-footer">
            <button class="btn btn-cancel" @click="handleClose">取消</button>
            <button 
              class="btn btn-confirm" 
              @click="handleConfirm"
              :disabled="!groupName.trim()"
            >
              确定
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useDark } from '@vueuse/core'
import { XMarkIcon } from '@heroicons/vue/24/outline'

interface Props {
  visible: boolean
  title?: string
  description?: string
  placeholder?: string
  defaultValue?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '创建分组',
  description: '分组可用于整理对话，让对话更有条理。',
  placeholder: '输入分组名称',
  defaultValue: ''
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', name: string, icon: string): void
}>()

const isDark = useDark()
const groupName = ref('')
const selectedIcon = ref('folder')
const inputRef = ref<HTMLInputElement>()

const icons = [
  { name: 'folder', emoji: '📁', label: '文件夹' },
  { name: 'star', emoji: '⭐', label: '星标' },
  { name: 'work', emoji: '💼', label: '工作' },
  { name: 'study', emoji: '📚', label: '学习' },
  { name: 'code', emoji: '💻', label: '代码' },
  { name: 'idea', emoji: '💡', label: '想法' },
  { name: 'heart', emoji: '❤️', label: '收藏' },
  { name: 'fire', emoji: '🔥', label: '热门' }
]

watch(() => props.visible, (newVal) => {
  if (newVal) {
    groupName.value = props.defaultValue
    selectedIcon.value = 'folder'
    nextTick(() => {
      inputRef.value?.focus()
    })
  }
})

const handleClose = () => {
  emit('update:visible', false)
}

const handleConfirm = () => {
  if (!groupName.value.trim()) return
  emit('confirm', groupName.value.trim(), selectedIcon.value)
  handleClose()
}
</script>

<style scoped lang="scss">
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.dialog-container {
  width: 90%;
  max-width: 480px;
  background: #fff;
  border-radius: 1rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  
  &.dark {
    background: #2a2a2a;
    color: #fff;
  }
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.5rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  
  .dark & {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }
  
  h3 {
    font-size: 1.25rem;
    font-weight: 600;
    margin: 0;
  }
  
  .close-btn {
    width: 2rem;
    height: 2rem;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    background: transparent;
    color: #666;
    cursor: pointer;
    border-radius: 0.5rem;
    transition: all 0.2s;
    
    &:hover {
      background: rgba(0, 0, 0, 0.05);
      color: #333;
    }
    
    .dark & {
      color: #999;
      
      &:hover {
        background: rgba(255, 255, 255, 0.1);
        color: #fff;
      }
    }
    
    .icon {
      width: 1.25rem;
      height: 1.25rem;
    }
  }
}

.dialog-body {
  padding: 1.5rem;
  
  .dialog-description {
    color: #666;
    font-size: 0.875rem;
    margin: 0 0 1.5rem;
    line-height: 1.5;
    
    .dark & {
      color: #999;
    }
  }
  
  .input-wrapper {
    position: relative;
    margin-bottom: 1.5rem;
    
    input {
      width: 100%;
      padding: 0.875rem 3.5rem 0.875rem 1rem;
      border: 1px solid rgba(0, 0, 0, 0.1);
      border-radius: 0.75rem;
      font-size: 1rem;
      background: rgba(0, 0, 0, 0.02);
      transition: all 0.2s;
      
      &:focus {
        outline: none;
        border-color: #007CF0;
        background: #fff;
      }
      
      .dark & {
        background: rgba(255, 255, 255, 0.05);
        border-color: rgba(255, 255, 255, 0.1);
        color: #fff;
        
        &:focus {
          background: rgba(255, 255, 255, 0.08);
          border-color: #007CF0;
        }
        
        &::placeholder {
          color: #666;
        }
      }
    }
    
    .char-count {
      position: absolute;
      right: 1rem;
      top: 50%;
      transform: translateY(-50%);
      font-size: 0.75rem;
      color: #999;
      pointer-events: none;
    }
  }
  
  .icon-selector {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 0.5rem;
    
    .icon-btn {
      aspect-ratio: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.5rem;
      border: 2px solid transparent;
      background: rgba(0, 0, 0, 0.02);
      border-radius: 0.75rem;
      cursor: pointer;
      transition: all 0.2s;
      
      &:hover {
        background: rgba(0, 124, 240, 0.1);
        transform: scale(1.1);
      }
      
      &.active {
        border-color: #007CF0;
        background: rgba(0, 124, 240, 0.1);
      }
      
      .dark & {
        background: rgba(255, 255, 255, 0.05);
        
        &:hover {
          background: rgba(0, 124, 240, 0.2);
        }
        
        &.active {
          background: rgba(0, 124, 240, 0.2);
        }
      }
    }
  }
}

.dialog-footer {
  display: flex;
  gap: 0.75rem;
  padding: 1.5rem;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  
  .dark & {
    border-top-color: rgba(255, 255, 255, 0.1);
  }
  
  .btn {
    flex: 1;
    padding: 0.75rem 1.5rem;
    border: none;
    border-radius: 0.75rem;
    font-size: 1rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    
    &.btn-cancel {
      background: rgba(0, 0, 0, 0.05);
      color: #666;
      
      &:hover {
        background: rgba(0, 0, 0, 0.1);
      }
      
      .dark & {
        background: rgba(255, 255, 255, 0.1);
        color: #999;
        
        &:hover {
          background: rgba(255, 255, 255, 0.15);
        }
      }
    }
    
    &.btn-confirm {
      background: #007CF0;
      color: #fff;
      
      &:hover:not(:disabled) {
        background: #0066cc;
      }
      
      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.3s ease;
  
  .dialog-container {
    transition: transform 0.3s ease;
  }
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
  
  .dialog-container {
    transform: scale(0.9);
  }
}
</style>
