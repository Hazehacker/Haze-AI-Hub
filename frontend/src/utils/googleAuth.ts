// 从 vue-blog 复刻：前端获取 Google id_token（用于后端换取应用 token）
// 说明：AiHub 若不需要 Google 登录，可不使用该文件；但为了“完全复刻登录框与功能”，保留。

type GetGoogleIdTokenOptions = {
  clientId: string
  useOneTap?: boolean
}

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${src}"]`)
    if (existing) return resolve()
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`Failed to load script: ${src}`))
    document.head.appendChild(script)
  })
}

export async function getGoogleIdToken(options: GetGoogleIdTokenOptions): Promise<string> {
  const { clientId, useOneTap = false } = options
  if (!clientId) throw new Error('缺少 Google Client ID（VITE_GOOGLE_CLIENT_ID）')

  await loadScript('https://accounts.google.com/gsi/client')

  const googleAny = (window as any).google
  if (!googleAny?.accounts?.id) throw new Error('Google Identity Services 未正确加载')

  return new Promise<string>((resolve, reject) => {
    let resolved = false

    googleAny.accounts.id.initialize({
      client_id: clientId,
      callback: (resp: any) => {
        if (resolved) return
        const token = resp?.credential
        if (!token) {
          reject(new Error('未获取到 Google credential'))
          return
        }
        resolved = true
        resolve(token)
      },
      auto_select: false,
      cancel_on_tap_outside: false,
    })

    if (useOneTap) {
      googleAny.accounts.id.prompt((notification: any) => {
        if (notification?.isNotDisplayed?.() || notification?.isSkippedMoment?.()) {
          // 不强行 reject，避免 OneTap 被浏览器策略拦截时影响用户体验
        }
      })
    } else {
      // Popup 模式：用 renderButton + click 不稳定；这里用 prompt 触发账户选择
      googleAny.accounts.id.prompt()
    }

    // 超时兜底，避免 Promise 悬挂
    setTimeout(() => {
      if (resolved) return
      reject(new Error('获取 Google id_token 超时'))
    }, 60_000)
  })
}


