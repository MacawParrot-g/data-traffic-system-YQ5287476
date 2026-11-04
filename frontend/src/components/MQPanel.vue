<!-- 文件路径: src/main/frontend/src/components/MQPanel.vue -->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { fetchMQOverview, fetchDlqRecords, retryDlqMessage, clearDlqRecords, fetchDlqSummary } from '../api/index.js'

const emit = defineEmits(['error'])

const activeTab = ref('overview')
const overview = ref(null)
const overviewLoading = ref(false)

const dlqType = ref('insert')
const dlqRecords = ref([])
const dlqLoading = ref(false)

const dlqSummary = ref({ insertDlqCount: 0, updateDlqCount: 0, deleteDlqCount: 0, totalDlqCount: 0 })
const summaryLoading = ref(false)

const retryingIndex = ref(-1)
const autoRefresh = ref(true)
let refreshTimer = null

async function loadOverview() {
  overviewLoading.value = true
  try {
    const json = await fetchMQOverview()
    if (json.success) overview.value = json.data
  } catch (e) { emit('error', 'MQ队列信息获取失败') }
  finally { overviewLoading.value = false }
}

async function loadDlqRecords() {
  dlqLoading.value = true
  try {
    const json = await fetchDlqRecords(dlqType.value)
    if (json.success) dlqRecords.value = json.data || []
  } catch (e) { emit('error', 'DLQ记录获取失败') }
  finally { dlqLoading.value = false }
}

async function loadDlqSummary() {
  summaryLoading.value = true
  try {
    const json = await fetchDlqSummary()
    if (json.success) dlqSummary.value = json.data || {}
  } catch (e) { /* silent */ }
  finally { summaryLoading.value = false }
}

async function handleRetry(index) {
  if (!confirm('确定要重试这条死信消息吗？')) return
  retryingIndex.value = index
  try {
    const json = await retryDlqMessage(dlqType.value, index)
    if (json.success) {
      alert('重试成功')
      loadDlqRecords()
      loadDlqSummary()
    } else {
      alert('重试失败: ' + (json.message || '未知错误'))
    }
  } catch (e) { alert('重试请求失败: ' + e.message) }
  finally { retryingIndex.value = -1 }
}

async function handleClear() {
  if (!confirm('确定要清除 ' + dlqType.value + ' 类型的所有死信记录吗？')) return
  try {
    const json = await clearDlqRecords(dlqType.value)
    if (json.success) {
      dlqRecords.value = []
      loadDlqSummary()
    }
  } catch (e) { alert('清除失败: ' + e.message) }
}

function switchDlqType(type) {
  dlqType.value = type
  loadDlqRecords()
}

function getQueueStatusClass(status) {
  if (status === 'healthy') return 'queue-healthy'
  if (status === 'error') return 'queue-error'
  return 'queue-unknown'
}

function formatTimestamp(ts) {
  if (!ts) return ''
  return ts
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    if (autoRefresh.value) {
      loadOverview()
      loadDlqSummary()
      if (activeTab.value === 'dlq') loadDlqRecords()
    }
  }, 5000)
}

function stopAutoRefresh() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
}

onMounted(() => {
  loadOverview()
  loadDlqSummary()
  loadDlqRecords()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div class="mq-panel">
    <div class="page-header">
      <div class="page-heading">
        <span class="page-kicker">MESSAGE FABRIC / RABBITMQ</span>
        <h2>MQ 健康中心</h2>
        <div class="page-header-sub">监测队列积压、连接状态与死信消息处置</div>
      </div>
      <span class="header-signal"><i></i>BROKER WATCH</span>
    </div>

    <div class="mq-command-bar">
      <div class="mq-tabs">
        <button :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">
          <span class="tab-code">QV</span>队列总览
        </button>
        <button :class="{ active: activeTab === 'dlq' }" @click="activeTab = 'dlq'">
          <span class="tab-code">DL</span>死信队列
          <span class="dlq-badge" v-if="dlqSummary.totalDlqCount > 0">{{ dlqSummary.totalDlqCount }}</span>
        </button>
      </div>
      <div class="mq-toolbar">
        <label class="auto-refresh-toggle">
          <input type="checkbox" v-model="autoRefresh" />
          <span class="toggle-track"><i></i></span>
          自动刷新 <span class="refresh-interval">5s</span>
        </label>
        <button class="btn-mq-refresh" @click="loadOverview(); loadDlqSummary()">刷新状态</button>
      </div>
    </div>

    <div v-if="activeTab === 'overview'" class="overview-section">
      <div class="section-heading">
        <div><span class="section-kicker">QUEUE TOPOLOGY</span><h3>队列实时状态</h3></div>
        <span class="section-note">每 5 秒同步 broker 指标</span>
      </div>
      <div v-if="overviewLoading && !overview" class="loading"><span class="loading-spinner"></span>加载中...</div>
      <div v-else-if="overview" class="queue-grid">
        <div v-for="q in overview.queues" :key="q.queueName" class="queue-card" :class="getQueueStatusClass(q.status)">
          <div class="queue-header">
            <div class="queue-title">
              <span class="queue-label">{{ q.label }}</span>
              <span class="queue-name-top">{{ q.queueName }}</span>
            </div>
            <span class="queue-health"><i class="queue-status-dot" :class="getQueueStatusClass(q.status)"></i>{{ q.status === 'healthy' ? 'HEALTHY' : q.status === 'error' ? 'ERROR' : 'UNKNOWN' }}</span>
          </div>
          <div class="queue-body">
            <div class="queue-stat queue-stat-metric">
              <span class="queue-stat-label">待处理消息</span>
              <span class="queue-stat-value" :class="{ 'text-danger': q.messageCount > 100 }">
                {{ q.messageCount >= 0 ? q.messageCount : 'N/A' }}
              </span>
            </div>
            <div class="queue-stat">
              <span class="queue-stat-label">队列名</span>
              <span class="queue-stat-name">{{ q.queueName }}</span>
            </div>
            <div class="queue-stat" v-if="q.dlqRecordCount !== undefined">
              <span class="queue-stat-label">死信记录</span>
              <span class="queue-stat-dlq" :class="{ 'text-danger': q.dlqRecordCount > 0 }">{{ q.dlqRecordCount }}</span>
            </div>
            <div class="queue-stat queue-stat-error-row" v-if="q.status === 'error'">
              <span class="queue-stat-label">错误</span>
              <span class="queue-stat-error">{{ q.error }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="section-heading summary-heading">
        <div><span class="section-kicker">DEAD LETTER SIGNAL</span><h3>死信记录分布</h3></div>
      </div>
      <div class="dlq-summary-cards">
        <div class="summary-card summary-insert">
          <div class="summary-top"><span class="summary-icon">IN</span><span class="summary-code">INSERT</span></div>
          <div class="summary-label">Insert DLQ</div>
          <div class="summary-count">{{ dlqSummary.insertDlqCount || 0 }}</div>
          <div class="summary-unit">RECORDS</div>
        </div>
        <div class="summary-card summary-update">
          <div class="summary-top"><span class="summary-icon">UP</span><span class="summary-code">UPDATE</span></div>
          <div class="summary-label">Update DLQ</div>
          <div class="summary-count">{{ dlqSummary.updateDlqCount || 0 }}</div>
          <div class="summary-unit">RECORDS</div>
        </div>
        <div class="summary-card summary-delete">
          <div class="summary-top"><span class="summary-icon">DE</span><span class="summary-code">DELETE</span></div>
          <div class="summary-label">Delete DLQ</div>
          <div class="summary-count">{{ dlqSummary.deleteDlqCount || 0 }}</div>
          <div class="summary-unit">RECORDS</div>
        </div>
        <div class="summary-card summary-total">
          <div class="summary-top"><span class="summary-icon">ALL</span><span class="summary-code">TOTAL</span></div>
          <div class="summary-label">死信总计</div>
          <div class="summary-count">{{ dlqSummary.totalDlqCount || 0 }}</div>
          <div class="summary-unit">RECORDS</div>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'dlq'" class="dlq-section">
      <div class="dlq-type-tabs">
        <div class="dlq-type-group">
          <button :class="{ active: dlqType === 'insert' }" @click="switchDlqType('insert')">
            Insert
            <span class="type-badge" v-if="dlqSummary.insertDlqCount > 0">{{ dlqSummary.insertDlqCount }}</span>
          </button>
          <button :class="{ active: dlqType === 'update' }" @click="switchDlqType('update')">
            Update
            <span class="type-badge" v-if="dlqSummary.updateDlqCount > 0">{{ dlqSummary.updateDlqCount }}</span>
          </button>
          <button :class="{ active: dlqType === 'delete' }" @click="switchDlqType('delete')">
            Delete
            <span class="type-badge" v-if="dlqSummary.deleteDlqCount > 0">{{ dlqSummary.deleteDlqCount }}</span>
          </button>
        </div>
        <button class="btn-clear-dlq" @click="handleClear">清空当前队列</button>
      </div>

      <div v-if="dlqLoading" class="loading"><span class="loading-spinner"></span>加载中...</div>
      <div v-else-if="dlqRecords.length === 0" class="dlq-empty">
        <div class="empty-icon"><span></span></div>
        <div class="empty-text">暂无死信消息，队列状态正常</div>
        <div class="empty-hint">当前类型没有需要处置的异常消息</div>
      </div>
      <div v-else class="dlq-list">
        <div v-for="(record, index) in dlqRecords" :key="index" class="dlq-item">
          <div class="dlq-item-header">
            <div class="dlq-record-id"><span class="dlq-item-index">MESSAGE #{{ index + 1 }}</span><span class="dlq-record-tag">DEAD LETTER</span></div>
            <span class="dlq-item-time">{{ record.timestamp }}</span>
          </div>
          <div class="dlq-item-body">
            <div class="dlq-field">
              <span class="dlq-field-label">队列</span>
              <span class="dlq-field-value">{{ record.queueName }}</span>
            </div>
            <div class="dlq-field">
              <span class="dlq-field-label">Exchange</span>
              <span class="dlq-field-value">{{ record.exchange || 'N/A' }}</span>
            </div>
            <div class="dlq-field">
              <span class="dlq-field-label">RoutingKey</span>
              <span class="dlq-field-value">{{ record.routingKey || 'N/A' }}</span>
            </div>
            <div class="dlq-field dlq-field-message">
              <span class="dlq-field-label">消息内容</span>
              <pre class="dlq-field-body">{{ record.body }}</pre>
            </div>
          </div>
          <div class="dlq-item-actions">
            <span class="dlq-action-hint">重新投递前请确认异常已处理</span>
            <button class="btn-retry" @click="handleRetry(index)" :disabled="retryingIndex === index">
              {{ retryingIndex === index ? '重试中...' : '重试投递' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mq-panel { width: 100%; max-width: 1400px; min-width: 0; margin: 0 auto; padding: 0 20px 40px; color: var(--text-primary); font-family: var(--font-sans); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 22px; padding: 4px 0 18px; border-bottom: 1px solid var(--border-color); }
.page-heading { min-width: 0; }
.page-kicker, .section-kicker { display: block; margin-bottom: 7px; color: var(--accent); font-size: 10px; font-weight: 800; letter-spacing: 1.7px; }
.page-header h2 { margin: 0; font-size: 24px; line-height: 1.25; letter-spacing: -0.6px; font-weight: 800; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 13px; }
.header-signal { display: inline-flex; align-items: center; gap: 7px; flex: 0 0 auto; padding: 7px 11px; border: 1px solid rgba(201,106,69,0.15); border-radius: 999px; background: rgba(255,255,255,0.62); color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.85); font-size: 9px; font-weight: 800; letter-spacing: 1px; }
.header-signal i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px var(--accent-glow); }
.mq-command-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; padding: 8px; border: 1px solid rgba(221,211,193,0.85); border-radius: var(--radius); background: rgba(255,255,255,0.66); box-shadow: var(--glass-shadow, var(--shadow-sm)); backdrop-filter: blur(18px); }
.mq-tabs { display: flex; min-width: 0; gap: 5px; }
.mq-tabs button { display: flex; min-height: 40px; align-items: center; gap: 8px; padding: 8px 13px; border: 1px solid transparent; border-radius: var(--radius-sm); background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 12px; font-weight: 700; transition: var(--transition); }
.mq-tabs button:hover { background: rgba(248,233,223,0.6); color: var(--text-primary); }
.mq-tabs button.active { border-color: rgba(201,106,69,0.13); background: var(--accent-soft); color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.72); }
.tab-code { display: grid; width: 25px; height: 25px; place-items: center; border: 1px solid var(--border-color); border-radius: 7px; background: rgba(255,255,255,0.65); font-family: var(--font-mono); font-size: 9px; }
.mq-tabs button.active .tab-code { border-color: rgba(201,106,69,0.15); color: var(--accent); }
.dlq-badge, .type-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 19px; min-height: 19px; padding: 2px 6px; border-radius: 999px; background: var(--danger); color: #fff; font-family: var(--font-mono); font-size: 9px; font-weight: 800; }
.mq-toolbar { display: flex; align-items: center; gap: 13px; padding-right: 4px; }
.auto-refresh-toggle { display: flex; align-items: center; gap: 7px; color: var(--text-secondary); cursor: pointer; font-size: 11px; white-space: nowrap; }
.auto-refresh-toggle input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.toggle-track { position: relative; width: 30px; height: 17px; border-radius: 999px; background: #ddd0bb; transition: background var(--transition); }
.toggle-track i { position: absolute; top: 3px; left: 3px; width: 11px; height: 11px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(70,55,35,0.18); transition: transform var(--transition); }
.auto-refresh-toggle input:checked + .toggle-track { background: var(--accent); }
.auto-refresh-toggle input:checked + .toggle-track i { transform: translateX(13px); }
.auto-refresh-toggle input:focus-visible + .toggle-track { outline: 2px solid var(--accent-light); outline-offset: 3px; }
.refresh-interval { font-family: var(--font-mono); color: var(--accent); }
.btn-mq-refresh, .btn-clear-dlq, .btn-retry { min-height: 36px; padding: 7px 14px; border: 1px solid transparent; border-radius: var(--radius-sm); cursor: pointer; font-size: 11px; font-weight: 750; transition: var(--transition); }
.btn-mq-refresh { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: 0 6px 16px rgba(201,106,69,0.17), inset 0 1px 0 rgba(255,255,255,0.22); }
.btn-mq-refresh:hover { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(201,106,69,0.23); }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; margin: 0 2px 13px; }
.section-heading h3 { margin: 0; font-size: 15px; letter-spacing: -0.2px; }
.section-kicker { margin-bottom: 4px; font-size: 8px; letter-spacing: 1.3px; }
.section-note { color: var(--text-secondary); font-size: 10px; }
.summary-heading { margin-top: 28px; }
.loading { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: 10px; border: 1px dashed var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.45); color: var(--text-secondary); font-size: 13px; }
.loading-spinner { width: 22px; height: 22px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.queue-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 15px; }
.queue-card { position: relative; min-width: 0; padding: 18px; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.88); box-shadow: var(--shadow-sm); transition: transform var(--transition), box-shadow var(--transition), border-color var(--transition); }
.queue-card::before { content: ''; position: absolute; inset: 0 0 auto; height: 3px; background: linear-gradient(90deg, var(--accent), var(--accent-light)); }
.queue-card:hover { border-color: var(--border-strong); box-shadow: var(--shadow-md); transform: translateY(-2px); }
.queue-card.queue-error::before { background: var(--danger); }
.queue-card.queue-unknown::before { background: #a99f8c; }
.queue-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 17px; }
.queue-title { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.queue-label { font-size: 14px; font-weight: 800; }
.queue-name-top { overflow: hidden; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.queue-health { display: inline-flex; align-items: center; gap: 6px; flex: 0 0 auto; padding: 4px 7px; border-radius: 999px; background: var(--success-soft); color: var(--success); font-family: var(--font-mono); font-size: 8px; font-weight: 800; }
.queue-error .queue-health { background: var(--danger-soft); color: var(--danger); }
.queue-unknown .queue-health { background: #f6f1e7; color: var(--text-secondary); }
.queue-status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); box-shadow: 0 0 0 3px rgba(90,110,72,0.1); }
.queue-status-dot.queue-error { background: var(--danger); box-shadow: none; }
.queue-status-dot.queue-unknown { background: var(--text-secondary); box-shadow: none; }
.queue-body { display: flex; flex-direction: column; gap: 10px; }
.queue-stat { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; }
.queue-stat-metric { margin: -2px -2px 2px; padding: 12px; border: 1px solid rgba(201,106,69,0.08); border-radius: var(--radius-sm); background: linear-gradient(115deg, var(--accent-soft), rgba(255,255,255,0.45)); }
.queue-stat-label { color: var(--text-secondary); font-size: 11px; }
.queue-stat-value { font-family: var(--font-mono); font-size: 26px; line-height: 1; font-weight: 700; letter-spacing: -1px; }
.queue-stat-value.text-danger, .queue-stat-dlq.text-danger { color: var(--danger); }
.queue-stat-name { max-width: 68%; overflow: hidden; color: var(--text-secondary); font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.queue-stat-dlq { font-family: var(--font-mono); font-size: 13px; font-weight: 750; }
.queue-stat-error-row { align-items: flex-start; }
.queue-stat-error { color: var(--danger); font-size: 11px; overflow-wrap: anywhere; text-align: right; }
.dlq-summary-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.summary-card { position: relative; min-width: 0; padding: 17px; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius); background: rgba(255,255,255,0.82); box-shadow: var(--shadow-sm); transition: var(--transition); }
.summary-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.summary-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 18px; }
.summary-icon { display: grid; min-width: 30px; height: 30px; padding: 0 7px; place-items: center; border: 1px solid rgba(201,106,69,0.13); border-radius: 9px; background: var(--accent-soft); color: var(--accent-strong); font-family: var(--font-mono); font-size: 9px; font-weight: 800; }
.summary-code, .summary-unit { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 1px; }
.summary-label { color: var(--text-secondary); font-size: 11px; font-weight: 650; }
.summary-count { margin: 5px 0 4px; font-family: var(--font-mono); font-size: 29px; line-height: 1.1; font-weight: 700; letter-spacing: -1px; }
.summary-total { border-color: rgba(201,106,69,0.24); background: linear-gradient(135deg, rgba(248,233,223,0.9), rgba(255,255,255,0.85)); }
.summary-total .summary-count { color: var(--accent-strong); }
.dlq-type-tabs { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 16px; padding: 10px; border: 1px solid var(--border-color); border-radius: var(--radius); background: rgba(255,255,255,0.64); box-shadow: inset 0 1px 0 rgba(255,255,255,0.8); }
.dlq-type-group { display: flex; gap: 6px; }
.dlq-type-group button { display: flex; min-height: 36px; align-items: center; gap: 7px; padding: 7px 13px; border: 1px solid transparent; border-radius: var(--radius-sm); background: transparent; color: var(--text-secondary); cursor: pointer; font-family: var(--font-mono); font-size: 11px; font-weight: 700; transition: var(--transition); }
.dlq-type-group button.active { border-color: rgba(201,106,69,0.14); background: var(--accent-soft); color: var(--accent-strong); }
.btn-clear-dlq { border-color: rgba(213,82,82,0.16); background: var(--danger-soft); color: var(--danger); }
.btn-clear-dlq:hover { background: #fbe4dd; }
.dlq-empty { padding: 64px 20px; border: 1px dashed var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.5); text-align: center; }
.empty-icon { display: grid; width: 48px; height: 48px; margin: 0 auto 15px; place-items: center; border: 1px solid rgba(90,110,72,0.15); border-radius: 15px; background: var(--success-soft); }
.empty-icon span { width: 17px; height: 9px; border-bottom: 2px solid var(--success); border-left: 2px solid var(--success); transform: translateY(-2px) rotate(-45deg); }
.empty-text { color: var(--text-primary); font-size: 14px; font-weight: 700; }
.empty-hint { margin-top: 7px; color: var(--text-secondary); font-size: 12px; }
.dlq-list { display: flex; flex-direction: column; gap: 13px; }
.dlq-item { min-width: 0; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.9); box-shadow: var(--shadow-sm); }
.dlq-item-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 18px; border-bottom: 1px solid #f6f1e7; background: #fcfbfa; }
.dlq-record-id { display: flex; align-items: center; gap: 9px; }
.dlq-item-index { color: var(--accent-strong); font-family: var(--font-mono); font-size: 11px; font-weight: 800; }
.dlq-record-tag { padding: 3px 7px; border-radius: 5px; background: var(--danger-soft); color: var(--danger); font-family: var(--font-mono); font-size: 8px; font-weight: 800; letter-spacing: 0.7px; }
.dlq-item-time { color: var(--text-secondary); font-family: var(--font-mono); font-size: 10px; }
.dlq-item-body { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px 20px; padding: 18px; }
.dlq-field { display: flex; min-width: 0; flex-direction: column; gap: 6px; }
.dlq-field-label { color: var(--text-secondary); font-size: 10px; font-weight: 650; }
.dlq-field-value { color: var(--text-primary); font-family: var(--font-mono); font-size: 11px; overflow-wrap: anywhere; }
.dlq-field-message { grid-column: 1 / -1; }
.dlq-field-body { width: 100%; max-height: 220px; margin: 0; padding: 14px; overflow: auto; border: 1px solid #eaddca; border-radius: var(--radius-sm); background: #f6f1e7; color: #4a3f2c; font-family: var(--font-mono); font-size: 11px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.dlq-item-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; border-top: 1px solid #f6f1e7; background: #fcfbfa; }
.dlq-action-hint { color: var(--text-secondary); font-size: 10px; }
.btn-retry { border-color: rgba(197,122,22,0.18); background: var(--warning-soft); color: var(--warning); }
.btn-retry:hover:not(:disabled) { background: #fbf3d6; transform: translateY(-1px); }
.btn-retry:disabled { opacity: 0.45; cursor: not-allowed; }
@media (max-width: 1000px) {
  .mq-command-bar { align-items: stretch; flex-direction: column; }
  .mq-toolbar { justify-content: space-between; padding: 4px; }
  .dlq-summary-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 700px) {
  .mq-panel { padding: 0 14px 28px; }
  .page-header { align-items: flex-start; gap: 14px; }
  .page-header h2 { font-size: 21px; }
  .header-signal { margin-top: 3px; }
  .queue-grid { grid-template-columns: 1fr; }
  .dlq-type-tabs { align-items: stretch; flex-direction: column; }
  .dlq-type-group { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dlq-type-group button { justify-content: center; padding-inline: 7px; }
  .dlq-item-body { grid-template-columns: 1fr; }
  .dlq-field-message { grid-column: auto; }
}
@media (max-width: 480px) {
  .mq-panel { padding-inline: 10px; }
  .page-header { display: block; }
  .header-signal { margin-top: 13px; }
  .page-kicker { font-size: 9px; letter-spacing: 1.3px; }
  .mq-command-bar { padding: 7px; }
  .mq-tabs { display: grid; grid-template-columns: 1fr 1fr; }
  .mq-tabs button { justify-content: center; padding-inline: 7px; }
  .tab-code { display: none; }
  .mq-toolbar { gap: 8px; }
  .btn-mq-refresh { padding-inline: 11px; }
  .section-note { display: none; }
  .dlq-summary-cards { gap: 9px; }
  .summary-card { padding: 13px; }
  .summary-code { display: none; }
  .summary-count { font-size: 25px; }
  .dlq-item-header { align-items: flex-start; flex-direction: column; }
  .dlq-item-actions { align-items: stretch; flex-direction: column; }
  .btn-retry { width: 100%; }
}
</style>
