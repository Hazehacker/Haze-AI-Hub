<template>
  <div class="message" :class="{ 'message-user': isUser, 'message-error': isError }">
    <div class="avatar">
      <UserCircleIcon v-if="isUser" class="icon" />
      <ExclamationTriangleIcon v-else-if="isError" class="icon error-icon" />
      <ComputerDesktopIcon v-else class="icon" :class="{ 'assistant': !isUser }" />
    </div>
    <div class="content">
      <!-- 错误消息展示区域 -->
      <div v-if="isError" class="error-section">
        <div class="error-header">
          <ExclamationTriangleIcon class="error-icon-large" />
          <span class="error-title">连接失败</span>
        </div>
        <div class="error-message">{{ message.content }}</div>
        <div class="error-hint">请检查网络连接后重试，或稍后再试</div>
      </div>
      
      <!-- 思考过程展示区域 -->
      <div v-else-if="hasThinkingContent && !isUser" class="thinking-section">
        <div class="thinking-header" @click="toggleThinking">
          <div class="thinking-title">
            <svg class="thinking-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            <span>深度思考已完成</span>
            <span v-if="thinkingTime" class="thinking-time">(用时{{ thinkingTime }})</span>
          </div>
          <svg class="expand-icon" :class="{ 'expanded': isThinkingExpanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
        <div v-show="isThinkingExpanded" class="thinking-content">
          <div class="thinking-text">{{ thinkingContent }}</div>
        </div>
      </div>
      
      <div class="text-container">
        <button v-if="isUser" class="user-copy-button" @click="copyContent" :title="copyButtonTitle">
          <DocumentDuplicateIcon v-if="!copied" class="copy-icon" />
          <CheckIcon v-else class="copy-icon copied" />
        </button>
        <div class="text" ref="contentRef" v-if="isUser">
          {{ message.content }}
        </div>
        <div class="text markdown-content" ref="contentRef" v-else v-html="processedContent"></div>
      </div>
      <div class="message-footer" v-if="!isUser">
        <button class="copy-button" @click="copyContent" :title="copyButtonTitle">
          <DocumentDuplicateIcon v-if="!copied" class="copy-icon" />
          <CheckIcon v-else class="copy-icon copied" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, nextTick, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { UserCircleIcon, ComputerDesktopIcon, DocumentDuplicateIcon, CheckIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const contentRef = ref(null)
const copied = ref(false)
const copyButtonTitle = computed(() => copied.value ? '已复制' : '复制内容')
const isThinkingExpanded = ref(false)

// 提取思考内容（从metadata或content中的think标签）
const thinkingContent = computed(() => {
  // 优先从metadata中获取
  if (props.message.metadata?.thinking_content) {
    return props.message.metadata.thinking_content
  }
  
  // 如果metadata中没有，尝试从content中提取think标签内容
  const content = props.message.content || ''
  const thinkMatch = content.match(/<think>([\s\S]*?)<\/think>/)
  if (thinkMatch && thinkMatch[1]) {
    return thinkMatch[1].trim()
  }
  
  return ''
})

// 获取不包含think标签的纯内容
const pureContent = computed(() => {
  const content = props.message.content || ''
  // 移除think标签及其内容
  return content.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
})

// 检查是否有思考内容
const hasThinkingContent = computed(() => {
  return !!thinkingContent.value && thinkingContent.value.trim().length > 0
})

// 思考时间（从metadata中获取）
const thinkingTime = computed(() => {
  const duration = props.message.metadata?.thinking_duration
  if (!duration) return ''
  
  // 如果duration是数字（秒），格式化显示
  if (typeof duration === 'number') {
    if (duration < 60) {
      return `${Math.round(duration)}秒`
    } else {
      const minutes = Math.floor(duration / 60)
      const seconds = Math.round(duration % 60)
      return `${minutes}分${seconds}秒`
    }
  }
  
  // 如果已经是字符串格式，直接返回
  return duration
})

// 切换思考内容展开/收起
const toggleThinking = () => {
  isThinkingExpanded.value = !isThinkingExpanded.value
}

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
  sanitize: false
})

// 处理内容
const processContent = (content) => {
  if (!content) return ''

  // 分析内容中的 think 标签
  let result = ''
  let isInThinkBlock = false
  let currentBlock = ''
  let thinkingBlocks = []

  // 逐字符分析，处理 think 标签
  for (let i = 0; i < content.length; i++) {
    if (content.slice(i, i + 7) === '<think>') {
      isInThinkBlock = true
      if (currentBlock) {
        // 将之前的普通内容转换为 HTML
        result += marked.parse(currentBlock)
      }
      currentBlock = ''
      i += 6 // 跳过 <think>
      continue
    }

    if (content.slice(i, i + 8) === '</think>') {
      isInThinkBlock = false
      // 收集思考块内容
      thinkingBlocks.push(currentBlock)
      // 使用与完成后相同的样式
      result += `<div class="thinking-section inline-thinking">
        <div class="thinking-header">
          <div class="thinking-title">
            <svg class="thinking-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            <span>深度思考中...</span>
          </div>
        </div>
        <div class="thinking-content-inline">${currentBlock}</div>
      </div>`
      currentBlock = ''
      i += 7 // 跳过 </think>
      continue
    }

    currentBlock += content[i]
  }

  // 处理剩余内容
  if (currentBlock) {
    if (isInThinkBlock) {
      thinkingBlocks.push(currentBlock)
      // 使用与完成后相同的样式
      result += `<div class="thinking-section inline-thinking">
        <div class="thinking-header">
          <div class="thinking-title">
            <svg class="thinking-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            <span>深度思考中...</span>
          </div>
        </div>
        <div class="thinking-content-inline">${currentBlock}</div>
      </div>`
    } else {
      result += marked.parse(currentBlock)
    }
  }

  // 净化处理后的 HTML
  const cleanHtml = DOMPurify.sanitize(result, {
    ADD_TAGS: ['think', 'code', 'pre', 'span', 'svg', 'path'],
    ADD_ATTR: ['class', 'language', 'viewBox', 'fill', 'stroke', 'stroke-linecap', 'stroke-linejoin', 'stroke-width', 'd', 'style']
  })
  
  // 在净化后的 HTML 中查找代码块并添加复制按钮
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = cleanHtml
  
  // 查找所有代码块
  const preElements = tempDiv.querySelectorAll('pre')
  preElements.forEach(pre => {
    const code = pre.querySelector('code')
    if (code) {
      // 创建包装器
      const wrapper = document.createElement('div')
      wrapper.className = 'code-block-wrapper'
      
      // 添加复制按钮
      const copyBtn = document.createElement('button')
      copyBtn.className = 'code-copy-button'
      copyBtn.title = '复制代码'
      copyBtn.innerHTML = `
        <svg xmlns="http://www.w3.org/2000/svg" class="code-copy-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
        </svg>
      `
      
      // 添加成功消息
      const successMsg = document.createElement('div')
      successMsg.className = 'copy-success-message'
      successMsg.textContent = '已复制!'
      
      // 组装结构
      wrapper.appendChild(copyBtn)
      wrapper.appendChild(pre.cloneNode(true))
      wrapper.appendChild(successMsg)
      
      // 替换原始的 pre 元素
      pre.parentNode.replaceChild(wrapper, pre)
    }
  })
  
  return tempDiv.innerHTML
}

// 修改计算属性
const processedContent = computed(() => {
  if (!props.message.content) return ''
  // 如果有思考内容，使用纯内容（不包含think标签）
  const contentToProcess = hasThinkingContent.value ? pureContent.value : props.message.content
  return processContent(contentToProcess)
})

// 为代码块添加复制功能
const setupCodeBlockCopyButtons = () => {
  if (!contentRef.value) return;
  
  const codeBlocks = contentRef.value.querySelectorAll('.code-block-wrapper');
  codeBlocks.forEach(block => {
    const copyButton = block.querySelector('.code-copy-button');
    const codeElement = block.querySelector('code');
    const successMessage = block.querySelector('.copy-success-message');
    
    if (copyButton && codeElement) {
      // 移除旧的事件监听器
      const newCopyButton = copyButton.cloneNode(true);
      copyButton.parentNode.replaceChild(newCopyButton, copyButton);
      
      // 添加新的事件监听器
      newCopyButton.addEventListener('click', async (e) => {
        e.preventDefault();
        e.stopPropagation();
        try {
          const code = codeElement.textContent || '';
          await navigator.clipboard.writeText(code);
          
          // 显示成功消息
          if (successMessage) {
            successMessage.classList.add('visible');
            setTimeout(() => {
              successMessage.classList.remove('visible');
            }, 2000);
          }
        } catch (err) {
          console.error('复制代码失败:', err);
        }
      });
    }
  });
}

// 在内容更新后手动应用高亮和设置复制按钮
const highlightCode = async () => {
  await nextTick()
  if (contentRef.value) {
    contentRef.value.querySelectorAll('pre code').forEach((block) => {
      hljs.highlightElement(block)
    })
    
    // 设置代码块复制按钮
    setupCodeBlockCopyButtons()
  }
}

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const isUser = computed(() => props.message.role === 'user')
const isError = computed(() => props.message.role === 'error' || props.message.type === 'error')

// 复制内容到剪贴板
const copyContent = async () => {
  try {
    // 获取纯文本内容
    let textToCopy = props.message.content;
    
    // 如果是AI回复，需要去除HTML标签
    if (!isUser.value && contentRef.value) {
      // 创建临时元素来获取纯文本
      const tempDiv = document.createElement('div');
      tempDiv.innerHTML = processedContent.value;
      textToCopy = tempDiv.textContent || tempDiv.innerText || '';
    }
    
    await navigator.clipboard.writeText(textToCopy);
    copied.value = true;
    
    // 3秒后重置复制状态
    setTimeout(() => {
      copied.value = false;
    }, 3000);
  } catch (err) {
    console.error('复制失败:', err);
  }
}

// 监听内容变化
watch(() => props.message.content, () => {
  if (!isUser.value) {
    highlightCode()
  }
})

// 初始化时也执行一次
onMounted(() => {
  if (!isUser.value) {
    highlightCode()
  }
})

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString()
}
</script>

<style scoped lang="scss">
.message {
  display: flex;
  margin-bottom: 1.5rem;
  gap: 1rem;

  &.message-user {
    flex-direction: row-reverse;

    .content {
      align-items: flex-end;
      
      .error-section {
        background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
        border: 1px solid #fca5a5;
        border-radius: 0.75rem;
        padding: 1rem;
        
        .error-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-bottom: 0.5rem;
          
          .error-icon-large {
            width: 20px;
            height: 20px;
            color: #dc2626;
          }
          
          .error-title {
            font-size: 0.875rem;
            font-weight: 600;
            color: #dc2626;
          }
        }
        
        .error-message {
          color: #991b1b;
          font-size: 0.875rem;
          line-height: 1.5;
          margin-bottom: 0.5rem;
        }
        
        .error-hint {
          color: #7f1d1d;
          font-size: 0.75rem;
          opacity: 0.8;
        }
      }
      
      .text-container {
        position: relative;
        
        .text {
          background: #f0f7ff; // 浅色背景
          color: #333;
          border-radius: 1rem 1rem 0 1rem;
        }
        
        .user-copy-button {
          position: absolute;
          left: -30px;
          top: 50%;
          transform: translateY(-50%);
          background: transparent;
          border: none;
          width: 24px;
          height: 24px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          opacity: 0;
          transition: opacity 0.2s;
          
          .copy-icon {
            width: 16px;
            height: 16px;
            color: #666;
            
            &.copied {
              color: #4ade80;
            }
          }
        }
        
        &:hover .user-copy-button {
          opacity: 1;
        }
      }
      
      .message-footer {
        flex-direction: row-reverse;
      }
    }
  }

  &.message-error {
    .avatar .icon.error-icon {
      color: #dc2626;
      background: #fee2e2;
      
      &:hover {
        background: #fecaca;
      }
    }
  }

  .avatar {
    width: 40px;
    height: 40px;
    flex-shrink: 0;

    .icon {
      width: 100%;
      height: 100%;
      color: #666;
      padding: 4px;
      border-radius: 8px;
      transition: all 0.3s ease;

      &.assistant {
        color: #333;
        background: #f0f0f0;

        &:hover {
          background: #e0e0e0;
          transform: scale(1.05);
        }
      }
      
      &.error-icon {
        color: #dc2626;
        background: #fee2e2;
      }
    }
  }

  .content {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    max-width: 80%;
    
    .thinking-section {
      background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
      border: 1px solid #bae6fd;
      border-radius: 0.75rem;
      overflow: hidden;
      margin-bottom: 0.5rem;
      
      &.inline-thinking {
        margin: 0.5rem 0;
        
        .thinking-header {
          cursor: default;
          
          &:hover {
            background-color: transparent;
          }
        }
        
        .expand-icon {
          display: none;
        }
      }
      
      .thinking-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.75rem 1rem;
        cursor: pointer;
        user-select: none;
        transition: background-color 0.2s;
        
        &:hover {
          background-color: rgba(255, 255, 255, 0.5);
        }
        
        .thinking-title {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.875rem;
          color: #0369a1;
          font-weight: 500;
          
          .thinking-icon {
            width: 18px;
            height: 18px;
            color: #0284c7;
          }
          
          .thinking-time {
            color: #64748b;
            font-weight: 400;
            font-size: 0.8rem;
          }
        }
        
        .expand-icon {
          width: 20px;
          height: 20px;
          color: #0369a1;
          transition: transform 0.3s ease;
          
          &.expanded {
            transform: rotate(180deg);
          }
        }
      }
      
      .thinking-content {
        padding: 0 1rem 0.75rem 1rem;
        animation: slideDown 0.3s ease;
        
        .thinking-text {
          padding: 0.75rem;
          background: rgba(255, 255, 255, 0.6);
          border-radius: 0.5rem;
          color: #334155;
          font-size: 0.875rem;
          line-height: 1.6;
          white-space: pre-wrap;
          word-wrap: break-word;
        }
      }
    }
    
    .text-container {
      position: relative;
    }
    
    .message-footer {
      display: flex;
      align-items: center;
      margin-top: 0.25rem;
      
      .time {
        font-size: 0.75rem;
        color: #666;
      }
      
      .copy-button {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        background: transparent;
        border: none;
        font-size: 0.75rem;
        color: #666;
        padding: 0.25rem 0.5rem;
        border-radius: 4px;
        cursor: pointer;
        margin-right: auto;
        transition: background-color 0.2s;
        
        &:hover {
          background-color: rgba(0, 0, 0, 0.05);
        }
        
        .copy-icon {
          width: 14px;
          height: 14px;
          
          &.copied {
            color: #4ade80;
          }
        }
        
        .copy-text {
          font-size: 0.75rem;
        }
      }
    }

    .text {
      padding: 1rem;
      border-radius: 1rem 1rem 1rem 0;
      line-height: 1.5;
      white-space: pre-wrap;
      color: var(--text-color);

      .cursor {
        animation: blink 1s infinite;
      }

      :deep(pre) {
        background: #f6f8fa;
        padding: 1rem;
        border-radius: 0.5rem;
        overflow-x: auto;
        margin: 0.5rem 0;
        border: 1px solid #e1e4e8;

        code {
          background: transparent;
          padding: 0;
          font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace;
          font-size: 0.9rem;
          line-height: 1.5;
          tab-size: 2;
        }
      }

      :deep(.hljs) {
        color: #24292e;
        background: transparent;
      }

      :deep(.hljs-keyword) {
        color: #d73a49;
      }

      :deep(.hljs-built_in) {
        color: #005cc5;
      }

      :deep(.hljs-type) {
        color: #6f42c1;
      }

      :deep(.hljs-literal) {
        color: #005cc5;
      }

      :deep(.hljs-number) {
        color: #005cc5;
      }

      :deep(.hljs-regexp) {
        color: #032f62;
      }

      :deep(.hljs-string) {
        color: #032f62;
      }

      :deep(.hljs-subst) {
        color: #24292e;
      }

      :deep(.hljs-symbol) {
        color: #e36209;
      }

      :deep(.hljs-class) {
        color: #6f42c1;
      }

      :deep(.hljs-function) {
        color: #6f42c1;
      }

      :deep(.hljs-title) {
        color: #6f42c1;
      }

      :deep(.hljs-params) {
        color: #24292e;
      }

      :deep(.hljs-comment) {
        color: #6a737d;
      }

      :deep(.hljs-doctag) {
        color: #d73a49;
      }

      :deep(.hljs-meta) {
        color: #6a737d;
      }

      :deep(.hljs-section) {
        color: #005cc5;
      }

      :deep(.hljs-name) {
        color: #22863a;
      }

      :deep(.hljs-attribute) {
        color: #005cc5;
      }

      :deep(.hljs-variable) {
        color: #e36209;
      }
    }
  }
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slideDown {
  from {
    opacity: 0;
    max-height: 0;
  }

  to {
    opacity: 1;
    max-height: 500px;
  }
}

.dark {
  .message {
    &.message-error {
      .avatar .icon.error-icon {
        color: #f87171;
        background: #7f1d1d;
        
        &:hover {
          background: #991b1b;
        }
      }
      
      .content .error-section {
        background: linear-gradient(135deg, #7f1d1d 0%, #991b1b 100%);
        border-color: #dc2626;
        
        .error-header {
          .error-icon-large {
            color: #fca5a5;
          }
          
          .error-title {
            color: #fca5a5;
          }
        }
        
        .error-message {
          color: #fecaca;
        }
        
        .error-hint {
          color: #fee2e2;
        }
      }
    }
    
    .avatar .icon {
      &.assistant {
        color: #fff;
        background: #444;

        &:hover {
          background: #555;
        }
      }
    }

    &.message-user {
      .content .text-container {
        .text {
          background: #1a365d; // 暗色模式下的浅蓝色背景
          color: #fff;
        }
        
        .user-copy-button {
          .copy-icon {
            color: #999;
            
            &.copied {
              color: #4ade80;
            }
          }
        }
      }
    }

    .content {
      .thinking-section {
        background: linear-gradient(135deg, #1e3a5f 0%, #2d4a6f 100%);
        border-color: #3d5a7f;
        
        &.inline-thinking {
          .thinking-header {
            &:hover {
              background-color: transparent;
            }
          }
        }
        
        .thinking-header {
          &:hover {
            background-color: rgba(255, 255, 255, 0.05);
          }
          
          .thinking-title {
            color: #7dd3fc;
            
            .thinking-icon {
              color: #38bdf8;
            }
            
            .thinking-time {
              color: #94a3b8;
            }
          }
          
          .expand-icon {
            color: #7dd3fc;
          }
        }
        
        .thinking-content {
          .thinking-text {
            background: rgba(0, 0, 0, 0.3);
            color: #cbd5e1;
          }
        }
      }
      
      .message-footer {
        .time {
          color: #999;
        }
        
        .copy-button {
          color: #999;
          
          &:hover {
            background-color: rgba(255, 255, 255, 0.1);
          }
        }
      }

      .text {
        :deep(pre) {
          background: #161b22;
          border-color: #30363d;

          code {
            color: #c9d1d9;
          }
        }

        :deep(.hljs) {
          color: #c9d1d9;
          background: transparent;
        }

        :deep(.hljs-keyword) {
          color: #ff7b72;
        }

        :deep(.hljs-built_in) {
          color: #79c0ff;
        }

        :deep(.hljs-type) {
          color: #ff7b72;
        }

        :deep(.hljs-literal) {
          color: #79c0ff;
        }

        :deep(.hljs-number) {
          color: #79c0ff;
        }

        :deep(.hljs-regexp) {
          color: #a5d6ff;
        }

        :deep(.hljs-string) {
          color: #a5d6ff;
        }

        :deep(.hljs-subst) {
          color: #c9d1d9;
        }

        :deep(.hljs-symbol) {
          color: #ffa657;
        }

        :deep(.hljs-class) {
          color: #f2cc60;
        }

        :deep(.hljs-function) {
          color: #d2a8ff;
        }

        :deep(.hljs-title) {
          color: #d2a8ff;
        }

        :deep(.hljs-params) {
          color: #c9d1d9;
        }

        :deep(.hljs-comment) {
          color: #8b949e;
        }

        :deep(.hljs-doctag) {
          color: #ff7b72;
        }

        :deep(.hljs-meta) {
          color: #8b949e;
        }

        :deep(.hljs-section) {
          color: #79c0ff;
        }

        :deep(.hljs-name) {
          color: #7ee787;
        }

        :deep(.hljs-attribute) {
          color: #79c0ff;
        }

        :deep(.hljs-variable) {
          color: #ffa657;
        }
      }

      &.message-user .content .text {
        background: #0066cc;
        color: white;
      }
    }
  }
}

.markdown-content {
  :deep(p) {
    margin: 0.5rem 0;

    &:first-child {
      margin-top: 0;
    }

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(ul),
  :deep(ol) {
    margin: 0.5rem 0;

    padding-left: 1.5rem;
  }

  :deep(li) {
    margin: 0.25rem 0;

  }

  :deep(code) {
    background: rgba(0, 0, 0, 0.05);
    padding: 0.2em 0.4em;
    border-radius: 3px;
    font-size: 0.9em;
    font-family: ui-monospace, monospace;
  }

  :deep(pre code) {
    background: transparent;
    padding: 0;
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 0.5rem 0;
    width: 100%;
  }

  :deep(th),
  :deep(td) {
    border: 1px solid #ddd;
    padding: 0.5rem;
    text-align: left;
  }

  :deep(th) {
    background: rgba(0, 0, 0, 0.05);
  }

  :deep(blockquote) {
    margin: 0.5rem 0;
    padding-left: 1rem;
    border-left: 4px solid #ddd;
    color: #666;
  }

  :deep(.thinking-section) {
    background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
    border: 1px solid #bae6fd;
    border-radius: 0.75rem;
    overflow: hidden;
    margin: 0.5rem 0;
    
    .thinking-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.5rem 0.75rem;
      cursor: default;
      user-select: none;
      
      .thinking-title {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.875rem;
        color: #0369a1;
        font-weight: 500;
        
        .thinking-icon {
          width: 18px;
          height: 18px;
          color: #0284c7;
        }
      }
    }
    
    .thinking-content {
      padding: 0 1rem 0.75rem 1rem;
      
      .thinking-text {
        padding: 0.75rem;
        background: rgba(255, 255, 255, 0.6);
        border-radius: 0.5rem;
        color: #334155;
        font-size: 0.875rem;
        line-height: 1.6;
        white-space: pre-wrap;
        word-wrap: break-word;
      }
    }
    
    .thinking-content-inline {
      padding: 0 1rem 0.75rem 1rem;
      color: #334155;
      font-size: 0.875rem;
      line-height: 1.6;
      white-space: pre-wrap;
      word-wrap: break-word;
    }
  }

  :deep(.code-block-wrapper) {
    position: relative;
    margin: 1rem 0;
    border-radius: 6px;
    overflow: hidden;
    
    .code-copy-button {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      background: rgba(255, 255, 255, 0.1);
      border: none;
      color: #e6e6e6;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 0.2s, background-color 0.2s;
      z-index: 10;
      
      &:hover {
        background-color: rgba(255, 255, 255, 0.2);
      }
      
      .code-copy-icon {
        width: 16px;
        height: 16px;
      }
    }
    
    &:hover .code-copy-button {
      opacity: 0.8;
    }
    
    pre {
      margin: 0;
      padding: 1rem;
      background: #1e1e1e;
      overflow-x: auto;
      
      code {
        background: transparent;
        padding: 0;
        font-family: ui-monospace, monospace;
      }
    }
    
    .copy-success-message {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      background: rgba(74, 222, 128, 0.9);
      color: white;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      opacity: 0;
      transform: translateY(-10px);
      transition: opacity 0.3s, transform 0.3s;
      pointer-events: none;
      z-index: 20;
      
      &.visible {
        opacity: 1;
        transform: translateY(0);
      }
    }
  }
}

.dark {
  .markdown-content {
    :deep(.code-block-wrapper) {
      .code-copy-button {
        background: rgba(255, 255, 255, 0.05);
        
        &:hover {
          background-color: rgba(255, 255, 255, 0.1);
        }
      }
      
      pre {
        background: #0d0d0d;
      }
    }
    
    :deep(code) {
      background: rgba(255, 255, 255, 0.1);
    }

    :deep(th),
    :deep(td) {
      border-color: #444;
    }

    :deep(th) {
      background: rgba(255, 255, 255, 0.1);
    }

    :deep(blockquote) {
      border-left-color: #444;
      color: #999;
    }

    :deep(.thinking-section) {
      background: linear-gradient(135deg, #1e3a5f 0%, #2d4a6f 100%);
      border-color: #3d5a7f;
      
      .thinking-header {
        .thinking-title {
          color: #7dd3fc;
          
          .thinking-icon {
            color: #38bdf8;
          }
        }
      }
      
      .thinking-content {
        .thinking-text {
          background: rgba(0, 0, 0, 0.3);
          color: #cbd5e1;
        }
      }
      
      .thinking-content-inline {
        color: #cbd5e1;
      }
    }
  }
}
</style>