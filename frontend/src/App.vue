<template>
  <div class="page">
    <!-- 顶部导航 -->
    <header class="topnav">
      <div class="nav-inner">
        <div class="brand"><span class="logo">♪</span> Echo 音乐</div>
        <div class="top-right">
          <button class="icon-btn" @click="loadList" title="刷新音乐库">↻</button>
        </div>
      </div>
    </header>

    <div class="main">
      <!-- B 站音频提取 -->
      <div class="extract-card">
        <h2>提取 B 站音频</h2>
        <p class="tip">粘贴 B 站视频链接，自动提取音频存入音乐库</p>
        <div class="extract-row">
          <input v-model="url" placeholder="https://www.bilibili.com/video/BV..." @keyup.enter="extract" />
          <button class="btn-primary" :disabled="extracting || !url" @click="extract">
            {{ extracting ? '提取中…' : '提取' }}
          </button>
        </div>
      </div>

      <!-- 音乐库 -->
      <h3 class="section-title">音乐库 <span v-if="musicList.length">（{{ musicList.length }}）</span></h3>
      <p v-if="!musicList.length && !loadingList" class="loading-text">
        音乐库还是空的，粘贴一个 B 站链接提取第一首歌吧 🎵
      </p>

      <div class="list">
        <div v-for="m in musicList" :key="m.id" class="music" :class="{ playing: playingId === m.id }">
          <img v-if="m.cover" class="cover" :src="m.cover" @error="hideCover" />
          <div v-else class="cover placeholder">♪</div>
          <div class="m-main">
            <div class="m-title">{{ m.title }}</div>
            <div class="m-meta">
              <span class="m-artist">{{ m.artist }}</span>
              <span>{{ fmtDuration(m.duration) }}</span>
            </div>
          </div>
          <button class="play" :class="{ on: playingId === m.id }" @click="togglePlay(m)">
            {{ playingId === m.id ? '⏸' : '▶' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 底部播放条 -->
    <div v-if="playing" class="player">
      <div class="p-info">
        <div class="p-title">{{ playing.title }}</div>
        <div class="p-artist">{{ playing.artist }}</div>
      </div>
      <audio ref="audio" :src="playingSrc" autoplay controls @ended="playingId = null"></audio>
    </div>

    <!-- 全局 Toast -->
    <transition-group name="toast" tag="div" class="toasts">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">{{ t.icon }} {{ t.text }}</div>
    </transition-group>
  </div>
</template>

<script>
export default {
  data() {
    return {
      url: '', extracting: false,
      musicList: [], loadingList: false,
      playingId: null, playing: null, playingSrc: '',
      toasts: [], toastId: 0
    }
  },
  created() {
    this.loadList()
  },
  methods: {
    toast(text, type = 'ok') {
      const id = ++this.toastId
      const icon = { ok: '✓', warn: '!', info: '⚡' }[type] || '✓'
      this.toasts.push({ id, text, type, icon })
      setTimeout(() => { this.toasts = this.toasts.filter(t => t.id !== id) }, 2400)
    },
    fmtDuration(s) {
      if (!s) return '--:--'
      const m = Math.floor(s / 60), sec = s % 60
      return m + ':' + String(sec).padStart(2, '0')
    },
    hideCover(e) { e.target.style.display = 'none' },
    async loadList() {
      this.loadingList = true
      try {
        this.musicList = await fetch('/api/music/list').then(r => r.json())
      } catch (e) {
        this.toast('音乐库加载失败', 'warn')
      }
      this.loadingList = false
    },
    async extract() {
      this.extracting = true
      try {
        const res = await fetch('/api/music/extract', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: this.url })
        }).then(r => r.json())
        if (res.ok) {
          this.toast(res.msg)
          this.url = ''
          await this.loadList()
        } else {
          this.toast(res.msg, 'warn')
        }
      } catch (e) {
        this.toast('提取失败，请稍后再试', 'warn')
      }
      this.extracting = false
    },
    togglePlay(m) {
      if (this.playingId === m.id) {
        this.$refs.audio.pause()
        this.playingId = null
        return
      }
      this.playingId = m.id
      this.playing = m
      this.playingSrc = '/music/' + m.filePath
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
.page { min-height: 100vh; padding-bottom: 90px; }

/* ---- 顶部导航 ---- */
.topnav { position: sticky; top: 0; z-index: 10; background: #fff; box-shadow: 0 1px 0 var(--line); }
.nav-inner { max-width: 720px; margin: 0 auto; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; }
.brand { font-size: 18px; font-weight: 800; display: flex; align-items: center; gap: 8px; }
.brand .logo { width: 30px; height: 30px; border-radius: 8px; font-size: 15px; display: inline-flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg, var(--accent), #ff7a45); }
.icon-btn { width: 34px; height: 34px; border-radius: 50%; border: 1px solid var(--line); background: #fff; color: var(--dim); font-size: 15px; cursor: pointer; transition: all .2s; }
.icon-btn:hover { color: var(--accent); border-color: var(--accent); transform: rotate(180deg); }

/* ---- 主区 ---- */
.main { max-width: 720px; margin: 0 auto; padding: 16px 16px 40px; }
.section-title { font-size: 15px; margin: 20px 2px 10px; color: var(--text); }
.section-title span { color: var(--dim); font-weight: 400; font-size: 13px; }
.loading-text { text-align: center; color: var(--dim); padding: 50px 0; }

/* ---- 提取卡片 ---- */
.extract-card { background: #fff; border-radius: 14px; padding: 20px; box-shadow: 0 2px 10px rgba(0,0,0,.05); }
.extract-card h2 { font-size: 17px; }
.extract-card .tip { color: var(--dim); font-size: 13px; margin: 6px 0 14px; }
.extract-row { display: flex; gap: 10px; }
.extract-row input { flex: 1; padding: 12px 14px; border-radius: 10px; border: 1px solid var(--line); background: #f8f9fb; font-size: 14px; outline: none; }
.extract-row input:focus { border-color: var(--accent); background: #fff; }
.btn-primary { padding: 12px 26px; border: none; border-radius: 10px; cursor: pointer; font-size: 14px; color: #fff; background: linear-gradient(135deg, var(--accent), var(--accent-dark)); box-shadow: 0 6px 18px rgba(224,69,60,.35); transition: transform .15s; white-space: nowrap; }
.btn-primary:hover { transform: translateY(-1px); }
.btn-primary:disabled { opacity: .6; cursor: wait; transform: none; }

/* ---- 音乐条目 ---- */
.list { display: flex; flex-direction: column; gap: 10px; }
.music { display: flex; align-items: center; gap: 12px; background: var(--card); border-radius: 12px; padding: 12px 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04); transition: box-shadow .2s, transform .15s; }
.music:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); transform: translateY(-1px); }
.music.playing { outline: 2px solid rgba(224,69,60,.5); }
.cover { width: 56px; height: 56px; border-radius: 10px; object-fit: cover; flex-shrink: 0; background: #f0f1f4; }
.cover.placeholder { display: flex; align-items: center; justify-content: center; color: #c3c6cd; font-size: 22px; }
.m-main { flex: 1; min-width: 0; }
.m-title { font-size: 15px; font-weight: 600; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.m-meta { display: flex; gap: 12px; margin-top: 6px; font-size: 12px; color: var(--dim); }
.m-artist { color: var(--accent); }
.play { width: 42px; height: 42px; border-radius: 50%; border: none; background: linear-gradient(135deg, var(--accent), var(--accent-dark)); color: #fff; font-size: 15px; cursor: pointer; flex-shrink: 0; transition: transform .15s; }
.play:hover { transform: scale(1.1); }
.play.on { outline: 3px solid rgba(224,69,60,.3); }

/* ---- 底部播放条 ---- */
.player { position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; background: #fff; box-shadow: 0 -4px 20px rgba(0,0,0,.1); display: flex; align-items: center; gap: 16px; padding: 10px 16px; }
.p-info { max-width: 40%; }
.p-title { font-size: 14px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.p-artist { font-size: 12px; color: var(--dim); }
.player audio { flex: 1; height: 40px; }

/* ---- Toast ---- */
.toasts { position: fixed; top: 18px; left: 50%; transform: translateX(-50%); z-index: 99; display: flex; flex-direction: column; gap: 8px; align-items: center; pointer-events: none; }
.toast { padding: 10px 20px; border-radius: 10px; font-size: 13px; color: #fff; background: rgba(26,26,26,.9); box-shadow: 0 8px 24px rgba(0,0,0,.25); }
.toast.ok { background: rgba(43,162,69,.95); }
.toast.warn { background: rgba(224,69,60,.95); }
.toast.info { background: rgba(26,26,26,.9); }
.toast-enter-active, .toast-leave-active { transition: all .25s ease; }
.toast-enter-from { opacity: 0; transform: translateY(-12px); }
.toast-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
