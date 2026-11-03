<!-- 文件路径: src/main/frontend/src/components/AuditLogPanel.vue -->
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { searchAuditLogs, cleanAuditLogs } from '../api/index.js'

const emit = defineEmits(['error'])

const list = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)
const pageSizeInput = ref(15)
const queried = ref(false)

const filters = reactive({
  operator: '',
  action: '',
  dateFrom: '',
  dateTo: ''
})

const cleaning = ref(false)
const cleanDays = ref(90)

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize.value))

async function fetchData(resetPage = false) {
  if (resetPage) currentPage.value = 1
  loading.value = true
  queried.value = true
  try {
    const json = await searchAuditLogs({
      operator: filters.operator.trim() || null,
      action: filters.action.trim() || null,
      dateFrom: filters.dateFrom || null,
      dateTo: filters.dateTo || null,
      page: currentPage.value,
      size: pageSize.value
    })
    if (json.success) {
      list.value = json.data || []
      total.value = json.total || 0
    } else {
      emit('error', json.message || '查询失败')
      list.value = []
    }
  } catch (e) {
    emit('error', '查询请求失败：' + e.message)
    list.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.operator = ''
  filters.action = ''
  filters.dateFrom = ''
  filters.dateTo = ''
  currentPage.value = 1
  fetchData(true)
}

function prevPage() { if (currentPage.value > 1) { currentPage.value--; fetchData() } }
function nextPage() { if (currentPage.value < totalPages()) { currentPage.value++; fetchData() } }

function applyPageSize() {
  const v = parseInt(pageSizeInput.value)
  if (v > 0) { pageSize.value = v; currentPage.value = 1; fetchData(true) }
}

async function handleClean() {
  if (!confirm(`确定要清理 ${cleanDays.value} 天前的审计日志吗？此操作不可撤销！`)) return
  cleaning.value = true
  try {
    const json = await cleanAuditLogs(cleanDays.value)
    if (json.success) {
      alert('✅ ' + json.message)
      await fetchData()
    } else {
      emit('error', json.message || '清理失败')
    }
  } catch (e) {
    emit('error', '清理请求失败：' + e.message)
  } finally {
    cleaning.value = false
  }
}

function formatDuration(ms) {
  if (ms == null) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(2) + 's'
}

onMounted(() => { fetchData() })
</script>

<template>
  <div class="audit-page">
    <div class="page-header">
      <div class="page-heading">
        <span class="page-kicker">AUDIT TRAIL / COMPLIANCE</span>
        <h2>操作审计日志</h2>
        <div class="page-header-sub">追踪关键用户操作、接口耗时与执行结果</div>
      </div>
      <span class="header-signal"><i></i>TRACE ACTIVE</span>
    </div>

    <div class="filter-card">
      <div class="filter-card-heading">
        <span>检索条件</span>
        <span class="filter-card-code">QUERY BUILDER</span>
      </div>
      <div class="filter-grid">
        <div class="filter-field">
          <label class="field-label">操作人</label>
          <input v-model="filters.operator" class="filter-input" placeholder="用户名搜索" @keyup.enter="fetchData(true)" />
        </div>
        <div class="filter-field">
          <label class="field-label">操作描述</label>
          <input v-model="filters.action" class="filter-input" placeholder="操作名称搜索" @keyup.enter="fetchData(true)" />
        </div>
        <div class="filter-field">
          <label class="field-label">开始日期</label>
          <input v-model="filters.dateFrom" type="date" class="filter-input filter-date" />
        </div>
        <div class="filter-field">
          <label class="field-label">结束日期</label>
          <input v-model="filters.dateTo" type="date" class="filter-input filter-date" />
        </div>
      </div>
      <div class="filter-action-row">
        <button class="btn-action btn-search" @click="fetchData(true)" :disabled="loading">
          {{ loading ? '查询中...' : '查询日志' }}
        </button>
        <button class="btn-action btn-reset" @click="resetFilters">重置条件</button>
        <div class="clean-section">
          <span class="clean-label">清理</span>
          <input v-model.number="cleanDays" type="number" class="clean-input" min="1" max="365" />
          <span class="clean-label">天前的日志</span>
          <button class="btn-action btn-clean" @click="handleClean" :disabled="cleaning">
            {{ cleaning ? '清理中...' : '执行清理' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="queried" class="result-toolbar">
      <span class="result-count">检索结果 <strong>{{ total }}</strong> 条</span>
      <span class="result-page">PAGE {{ currentPage }} / {{ totalPages() }}</span>
    </div>

    <div v-if="loading && !queried" class="state-block">
      <div class="state-spinner"></div>
      <div class="state-text">正在查询...</div>
    </div>
    <div v-if="!loading && queried && list.length === 0" class="state-block">
      <div class="state-icon">0</div>
      <div class="state-text">没有符合条件的审计记录</div>
      <div class="state-hint">调整操作人、日期范围或操作描述后重试</div>
    </div>

    <div v-if="list.length > 0" class="table-wrapper">
      <table class="data-table">
        <thead>
        <tr>
          <th>#</th><th>操作人</th><th>角色</th><th>操作</th><th>接口</th>
          <th>IP</th><th>耗时</th><th>结果</th><th>时间</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(item, index) in list" :key="item.id">
          <td class="cell-index">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
          <td><span class="operator-tag">{{ item.operator }}</span></td>
          <td><span class="role-tag" :class="'role-' + (item.operatorType || 'UNKNOWN').toLowerCase()">{{ item.operatorType || '-' }}</span></td>
          <td class="cell-action">{{ item.action || '-' }}</td>
          <td class="cell-uri" :title="item.uri">{{ item.uri || '-' }}</td>
          <td class="cell-mono">{{ item.ip || '-' }}</td>
          <td class="cell-num">{{ formatDuration(item.durationMs) }}</td>
          <td>
            <span :class="item.success ? 'tag-ok' : 'tag-fail'">{{ item.success ? '成功' : '失败' }}</span>
          </td>
          <td class="cell-time">{{ item.createdAt || '-' }}</td>
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
.audit-page {
  width: 100%;
  max-width: 1600px;
  min-width: 0;
  margin: 0 auto;
  padding: 0 20px 40px;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
  padding: 4px 0 18px;
  border-bottom: 1px solid var(--border-color);
}
.page-heading { min-width: 0; }
.page-kicker {
  display: block;
  margin-bottom: 7px;
  color: var(--accent);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.7px;
}
.page-header h2 { margin: 0; font-size: 24px; line-height: 1.25; letter-spacing: -0.6px; font-weight: 800; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 13px; }
.header-signal {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
  padding: 7px 11px;
  border: 1px solid rgba(15, 143, 131, 0.15);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: var(--accent-strong);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.85);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1px;
}
.header-signal i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px var(--accent-glow); }
.filter-card {
  position: relative;
  margin-bottom: 16px;
  padding: 20px;
  overflow: hidden;
  border: 1px solid rgba(203, 220, 215, 0.82);
  border-radius: var(--radius-lg);
  background: linear-gradient(125deg, rgba(255,255,255,0.88), rgba(248,252,250,0.7));
  box-shadow: var(--glass-shadow, var(--shadow-sm));
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}
.filter-card::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 3px; background: linear-gradient(var(--accent-light), var(--accent)); }
.filter-card-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; font-size: 13px; font-weight: 800; }
.filter-card-code { color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; letter-spacing: 1.1px; }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.filter-field { display: flex; min-width: 0; flex-direction: column; gap: 7px; }
.field-label { color: var(--text-secondary); font-size: 11px; font-weight: 700; letter-spacing: 0.3px; }
.filter-input, .clean-input, .page-size-input {
  width: 100%;
  min-width: 0;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  outline: none;
  background: rgba(255,255,255,0.82);
  color: var(--text-primary);
  font-size: 13px;
  transition: border-color var(--transition), box-shadow var(--transition), background var(--transition);
}
.filter-input:focus, .clean-input:focus, .page-size-input:focus { border-color: var(--accent-light); background: #fff; box-shadow: 0 0 0 3px var(--accent-glow); }
.filter-date { cursor: pointer; }
.filter-action-row { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.btn-action, .page-btn {
  min-height: 38px;
  padding: 8px 17px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  transition: transform var(--transition), box-shadow var(--transition), background var(--transition);
}
.btn-action:disabled, .page-btn:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }
.btn-search { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: 0 7px 18px rgba(15,143,131,0.17), inset 0 1px 0 rgba(255,255,255,0.22); }
.btn-search:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 10px 24px rgba(15,143,131,0.24); }
.btn-reset { border-color: var(--border-color); background: rgba(255,255,255,0.72); color: var(--text-secondary); }
.btn-reset:hover { background: #fff; color: var(--text-primary); }
.clean-section { display: flex; align-items: center; gap: 7px; margin-left: auto; padding-left: 16px; border-left: 1px solid var(--border-color); }
.clean-label { color: var(--text-secondary); font-size: 12px; white-space: nowrap; }
.clean-input { width: 64px; min-height: 36px; text-align: center; }
.btn-clean { min-height: 36px; border-color: rgba(213,82,82,0.17); background: var(--danger-soft); color: var(--danger); }
.btn-clean:hover:not(:disabled) { background: #ffe5e2; }
.result-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; padding: 11px 15px; border: 1px solid var(--border-color); border-radius: var(--radius); background: rgba(255,255,255,0.56); }
.result-count { color: var(--text-secondary); font-size: 12px; }
.result-count strong { margin-left: 5px; color: var(--accent-strong); font-family: var(--font-mono); font-size: 16px; }
.result-page { color: var(--text-secondary); font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.6px; }
.state-block { padding: 64px 20px; border: 1px dashed var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.46); text-align: center; }
.state-icon { display: grid; width: 46px; height: 46px; margin: 0 auto 13px; place-items: center; border: 1px solid var(--border-color); border-radius: 14px; background: var(--accent-soft); color: var(--accent); font-family: var(--font-mono); font-size: 20px; font-weight: 700; }
.state-text { color: var(--text-secondary); font-size: 14px; font-weight: 600; }
.state-hint { margin-top: 7px; color: var(--text-tertiary); font-size: 12px; }
.state-spinner { width: 30px; height: 30px; margin: 0 auto 13px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.table-wrapper { max-width: 100%; margin-bottom: 16px; overflow-x: auto; overscroll-behavior-inline: contain; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-sm); }
.data-table { width: 100%; min-width: 1060px; border-collapse: separate; border-spacing: 0; font-size: 12px; text-align: left; }
.data-table th { padding: 13px 14px; border-bottom: 1px solid var(--border-color); background: #f2f8f6; color: var(--text-secondary); white-space: nowrap; font-size: 10px; font-weight: 800; letter-spacing: 0.55px; }
.data-table td { padding: 13px 14px; border-bottom: 1px solid #edf3f1; color: var(--text-primary); vertical-align: middle; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover td { background: #f7fbfa; }
.cell-index { width: 46px; color: var(--text-tertiary); font-family: var(--font-mono); text-align: center; }
.cell-action { max-width: 180px; font-weight: 650; }
.cell-uri { max-width: 230px; overflow: hidden; color: var(--text-secondary); font-family: var(--font-mono); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.cell-mono, .cell-time { color: var(--text-secondary); font-family: var(--font-mono); font-size: 11px; white-space: nowrap; }
.cell-num { font-family: var(--font-mono); font-weight: 700; text-align: center; }
.operator-tag, .role-tag, .tag-ok, .tag-fail { display: inline-flex; align-items: center; min-height: 24px; padding: 3px 9px; border-radius: 999px; font-size: 10px; font-weight: 750; white-space: nowrap; }
.operator-tag { background: var(--accent-soft); color: var(--accent-strong); }
.role-user { background: var(--success-soft); color: var(--success); }
.role-admin { background: var(--warning-soft); color: var(--warning); }
.role-developer { background: var(--info-soft); color: var(--info); }
.role-unknown { background: #f0f4f3; color: var(--text-secondary); }
.tag-ok { background: var(--success-soft); color: var(--success); }
.tag-fail { background: var(--danger-soft); color: var(--danger); }
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; flex-wrap: wrap; padding: 12px 0; }
.page-btn { border-color: var(--border-color); background: rgba(255,255,255,0.76); color: var(--text-primary); box-shadow: var(--shadow-sm); }
.page-btn:hover:not(:disabled) { border-color: var(--accent-light); color: var(--accent-strong); transform: translateY(-1px); }
.page-info { color: var(--text-secondary); font-size: 12px; }
.page-size-label { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 12px; }
.page-size-input { width: 62px; min-height: 36px; text-align: center; }
@media (max-width: 1000px) {
  .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .clean-section { width: 100%; margin: 4px 0 0; padding: 14px 0 0; border-top: 1px solid var(--border-color); border-left: 0; }
}
@media (max-width: 700px) {
  .audit-page { padding: 0 14px 28px; }
  .page-header { align-items: flex-start; gap: 14px; }
  .page-header h2 { font-size: 21px; }
  .header-signal { margin-top: 3px; }
  .filter-card { padding: 17px; border-radius: var(--radius); }
  .filter-grid { gap: 12px; }
  .clean-section { flex-wrap: wrap; }
  .pagination { gap: 9px; }
  .page-info { order: -1; width: 100%; text-align: center; }
}
@media (max-width: 480px) {
  .audit-page { padding-inline: 10px; }
  .page-header { display: block; }
  .header-signal { margin-top: 13px; }
  .page-kicker { font-size: 9px; letter-spacing: 1.3px; }
  .filter-grid { grid-template-columns: 1fr; }
  .filter-card-heading { align-items: flex-start; gap: 8px; }
  .filter-action-row .btn-action { flex: 1; }
  .clean-section .btn-clean { width: 100%; }
  .result-toolbar { align-items: flex-start; flex-direction: column; gap: 5px; }
  .page-size-label { width: 100%; justify-content: center; margin-top: 4px; }
}
</style>
