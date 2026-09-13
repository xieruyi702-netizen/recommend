<template>
  <div class="page">
    <!-- 登录页 -->
    <div v-if="!user" class="login-wrap">
      <div class="login-card">
        <div class="login-logo">热</div>
        <h1>热点雷达</h1>
        <p class="slogan">你的个性化资讯推荐引擎</p>

        <template v-if="mode === 'pwd'">
          <input v-model="account" placeholder="用户名或邮箱 · demo" @keyup.enter="login" />
          <input v-model="password" type="password" placeholder="密码 · 123456" @keyup.enter="login" />
          <p v-if="loginMsg" class="msg">{{ loginMsg }}</p>
          <button class="btn-primary" :disabled="loading" @click="login">{{ loading ? '登录中…' : '进入首页' }}</button>
          <div class="switch-row">
            <a @click="mode = 'code'; loginMsg = ''">邮箱验证码登录</a>
            <a @click="mode = 'reg'; loginMsg = ''">注册新账号</a>
          </div>
        </template>

        <template v-else-if="mode === 'code'">
          <input v-model="email" placeholder="邮箱（未注册将自动创建账号）" />
          <div class="code-row">
            <input v-model="code" placeholder="验证码" @keyup.enter="codeLogin" />
            <button class="btn-ghost" :disabled="countdown > 0 || sending" @click="sendCode('login')">
              {{ countdown > 0 ? countdown + 's' : (sending ? '发送中' : '获取验证码') }}
            </button>
          </div>
          <p v-if="loginMsg" class="msg">{{ loginMsg }}</p>
          <p v-if="mockCode" class="mock-tip">模拟模式验证码：{{ mockCode }}</p>
          <button class="btn-primary" :disabled="loading" @click="codeLogin">{{ loading ? '登录中…' : '验证并登录' }}</button>
          <div class="switch-row">
            <a @click="mode = 'pwd'; loginMsg = ''">密码登录</a>
            <a @click="mode = 'reg'; loginMsg = ''">注册新账号</a>
          </div>
        </template>

        <template v-else>
          <input v-model="regUsername" placeholder="用户名" />
          <input v-model="email" placeholder="邮箱" />
          <div class="code-row">
            <input v-model="code" placeholder="验证码" @keyup.enter="register" />
            <button class="btn-ghost" :disabled="countdown > 0 || sending" @click="sendCode('register')">
              {{ countdown > 0 ? countdown + 's' : (sending ? '发送中' : '获取验证码') }}
            </button>
          </div>
          <input v-model="regPassword" type="password" placeholder="密码（可选）" />
          <input v-model="regTags" placeholder="感兴趣的频道：时政,财经,科技,体育,娱乐" />
          <p v-if="loginMsg" class="msg">{{ loginMsg }}</p>
          <p v-if="mockCode" class="mock-tip">模拟模式验证码：{{ mockCode }}</p>
          <button class="btn-primary" :disabled="loading" @click="register">{{ loading ? '注册中…' : '注册并进入' }}</button>
          <div class="switch-row">
            <a @click="mode = 'pwd'; loginMsg = ''">密码登录</a>
            <a @click="mode = 'code'; loginMsg = ''">验证码登录</a>
          </div>
        </template>
      </div>
    </div>

    <div v-else>
      <!-- ====== 顶部频道导航（今日头条式）====== -->
      <header class="topnav">
        <div class="nav-inner">
          <div class="brand"><span class="logo">热</span> 热点雷达</div>
          <div class="top-right">
            <button class="icon-btn" :disabled="crawling" @click="crawlNews" title="抓取最新资讯">⚡</button>
            <button class="icon-btn" @click="refresh" title="换一批">↻</button>
          </div>
        </div>
        <nav class="channels">
          <button v-for="c in channels" :key="c.key" :class="{ on: channel === c.key }" @click="switchChannel(c.key)">
            {{ c.name }}
          </button>
        </nav>
      </header>

      <div class="main">
        <p v-if="!currentList.length" class="loading-text">正在加载{{ channelName }}资讯…</p>

        <!-- 新闻卡片 -->
        <div class="list">
          <div v-for="item in currentList" :key="item.id" class="news" :data-id="item.id"
               :class="{ seen: readIds.has(item.id) }">
            <div class="news-main" @click="open(item)">
              <div class="n-title">{{ item.title }} <span v-if="isHot(item)" class="hot-badge">热</span></div>
              <div class="n-summary" v-if="item.summary">{{ item.summary }}</div>
              <div class="n-meta">
                <span class="n-src">{{ item.author }}</span>
                <span class="n-cat" v-for="t in item.tags.split(',').slice(0,1)" :key="t">{{ catName(t) }}</span>
                <span>{{ timeAgo(item.publishTime) }}</span>
                <span>🔥 {{ item.hotScore.toFixed(0) }}</span>
              </div>
            </div>
            <button class="like" :class="{ liked: isFav(item) }" @click.stop="toggleFav(item)">
              {{ isFav(item) ? '♥' : '♡' }}
            </button>
          </div>
        </div>

        <p v-if="currentList.length" class="feed-end">— 已经到底啦 —</p>
      </div>

      <!-- 底部收藏入口 -->
      <button class="fav-float" :class="{ on: channel === 'fav' }" @click="switchChannel('fav')">
        ♥<em v-if="favorites.length">{{ favorites.length }}</em>
      </button>
    </div>

    <!-- 全局 Toast -->
    <transition-group name="toast" tag="div" class="toasts">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">{{ t.icon }} {{ t.text }}</div>
    </transition-group>
  </div>
</template>

<script>
export default {
  created() {
    const saved = localStorage.getItem('user')
    if (saved && localStorage.getItem('token')) { try { this.user = JSON.parse(saved) } catch (e) {} }
  },
  data() {
    return {
      account: 'demo', password: '123456', loginMsg: '', loading: false,
      mode: 'pwd', email: '', code: '', mockCode: '',
      sending: false, countdown: 0, countdownTimer: null,
      regUsername: '', regPassword: '', regTags: '',
      toasts: [], toastId: 0,
      crawling: false,
      user: null, feed: [], channelList: [], favorites: [],
      channel: 'rec', readIds: new Set(),
      observer: null
    }
  },
  computed: {
    channels: () => [
      { key: 'rec', name: '推荐' },
      { key: '时政', name: '时政' },
      { key: '财经', name: '财经' },
      { key: '科技', name: '科技' },
      { key: '体育', name: '体育' },
      { key: '娱乐', name: '娱乐' },
      { key: 'B站', name: 'B站' },
      { key: '抖音', name: '抖音' },
      { key: 'fav', name: '收藏' }
    ],
    currentList() { return this.channel === 'rec' ? this.feed : (this.channel === 'fav' ? this.favorites : this.channelList) },
    channelName() { return this.channel === 'rec' || this.channel === 'fav' ? '' : this.channel }
  },
  methods: {
    authHeaders() {
      return { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (this.user?.token || localStorage.getItem('token') || '') }
    },
    handle401(res) {
      if (res.status === 401) {
        this.user = null
        localStorage.removeItem('user'); localStorage.removeItem('token')
        this.toast('登录已过期，请重新登录', 'warn')
        return true
      }
      return false
    },
    toast(text, type = 'ok') {
      const id = ++this.toastId
      const icon = { ok: '✓', warn: '!', info: '⚡' }[type] || '✓'
      this.toasts.push({ id, text, type, icon })
      setTimeout(() => { this.toasts = this.toasts.filter(t => t.id !== id) }, 2400)
    },
    catName(t) { return ['politics','finance','it','sports','ent'].includes(t) ? '' : t },
    isHot(item) { return item.hotScore >= 85 },
    timeAgo(s) {
      if (!s) return ''
      const d = new Date(s)
      const diff = (Date.now() - d.getTime()) / 3600000
      if (diff < 1) return Math.max(1, Math.round(diff * 60)) + ' 分钟前'
      if (diff < 24) return Math.round(diff) + ' 小时前'
      return Math.round(diff / 24) + ' 天前'
    },
    async login() {
      this.loading = true; this.loginMsg = ''
      const res = await fetch('/api/user/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ account: this.account, password: this.password })
      }).then(r => r.json())
      this.loading = false
      if (res.ok) { this.toast('欢迎回来，' + res.username); this.user = res; localStorage.setItem('user', JSON.stringify(res)); localStorage.setItem('token', res.token); this.refresh(); this.loadFavs() }
      else this.loginMsg = res.msg
    },
    async sendCode(purpose) {
      this.sending = true; this.loginMsg = ''; this.mockCode = ''
      const res = await fetch('/api/auth/send-code', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: this.email, purpose })
      }).then(r => r.json())
      this.sending = false
      this.loginMsg = res.ok ? '' : res.msg
      if (res.ok) {
        if (res.mockCode) this.mockCode = res.mockCode
        this.toast(res.mockCode ? '模拟模式：验证码已显示在下方' : '验证码已发送，请查收邮箱', 'info')
        this.countdown = 60
        clearInterval(this.countdownTimer)
        this.countdownTimer = setInterval(() => {
          if (--this.countdown <= 0) clearInterval(this.countdownTimer)
        }, 1000)
      }
    },
    async codeLogin() {
      this.loading = true; this.loginMsg = ''
      const res = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: this.email, code: this.code })
      }).then(r => r.json())
      this.loading = false
      if (res.ok) { this.toast('欢迎，' + res.username); this.user = res; localStorage.setItem('user', JSON.stringify(res)); localStorage.setItem('token', res.token); this.refresh(); this.loadFavs() }
      else this.loginMsg = res.msg
    },
    async register() {
      this.loading = true; this.loginMsg = ''
      const res = await fetch('/api/auth/register', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: this.regUsername, email: this.email, code: this.code,
          password: this.regPassword, interestTags: this.regTags
        })
      }).then(r => r.json())
      this.loading = false
      if (res.ok) { this.toast('注册成功'); this.user = res; localStorage.setItem('user', JSON.stringify(res)); localStorage.setItem('token', res.token); this.refresh(); this.loadFavs() }
      else this.loginMsg = res.msg
    },
    async crawlNews() {
      this.crawling = true
      const res = await fetch('/api/crawler/refresh', { method: 'POST' }).then(r => r.json())
      this.crawling = false
      if (res.ok) {
        this.toast('采集完成，当前资讯总量 ' + res.totalNews + ' 条')
        this.channel === 'rec' ? this.refresh() : this.loadChannel(this.channel)
      } else this.toast('采集失败，请稍后再试', 'warn')
    },
    async refresh() {
      const res = await fetch(`/api/feed/recommend?userId=${this.user.userId}&size=20`, { headers: this.authHeaders() })
      if (this.handle401(res)) return
      this.feed = await res.json()
      this.toast('已为你刷新推荐', 'info')
      this.$nextTick(() => this.setupObserver())
    },
    async loadChannel(cat) {
      this.channelList = await fetch(`/api/news/list?category=${encodeURIComponent(cat)}&size=30`).then(r => r.json())
      this.$nextTick(() => this.setupObserver())
    },
    async loadFavs() {
      const res = await fetch(`/api/favorite/list?userId=${this.user.userId}`, { headers: this.authHeaders() })
      if (this.handle401(res)) return
      this.favorites = await res.json()
    },
    switchChannel(c) {
      this.channel = c
      if (c === 'rec') this.$nextTick(() => this.setupObserver())
      else if (c !== 'fav') this.loadChannel(c)
    },
    isFav(item) { return this.favorites.some(f => f.id === item.id) },
    async toggleFav(item) {
      const liked = !this.isFav(item)
      await fetch('/api/favorite/toggle', {
        method: 'POST', headers: this.authHeaders(),
        body: JSON.stringify({ userId: this.user.userId, itemId: item.id, liked })
      })
      if (liked) this.report(item, 'like')
      this.toast(liked ? '已加入收藏' : '已取消收藏', liked ? 'ok' : 'info')
      this.loadFavs()
    },
    setupObserver() {
      if (this.observer) this.observer.disconnect()
      this.observer = new IntersectionObserver(entries => {
        entries.forEach(e => {
          if (e.isIntersecting) {
            const id = Number(e.target.dataset.id)
            const item = (this.channel === 'rec' ? this.feed : this.channelList).find(i => i.id === id)
            this.report(item, 'expose')
            this.observer.unobserve(e.target)
          }
        })
      }, { threshold: 0.5 })
      document.querySelectorAll('.news').forEach(el => this.observer.observe(el))
    },
    open(item) {
      if (!item.url) { this.toast('该资讯暂无原文链接', 'warn'); return }
      this.readIds.add(item.id)
      this.report(item, 'click')
      window.open(item.url, '_blank')
    },
    async report(item, action) {
      if (!item || !this.user) return
      fetch('/api/behavior/report', {
        method: 'POST', headers: this.authHeaders(),
        body: JSON.stringify({ userId: this.user.userId, itemId: item.id, action, timestamp: Date.now() })
      }).catch(() => {})
    }
  }
}
</script>

<style>
* { box-sizing: border-box; margin: 0; }
:root {
  --bg: #f4f5f7; --card: #ffffff; --line: #e8eaef;
  --text: #1a1a1a; --dim: #85888f; --accent: #e0453c; --accent-dark: #c93a32;
}
body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif; background: var(--bg); color: var(--text); min-height: 100vh; }
.page { min-height: 100vh; }

/* ---- 登录 ---- */
.login-wrap { display: flex; justify-content: center; padding-top: 13vh; }
.login-card { width: 340px; padding: 40px 36px; border-radius: 20px; text-align: center; background: #fff; box-shadow: 0 12px 48px rgba(0,0,0,.1); display: flex; flex-direction: column; gap: 14px; }
.login-logo { width: 60px; height: 60px; margin: 0 auto; border-radius: 16px; font-size: 26px; font-weight: 700; display: flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg, var(--accent), #ff7a45); box-shadow: 0 8px 24px rgba(224,69,60,.4); }
.login-card h1 { font-size: 24px; }
.slogan { color: var(--dim); font-size: 13px; margin-bottom: 6px; }
.login-card input { padding: 12px 14px; border-radius: 10px; border: 1px solid var(--line); background: #f8f9fb; color: var(--text); font-size: 14px; outline: none; }
.login-card input:focus { border-color: var(--accent); background: #fff; }
.btn-primary { padding: 12px; border: none; border-radius: 10px; cursor: pointer; font-size: 15px; color: #fff; background: linear-gradient(135deg, var(--accent), var(--accent-dark)); box-shadow: 0 6px 18px rgba(224,69,60,.35); transition: transform .15s; }
.btn-primary:hover { transform: translateY(-1px); }
.btn-primary:disabled { opacity: .6; cursor: wait; }
.btn-ghost { padding: 10px 12px; font-size: 12px; border-radius: 10px; cursor: pointer; background: #fff; color: var(--accent); border: 1px solid var(--accent); white-space: nowrap; }
.btn-ghost:disabled { color: var(--dim); border-color: var(--line); }
.switch-row { display: flex; justify-content: space-between; }
.switch-row a { color: var(--accent); cursor: pointer; font-size: 12px; }
.code-row { display: flex; gap: 8px; }
.code-row input { flex: 1; }
.msg { text-align: center; color: var(--accent); font-size: 13px; }
.mock-tip { text-align: center; color: #2ba245; font-size: 12px; }

/* ---- 顶部导航 ---- */
.topnav { position: sticky; top: 0; z-index: 10; background: #fff; box-shadow: 0 1px 0 var(--line); }
.nav-inner { max-width: 720px; margin: 0 auto; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; }
.brand { font-size: 18px; font-weight: 800; display: flex; align-items: center; gap: 8px; }
.brand .logo { width: 30px; height: 30px; border-radius: 8px; font-size: 14px; display: inline-flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg, var(--accent), #ff7a45); }
.icon-btn { width: 34px; height: 34px; border-radius: 50%; border: 1px solid var(--line); background: #fff; color: var(--dim); font-size: 15px; cursor: pointer; transition: all .2s; }
.icon-btn:hover { color: var(--accent); border-color: var(--accent); transform: rotate(180deg); }
.channels { max-width: 720px; margin: 0 auto; padding: 0 12px; display: flex; gap: 2px; overflow-x: auto; }
.channels button { padding: 10px 14px; border: none; background: none; font-size: 15px; color: var(--dim); cursor: pointer; position: relative; white-space: nowrap; }
.channels button.on { color: var(--accent); font-weight: 700; }
.channels button.on::after { content: ''; position: absolute; left: 50%; transform: translateX(-50%); bottom: 4px; width: 20px; height: 3px; border-radius: 2px; background: var(--accent); }

/* ---- 主区 ---- */
.main { max-width: 720px; margin: 0 auto; padding: 12px 16px 90px; }
.loading-text { text-align: center; color: var(--dim); padding: 60px 0; }
.feed-end { text-align: center; color: #c3c6cd; font-size: 12px; padding: 24px 0; }

/* ---- 新闻卡片 ---- */
.list { display: flex; flex-direction: column; gap: 10px; }
.news { display: flex; align-items: center; background: var(--card); border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,.04); transition: box-shadow .2s, transform .15s; }
.news:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); transform: translateY(-1px); }
.news.seen .n-title { color: #9a9da4; }
.news-main { flex: 1; min-width: 0; cursor: pointer; }
.n-title { font-size: 16px; font-weight: 600; line-height: 1.45; }
.hot-badge { color: #fff; background: var(--accent); font-size: 10px; padding: 1px 5px; border-radius: 4px; margin-left: 6px; vertical-align: 2px; font-weight: 400; }
.n-summary { color: var(--dim); font-size: 13px; line-height: 1.5; margin-top: 6px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.n-meta { display: flex; align-items: center; gap: 10px; margin-top: 8px; font-size: 12px; color: var(--dim); }
.n-src { color: var(--accent); }
.n-cat { background: #fdeeec; color: var(--accent); padding: 0 7px; border-radius: 4px; font-size: 11px; }
.like { background: none; border: none; font-size: 20px; cursor: pointer; color: #c3c6cd; padding: 6px 4px 6px 12px; transition: color .2s, transform .2s; flex-shrink: 0; }
.like:hover { transform: scale(1.2); }
.like.liked { color: var(--accent); }

/* ---- 收藏悬浮按钮 ---- */
.fav-float { position: fixed; right: 24px; bottom: 28px; width: 52px; height: 52px; border-radius: 50%; border: none; background: linear-gradient(135deg, var(--accent), var(--accent-dark)); color: #fff; font-size: 20px; cursor: pointer; box-shadow: 0 8px 24px rgba(224,69,60,.45); z-index: 9; transition: transform .2s; }
.fav-float:hover { transform: scale(1.08); }
.fav-float.on { outline: 3px solid rgba(224,69,60,.3); }
.fav-float em { position: absolute; top: -4px; right: -4px; background: #222; color: #fff; font-style: normal; font-size: 11px; border-radius: 10px; padding: 1px 6px; }

/* ---- Toast ---- */
.toasts { position: fixed; top: 18px; left: 50%; transform: translateX(-50%); z-index: 99; display: flex; flex-direction: column; gap: 8px; align-items: center; pointer-events: none; }
.toast { padding: 10px 20px; border-radius: 10px; font-size: 13px; color: #fff; background: rgba(26,26,26,.9); box-shadow: 0 8px 24px rgba(0,0,0,.25); }
.toast.ok { background: rgba(43,162,69,.95); }
.toast.warn { background: rgba(224,69,60,.95); }
.toast.info { background: rgba(26,26,26,.9); }
.toast-enter-active { transition: all .25s ease; }
.toast-leave-active { transition: all .25s ease; }
.toast-enter-from { opacity: 0; transform: translateY(-12px); }
.toast-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
