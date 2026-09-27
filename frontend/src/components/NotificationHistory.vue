<script setup>import { ref, onMounted } from 'vue'
import { fetchMyNotifications } from '../api/index.js'

const emit = defineEmits(['error'])

const list = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

async function loadData() {
  loading.value = true
  try {
    const json = await fetchMyNotifications(currentPage.value, pageSize.value)
    if (json.success) {
      list.value = Array.isArray(json.data) ? json.data : []
      total.value = json.total || list.value.length
    } else {
      emit('error', json.message || '加载失败')
    }
  } catch (e) {
    emit('error', '加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

function prevPage() {
  if (currentPage.value > 1) { currentPage.value--; loadData() }
}

function nextPage() {
  if (currentPage.value < totalPages()) { currentPage.value++; loadData() }
}

function totalPages() {
  return Math.max(1, Math.ceil(total.value / pageSize.value))
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
    case 'WARNING': return 'tag-warn'
    case 'EMERGENCY': return 'tag-emergency'
    default: return 'tag-info'
  }
}

function isExpired(item) {
  if (!item.expireAt) return false
  return new Date(item.expireAt) < new Date()
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="nh-page">
    <div class="nh-header">
      <h2 class="nh-title">历史通知</h2>
      <div class="nh-sub">管理员发送的通知记录，过期后自动消失</div>
    </div>

    <div class="nh-toolbar">
      <button class="nh-btn" @click="loadData" :disabled="loading">{{ loading ? '加载中...' : '刷新' }}</button>
      <span class="nh-count" v-if="total > 0">共 {{ total }} 条</span>
    </div>

    <div v-if="list.length === 0 && !loading" class="nh-empty">
      <div class="nh-empty-text">暂无历史通知</div>
    </div>

    <div v-if="list.length > 0" class="nh-table-wrap">
      <table class="nh-table">
        <thead>
        <tr>
          <th>类型</th>
          <th>标题</th>
          <th>内容</th>
          <th>发送者</th>
          <th>发送时间</th>
          <th>过期时间</th>
          <th>状态</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="n in list" :key="n.id">
          <td><span class="nh-tag" :class="getTypeClass(n.type)">{{ getTypeLabel(n.type) }}</span></td>
          <td class="nh-cell-title">{{ n.title }}</td>
          <td class="nh-cell-content" :title="n.content">{{ n.content || '-' }}</td>
          <td class="nh-cell-sender">{{ n.sender || '-' }}</td>
          <td class="nh-cell-time">{{ n.createdAt || '-' }}</td>
          <td class="nh-cell-time">{{ n.expireAt || '-' }}</td>
          <td>
            <span v-if="isExpired(n)" class="nh-status nh-status-expired">已过期</span>
            <span v-else class="nh-status nh-status-active">有效</span>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="nh-pagination">
      <button class="nh-page-btn" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
      <span class="nh-page-info">第 {{ currentPage }} / {{ totalPages() }} 页</span>
      <button class="nh-page-btn" :disabled="currentPage >= totalPages()" @click="nextPage">下一页</button>
    </div>
  </div>
</template>

<style scoped>.nh-page { width: 100%; max-width: 1200px; margin: 0 auto; padding: 0 20px 40px; box-sizing: border-box; }
.nh-header { margin-bottom: 16px; padding-bottom: 16px; border-bottom: 2px solid #f0f0f0; }
.nh-title { margin: 0 0 4px; font-size: 22px; color: #1a1a2e; font-weight: 700; }
.nh-sub { font-size: 13px; color: #999; }

.nh-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.nh-btn { border: none; padding: 8px 20px; font-size: 13px; border-radius: 8px; cursor: pointer; font-weight: 600; background: linear-gradient(135deg, #60a5fa, #3b82f6); color: #fff; transition: opacity 0.15s; }
.nh-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.nh-count { font-size: 13px; color: #666; font-weight: 600; }

.nh-empty { text-align: center; padding: 60px 20px; color: #aaa; }
.nh-empty-text { font-size: 14px; }

.nh-table-wrap { overflow-x: auto; border-radius: 10px; border: 1px solid #e8e8e8; margin-bottom: 16px; }
.nh-table { width: 100%; border-collapse: collapse; font-size: 12px; text-align: left; min-width: 700px; }
.nh-table th { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; padding: 11px 14px; white-space: nowrap; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px; }
.nh-table td { padding: 10px 14px; border-bottom: 1px solid #f0f0f0; color: #333; vertical-align: middle; }
.nh-table tbody tr:hover td { background: #f8f9ff; }

.nh-tag { padding: 3px 10px; border-radius: 4px; font-size: 10px; font-weight: 700; white-space: nowrap; }
.tag-info { background: #e0e7ff; color: #3730a3; }
.tag-warn { background: #fef3c7; color: #92400e; }
.tag-emergency { background: #fef2f2; color: #991b1b; }

.nh-cell-title { font-weight: 600; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nh-cell-content { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #666; }
.nh-cell-sender { font-weight: 600; color: #1a1a2e; }
.nh-cell-time { font-size: 11px; color: #888; white-space: nowrap; }

.nh-status { padding: 3px 10px; border-radius: 4px; font-size: 10px; font-weight: 700; }
.nh-status-active { background: #dcfce7; color: #166534; }
.nh-status-expired { background: #f0f0f0; color: #999; }

.nh-pagination { display: flex; justify-content: center; align-items: center; gap: 16px; padding: 14px 0; }
.nh-page-btn { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; border: none; padding: 8px 22px; border-radius: 10px; cursor: pointer; font-weight: 600; font-size: 13px; transition: transform 0.15s; }
.nh-page-btn:hover:not(:disabled) { transform: translateY(-2px); }
.nh-page-btn:disabled { opacity: 0.35; cursor: not-allowed; transform: none; }
.nh-page-info { font-size: 13px; color: #666; font-weight: 600; }
</style>