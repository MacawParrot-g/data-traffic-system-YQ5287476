<!-- 文件路径: frontend/src/components/DeepSeekChat.vue -->
<script setup>
import { ref, computed, onMounted, onUnmounted,nextTick } from 'vue'
import {
  deepSeekChat, deepSeekStatus, deepSeekBalance,
  deepSeekListSessions, deepSeekCreateSession,
  deepSeekGetSession, deepSeekSaveSession, deepSeekDeleteSession,
  deepSeekSetMessageTtl
} from '../api/index.js'
import { renderMarkdown } from '../utils/markdown.js'
const emit = defineEmits(['error'])

const ACTIVE_KEY = 'deepseek.activeSession'

const BALANCE_REFRESH_INTERVAL = 60000

const configured = ref(false)
const defaultModel = ref('deepseek-chat')
const model = ref('deepseek-chat')
const temperature = ref(0.7)
const maxTokens = ref(1024)
let balanceTimer = null
const sessions = ref([])
const activeId = ref('')
const messages = ref([])
const input = ref('')
const sending = ref(false)
const loadingSession = ref(false)
const scrollBox = ref(null)
const balance = ref(null)
const balanceLoading = ref(false)
const balanceError = ref('')
const modelOptions = [
  { value: 'deepseek-chat', label: 'DeepSeek Chat (通用对话)' },
  { value: 'deepseek-reasoner', label: 'DeepSeek Reasoner (逻辑推理)' }
]

// 当前会话累计 token：累加每条 assistant 消息 usage.total_tokens
const sessionTotalTokens = computed(() =>
    messages.value.reduce((sum, m) => sum + (m.usage && m.usage.total_tokens ? m.usage.total_tokens : 0), 0)
)

function formatTime(ts) {
  if (!ts) return '未获取'
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}

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

async function loadBalance(forceRefresh = false) {
  if (balanceLoading.value) return
  balanceLoading.value = true
  try {
    const json = await deepSeekBalance(forceRefresh)
    if (json.success && json.data) {
      balance.value = json.data
      balanceError.value = ''
    } else {
      balanceError.value = json.message || '余额查询失败'
    }
  } catch (e) {
    balanceError.value = '余额查询失败：' + e.message
  } finally {
    balanceLoading.value = false
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


function formatAmount(val) {
  const num = Number(val)
  return Number.isFinite(num) ? num.toFixed(2) : '--'
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
        error: m.error || undefined,
        expiresAt: m.expiresAt || undefined
      }))
  try {
    const json = await deepSeekSaveSession(activeId.value, snapshot, sessionTotalTokens.value)
    if (json.success && json.data) {
      const idx = sessions.value.findIndex(s => s.id === activeId.value)
      if (idx >= 0) sessions.value[idx] = { ...sessions.value[idx], ...json.data }
    }
  } catch (e) { /* 持久化失败不阻断聊天 */ }
}

async function onTtlChange(index, e) {
  const val = e.target.value
  const ttlSeconds = val ? Number(val) : 0
  try {
    const json = await deepSeekSetMessageTtl(activeId.value, index, ttlSeconds)
    if (json.success) {
      if (ttlSeconds > 0) {
        messages.value[index].expiresAt = Date.now() + ttlSeconds * 1000
      } else {
        delete messages.value[index].expiresAt
      }
    } else {
      emit('error', json.message || '设置过期时间失败')
    }
  } catch (err) {
    emit('error', '设置过期时间失败：' + err.message)
  }
}

function formatExpire(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
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
    loadBalance(false)
  }
}

function onEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  send()
}

onMounted(async () => {
  await loadStatus()
  loadBalance(true)
  balanceTimer = setInterval(() => loadBalance(false), BALANCE_REFRESH_INTERVAL)
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

onUnmounted(() => {
  if (balanceTimer) {
    clearInterval(balanceTimer)
    balanceTimer = null
  }
})
</script>

<template>
  <div class="ds-page">
    <header class="page-header">
      <div class="ds-heading">
        <span class="ds-eyebrow">AI MAAS / WORKSPACE</span>
        <div class="ds-title-line">
          <h2>DeepSeek 智能助手</h2>
          <span class="ds-service-state" :class="{ 'is-ready': configured }">
            <i></i>{{ configured ? '服务就绪' : '等待配置' }}
          </span>
        </div>
        <div class="page-header-sub">基于 OpenAI 兼容接口 · 由后端统一代理调用 · 对话已持久化</div>
      </div>
      <div class="ds-header-meta" aria-label="接口信息">
        <span>SECURE PROXY</span>
        <strong>DeepSeek API</strong>
      </div>
    </header>

    <div v-if="!configured" class="ds-warn">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v5m0 3.5v.1M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
      <span>未检测到 DeepSeek API Key，请运维在服务器 .env 文件中配置 DEEPSEEK_API_KEY 后重启后端。</span>
    </div>

    <div class="ds-layout">
      <aside class="ds-sider">
        <div class="ds-sider-head">
          <div class="ds-panel-label">
            <span>会话列表</span>
            <small>{{ sessions.length }} 个会话</small>
          </div>
          <button class="ds-btn ds-btn-primary ds-new-btn" @click="newSession" :disabled="sending">
            <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            <span>新建对话</span>
          </button>
        </div>
        <div class="ds-session-list">
          <div v-if="sessions.length === 0" class="ds-session-empty">暂无会话</div>
          <div v-for="s in sessions" :key="s.id" class="ds-session-item"
               :class="{ active: s.id === activeId }" @click="openSession(s.id)">
            <div class="ds-session-main">
              <div class="ds-session-title">{{ s.title || '新对话' }}</div>
              <div class="ds-session-meta">{{ s.messageCount || 0 }} 条 · {{ s.totalTokens || 0 }} tok</div>
            </div>
            <button class="ds-session-del" @click.stop="removeSession(s.id)" :disabled="sending" aria-label="删除会话" title="删除会话">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m6 6 8 8m0-8-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            </button>
          </div>
        </div>
        <div class="ds-sider-foot"><i></i>会话自动保存至当前账户</div>
      </aside>

      <section class="ds-main">
        <div class="ds-toolbar">
          <div class="ds-field ds-field-model">
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
            <label class="ds-label">最大 Tokens</label>
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
            <div class="ds-empty-mark" aria-hidden="true">
              <svg viewBox="0 0 32 32" fill="none"><path d="M7 8.5h18v12H14l-5.5 4v-4H7v-12Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M12 13h8m-8 4h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            </div>
            <div class="ds-empty-kicker">NEW CONVERSATION</div>
            <div class="ds-empty-title">开始与 DeepSeek 对话</div>
            <div class="ds-empty-hint">在下方输入问题，按 Enter 发送，Shift+Enter 换行</div>
          </div>

          <div v-for="(m, i) in messages" :key="i" class="ds-msg" :class="m.role === 'user' ? 'ds-msg-user' : 'ds-msg-ai'">
            <div class="ds-msg-role"><i></i>{{ m.role === 'user' ? '我' : 'DeepSeek' }}</div>

            <div v-if="m.reasoning" class="ds-reasoning">
              <div class="ds-reasoning-title"><span>REASONING</span>推理过程</div>
              <div class="ds-reasoning-text">{{ m.reasoning }}</div>
            </div>

            <div v-if="m.pending" class="ds-thinking">
              <span class="ds-spinner"></span> 正在生成回复...
            </div>
            <div v-else-if="m.error" class="ds-error-text">{{ m.error }}</div>
            <div v-else-if="m.role === 'assistant'" class="ds-msg-content ds-md" v-html="renderMarkdown(m.content)"></div>
            <div v-else class="ds-msg-content">{{ m.content }}</div>

            <div v-if="m.usage" class="ds-usage">
              <span class="ds-usage-name">TOKEN USAGE</span>
              <span>输入 {{ m.usage.prompt_tokens }} · 输出 {{ m.usage.completion_tokens }} · 合计 {{ m.usage.total_tokens }}</span>
            </div>

            <div v-if="m.role === 'assistant' && !m.pending && !m.error" class="ds-msg-actions">
              <label class="ds-ttl-label">本条回复有效期</label>
              <select class="ds-ttl-select" @change="onTtlChange(i, $event)">
                <option value="">永久</option>
                <option value="60">1 分钟</option>
                <option value="300">5 分钟</option>
                <option value="3600">1 小时</option>
                <option value="86400">1 天</option>
                <option value="604800">7 天</option>
              </select>
              <span v-if="m.expiresAt" class="ds-ttl-hint">{{ formatExpire(m.expiresAt) }} 后自动删除</span>
            </div>
          </div>
        </div>

        <div class="ds-composer">
          <div class="ds-input-bar">
            <textarea
                v-model="input"
                class="ds-textarea"
                rows="3"
                placeholder="请输入内容..."
                :disabled="sending"
                @keydown.enter="onEnter"
            ></textarea>
            <button class="ds-btn ds-btn-primary ds-send-btn" @click="send" :disabled="sending || !input.trim() || !activeId">
              <span>{{ sending ? '发送中...' : '发送' }}</span>
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m3 3 14 7-14 7 2.5-7L3 3Zm2.5 7H17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>
          </div>
          <div class="ds-input-meta"><span>Enter 发送</span><span>Shift + Enter 换行</span><span class="ds-input-secure"><i></i>后端安全代理</span></div>
        </div>
      </section>

      <aside class="ds-balance">
        <div class="ds-balance-head">
          <div>
            <span class="ds-balance-kicker">ACCOUNT</span>
            <span class="ds-balance-title">账户余额</span>
          </div>
          <button class="ds-balance-refresh" @click="loadBalance(true)" :disabled="balanceLoading">
            <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M16 7a6.5 6.5 0 1 0 .2 5.5M16 3v4h-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
            {{ balanceLoading ? '查询中' : '刷新' }}
          </button>
        </div>

        <div v-if="balanceError" class="ds-balance-error">{{ balanceError }}</div>
        <div v-else-if="!configured" class="ds-balance-error">未配置 DEEPSEEK_API_KEY，无法查询余额</div>

        <template v-else-if="balance">
          <div class="ds-balance-main">
            <span class="ds-balance-currency">{{ balance.currency === 'USD' ? '$' : '¥' }}</span>
            <span class="ds-balance-amount">{{ formatAmount(balance.totalBalance) }}</span>
          </div>
          <div class="ds-balance-rows">
            <div class="ds-balance-row">
              <span class="ds-balance-k">充值余额</span>
              <span class="ds-balance-v">{{ formatAmount(balance.toppedUpBalance) }}</span>
            </div>
            <div class="ds-balance-row">
              <span class="ds-balance-k">赠送余额</span>
              <span class="ds-balance-v">{{ formatAmount(balance.grantedBalance) }}</span>
            </div>
            <div class="ds-balance-row">
              <span class="ds-balance-k">服务状态</span>
              <span class="ds-balance-v" :class="balance.isAvailable ? 'ds-balance-ok' : 'ds-balance-bad'">
                {{ balance.isAvailable ? '正常' : '已停用' }}
              </span>
            </div>
          </div>
          <div v-if="balance.balanceInfos && balance.balanceInfos.length > 1" class="ds-balance-tip">
            含 {{ balance.balanceInfos.length }} 个币种账户，以上为合计金额
          </div>
        </template>

        <div v-else class="ds-balance-empty">{{ balanceLoading ? '正在查询...' : '暂无余额数据' }}</div>

        <div class="ds-balance-time">
          {{ balance ? '更新于 ' + formatTime(balance.fetchedAt) + (balance.cached ? '（缓存）' : '') : '' }}
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.ds-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: calc(100vh - 154px);
  height: calc(100dvh - 154px);
  min-height: 640px;
  max-height: 980px;
  padding: 2px 2px 8px;
  box-sizing: border-box;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
  padding: 0 2px;
  flex-shrink: 0;
}
.ds-heading { min-width: 0; }
.ds-eyebrow, .ds-empty-kicker, .ds-balance-kicker {
  display: block;
  color: var(--accent);
  font-size: 9px;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: 1.8px;
}
.ds-title-line { display: flex; align-items: center; gap: 12px; margin-top: 7px; flex-wrap: wrap; }
.page-header h2 { margin: 0; font-size: 25px; line-height: 1.25; letter-spacing: -0.7px; font-weight: 750; }
.page-header-sub { color: var(--text-secondary); font-size: 12px; line-height: 1.6; margin-top: 7px; }
.ds-service-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid #e6d6c0;
  border-radius: 999px;
  background: var(--warning-soft);
  color: var(--warning);
  font-size: 10px;
  font-weight: 700;
}
.ds-service-state i, .ds-sider-foot i, .ds-input-secure i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px rgba(197, 122, 22, 0.1);
}
.ds-service-state.is-ready { color: var(--success); border-color: #e2dcd2; background: var(--success-soft); }
.ds-service-state.is-ready i { box-shadow: 0 0 0 3px rgba(23, 140, 104, 0.1); }
.ds-header-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  padding-bottom: 3px;
  white-space: nowrap;
}
.ds-header-meta span { color: var(--text-tertiary, #a99f8c); font-size: 8px; font-weight: 800; letter-spacing: 1.6px; }
.ds-header-meta strong { font-family: var(--font-mono); font-size: 12px; font-weight: 600; color: var(--text-secondary); }

.ds-warn {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-shrink: 0;
  background: rgba(255, 246, 229, 0.9);
  border: 1px solid #eed9b7;
  color: #8c5c17;
  padding: 11px 14px;
  border-radius: var(--radius-sm, 10px);
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.7);
}
.ds-warn svg { width: 18px; height: 18px; flex: 0 0 18px; margin-top: 1px; }

.ds-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 210px;
  gap: 14px;
  flex: 1;
  min-height: 0;
}
.ds-sider, .ds-balance, .ds-main {
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.76);
  box-shadow: var(--shadow-sm);
}
.ds-sider {
  padding: 14px 10px 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ds-sider-head { padding: 0 4px; }
.ds-panel-label { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.ds-panel-label > span { font-size: 12px; font-weight: 700; }
.ds-panel-label small { color: var(--text-tertiary, #a99f8c); font-size: 9px; white-space: nowrap; }
.ds-new-btn { width: 100%; height: 38px; margin-bottom: 13px; justify-content: center; padding: 0 12px; }
.ds-new-btn svg { width: 16px; height: 16px; }
.ds-session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 1px 2px 4px;
}
.ds-session-empty { color: var(--text-tertiary, #a99f8c); font-size: 12px; text-align: center; margin-top: 28px; }
.ds-session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 11px 8px 11px 11px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background var(--transition, 180ms ease), border-color var(--transition, 180ms ease), transform var(--transition, 180ms ease);
}
.ds-session-item::before {
  content: '';
  position: absolute;
  left: -3px;
  top: 13px;
  bottom: 13px;
  width: 2px;
  border-radius: 2px;
  background: transparent;
}
.ds-session-item:hover { background: rgba(231, 246, 242, 0.65); transform: translateX(1px); }
.ds-session-item.active { background: var(--accent-soft); border-color: #e1dbd2; }
.ds-session-item.active::before { background: var(--accent); }
.ds-session-main { flex: 1; min-width: 0; overflow: hidden; }
.ds-session-title { font-size: 12px; color: var(--text-primary); font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ds-session-meta { font-family: var(--font-mono); font-size: 9px; color: var(--text-tertiary, #a99f8c); margin-top: 5px; }
.ds-session-del {
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  color: #b1aea9;
  cursor: pointer;
  width: 25px;
  height: 25px;
  flex: 0 0 25px;
  padding: 5px;
  border-radius: 7px;
  opacity: 0;
  transition: opacity var(--transition, 180ms ease), color var(--transition, 180ms ease), background var(--transition, 180ms ease);
}
.ds-session-del svg { width: 15px; height: 15px; }
.ds-session-item:hover .ds-session-del, .ds-session-del:focus-visible { opacity: 1; }
.ds-session-del:hover { color: var(--danger); background: var(--danger-soft); }
.ds-session-del:disabled { cursor: not-allowed; }
.ds-sider-foot { display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--text-tertiary, #a99f8c); font-size: 9px; padding: 12px 2px 2px; }
.ds-sider-foot i { color: var(--success); box-shadow: none; }

.ds-main {
  display: flex;
  flex-direction: column;
  padding: 12px;
  background: rgba(248, 252, 250, 0.74);
  overflow: hidden;
}
.ds-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
  border: 1px solid rgba(203, 220, 215, 0.85);
  border-radius: 12px;
  padding: 10px 11px;
  margin-bottom: 12px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.95), 0 5px 18px rgba(70,55,35,0.025);
}
.ds-field { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.ds-field-model { flex: 1; min-width: 190px; max-width: 300px; }
.ds-label { font-size: 9px; line-height: 1; color: var(--text-secondary); font-weight: 700; letter-spacing: 0.4px; }
.ds-select, .ds-input, .ds-ttl-select {
  height: 34px;
  border: 1px solid #e0ddd9;
  border-radius: 8px;
  padding: 0 9px;
  font-size: 11px;
  color: var(--text-primary);
  background: rgba(255,255,255,0.88);
  outline: none;
  transition: border-color var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease);
}
.ds-select { width: 100%; min-width: 0; }
.ds-input { width: 86px; box-sizing: border-box; }
.ds-select:focus, .ds-input:focus, .ds-ttl-select:focus { border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow, rgba(201,106,69,0.13)); }
.ds-select:disabled, .ds-input:disabled { opacity: 0.6; cursor: not-allowed; }
.ds-token-badge {
  margin-left: auto;
  min-width: 100px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  border-left: 1px solid var(--border-color);
  padding: 1px 3px 1px 14px;
}
.ds-token-label { font-size: 9px; color: var(--text-secondary); white-space: nowrap; }
.ds-token-value { font-family: var(--font-mono); font-size: 20px; line-height: 1.25; font-weight: 700; color: var(--accent-strong); letter-spacing: -0.6px; }

.ds-messages {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  background-color: rgba(255, 255, 255, 0.86);
  background-image: linear-gradient(rgba(201,106,69,0.018) 1px, transparent 1px), linear-gradient(90deg, rgba(201,106,69,0.018) 1px, transparent 1px);
  background-size: 28px 28px;
  border: 1px solid #eaddca;
  border-radius: 12px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  scroll-behavior: smooth;
}
.ds-empty { margin: auto; text-align: center; color: var(--text-secondary); padding: 32px 16px; }
.ds-empty-mark { width: 54px; height: 54px; display: grid; place-items: center; margin: 0 auto 16px; color: var(--accent); background: var(--accent-soft); border: 1px solid #e5e0d8; border-radius: 17px; box-shadow: inset 0 1px 0 #fff; }
.ds-empty-mark svg { width: 30px; height: 30px; }
.ds-empty-kicker { margin-bottom: 8px; font-size: 8px; }
.ds-empty-title { font-size: 17px; color: var(--text-primary); font-weight: 700; margin-bottom: 7px; }
.ds-empty-hint { font-size: 12px; line-height: 1.7; }
.ds-empty .ds-spinner { margin-bottom: 12px; }

.ds-msg {
  width: fit-content;
  max-width: 86%;
  min-width: 0;
  padding: 14px 16px;
  border-radius: 14px;
  box-sizing: border-box;
  overflow-wrap: anywhere;
  animation: ds-message-in 240ms ease both;
}
.ds-msg-user {
  align-self: flex-end;
  color: #faf6ee;
  background: linear-gradient(145deg, #675027, #56421f);
  border: 1px solid rgba(4, 95, 86, 0.45);
  border-bottom-right-radius: 5px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.16), 0 8px 20px rgba(100,80,50,0.12);
}
.ds-msg-ai {
  align-self: flex-start;
  width: min(92%, 860px);
  color: var(--text-primary);
  background: rgba(255,255,255,0.96);
  border: 1px solid #e0d5c2;
  border-left: 3px solid var(--accent-light);
  border-top-left-radius: 5px;
  box-shadow: 0 7px 24px rgba(70,55,35,0.045), inset 0 1px 0 #fff;
}
.ds-msg-role { display: flex; align-items: center; gap: 6px; font-size: 10px; line-height: 1; font-weight: 700; letter-spacing: 0.3px; color: var(--text-secondary); margin-bottom: 10px; }
.ds-msg-role i { width: 6px; height: 6px; border-radius: 2px; background: var(--accent-light); }
.ds-msg-user .ds-msg-role { color: rgba(235,255,250,0.72); }
.ds-msg-user .ds-msg-role i { background: #ddcfb6; }
.ds-msg-content { max-width: 100%; min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; font-size: 13px; line-height: 1.75; }
.ds-msg-user .ds-msg-content { color: #faf6ee; }

.ds-reasoning {
  background: #f6f1e7;
  border: 1px solid #e0d5c2;
  border-left: 3px solid #ac9d84;
  border-radius: 9px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.ds-reasoning-title { display: flex; align-items: center; gap: 8px; font-size: 11px; font-weight: 700; color: var(--accent-strong); margin-bottom: 6px; }
.ds-reasoning-title span { font-size: 7px; letter-spacing: 1.2px; color: #968d7d; }
.ds-reasoning-text { font-size: 12px; line-height: 1.75; color: #6b665e; white-space: pre-wrap; overflow-wrap: anywhere; }
.ds-error-text { color: var(--danger); background: var(--danger-soft); border: 1px solid #f1d5d2; border-radius: 8px; padding: 9px 11px; font-size: 12px; line-height: 1.6; }
.ds-usage { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; margin-top: 12px; padding-top: 9px; border-top: 1px solid #f6f1e7; font-family: var(--font-mono); font-size: 9px; line-height: 1.6; color: var(--text-tertiary, #a99f8c); }
.ds-usage-name { color: #8f8676; font-size: 7px; font-weight: 700; letter-spacing: 1px; }
.ds-msg-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 10px; padding-top: 10px; border-top: 1px dashed #e0d5c2; }
.ds-ttl-label { font-size: 10px; color: var(--text-secondary); }
.ds-ttl-select { height: 28px; font-size: 10px; cursor: pointer; }
.ds-ttl-hint { font-size: 10px; line-height: 1.5; color: var(--warning); }
.ds-thinking { display: flex; align-items: center; gap: 9px; color: var(--text-secondary); font-size: 12px; }
.ds-spinner { width: 15px; height: 15px; border: 2px solid #e6ddcb; border-top-color: var(--accent); border-radius: 50%; animation: ds-spin 0.8s linear infinite; display: inline-block; flex: 0 0 15px; box-sizing: border-box; }

.ds-md { white-space: normal; line-height: 1.8; }
.ds-md :deep(> :first-child) { margin-top: 0; }
.ds-md :deep(> :last-child) { margin-bottom: 0; }
.ds-md :deep(p) { margin: 8px 0; }
.ds-md :deep(h1), .ds-md :deep(h2), .ds-md :deep(h3), .ds-md :deep(h4), .ds-md :deep(h5), .ds-md :deep(h6) { margin: 20px 0 9px; color: #332a1b; font-weight: 750; line-height: 1.45; letter-spacing: -0.2px; }
.ds-md :deep(h1) { font-size: 20px; padding-bottom: 8px; border-bottom: 1px solid var(--border-color); }
.ds-md :deep(h2) { font-size: 17px; padding-bottom: 7px; border-bottom: 1px solid #f6f1e7; }
.ds-md :deep(h3) { font-size: 15px; }
.ds-md :deep(h4) { font-size: 14px; }
.ds-md :deep(ul), .ds-md :deep(ol) { margin: 8px 0; padding-left: 22px; }
.ds-md :deep(li) { margin: 4px 0; padding-left: 2px; }
.ds-md :deep(li::marker) { color: var(--accent); }
.ds-md :deep(strong) { font-weight: 750; color: #32291a; }
.ds-md :deep(a) { color: var(--accent-strong); text-decoration: none; border-bottom: 1px solid rgba(201,106,69,0.25); }
.ds-md :deep(a:hover) { border-bottom-color: var(--accent); }
.ds-md :deep(hr) { border: none; border-top: 1px solid var(--border-color); margin: 18px 0; }
.ds-md :deep(blockquote) { margin: 12px 0; padding: 9px 13px; color: #686258; background: #f6f1e7; border-left: 3px solid var(--accent-light); border-radius: 0 8px 8px 0; }
.ds-md :deep(code) { background: #f6f1e7; color: #5e4821; border: 1px solid #e0d5c2; padding: 1px 5px; border-radius: 5px; font-family: var(--font-mono); font-size: 0.9em; overflow-wrap: anywhere; }
.ds-md :deep(pre) { max-width: 100%; box-sizing: border-box; background: #231e16; border: 1px solid #3b352b; padding: 15px 16px; border-radius: 10px; overflow-x: auto; overscroll-behavior-x: contain; margin: 12px 0; box-shadow: inset 0 1px 0 rgba(255,255,255,0.05); }
.ds-md :deep(pre code) { display: block; width: max-content; min-width: 100%; background: transparent; color: #eaddca; border: 0; padding: 0; font-size: 12px; line-height: 1.7; white-space: pre; overflow-wrap: normal; word-break: normal; tab-size: 2; }
.ds-md :deep(table) { border-collapse: collapse; table-layout: fixed; width: 100%; max-width: 100%; margin: 12px 0; font-size: 11px; }
.ds-md :deep(th), .ds-md :deep(td) { border: 1px solid #e0d5c2; padding: 8px 9px; text-align: left; vertical-align: top; overflow-wrap: anywhere; }
.ds-md :deep(th) { background: #f6f1e7; color: #494132; font-weight: 700; }
.ds-md :deep(tr:nth-child(even) td) { background: rgba(243,248,246,0.5); }
.ds-md :deep(img) { display: block; max-width: 100%; height: auto; border-radius: 8px; }

.ds-composer {
  flex-shrink: 0;
  margin-top: 12px;
  padding: 10px 11px 7px;
  border: 1px solid rgba(221,211,193,0.9);
  border-radius: 12px;
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(22px) saturate(145%);
  -webkit-backdrop-filter: blur(22px) saturate(145%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.96), inset 0 -1px 0 rgba(201,106,69,0.03), 0 10px 28px rgba(70,55,35,0.06);
}
.ds-input-bar { display: flex; gap: 10px; align-items: flex-end; }
.ds-textarea {
  flex: 1;
  min-width: 0;
  min-height: 68px;
  max-height: 150px;
  resize: vertical;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 8px 9px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-primary);
  background: transparent;
  box-sizing: border-box;
  outline: none;
  transition: background var(--transition, 180ms ease), border-color var(--transition, 180ms ease);
}
.ds-textarea::placeholder { color: #a39e98; }
.ds-textarea:focus { background: rgba(255,255,255,0.72); border-color: #e2dfd9; }
.ds-textarea:disabled { opacity: 0.6; }
.ds-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 9px;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition: transform var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease), background var(--transition, 180ms ease);
}
.ds-btn-primary { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); border-color: rgba(201,106,69,0.35); color: #fff; box-shadow: inset 0 1px 0 rgba(255,255,255,0.2), 0 6px 16px rgba(201,106,69,0.16); }
.ds-btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: inset 0 1px 0 rgba(255,255,255,0.22), 0 9px 20px rgba(201,106,69,0.22); }
.ds-btn-primary:disabled { opacity: 0.42; cursor: not-allowed; box-shadow: none; }
.ds-send-btn { height: 42px; min-width: 88px; justify-content: center; margin-bottom: 2px; }
.ds-send-btn svg { width: 17px; height: 17px; }
.ds-input-meta { display: flex; align-items: center; gap: 12px; padding: 6px 3px 0; color: var(--text-tertiary, #a99f8c); font-size: 8px; line-height: 1.4; }
.ds-input-secure { display: inline-flex; align-items: center; gap: 5px; margin-left: auto; }
.ds-input-secure i { color: var(--success); box-shadow: none; }

.ds-balance { padding: 16px; display: flex; flex-direction: column; overflow: hidden; }
.ds-balance-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 22px; }
.ds-balance-head > div { display: flex; flex-direction: column; gap: 5px; }
.ds-balance-kicker { color: var(--text-tertiary, #a99f8c); font-size: 7px; }
.ds-balance-title { font-size: 13px; color: var(--text-primary); font-weight: 700; }
.ds-balance-refresh { display: inline-flex; align-items: center; gap: 4px; border: 1px solid var(--border-color); background: rgba(255,255,255,0.7); color: var(--accent-strong); font-size: 9px; border-radius: 7px; padding: 5px 7px; cursor: pointer; }
.ds-balance-refresh svg { width: 12px; height: 12px; }
.ds-balance-refresh:hover:not(:disabled) { background: var(--accent-soft); border-color: #dcd7cd; }
.ds-balance-refresh:disabled { opacity: 0.45; cursor: not-allowed; }
.ds-balance-main { display: flex; align-items: baseline; gap: 4px; margin-bottom: 20px; padding-bottom: 18px; border-bottom: 1px solid var(--border-color); }
.ds-balance-currency { font-family: var(--font-mono); font-size: 15px; color: var(--accent); }
.ds-balance-amount { font-family: var(--font-mono); font-size: 28px; line-height: 1; font-weight: 700; color: var(--accent-strong); letter-spacing: -1.2px; overflow-wrap: anywhere; }
.ds-balance-rows { display: flex; flex-direction: column; gap: 12px; }
.ds-balance-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; font-size: 11px; }
.ds-balance-k { color: var(--text-secondary); }
.ds-balance-v { font-family: var(--font-mono); color: var(--text-primary); font-weight: 600; }
.ds-balance-ok, .ds-balance-bad { display: inline-flex; align-items: center; gap: 5px; font-family: var(--font-sans); font-size: 10px; }
.ds-balance-ok::before, .ds-balance-bad::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.ds-balance-ok { color: var(--success); }
.ds-balance-bad { color: var(--danger); }
.ds-balance-error { font-size: 11px; line-height: 1.65; color: #8c5c17; background: var(--warning-soft); border: 1px solid #eed9b7; border-radius: 9px; padding: 10px; overflow-wrap: anywhere; }
.ds-balance-empty { font-size: 11px; color: var(--text-tertiary, #a99f8c); text-align: center; margin-top: 20px; }
.ds-balance-tip { font-size: 9px; line-height: 1.6; color: var(--text-secondary); background: #f6f1e7; border-radius: 7px; padding: 8px; margin-top: 16px; }
.ds-balance-time { font-size: 8px; line-height: 1.6; color: var(--text-tertiary, #a99f8c); margin-top: auto; padding-top: 18px; overflow-wrap: anywhere; }

@keyframes ds-spin { to { transform: rotate(360deg); } }
@keyframes ds-message-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }

@media (max-width: 1180px) {
  .ds-page { height: auto; max-height: none; min-height: 0; }
  .ds-layout { grid-template-columns: 210px minmax(0, 1fr); flex: none; }
  .ds-main { min-height: 650px; }
  .ds-balance { grid-column: 1 / -1; display: grid; grid-template-columns: minmax(150px, 0.8fr) minmax(200px, 1.4fr) minmax(150px, 1fr); gap: 12px 24px; align-items: center; }
  .ds-balance-head { margin: 0; }
  .ds-balance-main { margin: 0; padding: 0; border: 0; }
  .ds-balance-rows { grid-column: 2; grid-row: 1 / span 2; }
  .ds-balance-time { grid-column: 3; grid-row: 1; margin: 0; padding: 0; text-align: right; }
  .ds-balance-error, .ds-balance-empty, .ds-balance-tip { grid-column: 1 / -1; margin: 0; }
}

@media (max-width: 700px) {
  .ds-page { padding: 0 0 12px; }
  .page-header { align-items: flex-start; margin-bottom: 15px; }
  .page-header h2 { font-size: 22px; }
  .ds-header-meta { display: none; }
  .ds-layout { display: flex; flex-direction: column; gap: 12px; }
  .ds-sider { padding: 12px 10px 9px; overflow: visible; }
  .ds-sider-head { display: flex; align-items: center; gap: 12px; padding: 0 2px; }
  .ds-panel-label { flex: 1; margin: 0; }
  .ds-new-btn { width: auto; flex: 0 0 auto; margin: 0; }
  .ds-session-list { flex-direction: row; overflow-x: auto; overflow-y: hidden; padding: 10px 1px 3px; gap: 7px; }
  .ds-session-item { flex: 0 0 180px; }
  .ds-session-empty { margin: 8px auto; }
  .ds-session-del { opacity: 1; }
  .ds-sider-foot { display: none; }
  .ds-main { min-height: 0; padding: 9px; overflow: visible; }
  .ds-toolbar { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 11px; }
  .ds-field-model { grid-column: 1 / -1; max-width: none; min-width: 0; }
  .ds-input { width: 100%; }
  .ds-token-badge { grid-column: 1 / -1; margin: 0; padding: 9px 0 0; border-left: 0; border-top: 1px solid var(--border-color); flex-direction: row; align-items: baseline; justify-content: space-between; }
  .ds-token-value { font-size: 17px; }
  .ds-messages { flex: none; height: min(52vh, 480px); min-height: 330px; padding: 15px 12px; gap: 15px; }
  .ds-msg { max-width: 94%; padding: 12px 13px; }
  .ds-msg-ai { width: 96%; }
  .ds-composer { padding: 8px 8px 6px; }
  .ds-textarea { min-height: 72px; }
  .ds-balance { display: flex; padding: 15px; }
  .ds-balance-head { margin-bottom: 16px; }
  .ds-balance-main { margin-bottom: 16px; padding-bottom: 15px; border-bottom: 1px solid var(--border-color); }
  .ds-balance-rows { gap: 10px; }
  .ds-balance-time { margin-top: 14px; padding: 0; text-align: left; }
}

@media (max-width: 390px) {
  .ds-eyebrow { font-size: 8px; letter-spacing: 1.4px; }
  .ds-title-line { gap: 8px; }
  .page-header h2 { font-size: 20px; }
  .page-header-sub { font-size: 11px; }
  .ds-service-state { font-size: 9px; }
  .ds-panel-label small { display: none; }
  .ds-new-btn { padding: 0 11px; }
  .ds-messages { min-height: 310px; padding: 12px 9px; }
  .ds-msg, .ds-msg-ai { max-width: 98%; width: 98%; }
  .ds-msg-user { width: fit-content; }
  .ds-msg-content { font-size: 12px; }
  .ds-md :deep(pre) { padding: 12px; margin-inline: -2px; }
  .ds-md :deep(pre code) { font-size: 11px; }
  .ds-input-bar { gap: 6px; }
  .ds-send-btn { min-width: 70px; padding: 0 11px; }
  .ds-send-btn svg { display: none; }
  .ds-input-meta { gap: 8px; }
  .ds-input-secure { display: none; }
  .ds-usage { align-items: flex-start; flex-direction: column; gap: 3px; }
}

@media (prefers-reduced-motion: reduce) {
  .ds-msg, .ds-spinner { animation: none; }
  .ds-messages { scroll-behavior: auto; }
}
</style>
