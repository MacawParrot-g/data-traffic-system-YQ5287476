<template>
  <div class="grade-page">
    <header class="grade-header">
      <div class="grade-heading">
        <span class="page-eyebrow">APPLICATION QUALITY</span>
        <h2>应用评级管理</h2>
        <span class="grade-header-sub">管理所有应用的评级与测试要求</span>
      </div>
      <span class="header-code" aria-hidden="true">GRADE / 04</span>
    </header>

    <section class="grade-filter" aria-label="评级筛选">
      <div class="section-caption">
        <span>筛选条件</span>
        <small>按应用、等级或记录信息定位数据</small>
      </div>
      <div class="filter-row">
        <div class="filter-field">
          <label>Bundle ID</label>
          <input v-model="filters.bundleId" class="filter-input" placeholder="精准搜索 Bundle ID" @keyup.enter="doSearch(true)" />
        </div>
        <div class="filter-field">
          <label>评级等级</label>
          <select v-model="filters.grade" class="filter-input">
            <option value="">全部</option>
            <option v-for="g in ['A','B','C','D']" :key="g" :value="g">{{ g }}级</option>
          </select>
        </div>
        <div class="filter-field">
          <label>记录人</label>
          <input v-model="filters.recorder" class="filter-input" placeholder="输入记录人姓名" @keyup.enter="doSearch(true)" />
        </div>
        <div class="filter-field">
          <label>备注关键字</label>
          <input v-model="filters.keyword" class="filter-input" placeholder="模糊搜索备注" @keyup.enter="doSearch(true)" />
        </div>
        <div class="filter-actions">
          <button class="btn-search" @click="doSearch(true)" :disabled="loading">
            {{ loading ? '查询中...' : '查询' }}
          </button>
          <button class="btn-reset" @click="resetFilters">重置</button>
        </div>
      </div>
    </section>

    <div v-if="queried" class="result-bar">
      <span class="result-indicator" aria-hidden="true"></span>
      <span>共 <strong>{{ total }}</strong> 条评级记录</span>
      <span class="result-divider" aria-hidden="true"></span>
      <span>第 {{ currentPage }}/{{ totalPages }} 页</span>
    </div>

    <div v-if="loading && list.length === 0" class="state-block">
      <div class="state-spinner"></div>
      <div class="state-text">正在查询...</div>
    </div>

    <div v-if="!loading && queried && list.length === 0" class="state-block">
      <div class="state-icon" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none"><path d="M5 8.5h22v17H5zM5 12l11 8 11-8" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M10 5h12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </div>
      <div class="state-title">没有找到评级记录</div>
      <div class="state-text">调整筛选条件后重新查询</div>
    </div>

    <div v-if="list.length > 0" class="grade-table-wrap">
      <table class="grade-table">
        <thead>
        <tr>
          <th>#</th>
          <th>Bundle ID</th>
          <th>评级</th>
          <th>记录人</th>
          <th>备注</th>
          <th>操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(item, idx) in list" :key="item.bundleId" :class="{ 'row-editing': editingBundleId === item.bundleId }">
          <td class="cell-index">{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
          <td class="cell-url" :title="item.bundleId">{{ item.bundleId }}</td>
          <td>
            <span v-if="editingBundleId !== item.bundleId" class="grade-badge" :class="'grade-' + item.grade.toLowerCase()">{{ item.grade }}级</span>
            <select v-else v-model="editForm.grade" class="edit-select">
              <option v-for="g in ['A','B','C','D']" :key="g" :value="g">{{ g }}级</option>
            </select>
          </td>
          <td>{{ item.recorder || '-' }}</td>
          <td class="cell-remark" :title="editingBundleId !== item.bundleId ? item.remark : ''">
            <span v-if="editingBundleId !== item.bundleId">{{ item.remark || '-' }}</span>
            <input v-else v-model="editForm.remark" class="edit-input" placeholder="备注" />
          </td>
          <td class="cell-actions">
            <template v-if="editingBundleId !== item.bundleId">
              <button class="btn-sm btn-edit" @click="startEdit(item)">编辑</button>
              <button class="btn-sm btn-del" @click="handleDelete(item)" :disabled="editingBundleId !== null">删除</button>
            </template>
            <template v-else>
              <button class="btn-sm btn-save" @click="handleUpdate(item.bundleId)" :disabled="saving">
                {{ saving ? '保存中...' : '保存' }}
              </button>
              <button class="btn-sm btn-cancel" @click="cancelEdit">取消</button>
            </template>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 1" class="pagination">
      <button class="page-btn" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button class="page-btn" :disabled="currentPage >= totalPages" @click="nextPage">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { gradeManageSearch, gradeManageDelete, gradeManageUpdate } from '../api/index.js'

const emit = defineEmits(['error'])

const filters = reactive({ bundleId: '', grade: '', recorder: '', keyword: '' })
const list = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)
const loading = ref(false)
const queried = ref(false)

const editingUrl = ref(null)
const editForm = reactive({ grade: '', remark: '' })
const saving = ref(false)
const editingBundleId = ref(null)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

async function doSearch(resetPage = false) {
  if (resetPage) currentPage.value = 1
  loading.value = true
  queried.value = true
  try {
    const json = await gradeManageSearch({
      bundleId: filters.bundleId.trim() || null,
      grade: filters.grade || null,
      recorder: filters.recorder.trim() || null,
      keyword: filters.keyword.trim() || null,
      page: currentPage.value,
      size: pageSize.value
    })
    if (json.success && json.data) {
      list.value = json.data.list || []
      total.value = json.data.total || 0
    } else {
      emit('error', json.message || '查询失败')
      list.value = []
      total.value = 0
    }
  } catch (e) {
    emit('error', '查询请求失败：' + e.message)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.bundleId = ''
  filters.grade = ''
  filters.recorder = ''
  filters.keyword = ''
  doSearch(true)
}


function startEdit(item) {
  editingBundleId.value = item.bundleId
  editForm.grade = item.grade
  editForm.remark = item.remark || ''
}

function cancelEdit() {
  editingBundleId.value = null
  editForm.grade = ''
  editForm.remark = ''
}

async function handleUpdate(bundleId) {
  if (!editForm.grade) {
    emit('error', '请选择评级等级')
    return
  }
  saving.value = true
  try {
    const json = await gradeManageUpdate(bundleId, editForm.grade, editForm.remark.trim())
    if (json.success) {
      editingBundleId.value = null
      await doSearch()
    } else {
      emit('error', json.message || '更新失败')
    }
  } catch (e) {
    emit('error', '更新请求失败：' + e.message)
  } finally {
    saving.value = false
  }
}

async function handleDelete(item) {
  if (!confirm(`确定要删除该评级记录吗？\nBundle ID: ${item.bundleId}\n评级: ${item.grade}级\n\n此操作将同时从数据库和Redis缓存中删除。`)) return
  try {
    const json = await gradeManageDelete(item.bundleId)
    if (json.success) {
      await doSearch()
    } else {
      emit('error', json.message || '删除失败')
    }
  } catch (e) {
    emit('error', '删除请求失败：' + e.message)
  }
}

function prevPage() { if (currentPage.value > 1) { currentPage.value--; doSearch() } }
function nextPage() { if (currentPage.value < totalPages.value) { currentPage.value++; doSearch() } }

onMounted(() => { doSearch(true) })
</script>

<style scoped>
.grade-page,
.grade-page * { box-sizing: border-box; }
.grade-page { width: 100%; max-width: 1400px; margin: 0 auto; padding-bottom: 36px; color: var(--text-primary); font-family: var(--font-sans); }
.grade-page button,
.grade-page input,
.grade-page select { font-family: inherit; }
.grade-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 18px; padding-bottom: 17px; border-bottom: 1px solid var(--border-color); }
.grade-heading { min-width: 0; }
.page-eyebrow { display: block; margin-bottom: 7px; color: var(--accent); font-family: var(--font-mono); font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.grade-header h2 { margin: 0; color: var(--text-primary); font-size: 23px; font-weight: 700; letter-spacing: -.025em; }
.grade-header-sub { display: block; margin-top: 7px; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }
.header-code { flex: 0 0 auto; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 10px; letter-spacing: .08em; }
.grade-filter,
.tpl-filter {
  position: relative;
  isolation: isolate;
  margin-bottom: 16px;
  padding: 16px;
  background: rgba(255,255,255,.78);
  border: 1px solid rgba(215,227,225,.9);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.95);
  backdrop-filter: blur(14px) saturate(120%);
  -webkit-backdrop-filter: blur(14px) saturate(120%);
}
.section-caption { display: flex; align-items: baseline; gap: 10px; margin-bottom: 13px; }
.section-caption > span { color: var(--text-primary); font-size: 12px; font-weight: 700; }
.section-caption small { color: var(--text-tertiary); font-size: 10px; }
.filter-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 10px; }
.filter-field { display: flex; flex: 1 1 165px; min-width: 0; flex-direction: column; gap: 6px; }
.filter-field:first-child { flex-grow: 1.35; }
.filter-field label { color: var(--text-secondary); font-size: 10px; font-weight: 600; letter-spacing: .02em; }
.filter-input {
  width: 100%;
  min-width: 0;
  height: 38px;
  padding: 0 11px;
  color: var(--text-primary);
  background: rgba(251,253,253,.94);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  outline: none;
  font-size: 12px;
  transition: border-color .16s ease, box-shadow .16s ease, background .16s ease;
}
.filter-input::placeholder { color: var(--text-tertiary); }
.filter-input:hover { border-color: var(--accent-light); }
.filter-input:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
select.filter-input { cursor: pointer; }
.filter-actions { display: flex; flex: 0 0 auto; gap: 7px; }
.btn-search,
.btn-reset,
.page-btn,
.btn-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--radius);
  outline: none;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background .16s ease, border-color .16s ease, color .16s ease, box-shadow .16s ease;
}
.btn-search,
.btn-reset { min-height: 38px; padding: 8px 16px; }
.btn-search { color: #fff; background: var(--accent); border-color: var(--accent); box-shadow: inset 0 1px 0 rgba(255,255,255,.2); }
.btn-search:hover:not(:disabled) { background: var(--accent-strong); border-color: var(--accent-strong); }
.btn-reset { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-reset:hover { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-search:focus-visible,
.btn-reset:focus-visible,
.page-btn:focus-visible,
.btn-sm:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.btn-search:disabled,
.page-btn:disabled,
.btn-sm:disabled { opacity: .45; cursor: not-allowed; }
.result-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 2px 12px; color: var(--text-secondary); font-size: 11px; }
.result-bar strong { margin: 0 2px; color: var(--accent-strong); font-family: var(--font-mono); font-size: 14px; }
.result-indicator { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); }
.result-divider { width: 1px; height: 12px; margin: 0 3px; background: var(--border-color); }
.state-block { display: flex; min-height: 230px; align-items: center; justify-content: center; flex-direction: column; padding: 38px 20px; background: var(--bg-card); border: 1px dashed var(--border-color); border-radius: var(--radius-lg); text-align: center; }
.state-spinner { width: 28px; height: 28px; margin-bottom: 13px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: grade-spin .75s linear infinite; }
@keyframes grade-spin { to { transform: rotate(360deg); } }
.state-icon { width: 38px; height: 38px; margin-bottom: 13px; color: var(--accent); }
.state-icon svg { display: block; width: 100%; height: 100%; }
.state-title { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.state-text { margin-top: 7px; color: var(--text-tertiary); font-size: 12px; }
.grade-table-wrap { max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.grade-table { width: 100%; min-width: 860px; border-collapse: separate; border-spacing: 0; color: var(--text-primary); font-size: 12px; text-align: left; }
.grade-table th { height: 38px; padding: 9px 13px; color: var(--text-secondary); background: #f3f7f7; border-bottom: 1px solid var(--border-color); font-size: 10px; font-weight: 650; letter-spacing: .035em; white-space: nowrap; }
.grade-table td { height: 46px; padding: 9px 13px; color: var(--text-primary); background: var(--bg-card); border-bottom: 1px solid #edf1f1; vertical-align: middle; transition: background .14s ease; }
.grade-table tbody tr:last-child td { border-bottom: 0; }
.grade-table tbody tr:hover td { background: #f7fbfb; }
.grade-table tbody tr.row-editing td { background: var(--accent-soft); }
.cell-index { width: 48px; color: var(--text-tertiary) !important; font-family: var(--font-mono); font-size: 10px; text-align: center; }
.cell-url { max-width: 280px; overflow: hidden; color: var(--text-secondary) !important; font-family: var(--font-mono); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.cell-remark { max-width: 300px; overflow: hidden; color: var(--text-secondary) !important; text-overflow: ellipsis; white-space: nowrap; }
.cell-actions { min-width: 138px; white-space: nowrap; }
.grade-badge { display: inline-flex; min-width: 43px; align-items: center; justify-content: center; padding: 3px 9px; border: 1px solid; border-radius: 6px; font-family: var(--font-mono); font-size: 11px; font-weight: 700; }
.grade-a { color: #16715d; background: #eef9f5; border-color: #c9e9dd; }
.grade-b { color: #27677d; background: #eff7fa; border-color: #cfe3ea; }
.grade-c { color: #946214; background: #fff8e9; border-color: #eeddb8; }
.grade-d { color: #a34842; background: #fff3f2; border-color: #efceca; }
.edit-select,
.edit-input { height: 30px; padding: 4px 8px; color: var(--text-primary); background: #fff; border: 1px solid var(--accent); border-radius: 7px; outline: none; font-size: 11px; }
.edit-select:focus,
.edit-input:focus { box-shadow: 0 0 0 3px rgba(255,255,255,.75); }
.edit-input { width: min(100%, 240px); }
.btn-sm { min-height: 29px; margin-right: 5px; padding: 4px 9px; font-size: 11px; }
.btn-sm:last-child { margin-right: 0; }
.btn-edit { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-edit:hover { background: var(--accent-light); }
.btn-del { color: var(--danger); background: transparent; border-color: transparent; }
.btn-del:hover:not(:disabled) { background: #fff0ee; border-color: #f2cfcc; }
.btn-save { color: #fff; background: var(--accent); border-color: var(--accent); }
.btn-cancel { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.pagination { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.page-btn { min-height: 34px; padding: 6px 14px; color: var(--text-secondary); background: var(--bg-card); border-color: var(--border-color); }
.page-btn:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.page-info { color: var(--text-secondary); font-family: var(--font-mono); font-size: 11px; }
@media (max-width: 700px) {
  .grade-header { align-items: flex-start; }
  .header-code { display: none; }
  .grade-filter { padding: 14px; }
  .filter-field { flex-basis: calc(50% - 6px); }
  .filter-actions { width: 100%; margin-top: 2px; }
  .filter-actions .btn-search { flex: 1; }
  .section-caption { align-items: flex-start; flex-direction: column; gap: 3px; }
}
@media (max-width: 480px) {
  .grade-header h2 { font-size: 21px; }
  .filter-field { flex-basis: 100%; }
  .grade-filter { padding: 13px; border-radius: var(--radius); }
  .filter-actions .btn-reset { min-width: 82px; }
  .state-block { min-height: 200px; }
  .pagination { gap: 8px; }
  .page-info { width: 100%; order: -1; text-align: center; }
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .grade-filter { background: var(--bg-card); }
}
@media (prefers-reduced-motion: reduce) {
  .grade-page * { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
