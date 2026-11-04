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
    <header class="nc-page-header">
      <div class="nc-heading">
        <span class="nc-eyebrow">MESSAGE / INBOX</span>
        <h2>通知中心</h2>
        <div class="nc-page-sub">接收来自系统和其他用户的通知</div>
      </div>
      <div class="nc-header-actions">
        <span class="nc-count"><strong>{{ notifications.length }}</strong> 条记录</span>
        <button class="nc-refresh-btn" @click="loadNotifications" :disabled="loading">
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M16 7a6.5 6.5 0 1 0 .2 5.5M16 3v4h-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          {{ loading ? '刷新中...' : '刷新' }}
        </button>
      </div>
    </header>

    <div v-if="notifications.length === 0 && !loading" class="nc-empty">
      <div class="nc-empty-icon" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none"><path d="M8 22h16l-2-3v-6a6 6 0 0 0-12 0v6l-2 3Zm5 3a3 3 0 0 0 6 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </div>
      <div class="nc-empty-title">消息已读毕</div>
      <div class="nc-empty-text">暂无通知</div>
    </div>

    <div class="nc-list" v-if="notifications.length > 0">
      <div class="nc-list-caption"><span>最新通知</span><small>按接收时间排列</small></div>
      <article class="nc-item" v-for="n in notifications" :key="n.id" :class="[getTypeClass(n.type), { 'nc-expired': isExpired(n) }]">
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
            <span class="nc-expire-icon" aria-hidden="true"></span>
            <span class="nc-expire-label">{{ formatExpire(n) }}</span>
          </div>
        </div>
        <button class="nc-del-btn" @click="handleDelete(n)" title="删除此通知" aria-label="删除此通知">
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m6 6 8 8m0-8-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
        </button>
      </article>
    </div>
  </div>
</template>

<style scoped>
.nc-page {
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
  padding: 2px 2px 40px;
  box-sizing: border-box;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.nc-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 0 0 18px;
  border-bottom: 1px solid var(--border-color);
}
.nc-heading { min-width: 0; }
.nc-eyebrow { display: block; margin-bottom: 7px; color: var(--accent); font-size: 9px; font-weight: 800; letter-spacing: 1.8px; }
.nc-page-header h2 { margin: 0; font-size: 25px; line-height: 1.25; letter-spacing: -0.7px; font-weight: 750; }
.nc-page-sub { margin-top: 7px; font-size: 12px; line-height: 1.6; color: var(--text-secondary); }
.nc-header-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.nc-count { padding-right: 12px; border-right: 1px solid var(--border-color); color: var(--text-secondary); font-size: 11px; white-space: nowrap; }
.nc-count strong { margin-right: 3px; color: var(--accent-strong); font-family: var(--font-mono); font-size: 16px; }
.nc-refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 82px;
  height: 38px;
  padding: 0 14px;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  border: 1px solid rgba(201,106,69,0.35);
  border-radius: 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.2), 0 7px 18px rgba(201,106,69,0.15);
  transition: transform var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease);
}
.nc-refresh-btn svg { width: 15px; height: 15px; }
.nc-refresh-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: inset 0 1px 0 rgba(255,255,255,0.24), 0 10px 24px rgba(201,106,69,0.22); }
.nc-refresh-btn:disabled { opacity: 0.45; cursor: not-allowed; box-shadow: none; }

.nc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 64px 20px;
  text-align: center;
  border: 1px dashed #ddd0bb;
  border-radius: var(--radius-lg);
  background: rgba(255,255,255,0.46);
}
.nc-empty-icon { display: grid; place-items: center; width: 66px; height: 66px; margin-bottom: 18px; color: var(--accent); background: var(--accent-soft); border: 1px solid #d9e4d0; border-radius: 20px; box-shadow: inset 0 1px 0 #fff; }
.nc-empty-icon svg { width: 34px; height: 34px; }
.nc-empty-title { margin-bottom: 6px; color: var(--text-primary); font-size: 16px; font-weight: 700; }
.nc-empty-text { color: var(--text-secondary); font-size: 12px; }

.nc-list { display: flex; flex-direction: column; gap: 12px; }
.nc-list-caption { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 2px 2px; }
.nc-list-caption span { color: var(--text-secondary); font-size: 10px; font-weight: 700; letter-spacing: 0.5px; }
.nc-list-caption small { color: var(--text-tertiary, #a99f8c); font-size: 9px; }
.nc-item {
  --nc-type: var(--info);
  --nc-type-soft: var(--info-soft);
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 19px 18px 18px 22px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: rgba(255,255,255,0.88);
  box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,0.9);
  overflow: hidden;
  transition: transform var(--transition, 180ms ease), border-color var(--transition, 180ms ease), box-shadow var(--transition, 180ms ease);
}
.nc-item::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--nc-type); }
.nc-item::after { content: ''; position: absolute; width: 150px; height: 150px; right: -80px; top: -100px; border-radius: 50%; background: var(--nc-type-soft); opacity: 0.55; pointer-events: none; }
.nc-item.nc-info { --nc-type: var(--info); --nc-type-soft: var(--info-soft); }
.nc-item.nc-warn { --nc-type: var(--warning); --nc-type-soft: var(--warning-soft); }
.nc-item.nc-emergency { --nc-type: var(--danger); --nc-type-soft: var(--danger-soft); }
.nc-item:hover { transform: translateY(-1px); border-color: #ddd0bb; box-shadow: var(--shadow-md), inset 0 1px 0 #fff; }
.nc-item.nc-expired { opacity: 0.58; }
.nc-item-left { position: relative; z-index: 1; flex: 1; min-width: 0; }
.nc-item-top { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px; }
.nc-type-tag { display: inline-flex; align-items: center; gap: 5px; flex-shrink: 0; margin-top: 1px; padding: 4px 8px; border: 1px solid transparent; border-radius: 6px; font-size: 9px; line-height: 1; font-weight: 750; letter-spacing: 0.4px; }
.nc-type-tag::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.nc-type-tag.nc-info { color: var(--info); background: var(--info-soft); border-color: #dbe4ea; }
.nc-type-tag.nc-warn { color: var(--warning); background: var(--warning-soft); border-color: #eddbb7; }
.nc-type-tag.nc-emergency { color: var(--danger); background: var(--danger-soft); border-color: #f0d6cc; }
.nc-item-title { min-width: 0; color: var(--text-primary); font-size: 14px; line-height: 1.55; font-weight: 700; overflow-wrap: anywhere; }
.nc-item-content { margin: 0 0 13px; color: var(--text-secondary); font-size: 12px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.nc-item-meta { display: flex; align-items: center; gap: 8px 16px; flex-wrap: wrap; margin-bottom: 11px; color: var(--text-tertiary, #a99f8c); font-size: 10px; line-height: 1.5; }
.nc-meta-sender { color: var(--accent-strong); font-weight: 650; }
.nc-meta-time { font-family: var(--font-mono); font-size: 9px; }
.nc-item-expire { display: inline-flex; align-items: center; gap: 6px; max-width: 100%; padding: 5px 8px; border: 1px solid #e0d5c2; border-radius: 7px; background: var(--success-soft); }
.nc-expire-icon { position: relative; width: 11px; height: 11px; flex: 0 0 11px; border: 1.4px solid currentColor; border-radius: 50%; color: var(--success); box-sizing: border-box; }
.nc-expire-icon::before { content: ''; position: absolute; left: 4px; top: 1px; width: 1px; height: 3px; background: currentColor; transform-origin: bottom; }
.nc-expire-icon::after { content: ''; position: absolute; left: 4px; top: 4px; width: 3px; height: 1px; background: currentColor; }
.nc-expire-label { color: var(--success); font-size: 9px; line-height: 1.4; font-weight: 650; overflow-wrap: anywhere; }
.nc-item-expire.nc-expired-tag { background: var(--danger-soft); border-color: #f0d6cc; }
.nc-item-expire.nc-expired-tag .nc-expire-icon, .nc-item-expire.nc-expired-tag .nc-expire-label { color: var(--danger); }
.nc-del-btn { position: relative; z-index: 1; display: grid; place-items: center; width: 30px; height: 30px; flex: 0 0 30px; padding: 6px; color: #b3a892; background: rgba(255,255,255,0.55); border: 1px solid transparent; border-radius: 8px; cursor: pointer; transition: color var(--transition, 180ms ease), background var(--transition, 180ms ease), border-color var(--transition, 180ms ease); }
.nc-del-btn svg { width: 16px; height: 16px; }
.nc-del-btn:hover { color: var(--danger); background: var(--danger-soft); border-color: #f0d6cc; }

@media (max-width: 700px) {
  .nc-page { padding-bottom: 28px; }
  .nc-page-header { align-items: center; gap: 14px; margin-bottom: 18px; padding-bottom: 15px; }
  .nc-page-header h2 { font-size: 22px; }
  .nc-count { display: none; }
  .nc-item { padding: 17px 14px 16px 18px; }
  .nc-empty { min-height: 300px; }
}

@media (max-width: 390px) {
  .nc-page-header { align-items: flex-end; }
  .nc-eyebrow { font-size: 8px; letter-spacing: 1.4px; }
  .nc-page-header h2 { font-size: 20px; }
  .nc-page-sub { font-size: 11px; }
  .nc-refresh-btn { min-width: 70px; height: 36px; padding: 0 10px; }
  .nc-refresh-btn svg { display: none; }
  .nc-item-top { flex-direction: column; gap: 7px; }
  .nc-item-meta { align-items: flex-start; flex-direction: column; gap: 4px; }
  .nc-item { gap: 8px; padding-right: 10px; }
  .nc-del-btn { width: 28px; height: 28px; flex-basis: 28px; }
}
</style>
