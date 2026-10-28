<!-- 文件路径: frontend/src/components/DeepSeekChat.vue -->
<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import {
  deepSeekChat, deepSeekStatus,
  deepSeekListSessions, deepSeekCreateSession,
  deepSeekGetSession, deepSeekSaveSession, deepSeekDeleteSession
} from '../api/index.js'
import { renderMarkdown } from '../utils/markdown.js'
const emit = defineEmits(['error'])

const ACTIVE_KEY = 'deepseek.activeSession'

const configured = ref(false)
const defaultModel = ref('deepseek-chat')
const model = ref('deepseek-chat')
const temperature = ref(0.7)
const maxTokens = ref(1024)

const sessions = ref([])
const activeId = ref('')
const messages = ref([])
const input = ref('')
const sending = ref(false)
const loadingSession = ref(false)
const scrollBox = ref(null)

const modelOptions = [
  { value: 'deepseek-chat', label: 'DeepSeek Chat (通用对话)' },
  { value: 'deepseek-reasoner', label: 'DeepSeek Reasoner (逻辑推理)' }
]

// 当前会话累计 token：累加每条 assistant 消息 usage.total_tokens
const sessionTotalTokens = computed(() =>
    messages.value.reduce((sum, m) => sum + (m.usage && m.usage.total_tokens ? m.usage.total_tokens : 0), 0)
)

async function loadStatus() {
  try {
    const json = await deepSeekStatus()
    if (json.success && json.data) {
      configured.value = !!json.data.configured
      defaultModel.value = json.data.model || 'deepseek-chat'
      model.value = defaultModel.value
    }
  } catch (e) { /* silent */ }
}

async function loadSessions() {
  try {
    const json = await deepSeekListSessions()
    if (json.success) sessions.value = json.data || []
  } catch (e) {
    emit('error', '会话列表加载失败：' + e.message)
  }
}

async function openSession(id) {
  if (!id || sending.value) return
  activeId.value = id
  localStorage.setItem(ACTIVE_KEY, id)
  loadingSession.value = true
  try {
    const json = await deepSeekGetSession(id)
    messages.value = (json.success && json.data) ? json.data : []
  } catch (e) {
    messages.value = []
    emit('error', '会话内容加载失败：' + e.message)
  } finally {
    loadingSession.value = false
    scrollToBottom()
  }
}

async function newSession() {
  if (sending.value) return
  try {
    const json = await deepSeekCreateSession()
    if (json.success && json.data) {
      sessions.value.unshift(json.data)
      await openSession(json.data.id)
    } else {
      emit('error', json.message || '新建会话失败')
    }
  } catch (e) {
    emit('error', '新建会话失败：' + e.message)
  }
}

async function removeSession(id) {
  if (sending.value) return
  if (!confirm('确定删除该会话？删除后不可恢复。')) return
  try {
    const json = await deepSeekDeleteSession(id)
    if (json.success) {
      sessions.value = sessions.value.filter(s => s.id !== id)
      if (activeId.value === id) {
        if (sessions.value.length > 0) {
          await openSession(sessions.value[0].id)
        } else {
          await newSession()
        }
      }
    } else {
      emit('error', json.message || '删除会话失败')
    }
  } catch (e) {
    emit('error', '删除会话失败：' + e.message)
  }
}

async function scrollToBottom() {
  await nextTick()
  if (scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight
}

async function persist() {
  if (!activeId.value) return
  const snapshot = messages.value
      .filter(m => !m.pending)
      .map(m => ({
        role: m.role,
        content: m.content,
        reasoning: m.reasoning || undefined,
        usage: m.usage || undefined,
        error: m.error || undefined
      }))
  try {
    const json = await deepSeekSaveSession(activeId.value, snapshot, sessionTotalTokens.value)
    if (json.success && json.data) {
      const idx = sessions.value.findIndex(s => s.id === activeId.value)
      if (idx >= 0) sessions.value[idx] = { ...sessions.value[idx], ...json.data }
    }
  } catch (e) { /* 持久化失败不阻断聊天 */ }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!configured.value) {
    emit('error', 'DeepSeek 未配置，请先在服务器 .env 设置 DEEPSEEK_API_KEY')
    return
  }
  if (!activeId.value) {
    await newSession()
  }

  messages.value.push({ role: 'user', content: text, pending: false })
  input.value = ''
  await scrollToBottom()

  const history = messages.value
      .filter(m => !m.pending && !m.error)
      .map(m => ({ role: m.role, content: m.content }))

  sending.value = true
  const aiMsg = { role: 'assistant', content: '', reasoning: '', pending: true, usage: null }
  messages.value.push(aiMsg)
  try {
    const json = await deepSeekChat(history, {
      model: model.value,
      temperature: Number(temperature.value),
      maxTokens: Number(maxTokens.value)
    })
    if (json.success && json.data) {
      aiMsg.content = json.data.content || ''
      aiMsg.reasoning = json.data.reasoningContent || ''
      aiMsg.usage = json.data.usage || null
    } else {
      aiMsg.error = json.message || '调用失败'
    }
  } catch (e) {
    aiMsg.error = '请求失败：' + e.message
  } finally {
    aiMsg.pending = false
    sending.value = false
    await scrollToBottom()
    await persist()
  }
}

function onEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  send()
}

onMounted(async () => {
  await loadStatus()
  await loadSessions()
  const saved = localStorage.getItem(ACTIVE_KEY)
  if (saved && sessions.value.some(s => s.id === saved)) {
    await openSession(saved)
  } else if (sessions.value.length > 0) {
    await openSession(sessions.value[0].id)
  } else {
    await newSession()
  }
})
</script>

<template>
  <div class="ds-page">
    <div class="page-header">
      <h2>DeepSeek 智能助手</h2>
      <div class="page-header-sub">基于 OpenAI 兼容接口 · 由后端统一代理调用 · 对话已持久化</div>
    </div>

    <div v-if="!configured" class="ds-warn">
      未检测到 DeepSeek API Key，请运维在服务器 .env 文件中配置 DEEPSEEK_API_KEY 后重启后端。
    </div>

    <div class="ds-layout">
      <aside class="ds-sider">
        <button class="ds-btn ds-btn-primary ds-new-btn" @click="newSession" :disabled="sending">新建对话</button>
        <div class="ds-session-list">
          <div v-if="sessions.length === 0" class="ds-session-empty">暂无会话</div>
          <div v-for="s in sessions" :key="s.id" class="ds-session-item"
               :class="{ active: s.id === activeId }" @click="openSession(s.id)">
            <div class="ds-session-main">
              <div class="ds-session-title">{{ s.title || '新对话' }}</div>
              <div class="ds-session-meta">{{ s.messageCount || 0 }} 条 · {{ s.totalTokens || 0 }} tok</div>
            </div>
            <button class="ds-session-del" @click.stop="removeSession(s.id)" :disabled="sending">×</button>
          </div>
        </div>
      </aside>

      <section class="ds-main">
        <div class="ds-toolbar">
          <div class="ds-field">
            <label class="ds-label">模型</label>
            <select v-model="model" class="ds-select" :disabled="sending">
              <option v-for="opt in modelOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="ds-field">
            <label class="ds-label">温度</label>
            <input v-model.number="temperature" type="number" min="0" max="2" step="0.1" class="ds-input" :disabled="sending" />
          </div>
          <div class="ds-field">
            <label class="ds-label">最大Tokens</label>
            <input v-model.number="maxTokens" type="number" min="1" max="8192" step="1" class="ds-input" :disabled="sending" />
          </div>
          <div class="ds-token-badge">
            <span class="ds-token-label">当前会话累计 Token</span>
            <span class="ds-token-value">{{ sessionTotalTokens }}</span>
          </div>
        </div>

        <div ref="scrollBox" class="ds-messages">
          <div v-if="loadingSession" class="ds-empty">
            <div class="ds-spinner"></div>
            <div class="ds-empty-hint">正在加载会话...</div>
          </div>
          <div v-else-if="messages.length === 0" class="ds-empty">
            <div class="ds-empty-title">开始与 DeepSeek 对话</div>
            <div class="ds-empty-hint">在下方输入问题，按 Enter 发送，Shift+Enter 换行</div>
          </div>

          <div v-for="(m, i) in messages" :key="i" class="ds-msg" :class="m.role === 'user' ? 'ds-msg-user' : 'ds-msg-ai'">
            <div class="ds-msg-role">{{ m.role === 'user' ? '我' : 'DeepSeek' }}</div>

            <div v-if="m.reasoning" class="ds-reasoning">
              <div class="ds-reasoning-title">推理过程</div>
              <div class="ds-reasoning-text">{{ m.reasoning }}</div>
            </div>

            <div v-if="m.pending" class="ds-thinking">
              <span class="ds-spinner"></span> 正在生成回复...
            </div>
            <div v-else-if="m.error" class="ds-error-text">{{ m.error }}</div>
            <div v-else-if="m.role === 'assistant'" class="ds-msg-content ds-md" v-html="renderMarkdown(m.content)"></div>
            <div v-else class="ds-msg-content">{{ m.content }}</div>

            <div v-if="m.usage" class="ds-usage">
              tokens: 输入 {{ m.usage.prompt_tokens }} · 输出 {{ m.usage.completion_tokens }} · 合计 {{ m.usage.total_tokens }}
            </div>
          </div>
        </div>

        <div class="ds-input-bar">
          <textarea
              v-model="input"
              class="ds-textarea"
              rows="3"
              placeholder="请输入内容..."
              :disabled="sending"
              @keydown.enter="onEnter"
          ></textarea>
          <button class="ds-btn ds-btn-primary" @click="send" :disabled="sending || !input.trim() || !activeId">
            {{ sending ? '发送中...' : '发送' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ds-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  box-sizing: border-box;
}
.page-header { margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 20px; }
.page-header-sub { color: #888; font-size: 13px; margin-top: 4px; }

.ds-warn {
  background: #fff7e6;
  border: 1px solid #ffd591;
  color: #ad6800;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 12px;
}

.ds-layout { display: flex; gap: 14px; flex: 1; min-height: 0; }

.ds-sider {
  width: 220px;
  flex-shrink: 0;
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.ds-new-btn { width: 100%; height: 36px; margin-bottom: 10px; }
.ds-session-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.ds-session-empty { color: #aaa; font-size: 13px; text-align: center; margin-top: 20px; }
.ds-session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
}

/* ===== Markdown 渲染样式（v-html 内容需用 :deep 穿透 scoped） ===== */
.ds-md { white-space: normal; line-height: 1.7; }
.ds-md :deep(> :first-child) { margin-top: 0; }
.ds-md :deep(> :last-child) { margin-bottom: 0; }
.ds-md :deep(p) { margin: 6px 0; }
.ds-md :deep(h1), .ds-md :deep(h2), .ds-md :deep(h3),
.ds-md :deep(h4), .ds-md :deep(h5), .ds-md :deep(h6) {
  margin: 12px 0 8px;
  font-weight: 700;
  line-height: 1.4;
}
.ds-md :deep(h1) { font-size: 20px; }
.ds-md :deep(h2) { font-size: 18px; }
.ds-md :deep(h3) { font-size: 16px; }
.ds-md :deep(h4) { font-size: 15px; }
.ds-md :deep(ul), .ds-md :deep(ol) { margin: 6px 0; padding-left: 22px; }
.ds-md :deep(li) { margin: 3px 0; }
.ds-md :deep(li::marker) { color: #4c8dff; }
.ds-md :deep(strong) { font-weight: 700; color: #1a1a2e; }
.ds-md :deep(em) { font-style: italic; }
.ds-md :deep(a) { color: #4c8dff; text-decoration: none; }
.ds-md :deep(a:hover) { text-decoration: underline; }
.ds-md :deep(hr) { border: none; border-top: 1px solid #eee; margin: 12px 0; }
.ds-md :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 14px;
  color: #555;
  background: #f7f9fc;
  border-left: 3px solid #b7c8ff;
  border-radius: 4px;
}
.ds-md :deep(code) {
  background: #eef0f3;
  color: #c7254e;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.ds-md :deep(pre) {
  background: #282c34;
  padding: 12px 14px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
.ds-md :deep(pre code) {
  background: transparent;
  color: #e6e6e6;
  padding: 0;
  font-size: 13px;
  line-height: 1.5;
}
.ds-md :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 13px;
}
.ds-md :deep(th), .ds-md :deep(td) {
  border: 1px solid #e0e0e0;
  padding: 6px 10px;
  text-align: left;
}
.ds-md :deep(th) { background: #f5f7fa; font-weight: 600; }

.ds-session-item:hover { background: #eef2ff; }
.ds-session-item.active { background: #e6f4ff; border-color: #91caff; }
.ds-session-main { flex: 1; overflow: hidden; }
.ds-session-title {
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ds-session-meta { font-size: 11px; color: #999; margin-top: 2px; }
.ds-session-del {
  border: none;
  background: transparent;
  color: #bbb;
  font-size: 16px;
  cursor: pointer;
  width: 20px;
  line-height: 1;
}
.ds-session-del:hover { color: #cf1322; }

.ds-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.ds-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.ds-field { display: flex; flex-direction: column; gap: 4px; }
.ds-label { font-size: 12px; color: #666; }
.ds-select, .ds-input {
  height: 32px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 0 8px;
  font-size: 13px;
  background: #fff;
}
.ds-input { width: 100px; }

.ds-token-badge {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  background: #f0f5ff;
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  padding: 6px 12px;
}
.ds-token-label { font-size: 11px; color: #4c8dff; }
.ds-token-value { font-size: 18px; font-weight: 700; color: #2f54eb; }

.ds-messages {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ds-empty { margin: auto; text-align: center; color: #999; }
.ds-empty-title { font-size: 16px; margin-bottom: 6px; }
.ds-empty-hint { font-size: 13px; }

.ds-msg { max-width: 80%; padding: 10px 14px; border-radius: 10px; }
.ds-msg-user { align-self: flex-end; background: #e6f4ff; }
.ds-msg-ai { align-self: flex-start; background: #f5f5f5; }
.ds-msg-role { font-size: 12px; color: #888; margin-bottom: 6px; }
.ds-msg-content { white-space: pre-wrap; word-break: break-word; font-size: 14px; line-height: 1.6; }

.ds-reasoning {
  background: #f0f5ff;
  border-left: 3px solid #4c8dff;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.ds-reasoning-title { font-size: 12px; color: #4c8dff; margin-bottom: 4px; }
.ds-reasoning-text { font-size: 13px; color: #555; white-space: pre-wrap; }

.ds-error-text { color: #cf1322; font-size: 14px; }
.ds-usage { margin-top: 6px; font-size: 12px; color: #999; }

.ds-thinking { display: flex; align-items: center; gap: 8px; color: #888; font-size: 14px; }
.ds-spinner {
  width: 14px; height: 14px;
  border: 2px solid #d9d9d9;
  border-top-color: #4c8dff;
  border-radius: 50%;
  animation: ds-spin 0.8s linear infinite;
  display: inline-block;
}
@keyframes ds-spin { to { transform: rotate(360deg); } }

.ds-input-bar { display: flex; gap: 12px; margin-top: 12px; align-items: flex-end; }
.ds-textarea {
  flex: 1;
  resize: none;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  box-sizing: border-box;
}
.ds-btn {
  height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
}
.ds-btn-primary { background: #4c8dff; color: #fff; }
.ds-btn-primary:disabled { background: #b4cdff; cursor: not-allowed; }
</style>