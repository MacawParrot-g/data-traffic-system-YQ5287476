<!-- 文件路径: C:\Users\EDY\data-traffic-system-YQ5287476\frontend\src\components\UrlPreviewPanel.vue -->
<script setup>
import { ref, computed } from 'vue'
import { fetchUrlContent } from '../api/index.js'

const MIN_VIEW_H = 360

const viewMode = ref('live')          // live=代理直连可操作，snapshot=静态快照兜底
const urlInput = ref('')
const targetUrl = ref('')
const loading = ref(false)
const snap = ref(null)
const errText = ref('')

const frameEl = ref(null)
const frameReady = ref(false)

const sel = ref('')
const opAction = ref('click')
const opValue = ref('')
const opMsg = ref('')
const picking = ref(false)
let pickDoc = null

// 预览区高度：跟随视口自适应，可在下限/上限之间由用户拖动微调
const viewH = ref('')
const bodyStyle = computed(() => (viewH.value ? { height: viewH.value } : {}))

const isLive = computed(() => viewMode.value === 'live')
const proxySrc = computed(() => '/api/url-proxy/' + targetUrl.value)
const noteText = computed(() => errText.value || opMsg.value)
const modeBadge = computed(() => {
  if (isLive.value) return targetUrl.value ? (frameReady.value ? '可操作 · 已加载' : '可操作 · 加载中') : '可操作'
  if (snap.value) return snap.value.mode === 'json' ? 'JSON' : snap.value.mode === 'html' ? '快照' : '文本'
  return '快照'
})
const prettyJson = computed(() => {
  if (snap.value && snap.value.mode === 'json' && snap.value.json != null) {
    return JSON.stringify(snap.value.json, null, 2)
  }
  return ''
})

function switchMode(m) {
  viewMode.value = m
  errText.value = ''
  opMsg.value = ''
}

function validUrl(u) {
  if (!/^https?:\/\/\S+\.\S+/i.test(u)) {
    errText.value = '请输入合法的 http/https 地址'
    return false
  }
  return true
}

function go() {
  const u = urlInput.value.trim()
  if (!validUrl(u)) return
  errText.value = ''
  opMsg.value = ''
  if (isLive.value) {
    frameReady.value = false
    targetUrl.value = u
    return
  }
  loadSnapshot(u)
}

async function loadSnapshot(u) {
  loading.value = true
  snap.value = null
  try {
    const json = await fetchUrlContent(u)
    if (json.success && json.data) snap.value = json.data
    else errText.value = json.message || json.resultMsg || 'URL 抓取失败'
  } catch (e) {
    errText.value = '请求失败：' + e.message
  } finally {
    loading.value = false
  }
}

// ==================== iframe 内 DOM 操作 ====================

function frameDoc() {
  const f = frameEl.value
  if (!f) return null
  try {
    return f.contentDocument || (f.contentWindow && f.contentWindow.document) || null
  } catch (e) {
    return null
  }
}

function onFrameLoad() {
  const doc = frameDoc()
  if (!doc || !doc.body) {
    opMsg.value = '页面无法以同域方式加载（站点限制或尚未返回），可切换"快照"模式查看静态内容'
    frameReady.value = false
    return
  }
  frameReady.value = true
}

function runOp() {
  const doc = frameDoc()
  if (!doc) { opMsg.value = '页面未就绪，无法操作'; return }
  const selector = sel.value.trim()
  if (!selector) { opMsg.value = '请输入 CSS 选择器，或先"拾取元素"'; return }
  let el = null
  try {
    el = doc.querySelector(selector)
  } catch (e) {
    opMsg.value = '选择器语法错误：' + e.message
    return
  }
  if (!el) { opMsg.value = '未找到元素：' + selector; return }
  try {
    if (opAction.value === 'click') {
      el.click()
      opMsg.value = '已点击：' + selector
    } else if (opAction.value === 'focus') {
      el.focus()
      opMsg.value = '已聚焦：' + selector
    } else if (opAction.value === 'type') {
      if (!('value' in el)) { opMsg.value = '该元素不支持填入文本'; return }
      el.focus()
      el.value = opValue.value
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
      opMsg.value = '已填入文本：' + selector
    } else if (opAction.value === 'read') {
      const raw = (el.value || (el.textContent || '')).trim()
      opMsg.value = '读取内容：' + (raw.length > 200 ? raw.slice(0, 200) + '...' : raw)
    }
  } catch (e) {
    opMsg.value = '操作失败：' + e.message
  }
}

// 拾取模式：捕获 iframe 内点击，自动生成 CSS 选择器回填
function buildSelector(el, doc) {
  if (el.id) return '#' + CSS.escape(el.id)
  const parts = []
  let cur = el
  while (cur && cur !== doc.body) {
    if (cur.id) { parts.unshift('#' + CSS.escape(cur.id)); break }
    let name = cur.tagName.toLowerCase()
    const parent = cur.parentElement
    if (parent) {
      const sibs = Array.from(parent.children).filter(c => c.tagName === cur.tagName)
      if (sibs.length > 1) name += ':nth-of-type(' + (sibs.indexOf(cur) + 1) + ')'
    }
    parts.unshift(name)
    cur = parent
  }
  return parts.join(' > ')
}

function pickHandler(e) {
  e.preventDefault()
  e.stopPropagation()
  const doc = e.view && e.view.document ? e.view.document : frameDoc()
  sel.value = buildSelector(e.target, doc)
  opMsg.value = '已拾取元素：' + sel.value
  stopPick()
}

function togglePick() {
  const doc = frameDoc()
  if (!doc) { opMsg.value = '页面未就绪，无法拾取'; return }
  if (picking.value) { stopPick(); return }
  picking.value = true
  pickDoc = doc
  doc.addEventListener('click', pickHandler, true)
  opMsg.value = '拾取模式已开启，请点击页面中的目标元素'
}

function stopPick() {
  picking.value = false
  if (pickDoc) pickDoc.removeEventListener('click', pickHandler, true)
  pickDoc = null
}

// ==================== 预览区高度微调（可选） ====================

let resizing = false
let startY = 0, startH = 0, startPx = 0

function startResize(e) {
  const body = e.currentTarget.parentElement?.querySelector('.up-body')
  if (!body) return
  resizing = true
  startY = e.clientY
  startH = body.getBoundingClientRect().height
  startPx = Math.round(startH)
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', stopResize)
  e.preventDefault()
}

function onResizeMove(e) {
  if (!resizing) return
  const maxPx = Math.round(window.innerHeight * 0.78)
  const next = Math.max(MIN_VIEW_H, Math.min(Math.round(startPx + (e.clientY - startY)), maxPx))
  viewH.value = next + 'px'
}

function stopResize() {
  if (!resizing) return
  resizing = false
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', stopResize)
}
</script>

<template>
  <div class="up-wrap">
    <header class="up-head">
      <div class="up-head-text">
        <span class="up-kicker">URL SANDBOX / DOM OPERATOR</span>
        <h2 class="up-title">URL 执行器</h2>
        <p class="up-desc">在代理沙箱中打开任意网页，直接点击、输入、拾取 DOM 元素并执行操作；无法嵌入时可切换静态快照兜底。</p>
      </div>
      <div class="up-head-side">
        <span class="up-badge" :class="{ 'is-live': isLive && frameReady }">{{ modeBadge }}</span>
        <div class="up-seg">
          <button :class="{ active: isLive }" @click="switchMode('live')">可操作</button>
          <button :class="{ active: !isLive }" @click="switchMode('snapshot')">快照</button>
        </div>
      </div>
    </header>

    <div class="up-bar">
      <input v-model="urlInput" class="up-input" placeholder="输入 http/https 页面地址，回车加载" :disabled="loading" @keyup.enter="go" />
      <button class="up-btn" :disabled="loading" @click="go">{{ isLive ? '访问' : '抓取' }}</button>
    </div>

    <div v-if="isLive" class="up-bar up-bar-ops">
      <input v-model="sel" class="up-input up-input-sel" placeholder="CSS 选择器，如 #kw 或 input.search" />
      <select v-model="opAction" class="up-select">
        <option value="click">点击</option>
        <option value="focus">聚焦</option>
        <option value="type">填入文本</option>
        <option value="read">读取内容</option>
      </select>
      <input v-if="opAction === 'type'" v-model="opValue" class="up-input up-input-val" placeholder="要输入的文本" @keyup.enter="runOp" />
      <button class="up-btn" :disabled="!frameReady || !sel.trim()" @click="runOp">执行</button>
      <button class="up-btn up-btn-ghost" :class="{ 'is-picking': picking }" @click="togglePick">
        {{ picking ? '取消拾取' : '拾取元素' }}
      </button>
    </div>

    <div v-if="noteText" class="up-note" :class="{ 'is-err': errText }">{{ noteText }}</div>

    <div class="up-body" :style="bodyStyle">
      <template v-if="isLive">
        <iframe v-if="targetUrl" ref="frameEl" class="up-frame" :src="proxySrc"
                title="URL 执行器页面" @load="onFrameLoad"></iframe>
        <div v-else class="up-empty">
          <div class="up-empty-title">可操作模式</div>
          <div class="up-empty-hint">输入地址并点击"访问"后，页面即可直接点击、输入；<br>也可用上方"拾取元素 + 执行"对任意 DOM 进行操作</div>
        </div>
      </template>
      <template v-else>
        <div v-if="loading" class="up-empty">抓取中...</div>
        <div v-else-if="!snap" class="up-empty">输入 URL 后点击"抓取"，网页以静态快照展示，JSON 会格式化展示</div>
        <iframe v-else-if="snap.mode === 'html'" class="up-frame" :srcdoc="snap.text" sandbox="" title="URL 快照预览"></iframe>
        <pre v-else-if="snap.mode === 'json'" class="up-pre">{{ prettyJson }}</pre>
        <pre v-else class="up-pre">{{ snap.text }}</pre>
      </template>
    </div>

    <div class="up-resize" title="拖动调整预览区高度" @pointerdown="startResize"></div>
  </div>
</template>

<style scoped>
/* 该组件作为普通标签页嵌入 App.vue 的 .content-area，随内容流铺满，不再使用 position:fixed 浮窗 */
.up-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100vh - 172px);
}

.up-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.up-head-text { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.up-kicker { font-family: var(--font-mono); font-size: 9px; font-weight: 800; letter-spacing: 1.7px; color: #9a9388; }
.up-title { font-size: 19px; line-height: 1.3; font-weight: 700; color: var(--text-primary); letter-spacing: -0.35px; }
.up-desc { margin: 0; font-size: 12px; line-height: 1.6; color: var(--text-secondary); max-width: 640px; }
.up-head-side { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }

.up-badge { font-family: var(--font-mono); font-size: 9px; color: var(--text-secondary); background: #f6f1e7; border: 1px solid #eaddca; border-radius: 999px; padding: 3px 9px; white-space: nowrap; }
.up-badge.is-live { color: var(--success); border-color: #cde7df; background: var(--success-soft); }
.up-seg { display: inline-flex; padding: 2px; gap: 2px; background: #f3ede1; border-radius: 9px; }
.up-seg button { border: none; background: transparent; font-size: 11px; font-weight: 600; color: var(--text-secondary); padding: 5px 12px; border-radius: 7px; cursor: pointer; transition: var(--transition, 180ms ease); }
.up-seg button.active { background: #fff; color: var(--accent-strong); box-shadow: 0 1px 4px rgba(70, 55, 35, 0.12); }

.up-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.up-bar-ops { padding: 10px 12px; background: #fbf7ef; border: 1px solid #f0e8db; border-radius: 12px; }
.up-input { flex: 1; min-width: 180px; height: 34px; border: 1px solid #e0ddd9; border-radius: 8px; padding: 0 11px; font-size: 12px; outline: none; background: #fff; color: var(--text-primary); }
.up-input-sel { max-width: 260px; flex: 0 1 260px; font-family: var(--font-mono); font-size: 11px; }
.up-input-val { max-width: 180px; flex: 0 1 180px; font-size: 11px; }
.up-input:focus { border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow, rgba(201, 106, 69, 0.13)); }
.up-select { height: 34px; border: 1px solid #e0ddd9; border-radius: 8px; padding: 0 6px; font-size: 11px; background: #fff; color: var(--text-primary); outline: none; cursor: pointer; flex-shrink: 0; }
.up-btn { height: 34px; padding: 0 14px; border-radius: 8px; border: 1px solid rgba(201, 106, 69, 0.35); background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; font-size: 12px; font-weight: 600; cursor: pointer; flex-shrink: 0; transition: var(--transition, 180ms ease); }
.up-btn:hover:not(:disabled) { transform: translateY(-1px); }
.up-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.up-btn-ghost { background: #fff; color: var(--accent-strong); border-color: #e0ddd9; }
.up-btn-ghost:hover:not(:disabled) { background: var(--accent-soft); }
.up-btn-ghost.is-picking { border-color: var(--accent); background: var(--accent-soft); box-shadow: inset 0 0 0 1px var(--accent-light); }

.up-note { padding: 8px 12px; font-size: 11px; line-height: 1.6; border-radius: 9px; color: #8c5c17; background: var(--warning-soft); border: 1px solid #eed9b7; overflow-wrap: anywhere; }
.up-note.is-err { color: #a84545; background: var(--danger-soft); border-color: #f1d5d2; }

.up-body { position: relative; flex: 1; min-height: 360px; overflow: auto; background: #fff; border: 1px solid #ece3d5; border-radius: 14px; overscroll-behavior: contain; box-shadow: var(--shadow-sm, 0 8px 24px rgba(45, 35, 22, 0.06)); }
.up-empty { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; color: var(--text-secondary); font-size: 13px; line-height: 1.8; padding: 24px; }
.up-empty-title { font-size: 15px; font-weight: 700; color: var(--text-primary); margin-bottom: 6px; }
.up-frame { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; background: #fff; }
.up-pre { margin: 0; padding: 16px; font-family: var(--font-mono); font-size: 12px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--text-primary); user-select: text; }

.up-resize { height: 10px; margin-top: -6px; cursor: ns-resize; border-radius: 6px; touch-action: none; user-select: none; }
.up-resize:hover { background: rgba(201, 106, 69, 0.10); }
</style>
