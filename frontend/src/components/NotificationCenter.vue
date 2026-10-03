<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { fetchMyNotifications, deleteGlobalNotification } from '../api/index.js'

const emit = defineEmits(['error'])

const notifications = ref([])
const loading = ref(false)
let pollTimer = null
const POLL_INTERVAL = 5000

async function loadNotifications() {
  loading.value = true
  try {
    const json = await fetchMyNotifications(1, 50)
    if (json.success) {
      notifications.value = json.data || []
    }
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function startPolling() {
  loadNotifications()
  stopPolling()
  pollTimer = setInterval(loadNotifications, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleDelete(notif) {
  try {
    const json = await deleteGlobalNotification(notif.id)
    if (json.success) {
      notifications.value = notifications.value.filter(n => n.id !== notif.id)
    } else {
      emit('error', json.message || '删除失败')
    }
  } catch (e) {
    emit('error', '删除失败：' + e.message)
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
    case 'WARNING': return 'nc-warn'
    case 'EMERGENCY': return 'nc-emergency'
    default: return 'nc-info'
  }
}

function isExpired(item) {
  if (!item.expireAt) return false
  return new Date(item.expireAt) < new Date()
}

function formatExpire(item) {
  if (!item.expireAt) return ''
  const expire = new Date(item.expireAt)
  const now = new Date()
  const diff = expire - now
  if (diff <= 0) return '已过期'
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes} 分钟后过期`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分钟后过期`
  const days = Math.floor(hours / 24)
  return `${days} 天 ${hours % 24} 小时后过期`
}

defineExpose({ startPolling, stopPolling, loadNotifications })

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="nc-page">
    <div class="nc-page-header">
      <h2>通知中心</h2>
      <div class="nc-page-sub">接收来自系统和其他用户的通知</div>
      <button class="nc-refresh-btn" @click="loadNotifications" :disabled="loading">
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </div>

    <div v-if="notifications.length === 0 && !loading" class="nc-empty">
      <div class="nc-empty-icon">🔔</div>
      <div class="nc-empty-text">暂无通知</div>
    </div>

    <div class="nc-list" v-if="notifications.length > 0">
      <div class="nc-item" v-for="n in notifications" :key="n.id" :class="[getTypeClass(n.type), { 'nc-expired': isExpired(n) }]">
        <div class="nc-item-left">
          <div class="nc-item-top">
            <span class="nc-type-tag" :class="getTypeClass(n.type)">{{ getTypeLabel(n.type) }}</span>
            <span class="nc-item-title">{{ n.title }}</span>
          </div>
          <div class="nc-item-content" v-if="n.content">{{ n.content }}</div>
          <div class="nc-item-meta">
            <span class="nc-meta-sender">来自 {{ n.sender }}</span>
            <span class="nc-meta-time">{{ n.createdAt }}</span>
          </div>
          <div class="nc-item-expire" :class="{ 'nc-expired-tag': isExpired(n) }">
            <span class="nc-expire-icon">{{ isExpired(n) ? '⏰' : '⏳' }}</span>
            <span class="nc-expire-label">{{ formatExpire(n) }}</span>
          </div>
        </div>
        <button class="nc-del-btn" @click="handleDelete(n)" title="删除此通知">✕</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nc-page { width: 100%; max-width: 900px; margin: 0 auto; padding: 0 20px 40px; box-sizing: border-box; }

.nc-page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 2px solid #f0f0f0; flex-wrap: wrap; }
.nc-page-header h2 { margin: 0; font-size: 22px; color: #1a1a2e; font-weight: 700; }
.nc-page-sub { font-size: 13px; color: #999; flex: 1; }
.nc-refresh-btn { background: linear-gradient(135deg, #60a5fa, #3b82f6); color: #fff; border: none; padding: 8px 20px; font-size: 13px; border-radius: 10px; cursor: pointer; font-weight: 600; transition: transform 0.15s, box-shadow 0.15s; }
.nc-refresh-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(59,130,246,0.3); }
.nc-refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.nc-empty { text-align: center; padding: 80px 20px; }
.nc-empty-icon { font-size: 56px; margin-bottom: 16px; opacity: 0.3; }
.nc-empty-text { color: #aaa; font-size: 15px; }

.nc-list { display: flex; flex-direction: column; gap: 10px; }

.nc-item { display: flex; align-items: flex-start; justify-content: space-between; padding: 18px 20px; border-radius: 12px; border: 1px solid #e8e8e8; background: #fff; transition: all 0.15s; }
.nc-item:hover { border-color: #e0e7ff; box-shadow: 0 4px 16px rgba(99,102,241,0.08); }
.nc-item.nc-expired { opacity: 0.55; }

.nc-item-left { flex: 1; min-width: 0; }

.nc-item-top { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.nc-type-tag { font-size: 10px; font-weight: 700; padding: 3px 10px; border-radius: 20px; text-transform: uppercase; letter-spacing: 0.3px; flex-shrink: 0; }
.nc-type-tag.nc-info { background: #e0e7ff; color: #3730a3; }
.nc-type-tag.nc-warn { background: #fef3c7; color: #92400e; }
.nc-type-tag.nc-emergency { background: #fef2f2; color: #991b1b; }

.nc-item-title { font-size: 15px; font-weight: 600; color: #1a1a2e; line-height: 1.3; }
.nc-item-content { font-size: 13px; color: #64748b; line-height: 1.6; margin-bottom: 10px; }

.nc-item-meta { display: flex; align-items: center; gap: 16px; font-size: 12px; color: #94a3b8; margin-bottom: 8px; }
.nc-meta-sender { font-weight: 600; color: #6366f1; }

.nc-item-expire { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; padding: 5px 12px; border-radius: 8px; background: #f0fdf4; }
.nc-expire-icon { font-size: 13px; }
.nc-expire-label { color: #16a34a; font-weight: 600; }
.nc-item-expire.nc-expired-tag { background: #fef2f2; }
.nc-item-expire.nc-expired-tag .nc-expire-label { color: #dc2626; }

.nc-del-btn { background: none; border: none; cursor: pointer; font-size: 14px; color: #ccc; padding: 6px 8px; border-radius: 8px; transition: all 0.15s; line-height: 1; flex-shrink: 0; }
.nc-del-btn:hover { background: #fef2f2; color: #ef4444; }
</style>