<!-- 文件路径: frontend/src/components/DeepSeekChat.vue -->
<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { deepSeekChat, deepSeekStatus } from '../api/index.js'

const emit = defineEmits(['error'])

const configured = ref(false)
const defaultModel = ref('deepseek-chat')
const messages = ref([])
const input = ref('')
const sending = ref(false)
const scrollBox = ref(null)
const model = ref('deepseek-chat')
const temperature = ref(0.7)
const maxTokens = ref(1024)

const modelOptions = [
  { value: 'deepseek-chat', label: 'DeepSeek Chat (通用对话)' },
  { value: 'deepseek-reasoner', label: 'DeepSeek Reasoner (逻辑推理)' }
]

async function loadStatus() {
  try {
    const json = await deepSeekStatus()
    if (json.success && json.data) {
      configured.value = !!json.data.configured
      defaultModel.value = json.data.model || 'deepseek-chat'
      model.value = defaultModel.value
    }
  } catch (e) {
    /* silent */
  }
}

async function scrollToBottom() {
  await nextTick()
  if (scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!configured.value) {
    emit('error', 'DeepSeek 未配置，请先在服务器 .env 设置 DEEPSEEK_API_KEY')
    return
  }

  messages.value.push({ role: 'user', content: text, pending: false })
  input.value = ''
  await scrollToBottom()

  const history = messages.value
      .filter(m => !m.pending)
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
  }
}

function clearChat() {
  messages.value = []
}

function onEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  send()
}

onMounted(loadStatus)
</script>

<template>
  <div class="ds-page">
    <div class="page-header">
      <h2>DeepSeek 智能助手</h2>
      <div class="page-header-sub">基于 OpenAI 兼容接口 · 由后端统一代理调用</div>
    </div>

    <div v-if="!configured" class="ds-warn">
      未检测到 DeepSeek API Key，请运维在服务器 .env 文件中配置 DEEPSEEK_API_KEY 后重启后端。
    </div>

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
      <button class="ds-btn ds-btn-ghost" @click="clearChat" :disabled="sending || messages.length === 0">清空对话</button>
    </div>

    <div ref="scrollBox" class="ds-messages">
      <div v-if="messages.length === 0" class="ds-empty">
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
      <button class="ds-btn ds-btn-primary" @click="send" :disabled="sending || !input.trim()">
        {{ sending ? '发送中...' : '发送' }}
      </button>
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
.ds-empty {
  margin: auto;
  text-align: center;
  color: #999;
}
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
.ds-btn-ghost { height: 32px; background: #fff; border: 1px solid #d9d9d9; }
.ds-btn-ghost:disabled { color: #bbb; cursor: not-allowed; }
</style>