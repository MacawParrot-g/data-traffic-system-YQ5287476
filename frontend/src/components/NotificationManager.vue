<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { sendGlobalNotification, fetchGlobalNotificationHistory, deleteGlobalNotification, fetchUserList } from '../api/index.js'

const emit = defineEmits(['error'])

const list = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)
const pageSizeInput = ref(15)

const showSendForm = ref(false)
const sending = ref(false)
const sendMsg = ref('')

const sendMode = ref('single')
const selectedUser = ref('')
const selectedUsers = ref([])
const userList = ref([])
const userLoading = ref(false)

const newNotif = reactive({
  type: 'INFO',
  title: '',
  content: '',
  expireSeconds: 86400
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

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize.value))

async function loadUsers() {
  userLoading.value = true
  try {
    const json = await fetchUserList()
    if (json.success) {
      userList.value = (json.data || []).map(u => ({ name: u.name, type: u.type }))
    }
  } catch (e) {
    emit('error', '加载用户列表失败：' + e.message)
  } finally {
    userLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    const json = await fetchGlobalNotificationHistory(currentPage.value, pageSize.value)
    if (json.success) {
      list.value = json.data || []
      total.value = json.total || 0
    } else {
      emit('error', json.message || '加载失败')
    }
  } catch (e) {
    emit('error', '加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

function toggleUser(name) {
  const idx = selectedUsers.value.indexOf(name)
  if (idx >= 0) {
    selectedUsers.value.splice(idx, 1)
  } else {
    selectedUsers.value.push(name)
  }
}

async function handleSend() {
  let receivers = ''
  if (sendMode.value === 'single') {
    if (!selectedUser.value.trim()) { sendMsg.value = '请输入接收人'; return }
    receivers = selectedUser.value.trim()
  } else if (sendMode.value === 'multi') {
    if (selectedUsers.value.length === 0) { sendMsg.value = '请至少选择一个用户'; return }
    receivers = selectedUsers.value.join(',')
  } else {
    receivers = 'ALL'
  }
  if (!newNotif.title.trim()) { sendMsg.value = '请输入标题'; return }

  sending.value = true
  sendMsg.value = ''
  try {
    const json = await sendGlobalNotification({
      title: newNotif.title.trim(),
      content: newNotif.content.trim(),
      type: newNotif.type,
      receivers: receivers,
      expireSeconds: newNotif.expireSeconds
    })
    if (json.success) {
      sendMsg.value = json.message || '发送成功'
      newNotif.title = ''
      newNotif.content = ''
      selectedUser.value = ''
      selectedUsers.value = []
      showSendForm.value = false
      await loadAll()
    } else {
      sendMsg.value = json.message || '发送失败'
    }
  } catch (e) {
    sendMsg.value = '发送请求失败：' + e.message
  } finally {
    sending.value = false
  }
}

function toggleAllUsers() {
  if (selectedUsers.value.length === userList.value.length) {
    selectedUsers.value = []
  } else {
    selectedUsers.value = userList.value.map(u => u.name)
  }
}

async function handleDelete(n) {
  if (!confirm('确定要删除通知「' + n.title + '」吗？')) return
  try {
    const json = await deleteGlobalNotification(n.id)
    if (json.success) {
      await loadAll()
    } else {
      emit('error', json.message || '删除失败')
    }
  } catch (e) {
    emit('error', '删除请求失败：' + e.message)
  }
}

function prevPage() { if (currentPage.value > 1) { currentPage.value--; loadAll() } }
function nextPage() { if (currentPage.value < totalPages()) { currentPage.value++; loadAll() } }
function applyPageSize() {
  const v = parseInt(pageSizeInput.value)
  if (v > 0) { pageSize.value = v; currentPage.value = 1; loadAll() }
}

function getTypeLabel(type) {
  switch (type) {
    case 'INFO': return '普通'
    case 'WARNING': return '警告'
    case 'EMERGENCY': return '紧急'
    default: return type
  }
}

function getTypeClass(type) {
  switch (type) {
    case 'WARNING': return 'type-WARNING'
    case 'EMERGENCY': return 'type-EMERGENCY'
    default: return 'type-INFO'
  }
}

function isExpired(item) {
  if (!item.expireAt) return false
  return new Date(item.expireAt) < new Date()
}

onMounted(() => {
  loadAll()
  loadUsers()
})
</script>

<template>
  <div class="notify-page">
    <header class="page-header">
      <div class="notify-heading">
        <span class="notify-eyebrow">MESSAGE / DELIVERY</span>
        <h2>全域通知管理</h2>
        <div class="page-header-sub">发送通知将弹窗显示在目标用户屏幕上（除发送者外）</div>
      </div>
      <div class="notify-header-badge"><i></i>ADMIN CONSOLE</div>
    </header>

    <div class="notify-actions-bar">
      <button class="btn-action btn-send" @click="showSendForm = !showSendForm">
        <svg v-if="!showSendForm" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
        <svg v-else viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m6 6 8 8m0-8-8 8" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
        {{ showSendForm ? '取消' : '发送通知' }}
      </button>
      <button class="btn-action btn-refresh-n" @click="loadAll" :disabled="loading">
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M16 7a6.5 6.5 0 1 0 .2 5.5M16 3v4h-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
        {{ loading ? '加载中...' : '刷新' }}
      </button>
    </div>

    <section v-if="showSendForm" class="send-card">
      <div class="send-card-heading">
        <span class="send-card-kicker">NEW MESSAGE</span>
        <h3>发送全域通知</h3>
        <p>选择接收范围并填写通知内容，确认后立即投递。</p>
      </div>

      <div class="mode-selector">
        <label class="mode-option" :class="{ 'mode-active': sendMode === 'single' }">
          <input type="radio" v-model="sendMode" value="single" /> 单人发送
        </label>
        <label class="mode-option" :class="{ 'mode-active': sendMode === 'multi' }">
          <input type="radio" v-model="sendMode" value="multi" /> 多人发送
        </label>
        <label class="mode-option" :class="{ 'mode-active': sendMode === 'all' }">
          <input type="radio" v-model="sendMode" value="all" /> 全员广播
        </label>
      </div>

      <div class="send-grid">
        <div v-if="sendMode === 'single'" class="send-field send-field-full">
          <label class="field-label">接收人</label>
          <input v-model="selectedUser" class="send-input" placeholder="输入用户名" />
        </div>

        <div v-if="sendMode === 'multi'" class="send-field send-field-full">
          <label class="field-label">选择用户（已选 {{ selectedUsers.length }} 人）</label>
          <div class="user-select-bar">
            <button class="btn-toggle-all" @click="toggleAllUsers">
              {{ selectedUsers.length === userList.length ? '取消全选' : '全选' }}
            </button>
            <span class="user-loading" v-if="userLoading">加载用户列表中...</span>
          </div>
          <div class="user-checkbox-grid">
            <div v-for="u in userList" :key="u.name" class="user-checkbox-item" :class="{ 'user-checked': selectedUsers.includes(u.name) }" @click="toggleUser(u.name)">
              <span class="user-cb-name">{{ u.name }}</span>
              <span class="user-cb-type">{{ u.type }}</span>
            </div>
          </div>
        </div>

        <div v-if="sendMode === 'all'" class="send-field send-field-full">
          <div class="broadcast-hint"><strong>全员广播</strong><span>将发送给系统内所有用户（共 {{ userList.length }} 人）</span></div>
        </div>

        <div class="send-field">
          <label class="field-label">通知类型</label>
          <select v-model="newNotif.type" class="send-input">
            <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="send-field">
          <label class="field-label">过期时间</label>
          <select v-model="newNotif.expireSeconds" class="send-input">
            <option v-for="opt in expireOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="send-field send-field-title">
          <label class="field-label">标题</label>
          <input v-model="newNotif.title" class="send-input" placeholder="通知标题" />
        </div>
        <div class="send-field send-field-full">
          <label class="field-label">内容</label>
          <textarea v-model="newNotif.content" class="send-textarea" placeholder="通知内容（可选）" rows="3"></textarea>
        </div>
      </div>
      <div v-if="sendMsg" class="send-msg" :class="{ 'send-msg-ok': sendMsg.includes('成功'), 'send-msg-err': !sendMsg.includes('成功') }">{{ sendMsg }}</div>
      <div class="send-submit-row">
        <span>请确认接收范围与通知级别</span>
        <button class="btn-action btn-submit" @click="handleSend" :disabled="sending">
          {{ sending ? '发送中...' : '确认发送' }}
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M4 10h11m-4-4 4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </div>
    </section>

    <div v-if="total > 0" class="result-toolbar">
      <span class="result-count">通知记录 <strong>{{ total }}</strong> 条</span>
      <span class="result-page">第 {{ currentPage }} / {{ totalPages() }} 页</span>
    </div>

    <div v-if="list.length === 0 && !loading" class="state-block">
      <div class="state-icon" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none"><path d="M6 8h20v16H6V8Zm3 4h14M9 16h9m-9 4h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </div>
      <div class="state-title">暂无通知记录</div>
      <div class="state-text">发送一条通知后，记录将显示在这里</div>
    </div>

    <div v-if="list.length > 0" class="table-wrapper">
      <table class="data-table">
        <thead>
        <tr>
          <th>#</th><th>类型</th><th>发送者</th><th>标题</th><th>内容</th>
          <th>发送时间</th><th>过期时间</th><th>状态</th><th>操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(n, index) in list" :key="n.id">
          <td class="cell-index">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
          <td><span class="type-tag" :class="getTypeClass(n.type)">{{ getTypeLabel(n.type) }}</span></td>
          <td><span class="sender-tag">{{ n.sender }}</span></td>
          <td class="cell-title">{{ n.title }}</td>
          <td class="cell-content" :title="n.content">{{ n.content || '-' }}</td>
          <td class="cell-time">{{ n.createdAt || '-' }}</td>
          <td class="cell-time">{{ n.expireAt || '-' }}</td>
          <td>
            <span v-if="isExpired(n)" class="tag-expired">已过期</span>
            <span v-else class="tag-active">有效</span>
          </td>
          <td>
            <button class="btn-sm-del" @click="handleDelete(n)">删除</button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="pagination">
      <button class="page-btn" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages() }} 页，共 {{ total }} 条</span>
      <button class="page-btn" :disabled="currentPage >= totalPages()" @click="nextPage">下一页</button>
      <label class="page-size-label">
        每页 <input class="page-size-input" type="number" v-model="pageSizeInput" @keydown.enter="applyPageSize" @blur="applyPageSize" min="1" max="200" /> 条
      </label>
    </div>
  </div>
</template>

<style scoped>
.notify-page {
  width: 100%;
  max-width: 1480px;
  min-width: 0;
  margin: 0 auto;
  padding: 2px 2px 40px;
  box-sizing: border-box;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 20px; padding: 0 0 18px; border-bottom: 1px solid var(--border-color); }
.notify-heading { min-width: 0; }
.notify-eyebrow { display: block; margin-bottom: 7px; color: var(--accent); font-size: 9px; font-weight: 800; letter-spacing: 1.8px; }
.page-header h2 { margin: 0; font-size: 25px; line-height: 1.25; letter-spacing: -0.7px; font-weight: 750; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
.notify-header-badge { display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--border-color); border-radius: 8px; background: rgba(255,255,255,0.55); color: var(--text-secondary); font-size: 8px; font-weight: 800; letter-spacing: 1.1px; white-space: nowrap; }
.notify-header-badge i { width: 5px; height: 5px; border-radius: 50%; background: var(--success); box-shadow: 0 0 0 3px rgba(90,110,72,0.09); }

.notify-actions-bar { display: flex; gap: 9px; margin-bottom: 18px; }
.btn-action { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 38px; padding: 9px 16px; border: 1px solid transparent; border-radius: 10px; cursor: pointer; font-size: 12px; font-weight: 650; transition: transform var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease), background var(--transition, 180ms ease); }
.btn-action svg { width: 15px; height: 15px; }
.btn-action:disabled { opacity: 0.45; cursor: not-allowed; box-shadow: none; }
.btn-send, .btn-submit { color: #fff; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); border-color: rgba(201,106,69,0.35); box-shadow: inset 0 1px 0 rgba(255,255,255,0.2), 0 7px 18px rgba(201,106,69,0.15); }
.btn-send:hover:not(:disabled), .btn-submit:hover:not(:disabled) { transform: translateY(-1px); box-shadow: inset 0 1px 0 rgba(255,255,255,0.24), 0 10px 24px rgba(201,106,69,0.22); }
.btn-refresh-n { color: var(--text-primary); background: rgba(255,255,255,0.7); border-color: var(--border-color); box-shadow: inset 0 1px 0 #fff; }
.btn-refresh-n:hover:not(:disabled) { background: #fff; border-color: #d6d2ca; }

.send-card { margin-bottom: 20px; padding: 23px; border: 1px solid rgba(215,205,188,0.95); border-radius: var(--radius-lg); background: rgba(255,255,255,0.72); backdrop-filter: blur(20px) saturate(140%); -webkit-backdrop-filter: blur(20px) saturate(140%); box-shadow: inset 0 1px 0 rgba(255,255,255,0.95), inset 0 -1px 0 rgba(201,106,69,0.035), var(--shadow-md); }
.send-card-heading { margin-bottom: 19px; }
.send-card-kicker { display: block; margin-bottom: 5px; color: var(--accent); font-size: 8px; font-weight: 800; letter-spacing: 1.5px; }
.send-card h3 { margin: 0; font-size: 17px; letter-spacing: -0.3px; }
.send-card-heading p { margin: 6px 0 0; color: var(--text-secondary); font-size: 11px; line-height: 1.6; }
.mode-selector { display: inline-flex; gap: 4px; max-width: 100%; margin-bottom: 20px; padding: 4px; border: 1px solid var(--border-color); border-radius: 11px; background: rgba(235,243,240,0.72); }
.mode-option { position: relative; display: flex; align-items: center; justify-content: center; min-height: 34px; padding: 8px 16px; border: 1px solid transparent; border-radius: 8px; cursor: pointer; color: var(--text-secondary); background: transparent; font-size: 11px; font-weight: 650; transition: color var(--transition, 180ms ease), background var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease); }
.mode-option input { position: absolute; opacity: 0; pointer-events: none; }
.mode-option:hover { color: var(--accent-strong); }
.mode-active { color: var(--accent-strong); background: rgba(255,255,255,0.94); border-color: #e3e0da; box-shadow: 0 2px 8px rgba(70,55,35,0.06), inset 0 1px 0 #fff; }

.send-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 15px; }
.send-field { display: flex; flex-direction: column; gap: 7px; min-width: 0; }
.send-field-full { grid-column: 1 / -1; }
.send-field-title { grid-column: 1 / -1; }
.field-label { color: var(--text-secondary); font-size: 10px; font-weight: 700; letter-spacing: 0.25px; }
.send-input, .send-textarea, .page-size-input { width: 100%; box-sizing: border-box; border: 1px solid #e0ddd8; border-radius: 9px; outline: none; color: var(--text-primary); background: rgba(255,255,255,0.84); font-size: 12px; box-shadow: inset 0 1px 2px rgba(70,55,35,0.025); transition: border-color var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease), background var(--transition, 180ms ease); }
.send-input { height: 41px; padding: 0 12px; }
.send-textarea { min-height: 92px; padding: 11px 12px; resize: vertical; line-height: 1.65; font-family: inherit; }
.send-input::placeholder, .send-textarea::placeholder { color: #a8a49e; }
.send-input:focus, .send-textarea:focus, .page-size-input:focus { border-color: var(--accent-light); background: #fff; box-shadow: 0 0 0 3px var(--accent-glow, rgba(201,106,69,0.13)); }

.user-select-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 9px; }
.btn-toggle-all { padding: 6px 12px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #e2ddd4; border-radius: 8px; cursor: pointer; font-size: 10px; font-weight: 650; }
.btn-toggle-all:hover { background: #f1e3d5; }
.user-loading { color: var(--text-secondary); font-size: 10px; }
.user-checkbox-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 7px; max-height: 210px; overflow-y: auto; padding: 2px; }
.user-checkbox-item { position: relative; display: flex; align-items: center; gap: 8px; min-width: 0; padding: 10px 10px 10px 33px; border: 1px solid #e0d5c2; border-radius: 9px; cursor: pointer; color: var(--text-primary); background: rgba(255,255,255,0.74); font-size: 11px; transition: border-color var(--transition, 180ms ease), background var(--transition, 180ms ease); }
.user-checkbox-item::before { content: ''; position: absolute; left: 11px; top: 50%; width: 12px; height: 12px; border: 1px solid #c9c5be; border-radius: 4px; background: #fff; transform: translateY(-50%); box-sizing: border-box; }
.user-checkbox-item::after { content: ''; position: absolute; left: 15px; top: calc(50% - 1px); width: 5px; height: 3px; border-left: 1.5px solid #fff; border-bottom: 1.5px solid #fff; opacity: 0; transform: translateY(-50%) rotate(-45deg); box-sizing: border-box; }
.user-checkbox-item:hover { border-color: #d3cdc3; }
.user-checked { border-color: #c4baa8; background: var(--accent-soft); }
.user-checked::before { border-color: var(--accent); background: var(--accent); }
.user-checked::after { opacity: 1; }
.user-cb-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; font-weight: 650; white-space: nowrap; }
.user-cb-type { margin-left: auto; padding: 2px 5px; color: var(--text-secondary); background: rgba(255,255,255,0.72); border: 1px solid #e0d5c2; border-radius: 5px; font-family: var(--font-mono); font-size: 7px; white-space: nowrap; }
.broadcast-hint { display: flex; align-items: center; gap: 12px; padding: 13px 14px; color: #8c5c17; background: var(--warning-soft); border: 1px solid #eeddbd; border-left: 3px solid var(--warning); border-radius: 9px; font-size: 11px; line-height: 1.6; }
.broadcast-hint strong { flex-shrink: 0; font-size: 11px; }
.send-msg { margin-bottom: 13px; padding: 10px 12px; border: 1px solid transparent; border-radius: 9px; font-size: 11px; line-height: 1.6; font-weight: 600; overflow-wrap: anywhere; }
.send-msg-ok { color: var(--success); background: var(--success-soft); border-color: #e2ddd4; }
.send-msg-err { color: var(--danger); background: var(--danger-soft); border-color: #f0d6cc; }
.send-submit-row { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding-top: 15px; border-top: 1px solid var(--border-color); }
.send-submit-row > span { color: var(--text-tertiary, #a99f8c); font-size: 9px; }

.result-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 11px; padding: 0 2px; }
.result-count, .result-page { color: var(--text-secondary); font-size: 10px; }
.result-count strong { margin: 0 3px; color: var(--accent-strong); font-family: var(--font-mono); font-size: 14px; }
.result-page { font-family: var(--font-mono); font-size: 9px; }
.state-block { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; padding: 50px 20px; text-align: center; border: 1px dashed #ddd0bb; border-radius: var(--radius-lg); background: rgba(255,255,255,0.46); }
.state-icon { display: grid; place-items: center; width: 58px; height: 58px; margin-bottom: 15px; color: var(--accent); background: var(--accent-soft); border: 1px solid #e0d5c2; border-radius: 17px; }
.state-icon svg { width: 30px; height: 30px; }
.state-title { margin-bottom: 6px; font-size: 15px; font-weight: 700; }
.state-text { color: var(--text-secondary); font-size: 11px; }

.table-wrapper { width: 100%; max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; border: 1px solid var(--border-color); border-radius: var(--radius); background: rgba(255,255,255,0.88); box-shadow: var(--shadow-sm); }
.data-table { width: 100%; min-width: 1080px; border-collapse: collapse; text-align: left; font-size: 11px; }
.data-table th { padding: 12px 13px; color: #726d64; background: #f2f1ef; border-bottom: 1px solid #e0d5c2; white-space: nowrap; font-size: 9px; font-weight: 750; letter-spacing: 0.35px; }
.data-table td { padding: 12px 13px; color: var(--text-primary); border-bottom: 1px solid #f1f0ee; vertical-align: middle; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover td { background: #faf6ee; }
.cell-index { width: 42px; color: var(--text-tertiary, #a99f8c) !important; font-family: var(--font-mono); text-align: center; }
.cell-title { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 650; }
.cell-content { max-width: 230px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-secondary) !important; }
.cell-time { color: var(--text-secondary) !important; font-family: var(--font-mono); font-size: 9px; white-space: nowrap; }
.type-tag, .sender-tag, .tag-active, .tag-expired { display: inline-flex; align-items: center; gap: 5px; width: fit-content; padding: 4px 8px; border: 1px solid transparent; border-radius: 6px; font-size: 9px; line-height: 1.2; font-weight: 700; white-space: nowrap; }
.type-tag::before, .tag-active::before, .tag-expired::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.type-INFO { color: var(--info); background: var(--info-soft); border-color: #dbe4ea; }
.type-WARNING { color: var(--warning); background: var(--warning-soft); border-color: #eddbb7; }
.type-EMERGENCY { color: var(--danger); background: var(--danger-soft); border-color: #f0d6cc; }
.sender-tag { max-width: 120px; overflow: hidden; text-overflow: ellipsis; color: var(--accent-strong); background: var(--accent-soft); border-color: #e0d5c2; }
.tag-active { color: var(--success); background: var(--success-soft); border-color: #e3ded5; }
.tag-expired { color: var(--text-secondary); background: #f2f1f0; border-color: #e5e4e1; }
.btn-sm-del { padding: 5px 9px; color: var(--danger); background: transparent; border: 1px solid #f0d6cc; border-radius: 7px; cursor: pointer; font-size: 9px; font-weight: 650; }
.btn-sm-del:hover { background: var(--danger-soft); border-color: #e6c3b8; }

.pagination { display: flex; justify-content: center; align-items: center; gap: 10px; flex-wrap: wrap; padding: 18px 0 0; }
.page-btn { min-height: 34px; padding: 7px 14px; color: var(--text-primary); background: rgba(255,255,255,0.74); border: 1px solid var(--border-color); border-radius: 9px; cursor: pointer; font-size: 11px; font-weight: 650; box-shadow: inset 0 1px 0 #fff; }
.page-btn:hover:not(:disabled) { color: var(--accent-strong); border-color: #d2ccc2; background: var(--accent-soft); }
.page-btn:disabled { opacity: 0.38; cursor: not-allowed; }
.page-info { color: var(--text-secondary); font-size: 10px; }
.page-size-label { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 10px; }
.page-size-input { width: 58px; height: 34px; padding: 0 6px; text-align: center; font-size: 11px; }

@media (max-width: 700px) {
  .notify-page { padding-bottom: 28px; }
  .page-header { align-items: center; margin-bottom: 16px; padding-bottom: 15px; }
  .page-header h2 { font-size: 22px; }
  .notify-header-badge { display: none; }
  .send-card { padding: 18px 15px; border-radius: var(--radius); }
  .mode-selector { display: flex; width: 100%; box-sizing: border-box; }
  .mode-option { flex: 1; padding-inline: 8px; }
  .send-grid { grid-template-columns: 1fr; }
  .send-field, .send-field-full, .send-field-title { grid-column: 1; }
  .user-checkbox-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .pagination { gap: 8px; }
  .page-info { order: -1; width: 100%; text-align: center; }
}

@media (max-width: 390px) {
  .notify-eyebrow { font-size: 8px; letter-spacing: 1.4px; }
  .page-header h2 { font-size: 20px; }
  .page-header-sub { font-size: 11px; }
  .btn-action { padding-inline: 12px; }
  .mode-option { font-size: 10px; }
  .user-checkbox-grid { grid-template-columns: 1fr; }
  .broadcast-hint { align-items: flex-start; flex-direction: column; gap: 3px; }
  .send-submit-row { align-items: stretch; flex-direction: column; }
  .send-submit-row .btn-action { width: 100%; }
  .page-size-label { width: 100%; justify-content: center; margin-top: 4px; }
}
</style>
