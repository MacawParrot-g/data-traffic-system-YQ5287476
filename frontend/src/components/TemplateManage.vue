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
.tpl-page { max-width: 1400px; margin: 0 auto; padding: 0 0 40px; }
.tpl-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 2px solid #f0f0f0; }
.tpl-header h2 { margin: 0; font-size: 22px; color: #1a1a2e; font-weight: 700; }
.tpl-header-sub { font-size: 13px; color: #999; }

.tpl-add-card, .tpl-warm-card { background: #fff; border: 1px solid #e8eaef; border-radius: 12px; padding: 18px 20px; margin-bottom: 16px; }
.tpl-card-title { font-size: 14px; font-weight: 700; color: #1a1a2e; margin-bottom: 12px; }
.tpl-add-row { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }
.tpl-add-content { flex: 1; min-width: 260px; }
.tpl-warm-tip { font-size: 12px; color: #94a3b8; align-self: flex-end; padding-bottom: 8px; }

.filter-field { display: flex; flex-direction: column; gap: 4px; min-width: 160px; }
.filter-field label { font-size: 12px; font-weight: 600; color: #64748b; }
.filter-input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; color: #1a1a2e; background: #fff; transition: border-color 0.2s; }
.filter-input:focus { outline: none; border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.writer-input { background: #f1f5f9; color: #94a3b8; cursor: not-allowed; }

.tpl-feedback { margin-top: 12px; font-size: 13px; font-weight: 600; }
.tpl-ok { color: #166534; }
.tpl-err { color: #dc2626; }

.tpl-filter { background: #fff; border: 1px solid #e8eaef; border-radius: 12px; padding: 18px 20px; margin-bottom: 20px; }
.filter-row { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }
.filter-actions { display: flex; gap: 8px; align-items: flex-end; }
.btn-search { padding: 8px 20px; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; border: none; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.btn-search:hover:not(:disabled) { box-shadow: 0 2px 10px rgba(99,102,241,0.3); }
.btn-search:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-reset { padding: 8px 16px; background: #f1f5f9; color: #64748b; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; cursor: pointer; }
.btn-reset:hover { background: #e2e8f0; }

.result-bar { font-size: 13px; color: #64748b; margin-bottom: 14px; padding: 0 4px; }
.result-bar strong { color: #6366f1; font-size: 16px; }

.state-block { display: flex; flex-direction: column; align-items: center; padding: 50px 20px; }
.state-spinner { width: 32px; height: 32px; border: 3px solid #e2e8f0; border-top-color: #6366f1; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.state-text { margin-top: 12px; font-size: 14px; color: #94a3b8; }

.tpl-table-wrap { background: #fff; border: 1px solid #e8eaef; border-radius: 12px; overflow: hidden; }
.tpl-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.tpl-table thead tr { background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%); color: #fff; }
.tpl-table th { padding: 10px 14px; text-align: left; font-weight: 600; font-size: 12px; letter-spacing: 0.3px; }
.tpl-table td { padding: 10px 14px; border-bottom: 1px solid #f1f5f9; color: #374151; vertical-align: middle; }
.tpl-table tbody tr:hover { background: #f8f9fc; }
.tpl-table tbody tr:last-child td { border-bottom: none; }
.cell-hash { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; color: #94a3b8; font-family: monospace; }
.cell-content { max-width: 360px; word-break: break-all; }
.cell-actions { white-space: nowrap; }

.tpl-type-badge { display: inline-block; padding: 3px 10px; border-radius: 6px; font-size: 12px; font-weight: 700; }
.type-exc { background: #fee2e2; color: #991b1b; }
.type-tpl { background: #dbeafe; color: #1e40af; }
.type-frozen { background: #fef3c7; color: #92400e; }
.type-forbidden { background: #f1f5f9; color: #475569; }

.edit-select { padding: 5px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 12px; }
.edit-input { padding: 5px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 12px; width: 220px; }

.btn-sm { padding: 5px 10px; border: none; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; margin-right: 4px; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-edit { background: #eef2ff; color: #4338ca; }
.btn-edit:hover { background: #e0e7ff; }
.btn-del { background: #fef2f2; color: #dc2626; }
.btn-del:hover { background: #fee2e2; }
.btn-save { background: #dcfce7; color: #166534; }
.btn-save:hover { background: #bbf7d0; }
.btn-cancel { background: #f1f5f9; color: #64748b; }
.btn-cancel:hover { background: #e2e8f0; }

.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 20px; }
.page-btn { padding: 8px 16px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; font-size: 13px; cursor: pointer; color: #374151; transition: all 0.2s; }
.page-btn:hover:not(:disabled) { background: #f8f9fc; border-color: #6366f1; color: #6366f1; }
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-info { font-size: 13px; color: #64748b; }
</style>