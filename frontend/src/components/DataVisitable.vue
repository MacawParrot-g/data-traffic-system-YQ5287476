<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  fetchRecordList,
  deleteRecord,
  fetchUnexportedByUser,
  adminBatchDelete,
  fetchCountByRecorder,
  executeExportByDate,
  executeExportByHashes,
  fetchExportStatus,
  getExportDownloadUrl
} from '../api/index.js'

const emit = defineEmits(['error'])
const ATTR_OPTIONS = ['appflyer', 'adjust', 'singular', 'tenjin']

const selectedAscribe = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const list = ref([])
const loading = ref(false)
const queried = ref(false)
const currentPage = ref(1)
const pageSize = ref(15)
const pageSizeInput = ref(15)
const total = ref(0)
const selectedHashes = ref([])
const batchDeleting = ref(false)
const exporting = ref(false)
const polling = ref(false)
const fileReady = ref(false)
const fileName = ref('')
const resultMsg = ref('')
const resultSuccess = ref(false)
const currentUser = ref(localStorage.getItem('userName') || '')
const totalUnexported = ref(null)
const todayUnexportedLoading = ref(false)
let pollTimer = null

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const hasSelection = computed(() => selectedHashes.value.length > 0)

function formatDateForQuery(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  return parts[0] + '/' + parseInt(parts[1]) + '/' + parseInt(parts[2])
}

async function refreshTodayUnexported() {
  todayUnexportedLoading.value = true
  try {
    const json = await fetchUnexportedByUser(currentUser.value)
    if (json.success && json.data) {
      totalUnexported.value = json.data.total || 0
    }
  } catch (e) { /* silent */ }
  finally { todayUnexportedLoading.value = false }
}

function handleSearch(resetPage = false) {
  if (resetPage) currentPage.value = 1
  fetchData()
}

async function fetchData() {
  list.value = []
  total.value = 0
  loading.value = true
  queried.value = true
  selectedHashes.value = []
  try {
    const json = await fetchRecordList(
        selectedAscribe.value || null,
        false,
        currentPage.value,
        pageSize.value,
        currentUser.value,
        formatDateForQuery(dateFrom.value),
        formatDateForQuery(dateTo.value)
    )
    if (json.success) {
      list.value = json.data || []
      total.value = json.total || 0
    } else {
      emit('error', json.message || '查询失败')
      list.value = []
      total.value = 0
    }
  } catch (e) {
    emit('error', '查询请求失败：' + e.message)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  selectedAscribe.value = ''
  dateFrom.value = ''
  dateTo.value = ''
  currentPage.value = 1
  fetchData()
}

function prevPage() {
  if (currentPage.value > 1) { currentPage.value--; fetchData() }
}

function nextPage() {
  if (currentPage.value < totalPages.value) { currentPage.value++; fetchData() }
}

function toggleSelectAll(event) {
  if (event.target.checked) {
    selectedHashes.value = list.value.map(item => item.hash)
  } else {
    selectedHashes.value = []
  }
}

function applyPageSize() {
  const v = parseInt(pageSizeInput.value)
  if (v > 0) {
    pageSize.value = v
    currentPage.value = 1
    fetchData()
  }
}

function isAllSelected() {
  return list.value.length > 0 && selectedHashes.value.length === list.value.length
}

async function doExportByDate() {
  if (exporting.value || polling.value) return
  exporting.value = true
  resultMsg.value = ''
  resultSuccess.value = false
  fileReady.value = false
  fileName.value = ''
  try {
    const json = await executeExportByDate(currentUser.value, formatDateForQuery(dateTo.value || dateFrom.value))
    resultSuccess.value = json.success
    resultMsg.value = json.message || ''
    if (json.success) {
      fileName.value = json.data?.fileName || ''
      startPolling()
    }
  } catch (e) {
    resultSuccess.value = false
    resultMsg.value = '导出请求失败：' + e.message
  } finally {
    exporting.value = false
  }
}

async function doExportByHashes() {
  if (selectedHashes.value.length === 0) {
    emit('error', '请先选择要导出的数据')
    return
  }
  if (exporting.value || polling.value) return
  exporting.value = true
  resultMsg.value = ''
  resultSuccess.value = false
  fileReady.value = false
  fileName.value = ''
  try {
    const json = await executeExportByHashes(currentUser.value, selectedHashes.value)
    resultSuccess.value = json.success
    resultMsg.value = json.message || ''
    if (json.success) {
      fileName.value = json.data?.fileName || ''
      startPolling()
    }
  } catch (e) {
    resultSuccess.value = false
    resultMsg.value = '导出请求失败：' + e.message
  } finally {
    exporting.value = false
  }
}

function startPolling() {
  polling.value = true
  pollTimer = setInterval(async () => {
    try {
      const json = await fetchExportStatus(currentUser.value)
      if (json.success && json.data?.ready) {
        stopPolling()
        fileReady.value = true
        fileName.value = json.data.fileName || fileName.value
      }
    } catch (e) { /* ignore */ }
  }, 2000)
}

function stopPolling() {
  polling.value = false
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function doDownload() {
  const url = getExportDownloadUrl(currentUser.value)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName.value
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => {
    fileReady.value = false
    fileName.value = ''
    resultMsg.value = '✅ 文件已下载，服务器端文件已自动删除'
    resultSuccess.value = true
  }, 1500)
}

async function doBatchDelete() {
  if (selectedHashes.value.length === 0) {
    emit('error', '请先选择要删除的记录')
    return
  }
  if (!confirm(`确定要删除选中的 ${selectedHashes.value.length} 条记录吗？此操作不可撤销！`)) return
  batchDeleting.value = true
  try {
    const json = await adminBatchDelete(selectedHashes.value)
    if (json.success) {
      alert('✅ ' + json.message)
      selectedHashes.value = []
      await fetchData()
    } else {
      emit('error', json.message || '批量删除失败')
    }
  } catch (e) {
    emit('error', '批量删除请求失败：' + e.message)
  } finally {
    batchDeleting.value = false
  }
}

async function fetchDataByName() {
  const json = await fetchCountByRecorder()
  if (json.success) {
    alert("用户" + currentUser.value + "已入库" + json.data + "条")
  } else {
    alert("获取事件数失败，请联系工作人员")
  }
}

function sup() {
  alert('谢谢你，成都。谢谢你，我的同桌：吴雨芹。谢谢我自己：完整的完成了这一切！！！')
}

onMounted(() => { fetchData() })
onUnmounted(() => { stopPolling() })
</script>

<template>
  <div class="admin-page">
    <header class="page-header">
      <div class="page-heading">
        <span class="page-eyebrow">PERSONAL DATA WORKSPACE</span>
        <h2>数据看板</h2>
        <div class="page-header-sub">查询、导出并跟踪当前用户的数据记录</div>
      </div>
      <div class="user-context">
        <span class="user-context-label">当前用户</span>
        <strong>{{ currentUser }}</strong>
      </div>
    </header>

    <button type="button" @click="fetchDataByName()" class="metric-link">
      <span class="metric-link-mark" aria-hidden="true">IN</span>
      <span class="metric-link-copy"><strong>今日入库</strong><small>点我查看今日入库数量</small></span>
      <span class="metric-link-action">查看</span>
    </button>

    <section class="filter-card" aria-label="数据筛选">
      <div class="section-caption">
        <span>查询条件</span>
        <small>按归因平台与记录日期筛选</small>
      </div>
      <div class="filter-top-row">
        <div class="filter-section">
          <div class="filter-section-title">归因筛选</div>
          <div class="radio-group">
            <label class="radio-tag radio-all" :class="{ active: selectedAscribe === '' }">
              <input type="radio" name="ascribe" value="" :checked="selectedAscribe === ''" @change="selectedAscribe = ''" />
              <span>全部</span>
            </label>
            <label v-for="opt in ATTR_OPTIONS" :key="opt" class="radio-tag" :class="[opt, { active: selectedAscribe === opt }]">
              <input type="radio" name="ascribe" :value="opt" :checked="selectedAscribe === opt" @change="selectedAscribe = opt" />
              <span>{{ opt }}</span>
            </label>
          </div>
        </div>
        <div class="filter-top-actions">
          <div class="filter-field">
            <label class="field-label">开始日期</label>
            <input v-model="dateFrom" type="date" class="filter-input filter-date" />
          </div>
          <div class="filter-field">
            <label class="field-label">结束日期</label>
            <input v-model="dateTo" type="date" class="filter-input filter-date" />
          </div>
          <div class="filter-actions">
            <button class="btn-action btn-search" @click="handleSearch(true)">查询</button>
            <button class="btn-action btn-reset" @click="resetFilters">重置</button>
          </div>
        </div>
      </div>
    </section>

    <section class="export-action-bar" aria-label="导出操作">
      <div class="export-action-left">
        <button class="btn-action btn-export-date" @click="doExportByDate" :disabled="exporting || polling || hasSelection">
          {{ exporting ? '导出中...' : '按当前日期导出' }}
        </button>
        <button class="btn-action btn-export-hash" @click="doExportByHashes" :disabled="exporting || polling || !hasSelection">
          {{ exporting ? '导出中...' : '导出选中行 (' + selectedHashes.length + ')' }}
        </button>
        <button class="btn-action btn-refresh-unexported" @click="refreshTodayUnexported" :disabled="todayUnexportedLoading">
          {{ todayUnexportedLoading ? '查询中...' : '刷新未导出' }}
        </button>
        <span class="export-tip" v-if="hasSelection">已切换为选中行导出模式，按日期导出已禁用</span>
        <span class="total-unexported-badge" v-if="totalUnexported !== null" :class="{ 'badge-zero': totalUnexported === 0 }">
          <span class="badge-dot" aria-hidden="true"></span>
          总未导出: <strong>{{ totalUnexported }}</strong> 条
        </span>
      </div>
      <div class="export-action-right" v-if="fileReady">
        <div class="download-section-inline">
          <span class="download-file-name" :title="fileName">{{ fileName }}</span>
          <button class="btn-action btn-download" @click="doDownload">下载文件</button>
        </div>
      </div>
    </section>

    <div v-if="resultMsg" class="feedback" :class="{ 'feedback-ok': resultSuccess, 'feedback-err': !resultSuccess }" role="status">
      {{ resultMsg }}
    </div>

    <div v-if="polling" class="polling-tip" role="status">
      <div class="state-spinner state-spinner-inline"></div>
      文件生成中，请稍候...
    </div>

    <div v-if="queried" class="result-toolbar">
      <div class="result-toolbar-left">
        <span class="result-count">共 <strong>{{ total }}</strong> 条</span>
        <span class="toolbar-divider" aria-hidden="true"></span>
        <span class="result-page">第 {{ currentPage }}/{{ totalPages }} 页</span>
      </div>
      <div class="result-toolbar-right">
        <label class="batch-select-label" v-if="list.length > 0">
          <input type="checkbox" :checked="isAllSelected()" @change="toggleSelectAll" />
          <span>全选</span>
        </label>
        <span class="selected-count" v-if="selectedHashes.length > 0">已选 {{ selectedHashes.length }} 条</span>
        <button class="btn-batch-del" v-if="selectedHashes.length > 0" @click="doBatchDelete" :disabled="batchDeleting">
          {{ batchDeleting ? '删除中...' : '批量删除' }}
        </button>
      </div>
    </div>

    <div v-if="loading && !queried" class="state-block">
      <div class="state-spinner"></div>
      <div class="state-text">正在查询数据...</div>
    </div>
    <div v-if="!loading && queried && list.length === 0" class="state-block">
      <div class="state-icon" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none"><path d="M5 8.5h22v17H5zM5 12l11 8 11-8" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M10 5h12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </div>
      <div class="state-title">没有符合条件的数据</div>
      <div class="state-text">调整归因或日期范围后重新查询</div>
    </div>

    <div v-if="list.length > 0" class="table-wrapper">
      <table class="data-table">
        <thead>
        <tr>
          <th class="th-check"><input type="checkbox" :checked="isAllSelected()" @change="toggleSelectAll" /></th>
          <th>#</th><th>URL</th><th>Bundle ID</th><th>归因</th><th>事件数</th>
          <th>异常类型</th><th>记录日期</th><th>记录人</th><th>备注</th><th>导出</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(item, index) in list" :key="item.hash" :class="{ 'row-selected': selectedHashes.includes(item.hash) }">
          <td class="td-check"><input type="checkbox" :value="item.hash" v-model="selectedHashes" /></td>
          <td class="cell-index">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
          <td :title="item.URL" class="cell-url">{{ item.URL }}</td>
          <td class="cell-mono">{{ item.bundleId }}</td>
          <td>{{ item.ascribe || '-' }}</td>
          <td class="cell-num">{{ item.event_number }}</td>
          <td>
            <span v-if="item.exception_type" class="exception-tag" :class="'ex-' + item.exception_type">{{ item.exception_type }}</span>
            <span v-else>-</span>
          </td>
          <td class="cell-date">{{ item.record_data || '-' }}</td>
          <td>{{ item.recorder || '-' }}</td>
          <td :title="item.remark" class="cell-remark">{{ item.remark || '-' }}</td>
          <td><span :class="item.isOutput === 1 ? 'tag-exported' : 'tag-unexported'">{{ item.isOutput === 1 ? '已导出' : '未导出' }}</span></td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="pagination">
      <button class="page-btn" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页，共 {{ total }} 条</span>
      <button class="page-btn" :disabled="currentPage >= totalPages" @click="nextPage">下一页</button>
      <label class="page-size-label">
        每页
        <input class="page-size-input" type="number" v-model="pageSizeInput" @keydown.enter="applyPageSize" @blur="applyPageSize" min="1" max="500" />
        条
      </label>
    </div>

    <div @click="sup" class="sup-area">点我试试</div>
  </div>
</template>

<style scoped>
.admin-page,
.admin-page * { box-sizing: border-box; }
.admin-page { width: 100%; max-width: 1600px; margin: 0 auto; padding: 0 20px 36px; color: var(--text-primary); font-family: var(--font-sans); }
.admin-page button,
.admin-page input,
.admin-page select { font-family: inherit; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 14px; padding-bottom: 17px; border-bottom: 1px solid var(--border-color); }
.page-heading { min-width: 0; }
.page-eyebrow { display: block; margin-bottom: 7px; color: var(--accent); font-family: var(--font-mono); font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.page-header h2 { margin: 0; color: var(--text-primary); font-size: 23px; font-weight: 700; letter-spacing: -.025em; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 12px; }
.user-context { display: flex; flex: 0 0 auto; align-items: flex-end; flex-direction: column; gap: 3px; max-width: 40%; }
.user-context-label { color: var(--text-tertiary); font-size: 9px; font-weight: 600; letter-spacing: .08em; }
.user-context strong { max-width: 100%; overflow: hidden; color: var(--accent-strong); font-family: var(--font-mono); font-size: 12px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.metric-link { display: inline-flex; align-items: center; gap: 10px; margin: 0 0 14px; padding: 8px 11px; color: var(--text-primary); background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; text-align: left; cursor: pointer; transition: border-color .16s ease, background .16s ease, box-shadow .16s ease; }
.metric-link:hover { background: var(--accent-soft); border-color: var(--accent-light); }
.metric-link:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.metric-link-mark { display: inline-flex; width: 29px; height: 29px; align-items: center; justify-content: center; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 7px; font-family: var(--font-mono); font-size: 9px; font-weight: 700; }
.metric-link-copy { display: flex; flex-direction: column; gap: 1px; }
.metric-link-copy strong { font-size: 11px; font-weight: 650; }
.metric-link-copy small { color: var(--text-tertiary); font-size: 9px; }
.metric-link-action { margin-left: 9px; color: var(--accent); font-size: 10px; font-weight: 650; }
.filter-card { position: relative; isolation: isolate; margin-bottom: 12px; padding: 16px; background: rgba(255,255,255,.78); border: 1px solid rgba(215,227,225,.9); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.95); backdrop-filter: blur(14px) saturate(120%); -webkit-backdrop-filter: blur(14px) saturate(120%); }
.section-caption { display: flex; align-items: baseline; gap: 10px; margin-bottom: 13px; }
.section-caption > span { color: var(--text-primary); font-size: 12px; font-weight: 700; }
.section-caption small { color: var(--text-tertiary); font-size: 10px; }
.filter-top-row { display: flex; align-items: flex-end; justify-content: space-between; flex-wrap: wrap; gap: 14px; }
.filter-section { min-width: 0; }
.filter-section-title,
.field-label { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 10px; font-weight: 600; }
.filter-top-actions { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 9px; }
.filter-field { display: flex; min-width: 0; flex-direction: column; }
.filter-input { width: 156px; min-width: 0; height: 36px; padding: 0 10px; color: var(--text-primary); background: rgba(251,253,253,.95); border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; font-size: 11px; transition: border-color .16s ease, box-shadow .16s ease, background .16s ease; }
.filter-input:hover { border-color: var(--accent-light); }
.filter-input:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.filter-date { color-scheme: light; cursor: pointer; }
.filter-date::-webkit-calendar-picker-indicator { opacity: .55; cursor: pointer; }
.filter-actions { display: flex; gap: 7px; }
.radio-group { display: flex; flex-wrap: wrap; gap: 6px; }
.radio-tag { position: relative; display: inline-flex; min-height: 30px; align-items: center; gap: 6px; padding: 5px 10px; color: var(--text-secondary); background: rgba(255,255,255,.66); border: 1px solid var(--border-color); border-radius: 7px; font-family: var(--font-mono); font-size: 10px; font-weight: 600; cursor: pointer; transition: color .15s ease, background .15s ease, border-color .15s ease; }
.radio-tag::before { width: 5px; height: 5px; background: #94a3a0; border-radius: 50%; content: ""; }
.radio-tag.appflyer::before { background: #2c9b78; }
.radio-tag.adjust::before { background: #d39536; }
.radio-tag.singular::before { background: #4688a0; }
.radio-tag.tenjin::before { background: #368f91; }
.radio-tag input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.radio-tag:hover { color: var(--accent-strong); border-color: var(--accent-light); }
.radio-tag.active { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent); box-shadow: inset 0 1px 0 rgba(255,255,255,.65); }
.radio-tag:has(input:focus-visible) { box-shadow: 0 0 0 3px var(--accent-soft); }
.btn-action,
.btn-batch-del,
.page-btn { display: inline-flex; align-items: center; justify-content: center; min-height: 34px; padding: 7px 12px; border: 1px solid transparent; border-radius: var(--radius); outline: none; font-size: 11px; font-weight: 600; line-height: 1.35; white-space: nowrap; cursor: pointer; transition: color .16s ease, background .16s ease, border-color .16s ease, box-shadow .16s ease; }
.btn-action:focus-visible,
.btn-batch-del:focus-visible,
.page-btn:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.btn-action:disabled,
.btn-batch-del:disabled,
.page-btn:disabled { opacity: .43; cursor: not-allowed; }
.btn-search,
.btn-export-date { color: #fff; background: var(--accent); border-color: var(--accent); box-shadow: inset 0 1px 0 rgba(255,255,255,.2); }
.btn-search:hover:not(:disabled),
.btn-export-date:hover:not(:disabled) { background: var(--accent-strong); border-color: var(--accent-strong); }
.btn-reset,
.btn-refresh-unexported { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-reset:hover:not(:disabled),
.btn-refresh-unexported:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-export-hash,
.btn-download { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-export-hash:hover:not(:disabled),
.btn-download:hover:not(:disabled) { background: var(--accent-light); }
.export-action-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; padding: 11px 12px; background: rgba(247,251,250,.86); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: inset 0 1px 0 rgba(255,255,255,.9); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); }
.export-action-left,
.export-action-right { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: 7px; }
.export-action-left { flex: 1 1 620px; }
.export-action-right { flex: 0 1 auto; }
.export-tip { padding: 5px 8px; color: #946214; background: #fff8e9; border: 1px solid #eeddb8; border-radius: 6px; font-size: 10px; line-height: 1.45; }
.total-unexported-badge { display: inline-flex; min-height: 29px; align-items: center; gap: 5px; padding: 4px 9px; color: #946214; background: #fff8e9; border: 1px solid #eeddb8; border-radius: 999px; font-size: 10px; white-space: nowrap; }
.total-unexported-badge strong { font-family: var(--font-mono); font-size: 12px; }
.badge-dot { width: 5px; height: 5px; background: var(--warning); border-radius: 50%; }
.total-unexported-badge.badge-zero { color: #16715d; background: #eef9f5; border-color: #c9e9dd; }
.badge-zero .badge-dot { background: var(--success); }
.download-section-inline { display: flex; min-width: 0; align-items: center; gap: 8px; padding: 5px 6px 5px 10px; background: #eef9f5; border: 1px solid #c9e9dd; border-radius: var(--radius); }
.download-file-name { max-width: 230px; overflow: hidden; color: #16715d; font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.feedback,
.polling-tip { margin-bottom: 10px; padding: 10px 12px; border: 1px solid; border-radius: var(--radius); font-size: 11px; font-weight: 550; line-height: 1.55; overflow-wrap: anywhere; }
.feedback-ok { color: #16715d; background: #f0faf6; border-color: #cce9df; }
.feedback-err { color: var(--danger); background: #fff5f4; border-color: #f2cfcc; }
.polling-tip { display: flex; align-items: center; gap: 8px; color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.state-spinner-inline { width: 15px !important; height: 15px !important; margin: 0 !important; border-width: 2px !important; }
.result-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 9px; margin-bottom: 10px; padding: 8px 11px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); }
.result-toolbar-left,
.result-toolbar-right { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.result-count,
.result-page { color: var(--text-secondary); font-size: 11px; }
.result-count strong { color: var(--accent-strong); font-family: var(--font-mono); font-size: 13px; }
.toolbar-divider { width: 1px; height: 12px; background: var(--border-color); }
.batch-select-label { display: inline-flex; align-items: center; gap: 5px; color: var(--text-secondary); font-size: 11px; cursor: pointer; }
.batch-select-label input,
.data-table input[type="checkbox"] { width: 14px; height: 14px; margin: 0; accent-color: var(--accent); cursor: pointer; }
.selected-count { padding: 3px 8px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 999px; font-family: var(--font-mono); font-size: 9px; font-weight: 650; }
.btn-batch-del { min-height: 29px; padding: 5px 10px; color: var(--danger); background: #fff5f4; border-color: #f2cfcc; }
.btn-batch-del:hover:not(:disabled) { background: #ffe9e6; }
.state-block { display: flex; min-height: 220px; align-items: center; justify-content: center; flex-direction: column; padding: 38px 20px; background: var(--bg-card); border: 1px dashed var(--border-color); border-radius: var(--radius-lg); text-align: center; }
.state-spinner { width: 28px; height: 28px; margin-bottom: 13px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: data-spin .75s linear infinite; }
@keyframes data-spin { to { transform: rotate(360deg); } }
.state-icon { width: 38px; height: 38px; margin-bottom: 13px; color: var(--accent); }
.state-icon svg { display: block; width: 100%; height: 100%; }
.state-title { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.state-text { color: var(--text-tertiary); font-size: 12px; }
.state-title + .state-text { margin-top: 7px; }
.table-wrapper { max-width: 100%; margin-bottom: 12px; overflow-x: auto; overscroll-behavior-x: contain; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.data-table { width: 100%; min-width: 1120px; border-collapse: separate; border-spacing: 0; color: var(--text-primary); font-size: 11px; text-align: left; }
.data-table th { height: 38px; padding: 9px 11px; color: var(--text-secondary); background: #f3f7f7; border-bottom: 1px solid var(--border-color); font-size: 9px; font-weight: 650; letter-spacing: .035em; white-space: nowrap; }
.data-table td { height: 44px; padding: 8px 11px; color: var(--text-primary); background: var(--bg-card); border-bottom: 1px solid #edf1f1; vertical-align: middle; transition: background .14s ease; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover td { background: #f7fbfb; }
.data-table tbody tr.row-selected td { background: var(--accent-soft); }
.th-check { width: 38px; text-align: center; }
.td-check { text-align: center; }
.cell-index { width: 42px; color: var(--text-tertiary) !important; font-family: var(--font-mono); font-size: 9px; text-align: center; }
.cell-url { max-width: 260px; overflow: hidden; color: var(--text-secondary) !important; text-overflow: ellipsis; white-space: nowrap; }
.cell-remark { max-width: 190px; overflow: hidden; color: var(--text-secondary) !important; text-overflow: ellipsis; white-space: nowrap; }
.cell-mono { max-width: 190px; overflow: hidden; color: var(--text-secondary) !important; font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.cell-date { color: var(--text-secondary) !important; font-family: var(--font-mono); font-size: 10px; white-space: nowrap; }
.cell-num { font-family: var(--font-mono); font-weight: 650; text-align: right; }
.tag-exported,
.tag-unexported,
.exception-tag { display: inline-flex; align-items: center; padding: 3px 7px; border: 1px solid; border-radius: 5px; font-size: 9px; font-weight: 650; white-space: nowrap; }
.tag-exported { color: #16715d; background: #eef9f5; border-color: #c9e9dd; }
.tag-unexported { color: #946214; background: #fff8e9; border-color: #eeddb8; }
.exception-tag { color: #946214; background: #fff8e9; border-color: #eeddb8; }
.exception-tag.ex-正常,
.exception-tag.ex-验证已解决 { color: #16715d; background: #eef9f5; border-color: #c9e9dd; }
.pagination { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px; padding: 5px 0; }
.page-btn { color: var(--text-secondary); background: var(--bg-card); border-color: var(--border-color); }
.page-btn:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.page-info { color: var(--text-secondary); font-family: var(--font-mono); font-size: 10px; }
.page-size-label { display: inline-flex; align-items: center; gap: 5px; margin-left: 6px; color: var(--text-secondary); font-size: 10px; }
.page-size-input { width: 55px; height: 32px; padding: 0 6px; color: var(--text-primary); background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 7px; outline: none; font-family: var(--font-mono); font-size: 10px; text-align: center; }
.page-size-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.sup-area { width: 20px; height: 15px; color: transparent; font-size: 10px; user-select: none; }
@media (max-width: 1000px) {
  .filter-top-row { align-items: flex-start; flex-direction: column; }
  .filter-top-actions { width: 100%; }
}
@media (max-width: 700px) {
  .admin-page { padding: 0 0 30px; }
  .filter-card { padding: 14px; }
  .filter-top-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-input { width: 100%; }
  .filter-actions { grid-column: 1 / -1; }
  .filter-actions .btn-search { flex: 1; }
  .export-action-left { flex-basis: 100%; }
  .export-action-right { width: 100%; }
  .download-section-inline { width: 100%; justify-content: space-between; }
  .download-file-name { max-width: 55vw; }
  .section-caption { align-items: flex-start; flex-direction: column; gap: 3px; }
}
@media (max-width: 480px) {
  .page-header { align-items: flex-start; flex-direction: column; gap: 9px; }
  .page-header h2 { font-size: 21px; }
  .user-context { max-width: 100%; align-items: flex-start; }
  .metric-link { width: 100%; }
  .metric-link-action { margin-left: auto; }
  .filter-card { padding: 13px; border-radius: var(--radius); }
  .radio-group { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .radio-tag { justify-content: center; }
  .export-action-bar { padding: 10px; }
  .export-action-left .btn-action { flex: 1 1 calc(50% - 5px); }
  .export-action-left .btn-refresh-unexported { flex-basis: 100%; }
  .export-tip { width: 100%; }
  .result-toolbar { align-items: flex-start; flex-direction: column; }
  .page-info { width: 100%; order: -1; text-align: center; }
  .page-size-label { margin-left: 0; }
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .filter-card,
  .export-action-bar { background: var(--bg-card); }
}
@media (prefers-reduced-motion: reduce) {
  .admin-page * { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
