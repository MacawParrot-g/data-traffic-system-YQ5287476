// 新文件: C:\Users\EDY\data-traffic-system-YQ5287476\frontend\src\components\NotificationCenter.vue
<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { sendGlobalNotification, fetchMyNotifications, fetchUserList } from '../api/index.js'

const emit = defineEmits(['error'])

const currentUser = ref(localStorage.getItem('userName') || '')
const currentUserRole = ref(localStorage.getItem('accType') || '')
const isAdmin = computed(() => currentUserRole.value === 'ADMIN' || currentUserRole.value === 'DEVELOPER')

const showPanel = ref(false)
const showSendForm = ref(false)
const notifications = ref([])
const loading = ref(false)
const sending = ref(false)
const sendMsg = ref('')
const userList = ref([])

const form = ref({
  title: '',
  content: '',
  type: 'INFO',
  receivers: 'ALL',
  expireSeconds: 86400,
  selectedUsers: []
})

const typeOptions = [
  { value: 'INFO', label: '普通通知' },
  { value: 'WARNING', label: '警告通知' },
  { value: 'EMERGENCY', label: '紧急通知' }
]

const expireOptions = [
  { value: 3600, label: '1小时' },
  { value: 21600, label: '6小时' },
  { value: 86400, label: '1天' },
  { value: 259200, label: '3天' },
  { value: 604800, label: '7天' },
  { value: 2592000, label: '30天' }
]

async function loadUsers() {
  try {
    const json = await fetchUserList()
    if (json.success) {
      userList.value = (json.data || []).map(u => ({ name: u.name, type: u.type }))
    }
  } catch (e) { /* silent */ }
}

async function loadRecent() {
  loading.value = true
  try {
    const json = await fetchMyNotifications(1, 10)
    if (json.success && json.data) {
      notifications.value = Array.isArray(json.data) ? json.data : []
    }
  } catch (e) { /* silent */ }
  finally { loading.value = false }
}

function togglePanel() {
  showPanel.value = !showPanel.value
  if (showPanel.value) loadRecent()
}

function openSendForm() {
  showSendForm.value = true
  showPanel.value = false
  if (userList.value.length === 0) loadUsers()
}

function cancelSend() {
  showSendForm.value = false
  form.value = { title: '', content: '', type: 'INFO', receivers: 'ALL', expireSeconds: 86400, selectedUsers: [] }
  sendMsg.value = ''
}

function toggleUser(name) {
  const idx = form.value.selectedUsers.indexOf(name)
  if (idx >= 0) form.value.selectedUsers.splice(idx, 1)
  else form.value.selectedUsers.push(name)
}

async function handleSend() {
  if (!form.value.title.trim()) { sendMsg.value = '请输入标题'; return }
  let receivers = form.value.receivers
  if (receivers === 'MULTI') {
    if (form.value.selectedUsers.length === 0) { sendMsg.value = '请至少选择一个用户'; return }
    receivers = form.value.selectedUsers.join(',')
  }
  sending.value = true
  sendMsg.value = ''
  try {
    const json = await sendGlobalNotification({
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      type: form.value.type,
      receivers: receivers,
      expireSeconds: form.value.expireSeconds
    })
    if (json.success) {
      sendMsg.value = '发送成功'
      setTimeout(() => cancelSend(), 1500)
    } else {
      sendMsg.value = json.message || '发送失败'
    }
  } catch (e) {
    sendMsg.value = '发送请求失败：' + e.message
  } finally {
    sending.value = false
  }
}

function getTypeLabel(type) {
  switch (type) {
    case 'WARNING': return '警告'
    case 'EMERGENCY': return '紧急'
    default: return '通知'
  }
}

function getTypeClass(type) {
  switch (type) {
    case 'WARNING': return 'type-warn'
    case 'EMERGENCY': return 'type-emergency'
    default: return 'type-info'
  }
}

function formatTime(t) {
  if (!t) return '-'
  return t
}

function handleVisibilityChange() {
  if (!document.hidden && showPanel.value) loadRecent()
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
})
onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

defineExpose({ loadRecent })
</script>

<template>
  <div class="nc-wrapper">
    <button class="nc-bell" @click="togglePanel">
      <span class="nc-bell-icon">N</span>
    </button>

    <div v-if="showPanel" class="nc-overlay" @click.self="showPanel = false">
      <div class="nc-panel">
        <div class="nc-header">
          <span class="nc-title">通知中心</span>
          <div class="nc-actions">
            <button v-if="isAdmin" class="nc-btn nc-btn-primary" @click="openSendForm">发送通知</button>
            <button class="nc-btn nc-btn-close" @click="showPanel = false">x</button>
          </div>
        </div>

        <div v-if="loading" class="nc-loading">加载中...</div>

        <div v-if="!loading && notifications.length === 0" class="nc-empty">
          <div class="nc-empty-text">暂无通知</div>
        </div>

        <div v-if="notifications.length > 0" class="nc-list">
          <div v-for="n in notifications" :key="n.id" class="nc-item">
            <div class="nc-item-left">
              <span class="nc-type-badge" :class="getTypeClass(n.type)">{{ getTypeLabel(n.type) }}</span>
            </div>
            <div class="nc-item-body">
              <div class="nc-item-title">{{ n.title }}</div>
              <div class="nc-item-content" v-if="n.content">{{ n.content }}</div>
              <div class="nc-item-meta">
                <span class="nc-item-sender">{{ n.sender }}</span>
                <span class="nc-item-time">{{ formatTime(n.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showSendForm" class="nc-send-overlay" @click.self="cancelSend">
      <div class="nc-send-panel">
        <div class="nc-send-header">
          <span class="nc-send-title">发送全域通知</span>
          <button class="nc-btn nc-btn-close" @click="cancelSend">x</button>
        </div>

        <div class="nc-send-body">
          <div class="nc-field">
            <label class="nc-label">通知类型</label>
            <select v-model="form.type" class="nc-input">
              <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>

          <div class="nc-field">
            <label class="nc-label">标题</label>
            <input v-model="form.title" class="nc-input" placeholder="输入通知标题" />
          </div>

          <div class="nc-field">
            <label class="nc-label">内容</label>
            <textarea v-model="form.content" class="nc-textarea" placeholder="输入通知内容（可选）" rows="3"></textarea>
          </div>

          <div class="nc-field">
            <label class="nc-label">过期时间</label>
            <select v-model="form.expireSeconds" class="nc-input">
              <option v-for="opt in expireOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>

          <div class="nc-field">
            <label class="nc-label">接收范围</label>
            <div class="nc-receiver-options">
              <label class="nc-radio" :class="{ active: form.receivers === 'ALL' }">
                <input type="radio" v-model="form.receivers" value="ALL" /> 全员广播
              </label>
              <label class="nc-radio" :class="{ active: form.receivers === 'MULTI' }">
                <input type="radio" v-model="form.receivers" value="MULTI" /> 指定用户
              </label>
            </div>
          </div>

          <div v-if="form.receivers === 'MULTI'" class="nc-field">
            <label class="nc-label">选择用户（已选 {{ form.selectedUsers.length }} 人）</label>
            <div class="nc-user-grid">
              <label v-for="u in userList" :key="u.name" class="nc-user-item" :class="{ checked: form.selectedUsers.includes(u.name) }">
                <input type="checkbox" :value="u.name" :checked="form.selectedUsers.includes(u.name)" @change="toggleUser(u.name)" />
                <span class="nc-user-name">{{ u.name }}</span>
                <span class="nc-user-type">{{ u.type }}</span>
              </label>
            </div>
          </div>

          <div v-if="sendMsg" class="nc-send-msg" :class="{ 'nc-msg-ok': sendMsg === '发送成功', 'nc-msg-err': sendMsg !== '发送成功' }">{{ sendMsg }}</div>

          <button class="nc-btn nc-btn-submit" @click="handleSend" :disabled="sending">
            {{ sending ? '发送中...' : '确认发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nc-wrapper { position: relative; }
.nc-bell { background: none; border: none; cursor: pointer; padding: 4px 8px; border-radius: 8px; transition: background 0.2s; }
.nc-bell:hover { background: rgba(255,255,255,0.1); }
.nc-bell-icon { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 6px; background: #667eea; color: #fff; font-size: 12px; font-weight: 800; }

.nc-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.35); z-index: 9999; display: flex; align-items: center; justify-content: center; }
.nc-panel { width: 420px; max-height: 560px; background: #fff; border-radius: 16px; box-shadow: 0 12px 48px rgba(0,0,0,0.2); border: 1px solid #e8e8e8; display: flex; flex-direction: column; overflow: hidden; animation: ncPanelIn 0.2s ease-out; }
@keyframes ncPanelIn { from { opacity: 0; transform: scale(0.95) translateY(-10px); } to { opacity: 1; transform: scale(1) translateY(0); } }

.nc-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; }
.nc-title { font-size: 15px; font-weight: 700; color: #1a1a2e; }
.nc-actions { display: flex; gap: 8px; align-items: center; }

.nc-btn { border: none; padding: 6px 16px; font-size: 12px; border-radius: 8px; cursor: pointer; font-weight: 600; transition: all 0.15s; }
.nc-btn-primary { background: #667eea; color: #fff; }
.nc-btn-primary:hover { background: #5a6fd6; }
.nc-btn-close { background: none; color: #999; font-size: 16px; padding: 2px 8px; font-weight: 700; }
.nc-btn-close:hover { color: #333; }
.nc-btn-submit { width: 100%; background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; padding: 10px; font-size: 14px; margin-top: 8px; }
.nc-btn-submit:hover:not(:disabled) { opacity: 0.9; }
.nc-btn-submit:disabled { opacity: 0.5; cursor: not-allowed; }

.nc-loading { padding: 30px; text-align: center; color: #aaa; font-size: 13px; }
.nc-empty { padding: 40px 20px; text-align: center; color: #aaa; }
.nc-empty-text { font-size: 14px; }

.nc-list { overflow-y: auto; max-height: 460px; }
.nc-item { display: flex; gap: 10px; padding: 12px 20px; border-bottom: 1px solid #f5f5f5; transition: background 0.15s; }
.nc-item:hover { background: #f8f9ff; }
.nc-item-left { flex-shrink: 0; margin-top: 2px; }
.nc-type-badge { padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: 700; white-space: nowrap; }
.type-info { background: #e0e7ff; color: #3730a3; }
.type-warn { background: #fef3c7; color: #92400e; }
.type-emergency { background: #fef2f2; color: #991b1b; }

.nc-item-body { flex: 1; min-width: 0; }
.nc-item-title { font-size: 13px; font-weight: 600; color: #1a1a2e; margin-bottom: 2px; }
.nc-item-content { font-size: 12px; color: #666; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nc-item-meta { display: flex; gap: 12px; font-size: 10px; color: #bbb; }
.nc-item-sender { color: #999; }

.nc-send-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); z-index: 10000; display: flex; align-items: center; justify-content: center; }
.nc-send-panel { width: 520px; max-height: 80vh; background: #fff; border-radius: 16px; box-shadow: 0 16px 64px rgba(0,0,0,0.25); overflow-y: auto; animation: ncPanelIn 0.2s ease-out; }
.nc-send-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; border-bottom: 1px solid #f0f0f0; background: #fafbfc; position: sticky; top: 0; z-index: 1; }
.nc-send-title { font-size: 16px; font-weight: 700; color: #1a1a2e; }
.nc-send-body { padding: 20px 24px; }

.nc-field { margin-bottom: 14px; }
.nc-label { display: block; font-size: 11px; font-weight: 700; color: #999; text-transform: uppercase; letter-spacing: 0.3px; margin-bottom: 4px; }
.nc-input { width: 100%; padding: 10px 14px; font-size: 13px; border: 2px solid #e8e8e8; border-radius: 10px; outline: none; background: #fff; box-sizing: border-box; transition: border-color 0.2s; }
.nc-input:focus { border-color: #667eea; }
.nc-textarea { width: 100%; padding: 10px 14px; font-size: 13px; border: 2px solid #e8e8e8; border-radius: 10px; outline: none; background: #fff; resize: vertical; font-family: inherit; box-sizing: border-box; transition: border-color 0.2s; }
.nc-textarea:focus { border-color: #667eea; }

.nc-receiver-options { display: flex; gap: 10px; }
.nc-radio { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 600; background: #f0f0f0; color: #666; transition: all 0.2s; border: 2px solid transparent; }
.nc-radio input { display: none; }
.nc-radio.active { background: #e0e7ff; color: #3730a3; border-color: #667eea; }

.nc-user-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px; max-height: 180px; overflow-y: auto; padding: 4px; }
.nc-user-item { display: flex; align-items: center; gap: 6px; padding: 8px 10px; border-radius: 8px; cursor: pointer; background: #fff; border: 2px solid #e8e8e8; transition: all 0.15s; font-size: 12px; }
.nc-user-item input { accent-color: #667eea; }
.nc-user-item.checked { border-color: #667eea; background: #f0f4ff; }
.nc-user-name { font-weight: 600; color: #1a1a2e; }
.nc-user-type { font-size: 10px; color: #999; background: #f0f0f0; padding: 1px 6px; border-radius: 4px; }

.nc-send-msg { font-size: 13px; font-weight: 600; padding: 8px 14px; border-radius: 8px; text-align: center; }
.nc-msg-ok { background: #dcfce7; color: #166534; }
.nc-msg-err { background: #fef2f2; color: #991b1b; }
</style>