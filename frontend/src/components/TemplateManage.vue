<template>
  <div class="tpl-page">
    <div class="tpl-header">
      <h2>备注管理</h2>
      <span class="tpl-header-sub">异常类型 / 备注模板 / 冻结理由 / 冻结理由屏蔽词 持久化管理，所有人可操作</span>
    </div>

    <div class="tpl-add-card">
      <div class="tpl-card-title">新增条目</div>
      <div class="tpl-add-row">
        <div class="filter-field">
          <label>类型</label>
          <select v-model="addForm.type" class="filter-input">
            <option value="" disabled>请选择类型</option>
            <option v-for="t in types" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="filter-field tpl-add-content">
          <label>内容</label>
          <input v-model="addForm.content" class="filter-input" maxlength="500" placeholder="输入内容，入库后自动生成hash" @keyup.enter="handleAdd" />
        </div>
        <div class="filter-field">
          <label>写入者（不可修改）</label>
          <input :value="writer" class="filter-input writer-input" disabled />
        </div>
        <button class="btn-search" @click="handleAdd" :disabled="adding">
          {{ adding ? '提交中...' : '新增' }}
        </button>
      </div>
      <div v-if="addMsg" class="tpl-feedback" :class="addMsgOk ? 'tpl-ok' : 'tpl-err'">{{ addMsg }}</div>
    </div>

    <div class="tpl-warm-card">
      <div class="tpl-card-title">Redis 预热（4号库）</div>
      <div class="tpl-add-row">
        <div class="filter-field">
          <label>TTL（分钟，默认120 = 2小时）</label>
          <input v-model.number="warmTtl" type="number" min="1" max="1440" class="filter-input" />
        </div>
        <button class="btn-search" @click="handleWarmUp" :disabled="warming">
          {{ warming ? '预热中...' : '手动预热' }}
        </button>
        <span class="tpl-warm-tip">MySQL 压力增大时系统会自动预热到 Redis db4；此处手动预热可自定义 TTL</span>
      </div>
      <div v-if="warmMsg" class="tpl-feedback" :class="warmMsgOk ? 'tpl-ok' : 'tpl-err'">{{ warmMsg }}</div>
    </div>

    <div class="tpl-filter">
      <div class="filter-row">
        <div class="filter-field">
          <label>类型</label>
          <select v-model="filters.type" class="filter-input">
            <option value="">全部</option>
            <option v-for="t in types" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="filter-field">
          <label>内容关键字</label>
          <input v-model="filters.keyword" class="filter-input" placeholder="模糊搜索内容" @keyup.enter="doSearch(true)" />
        </div>
        <div class="filter-field">
          <label>写入者</label>
          <input v-model="filters.writer" class="filter-input" placeholder="输入写入者姓名" @keyup.enter="doSearch(true)" />
        </div>
        <div class="filter-actions">
          <button class="btn-search" @click="doSearch(true)" :disabled="loading">
            {{ loading ? '查询中...' : '查询' }}
          </button>
          <button class="btn-reset" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <div v-if="queried" class="result-bar">
      <span>共 <strong>{{ total }}</strong> 条记录 · 第 {{ currentPage }}/{{ totalPages }} 页</span>
    </div>

    <div v-if="loading && list.length === 0" class="state-block">
      <div class="state-spinner"></div>
      <div class="state-text">正在查询...</div>
    </div>

    <div v-if="!loading && queried && list.length === 0" class="state-block">
      <div class="state-text">没有找到模板记录</div>
    </div>

    <div v-if="list.length > 0" class="tpl-table-wrap">
      <table class="tpl-table">
        <thead>
        <tr>
          <th>#</th>
          <th>hash</th>
          <th>内容</th>
          <th>类型</th>
          <th>写入者</th>
          <th>操作</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(item, idx) in list" :key="item.hash">
          <td>{{ (currentPage - 1) * pageSize + idx + 1 }}</td>
          <td class="cell-hash" :title="item.hash">{{ item.hash }}</td>
          <td class="cell-content">
            <span v-if="editingHash !== item.hash">{{ item.content }}</span>
            <input v-else v-model="editForm.content" class="edit-input" placeholder="内容" maxlength="500" />
          </td>
          <td>
            <span v-if="editingHash !== item.hash" class="tpl-type-badge" :class="'type-' + typeClass(item.type)">{{ item.type }}</span>
            <select v-else v-model="editForm.type" class="edit-select">
              <option v-for="t in types" :key="t" :value="t">{{ t }}</option>
            </select>
          </td>
          <td>{{ item.writer || '-' }}</td>
          <td class="cell-actions">
            <template v-if="editingHash !== item.hash">
              <button class="btn-sm btn-edit" @click="startEdit(item)">编辑</button>
              <button class="btn-sm btn-del" @click="handleDelete(item)">删除</button>
            </template>
            <template v-else>
              <button class="btn-sm btn-save" @click="handleUpdate" :disabled="saving">
                {{ saving ? '保存中...' : '保存' }}
              </button>
              <button class="btn-sm btn-cancel" @click="cancelEdit">取消</button>
            </template>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > pageSize" class="pagination">
      <button class="page-btn" :disabled="currentPage <= 1" @click="prevPage">‹ 上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button class="page-btn" :disabled="currentPage >= totalPages" @click="nextPage">下一页 ›</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  templateManageSearch, templateManageAdd,
  templateManageUpdate, templateManageDelete, templateWarmUp
} from '../api/index.js'
import { TEMPLATE_TYPES, invalidateTemplateCache, useTemplateOptions } from '../utils/useTemplateOptions.js'

const emit = defineEmits(['error'])

const { preloadAllTemplateOptions } = useTemplateOptions()

const types = TEMPLATE_TYPES
const writer = localStorage.getItem('userName') || ''

const addForm = reactive({ type: '', content: '' })
const adding = ref(false)
const addMsg = ref('')
const addMsgOk = ref(false)

const warmTtl = ref(120)
const warming = ref(false)
const warmMsg = ref('')
const warmMsgOk = ref(false)

const filters = reactive({ type: '', keyword: '', writer: '' })
const list = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)
const loading = ref(false)
const queried = ref(false)
const saving = ref(false)

const editingHash = ref(null)
const editForm = reactive({ content: '', type: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function typeClass(t) {
  if (t === '异常类型') return 'exc'
  if (t === '备注模板') return 'tpl'
  if (t === '冻结理由') return 'frozen'
  return 'forbidden'
}

function refreshTemplateCaches() {
  types.forEach(t => invalidateTemplateCache(t))
  preloadAllTemplateOptions()
}

async function doSearch(resetPage = false) {
  if (resetPage) currentPage.value = 1
  loading.value = true
  queried.value = true
  try {
    const json = await templateManageSearch({
      type: filters.type || null,
      keyword: filters.keyword.trim() || null,
      writer: filters.writer.trim() || null,
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
  filters.type = ''
  filters.keyword = ''
  filters.writer = ''
  doSearch(true)
}

async function handleAdd() {
  addMsg.value = ''
  if (!addForm.type) { addMsgOk.value = false; addMsg.value = '请选择类型'; return }
  if (!addForm.content.trim()) { addMsgOk.value = false; addMsg.value = '内容不能为空'; return }
  adding.value = true
  try {
    const json = await templateManageAdd(addForm.content.trim(), addForm.type)
    if (json.success) {
      addMsgOk.value = true
      addMsg.value = '新增成功'
      addForm.content = ''
      await doSearch()
      refreshTemplateCaches()
    } else {
      addMsgOk.value = false
      addMsg.value = json.message || '新增失败'
    }
  } catch (e) {
    addMsgOk.value = false
    addMsg.value = '新增请求失败：' + e.message
  } finally {
    adding.value = false
  }
}

function startEdit(item) {
  editingHash.value = item.hash
  editForm.content = item.content
  editForm.type = item.type
}

function cancelEdit() {
  editingHash.value = null
  editForm.content = ''
  editForm.type = ''
}

async function handleUpdate() {
  if (!editForm.content.trim()) { emit('error', '内容不能为空'); return }
  saving.value = true
  try {
    const json = await templateManageUpdate(editingHash.value, editForm.content.trim(), editForm.type)
    if (json.success) {
      cancelEdit()
      await doSearch()
      refreshTemplateCaches()
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
  if (!confirm(`确定要删除该条记录吗？\n类型: ${item.type}\n内容: ${item.content}\n\n此操作将同时删除 MySQL 记录并使 Redis 缓存失效。`)) return
  try {
    const json = await templateManageDelete(item.hash)
    if (json.success) {
      await doSearch()
      refreshTemplateCaches()
    } else {
      emit('error', json.message || '删除失败')
    }
  } catch (e) {
    emit('error', '删除请求失败：' + e.message)
  }
}

async function handleWarmUp() {
  warmMsg.value = ''
  if (!warmTtl.value || warmTtl.value < 1 || warmTtl.value > 1440) {
    warmMsgOk.value = false
    warmMsg.value = 'TTL 需在 1 ~ 1440 分钟之间'
    return
  }
  warming.value = true
  try {
    const json = await templateWarmUp(warmTtl.value * 60)
    warmMsgOk.value = !!json.success
    warmMsg.value = json.success ? (json.message || '预热成功') : (json.message || '预热失败')
  } catch (e) {
    warmMsgOk.value = false
    warmMsg.value = '预热请求失败：' + e.message
  } finally {
    warming.value = false
  }
}

function prevPage() { if (currentPage.value > 1) { currentPage.value--; doSearch() } }
function nextPage() { if (currentPage.value < totalPages.value) { currentPage.value++; doSearch() } }

onMounted(() => { doSearch(true) })
</script>

<style scoped>
.tpl-page,
.tpl-page * { box-sizing: border-box; }
.tpl-page { width: 100%; max-width: 1400px; margin: 0 auto; padding-bottom: 36px; color: var(--text-primary); font-family: var(--font-sans); }
.tpl-page button,
.tpl-page input,
.tpl-page select { font-family: inherit; }
.tpl-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 18px; padding-bottom: 17px; border-bottom: 1px solid var(--border-color); }
.tpl-heading { min-width: 0; }
.page-eyebrow { display: block; margin-bottom: 7px; color: var(--accent); font-family: var(--font-mono); font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.tpl-header h2 { margin: 0; color: var(--text-primary); font-size: 23px; font-weight: 700; letter-spacing: -.025em; }
.tpl-header-sub { display: block; max-width: 900px; margin-top: 7px; color: var(--text-secondary); font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
.header-code { flex: 0 0 auto; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 10px; letter-spacing: .08em; }
.tpl-operations-grid { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(310px, .85fr); gap: 14px; margin-bottom: 14px; }
.tpl-add-card,
.tpl-warm-card { min-width: 0; padding: 17px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.8); }
.tpl-section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.section-kicker { display: block; margin-bottom: 4px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 9px; font-weight: 600; letter-spacing: .1em; }
.tpl-card-title { color: var(--text-primary); font-size: 13px; font-weight: 700; }
.section-index { color: var(--accent); font-family: var(--font-mono); font-size: 18px; font-weight: 500; line-height: 1; }
.cache-live { display: inline-flex; align-items: center; gap: 5px; padding: 3px 7px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 5px; font-family: var(--font-mono); font-size: 9px; font-weight: 700; }
.cache-live i { width: 5px; height: 5px; background: var(--accent); border-radius: 50%; }
.tpl-add-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 10px; }
.filter-field { display: flex; flex: 0 1 170px; min-width: 0; flex-direction: column; gap: 6px; }
.filter-field label { color: var(--text-secondary); font-size: 10px; font-weight: 600; line-height: 1.4; }
.tpl-add-content { flex: 1 1 240px; }
.filter-input { width: 100%; min-width: 0; height: 38px; padding: 0 11px; color: var(--text-primary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; font-size: 12px; transition: border-color .16s ease, box-shadow .16s ease, background .16s ease; }
.filter-input::placeholder { color: var(--text-tertiary); }
.filter-input:hover { border-color: var(--accent-light); }
.filter-input:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
select.filter-input { cursor: pointer; }
.writer-input { color: var(--text-tertiary); background: #f3eee4; cursor: not-allowed; }
.tpl-warm-row .filter-field { flex: 1 1 170px; }
.tpl-warm-tip { margin: 12px 0 0; padding-top: 11px; color: var(--text-tertiary); border-top: 1px solid #efe9de; font-size: 10px; line-height: 1.65; }
.tpl-feedback { display: flex; align-items: center; gap: 7px; margin-top: 12px; padding: 9px 11px; border: 1px solid; border-radius: var(--radius); font-size: 11px; font-weight: 550; line-height: 1.5; overflow-wrap: anywhere; }
.tpl-feedback::before { flex: 0 0 auto; width: 6px; height: 6px; background: currentColor; border-radius: 50%; content: ""; }
.tpl-ok { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.tpl-err { color: var(--danger); background: #fbeee7; border-color: #f2cfcc; }
.tpl-filter { position: relative; isolation: isolate; margin-bottom: 16px; padding: 16px; background: rgba(255,255,255,.78); border: 1px solid rgba(215,227,225,.9); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.95); backdrop-filter: blur(14px) saturate(120%); -webkit-backdrop-filter: blur(14px) saturate(120%); }
.section-caption { display: flex; align-items: baseline; gap: 10px; margin-bottom: 13px; }
.section-caption > span { color: var(--text-primary); font-size: 12px; font-weight: 700; }
.section-caption small { color: var(--text-tertiary); font-size: 10px; }
.filter-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 10px; }
.filter-row .filter-field { flex: 1 1 170px; }
.filter-row .filter-field-grow { flex-grow: 1.6; }
.filter-actions { display: flex; flex: 0 0 auto; gap: 7px; }
.btn-search,
.btn-reset,
.page-btn,
.btn-sm { display: inline-flex; align-items: center; justify-content: center; border: 1px solid transparent; border-radius: var(--radius); outline: none; font-size: 12px; font-weight: 600; white-space: nowrap; cursor: pointer; transition: background .16s ease, border-color .16s ease, color .16s ease, box-shadow .16s ease; }
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
.result-indicator { width: 6px; height: 6px; background: var(--accent); border-radius: 50%; }
.result-divider { width: 1px; height: 12px; margin: 0 3px; background: var(--border-color); }
.state-block { display: flex; min-height: 220px; align-items: center; justify-content: center; flex-direction: column; padding: 38px 20px; background: var(--bg-card); border: 1px dashed var(--border-color); border-radius: var(--radius-lg); text-align: center; }
.state-spinner { width: 28px; height: 28px; margin-bottom: 13px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: tpl-spin .75s linear infinite; }
@keyframes tpl-spin { to { transform: rotate(360deg); } }
.state-icon { width: 38px; height: 38px; margin-bottom: 13px; color: var(--accent); }
.state-icon svg { display: block; width: 100%; height: 100%; }
.state-title { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.state-text { margin-top: 7px; color: var(--text-tertiary); font-size: 12px; }
.tpl-table-wrap { max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.tpl-table { width: 100%; min-width: 960px; border-collapse: separate; border-spacing: 0; color: var(--text-primary); font-size: 12px; text-align: left; }
.tpl-table th { height: 38px; padding: 9px 13px; color: var(--text-secondary); background: #efe9de; border-bottom: 1px solid var(--border-color); font-size: 10px; font-weight: 650; letter-spacing: .035em; white-space: nowrap; }
.tpl-table td { min-height: 46px; padding: 10px 13px; color: var(--text-primary); background: var(--bg-card); border-bottom: 1px solid #efe9de; vertical-align: middle; transition: background .14s ease; }
.tpl-table tbody tr:last-child td { border-bottom: 0; }
.tpl-table tbody tr:hover td { background: #faf6ee; }
.tpl-table tbody tr.row-editing td { background: var(--accent-soft); }
.cell-index { width: 48px; color: var(--text-tertiary) !important; font-family: var(--font-mono); font-size: 10px; text-align: center; }
.cell-hash { max-width: 190px; overflow: hidden; color: var(--text-tertiary) !important; font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.cell-content { min-width: 260px; max-width: 430px; color: var(--text-secondary) !important; line-height: 1.6; overflow-wrap: anywhere; }
.cell-actions { min-width: 138px; white-space: nowrap; }
.tpl-type-badge { display: inline-flex; align-items: center; padding: 3px 8px; border: 1px solid; border-radius: 6px; font-size: 10px; font-weight: 650; white-space: nowrap; }
.type-exc { color: #b04b45; background: #fbeee7; border-color: #f0d6cc; }
.type-tpl { color: #276b83; background: #f6f1e7; border-color: #dbe4ea; }
.type-frozen { color: #8c5c17; background: #fbf3e2; border-color: #eeddb8; }
.type-forbidden { color: var(--text-secondary); background: #f5f5f4; border-color: var(--border-color); }
.edit-select,
.edit-input { height: 30px; padding: 4px 8px; color: var(--text-primary); background: #fff; border: 1px solid var(--accent); border-radius: 7px; outline: none; font-size: 11px; }
.edit-select:focus,
.edit-input:focus { box-shadow: 0 0 0 3px rgba(255,255,255,.75); }
.edit-input { width: min(100%, 320px); }
.btn-sm { min-height: 29px; margin-right: 5px; padding: 4px 9px; font-size: 11px; }
.btn-sm:last-child { margin-right: 0; }
.btn-edit { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-edit:hover { background: var(--accent-light); }
.btn-del { color: var(--danger); background: transparent; border-color: transparent; }
.btn-del:hover { background: #fbeee7; border-color: #f2cfcc; }
.btn-save { color: #fff; background: var(--accent); border-color: var(--accent); }
.btn-cancel { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.pagination { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.page-btn { min-height: 34px; padding: 6px 14px; color: var(--text-secondary); background: var(--bg-card); border-color: var(--border-color); }
.page-btn:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.page-info { color: var(--text-secondary); font-family: var(--font-mono); font-size: 11px; }
@media (max-width: 1100px) {
  .tpl-operations-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .tpl-header { align-items: flex-start; }
  .header-code { display: none; }
  .tpl-add-card,
  .tpl-warm-card,
  .tpl-filter { padding: 14px; }
  .tpl-add-row .filter-field,
  .filter-row .filter-field { flex: 1 1 calc(50% - 6px); }
  .tpl-add-content { flex-basis: 100%; }
  .tpl-add-row > .btn-search { flex: 1; }
  .filter-actions { width: 100%; margin-top: 2px; }
  .filter-actions .btn-search { flex: 1; }
  .section-caption { align-items: flex-start; flex-direction: column; gap: 3px; }
}
@media (max-width: 480px) {
  .tpl-header h2 { font-size: 21px; }
  .tpl-add-card,
  .tpl-warm-card,
  .tpl-filter { padding: 13px; border-radius: var(--radius); }
  .tpl-add-row .filter-field,
  .filter-row .filter-field { flex-basis: 100%; }
  .filter-actions .btn-reset { min-width: 82px; }
  .state-block { min-height: 200px; }
  .page-info { width: 100%; order: -1; text-align: center; }
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .tpl-filter { background: var(--bg-card); }
}
@media (prefers-reduced-motion: reduce) {
  .tpl-page * { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
