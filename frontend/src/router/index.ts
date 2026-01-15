import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import NotFound from '../views/NotFound.vue'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home,
    },
    {
      path: '/home',
      redirect: '/'
    },
    {
      path: '/ai-chat',
      name: 'AIChat',
      component: () => import('../views/AIChat.vue')
    },
    {
      path: '/comfort-simulator',
      name: 'ComfortSimulator',
      component: () => import('../views/ComfortSimulator.vue')
    },
    {
      path: '/customer-service',
      name: 'CustomerService',
      component: () => import('../views/CustomerService.vue')
    },
    {
      path: '/chat-pdf',
      name: 'ChatPDF',
      component: () => import('../views/ChatPDF.vue')
    },
    {
      path: '/game',
      name: 'game',
      component: () => import('../views/GameChat.vue')
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue'),
    },
    {
      path: '/thinking-chat',
      name: 'thinking-chat',
      component: () => import('../views/Home.vue'), // 临时指向 Home
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('../views/Profile.vue'),
      meta: {
        requiresAuth: true,
        title: '个人中心'
      }
    },
    {
      path: '/profile/edit',
      name: 'ProfileEdit',
      component: () => import('../views/ProfileEdit.vue'),
      meta: {
        requiresAuth: true,
        title: '编辑资料'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: NotFound,
      meta: {
        title: '页面不存在'
      }
    }
  ],
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const token = getToken()

  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - Haze AI Hub`
  }

  // 检查是否需要认证
  if (to.meta.requiresAuth) {
    if (!token) {
      ElMessage.warning('请先登录')
      // 触发打开登录对话框事件
      window.dispatchEvent(new CustomEvent('open-login-dialog'))
      // 如果直接访问受限页面（如刷新 /profile），使用重定向而不是取消导航，
      // 避免在没有上一页路由时出现空白页面。
      if (from.name) {
        next(false)
      } else {
        next({ path: '/', replace: true })
      }
      return
    }

    // 检查用户信息是否存在
    if (!userStore.userInfo) {
      try {
        await userStore.getUserInfo()
      } catch (error) {
        // 获取用户信息失败，清除 token，并根据来源路由决定跳转逻辑，
        // 避免在首次直接访问受限页面（from.name 为空）时出现空白页。
        await userStore.logout()
        ElMessage.error('登录已过期，请重新登录')
        window.dispatchEvent(new CustomEvent('open-login-dialog'))
        if (from.name) {
          // 有来源页时，留在原页面
          next(false)
        } else {
          // 没有来源页，多数是用户直接在地址栏输入受限路由（如 /profile），
          // 使用重定向方式跳转到首页，避免空白页面。
          next({ path: '/', replace: true })
        }
        return
      }
    }
  }

  next()
})

export default router
