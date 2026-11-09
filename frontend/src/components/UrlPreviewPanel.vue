<!-- 文件路径: frontend/src/components/UrlPreviewPanel.vue -->
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { fetchUrlContent } from '../api/index.js'

const emit = defineEmits(['close'])

const SIZE_KEY = 'urlpreview.panel.size'
const MIN_W = 380
const MIN_H = 260
const EDGE_GAP = 24

const urlInput = ref('')
const loading = ref(false)
const result = ref(null)
const errText = ref('')
const size = ref({ w: 560, h: 420 })

const panelStyle = computed(() => ({
  width: size.value.w + 'px',
  height: size.value.h + 'px'
}))

const modeLabel = computed(() => {
  if (!result.value) return ''
  if (result.value.mode === 'json') return 'JSON'
  if (result.value.mode === 'html') return '网页'
  return '文本'
})

const prettyJson = computed(() => {
  if (result.value && result.value.mode === 'json' && result.value.json != null) {
    return JSON.stringify(result.value.json, null, 2)
  }
  return ''
})

function clamp(w, h) {
  const maxW = Math.max(MIN_W, window.innerWidth - EDGE_GAP * 2)
  const maxH = Math.max(MIN_H, window.innerHeight - EDGE_GAP * 2)
  return {
    w: Math.max(MIN_W, Math.min(Math.round(w), maxW)),
    h: Math.max(MIN_H, Math.min(Math.round(h), maxH))
  }
}

// 独立缩放：pointer 拖拽 e / s / se 三个方向，尺寸落盘 localStorage
let resizeDir = ''
let startX = 0, startY = 0, startW = 0, startH = 0

function startResize(e, dir) {
  resizeDir = dir
  startX = e.clientX
  startY = e.clientY
  startW = size.value.w
  startH = size.value.h
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', stopResize)
  e.preventDefault()
}

function onResizeMove(e) {
  if (!resizeDir) return
  const dx = e.clientX - startX
  const dy = e.clientY - startY
  const w = resizeDir.includes('e') ? startW + dx : startW
  const h = resizeDir.includes('s') ? startH + dy : startH
  size.value = clamp(w, h)
}

function stopResize() {
  if (!resizeDir) return
  resizeDir = ''
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', stopResize)
  try { localStorage.setItem(SIZE_KEY, JSON.stringify(size.value)) } catch (err) { /* 忽略 */ }
}

async function load() {
  const u = urlInput.value.trim()
  if (!u) { errText.value = '请先输入 URL'; return }
  if (!/^https?:\/\//i.test(u)) { errText.value = '仅支持 http/https 协议'; return }
  loading.value = true
  errText.value = ''
  result.value = null
  try {
    const json = await fetchUrlContent(u)
    if (json.success && json.data) {
      result.value = json.data
    } else {
      errText.value = json.message || json.resultMsg || 'URL 抓取失败'
    }
  } catch (e) {
    errText.value = '请求失败：' + e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  try {
    const saved = JSON.parse(localStorage.getItem(SIZE_KEY) || '')
    if (saved && Number.isFinite(saved.w) && Number.isFinite(saved.h)) size.value = clamp(saved.w, saved.h)
  } catch (e) { /* 使用默认尺寸 */ }
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', stopResize)
})
</script>

<template>
  <div class="up-panel" :style="panelStyle">
    <div class="up-header">
      <span class="up-title">URL 预览</span>
      <span v-if="result" class="up-badge">{{ modeLabel }} · {{ result.httpStatus }}</span>
      <button class="up-close" @click="emit('close')" aria-label="关闭预览窗口">
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m6 6 8 8m0-8-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
      </button>
    </div>
    <div class="up-toolbar">
      <input v-model="urlInput" class="up-input" placeholder="输入 http/https 地址，回车抓取" :disabled="loading" @keyup.enter="load" />
      <button class="up-btn" :disabled="loading" @click="load">{{ loading ? '抓取中' : '抓取' }}</button>
    </div>
    <div v-if="result && result.truncated" class="up-trunc">内容超过 2MB，已截断展示</div>
    <div class="up-body">
      <div v-if="errText" class="up-error">{{ errText }}</div>
      <div v-else-if="loading" class="up-empty">正在抓取...</div>
      <div v-else-if="!result" class="up-empty">输入 URL 后点击"抓取"，网页将以静态快照展示，JSON 将格式化展示</div>
      <iframe v-else-if="result.mode === 'html'" class="up-frame" :srcdoc="result.text" sandbox="" referrerpolicy="no-referrer" title="URL 网页预览"></iframe>
      <pre v-else-if="result.mode === 'json'" class="up-pre">{{ prettyJson }}</pre>
      <pre v-else class="up-pre">{{ result.text }}</pre>
    </div>
    <div class="up-handle up-handle-e" @pointerdown="startResize($event, 'e')"></div>
    <div class="up-handle up-handle-s" @pointerdown="startResize($event, 's')"></div>
    <div class="up-handle up-handle-se" @pointerdown="startResize($event, 'se')"></div>
  </div>
</template>

<style scoped>
.up-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  border: 1px solid #e0d5c2;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 16px 40px rgba(70, 55, 35, 0.18);
  overflow: hidden;
}
.up-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-bottom: 1px solid #f0e8db; flex-shrink: 0; }
.up-title { font-size: 12px; font-weight: 700; color: var(--accent-strong); }
.up-badge { margin-left: auto; font-family: var(--font-mono); font-size: 9px; color: var(--text-secondary); background: #f6f1e7; border-radius: 6px; padding: 2px 6px; white-space: nowrap; }
.up-close { width: 22px; height: 22px; border: none; background: transparent; color: #b1aea9; cursor: pointer; display: grid; place-items: center; border-radius: 6px; padding: 0; flex: 0 0 22px; }
.up-close svg { width: 12px; height: 12px; }
.up-close:hover { color: var(--danger); background: var(--danger-soft); }
.up-toolbar { display: flex; gap: 8px; padding: 8px 12px; border-bottom: 1px solid #f0e8db; flex-shrink: 0; }
.up-input { flex: 1; min-width: 0; height: 30px; border: 1px solid #e0ddd9; border-radius: 8px; padding: 0 9px; font-size: 11px; outline: none; background: rgba(255, 255, 255, 0.9); color: var(--text-primary); box-sizing: border-box; }
.up-input:focus { border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow, rgba(201, 106, 69, 0.13)); }
.up-btn { height: 30px; padding: 0 12px; border-radius: 8px; border: 1px solid rgba(201, 106, 69, 0.35); background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; font-size: 11px; cursor: pointer; flex-shrink: 0; }
.up-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.up-trunc { padding: 5px 12px; font-size: 10px; color: #8c5c17; background: var(--warning-soft); border-bottom: 1px solid #eed9b7; flex-shrink: 0; }
.up-body { position: relative; flex: 1; min-height: 0; overflow: auto; background: #fbf9f4; overscroll-behavior: contain; }
.up-empty, .up-error { padding: 24px 16px; font-size: 12px; color: var(--text-secondary); text-align: center; line-height: 1.7; }
.up-error { color: var(--danger); }
.up-frame { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; background: #fff; }
.up-pre { margin: 0; padding: 12px; font-family: var(--font-mono); font-size: 11px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--text-primary); user-select: text; }
.up-handle { position: absolute; z-index: 3; user-select: none; touch-action: none; }
.up-handle-e { top: 0; right: 0; width: 5px; height: 100%; cursor: ew-resize; }
.up-handle-s { left: 0; right: 0; bottom: 0; height: 5px; cursor: ns-resize; }
.up-handle-se { right: 0; bottom: 0; width: 16px; height: 16px; cursor: nwse-resize; }
.up-handle-se::after { content: ''; position: absolute; right: 3px; bottom: 3px; width: 8px; height: 8px; border-right: 2px solid #cbb896; border-bottom: 2px solid #cbb896; border-radius: 0 0 3px 0; }
</style>