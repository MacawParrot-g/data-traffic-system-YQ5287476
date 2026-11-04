<!-- 文件路径: src/main/frontend/src/components/ScheduledTaskPanel.vue -->
<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { fetchTaskList, createTask, toggleTask, deleteTask, executeTaskNow } from '../api/index.js'

const emit = defineEmits(['error'])

const list = ref([])
const loading = ref(false)
const showCreateForm = ref(false)
const creating = ref(false)
const createMsg = ref('')

const newTask = reactive({
  name: '',
  taskType: 'AUDIT_LOG_CLEAN',
  cronExpression: '24h',
  params: ''
})

const taskTypeOptions = [
  { value: 'AUDIT_LOG_CLEAN', label: '📋 审计日志清理', desc: '自动清理过期的审计日志' },
  { value: 'DATA_ARCHIVE', label: '📦 数据归档', desc: '将老数据迁移至归档表' },
  { value: 'EXPORT_MARK_RESET', label: '🔄 导出标记重置', desc: '重置已导出数据的标记' }
]

const cronPresets = [
  { value: '1h', label: '每 1 小时' },
  { value: '6h', label: '每 6 小时' },
  { value: '12h', label: '每 12 小时' },
  { value: '24h', label: '每 24 小时' },
  { value: '7d', label: '每 7 天' },
  { value: '30d', label: '每 30 天' }
]

async function loadTasks() {
  loading.value = true
  try {
    const json = await fetchTaskList()
    if (json.success) {
      list.value = json.data || []
    } else {
      emit('error', json.message || '加载失败')
    }
  } catch (e) {
    emit('error', '加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!newTask.name.trim()) { createMsg.value = '❌ 请输入任务名称'; return }
  creating.value = true
  createMsg.value = ''
  try {
    const json = await createTask({
      name: newTask.name.trim(),
      taskType: newTask.taskType,
      cronExpression: newTask.cronExpression,
      params: newTask.params.trim()
    })
    if (json.success) {
      createMsg.value = '✅ ' + json.message
      newTask.name = ''
      newTask.params = ''
      showCreateForm.value = false
      await loadTasks()
    } else {
      createMsg.value = '❌ ' + (json.message || '创建失败')
    }
  } catch (e) {
    createMsg.value = '❌ 创建请求失败：' + e.message
  } finally {
    creating.value = false
  }
}

async function handleToggle(task) {
  try {
    const json = await toggleTask(task.id, !task.enabled)
    if (json.success) {
      task.enabled = !task.enabled
    } else {
      emit('error', json.message || '操作失败')
    }
  } catch (e) {
    emit('error', '操作失败：' + e.message)
  }
}

async function handleDelete(task) {
  if (!confirm(`确定要删除任务「${task.name}」吗？`)) return
  try {
    const json = await deleteTask(task.id)
    if (json.success) await loadTasks()
    else emit('error', json.message || '删除失败')
  } catch (e) {
    emit('error', '删除请求失败：' + e.message)
  }
}

async function handleExecuteNow(task) {
  if (!confirm(`确定要立即执行任务「${task.name}」吗？`)) return
  try {
    const json = await executeTaskNow(task.id)
    if (json.success) {
      alert('✅ ' + json.message)
      await loadTasks()
    } else {
      emit('error', json.message || '执行失败')
    }
  } catch (e) {
    emit('error', '执行请求失败：' + e.message)
  }
}

function getTaskTypeLabel(type) {
  const found = taskTypeOptions.find(t => t.value === type)
  return found ? found.label : type
}

onMounted(() => {
  loadTasks()
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
  <div class="task-page">
    <div class="page-header">
      <div class="page-heading">
        <span class="page-kicker">AUTOMATION / SCHEDULER</span>
        <h2>定时任务管理</h2>
        <div class="page-header-sub">编排系统自动化任务，跟踪启用状态与最近执行结果</div>
      </div>
      <span class="header-signal"><i></i>SCHEDULER ONLINE</span>
    </div>

    <div class="task-actions-bar">
      <div class="task-actions-copy">
        <span class="task-actions-title">任务编排</span>
        <span class="task-actions-sub">TASK WORKFLOWS</span>
      </div>
      <div class="task-actions-buttons">
        <button class="btn-action btn-create" @click="showCreateForm = !showCreateForm">
          {{ showCreateForm ? '取消创建' : '创建任务' }}
        </button>
        <button class="btn-action btn-refresh-task" @click="loadTasks" :disabled="loading">
          {{ loading ? '加载中...' : '刷新列表' }}
        </button>
      </div>
    </div>

    <div v-if="showCreateForm" class="create-card">
      <div class="create-card-header">
        <div>
          <span class="section-kicker">NEW WORKFLOW</span>
          <h3>创建新任务</h3>
        </div>
        <span class="create-step">CONFIG / 01</span>
      </div>
      <div class="create-grid">
        <div class="create-field">
          <label class="field-label">任务名称</label>
          <input v-model="newTask.name" class="create-input" placeholder="例如：每日清理审计日志" />
        </div>
        <div class="create-field">
          <label class="field-label">任务类型</label>
          <select v-model="newTask.taskType" class="create-input">
            <option v-for="opt in taskTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }} - {{ opt.desc }}</option>
          </select>
        </div>
        <div class="create-field">
          <label class="field-label">执行间隔</label>
          <div class="cron-row">
            <select v-model="newTask.cronExpression" class="create-input cron-select">
              <option v-for="p in cronPresets" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
            <span class="cron-or">或</span>
            <input v-model="newTask.cronExpression" class="create-input cron-custom" placeholder="自定义：如 2h, 30m, 7d" />
          </div>
        </div>
        <div class="create-field">
          <label class="field-label">参数（可选）</label>
          <input v-model="newTask.params" class="create-input" placeholder="如：保留天数 90" />
        </div>
      </div>
      <div v-if="createMsg" class="create-msg" :class="{ 'create-msg-ok': createMsg.charCodeAt(0) === 9989, 'create-msg-err': createMsg.charCodeAt(0) === 10060 }">{{ createMsg }}</div>
      <div class="create-footer">
        <span class="create-note">保存后将按设定间隔自动调度</span>
        <button class="btn-action btn-submit" @click="handleCreate" :disabled="creating">
          {{ creating ? '创建中...' : '确认创建' }}
        </button>
      </div>
    </div>

    <div v-if="list.length === 0 && !loading" class="state-block">
      <div class="state-icon">0</div>
      <div class="state-text">暂无定时任务</div>
      <div class="state-hint">点击上方“创建任务”配置第一条自动化工作流</div>
    </div>

    <div v-if="list.length > 0" class="task-list">
      <div v-for="task in list" :key="task.id" class="task-card" :class="{ 'task-disabled': !task.enabled }">
        <div class="task-card-accent"></div>
        <div class="task-card-header">
          <div class="task-card-title-row">
            <span class="task-status-dot" :class="task.enabled ? 'dot-on' : 'dot-off'"></span>
            <div class="task-title-copy">
              <span class="task-card-name">{{ task.name }}</span>
              <span class="task-state-text">{{ task.enabled ? '运行中' : '已停用' }}</span>
            </div>
            <span class="task-type-tag">{{ getTaskTypeLabel(task.taskType) }}</span>
          </div>
          <div class="task-card-actions">
            <button class="btn-sm btn-sm-run" @click="handleExecuteNow(task)" title="立即执行">立即执行</button>
            <button class="btn-sm" :class="task.enabled ? 'btn-sm-stop' : 'btn-sm-start'" @click="handleToggle(task)">
              {{ task.enabled ? '停用' : '启用' }}
            </button>
            <button class="btn-sm btn-sm-del" @click="handleDelete(task)">删除</button>
          </div>
        </div>
        <div class="task-card-body">
          <div class="task-info-row task-info-primary">
            <span class="task-info-label">执行间隔</span>
            <span class="task-info-value task-interval">{{ task.cronExpression }}</span>
          </div>
          <div class="task-info-row">
            <span class="task-info-label">已执行</span>
            <span class="task-info-value">{{ task.totalExecutions || 0 }} 次</span>
          </div>
          <div class="task-info-row" v-if="task.params">
            <span class="task-info-label">参数</span>
            <span class="task-info-value">{{ task.params }}</span>
          </div>
          <div class="task-info-row" v-if="task.lastExecutedAt">
            <span class="task-info-label">上次执行</span>
            <span class="task-info-value">{{ task.lastExecutedAt }}</span>
          </div>
          <div class="task-info-row task-result-row" v-if="task.lastResult">
            <span class="task-info-label">上次结果</span>
            <span class="task-info-value task-result" :class="{ 'task-result-err': task.lastResult.includes('异常') }">{{ task.lastResult }}</span>
          </div>
          <div class="task-info-row" v-if="task.createdBy">
            <span class="task-info-label">创建人</span>
            <span class="task-info-value">{{ task.createdBy }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-page { width: 100%; max-width: 1280px; min-width: 0; margin: 0 auto; padding: 0 20px 40px; color: var(--text-primary); font-family: var(--font-sans); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 22px; padding: 4px 0 18px; border-bottom: 1px solid var(--border-color); }
.page-heading { min-width: 0; }
.page-kicker, .section-kicker { display: block; margin-bottom: 7px; color: var(--accent); font-size: 10px; font-weight: 800; letter-spacing: 1.7px; }
.page-header h2 { margin: 0; font-size: 24px; line-height: 1.25; letter-spacing: -0.6px; font-weight: 800; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 13px; }
.header-signal { display: inline-flex; align-items: center; gap: 7px; flex: 0 0 auto; padding: 7px 11px; border: 1px solid rgba(201,106,69,0.15); border-radius: 999px; background: rgba(255,255,255,0.62); color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.85); font-size: 9px; font-weight: 800; letter-spacing: 1px; }
.header-signal i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px var(--accent-glow); }
.task-actions-bar { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 18px; padding: 14px 16px; border: 1px solid var(--border-color); border-radius: var(--radius); background: rgba(255,255,255,0.58); box-shadow: inset 0 1px 0 rgba(255,255,255,0.8); backdrop-filter: blur(14px); }
.task-actions-copy { display: flex; flex-direction: column; gap: 3px; }
.task-actions-title { font-size: 13px; font-weight: 800; }
.task-actions-sub { color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; letter-spacing: 1px; }
.task-actions-buttons { display: flex; gap: 9px; }
.btn-action, .btn-sm { border: 1px solid transparent; border-radius: var(--radius-sm); cursor: pointer; font-weight: 700; transition: transform var(--transition), box-shadow var(--transition), background var(--transition), border-color var(--transition); }
.btn-action { min-height: 38px; padding: 8px 17px; font-size: 12px; }
.btn-action:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }
.btn-create, .btn-submit { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: 0 7px 18px rgba(201,106,69,0.17), inset 0 1px 0 rgba(255,255,255,0.22); }
.btn-create:hover, .btn-submit:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 10px 24px rgba(201,106,69,0.24); }
.btn-refresh-task { border-color: var(--border-color); background: rgba(255,255,255,0.8); color: var(--text-secondary); }
.btn-refresh-task:hover:not(:disabled) { border-color: var(--accent-light); color: var(--accent-strong); }
.create-card { position: relative; margin-bottom: 20px; padding: 22px; overflow: hidden; border: 1px solid rgba(190,160,105,0.32); border-radius: var(--radius-lg); background: linear-gradient(130deg, rgba(255,255,255,0.91), rgba(248,233,223,0.67)); box-shadow: var(--glass-shadow, var(--shadow-md)); backdrop-filter: blur(20px); animation: reveal 240ms ease-out; }
.create-card::after { content: ''; position: absolute; top: -70px; right: -45px; width: 180px; height: 180px; border-radius: 50%; background: radial-gradient(circle, rgba(190,160,105,0.16), transparent 68%); pointer-events: none; }
@keyframes reveal { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: translateY(0); } }
.create-card-header { position: relative; z-index: 1; display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.create-card h3 { margin: 0; font-size: 17px; letter-spacing: -0.2px; }
.create-step { color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; letter-spacing: 1px; }
.create-grid { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 15px; margin-bottom: 16px; }
.create-field { display: flex; min-width: 0; flex-direction: column; gap: 7px; }
.field-label { color: var(--text-secondary); font-size: 11px; font-weight: 700; }
.create-input { width: 100%; min-width: 0; min-height: 42px; padding: 9px 12px; border: 1px solid var(--border-color); border-radius: var(--radius-sm); outline: none; background: rgba(255,255,255,0.84); color: var(--text-primary); font-size: 13px; transition: border-color var(--transition), box-shadow var(--transition), background var(--transition); }
.create-input:focus { border-color: var(--accent-light); background: #fff; box-shadow: 0 0 0 3px var(--accent-glow); }
.cron-row { display: grid; grid-template-columns: minmax(110px, 0.8fr) auto minmax(130px, 1.2fr); align-items: center; gap: 8px; }
.cron-or { color: var(--text-tertiary); font-size: 11px; }
.create-msg { position: relative; z-index: 1; margin-bottom: 13px; padding: 10px 13px; border-radius: var(--radius-sm); font-size: 12px; font-weight: 650; }
.create-msg-ok { border: 1px solid rgba(90,110,72,0.14); background: var(--success-soft); color: var(--success); }
.create-msg-err { border: 1px solid rgba(213,82,82,0.14); background: var(--danger-soft); color: var(--danger); }
.create-footer { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.create-note { color: var(--text-secondary); font-size: 11px; }
.state-block { padding: 64px 20px; border: 1px dashed var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.46); text-align: center; }
.state-icon { display: grid; width: 46px; height: 46px; margin: 0 auto 13px; place-items: center; border: 1px solid var(--border-color); border-radius: 14px; background: var(--accent-soft); color: var(--accent); font-family: var(--font-mono); font-size: 20px; font-weight: 700; }
.state-text { color: var(--text-secondary); font-size: 14px; font-weight: 650; }
.state-hint { margin-top: 7px; color: var(--text-tertiary); font-size: 12px; }
.task-list { display: flex; flex-direction: column; gap: 14px; }
.task-card { position: relative; min-width: 0; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.9); box-shadow: var(--shadow-sm); transition: transform var(--transition), box-shadow var(--transition), border-color var(--transition); }
.task-card:hover { border-color: var(--border-strong); box-shadow: var(--shadow-md); transform: translateY(-1px); }
.task-card-accent { position: absolute; inset: 0 auto 0 0; width: 3px; background: linear-gradient(var(--accent-light), var(--accent)); }
.task-disabled { background: rgba(249,251,250,0.76); }
.task-disabled .task-card-accent { background: #ddd0bb; }
.task-disabled .task-card-name, .task-disabled .task-type-tag { color: var(--text-secondary); }
.task-card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 17px 20px 15px 22px; border-bottom: 1px solid #f6f1e7; background: linear-gradient(105deg, rgba(247,251,250,0.78), rgba(255,255,255,0.4)); }
.task-card-title-row { display: flex; min-width: 0; align-items: center; gap: 11px; flex-wrap: wrap; }
.task-status-dot { width: 9px; height: 9px; flex: 0 0 9px; border-radius: 50%; }
.dot-on { background: var(--success); box-shadow: 0 0 0 5px rgba(90,110,72,0.09); }
.dot-off { background: #b3a892; box-shadow: 0 0 0 5px rgba(140,130,110,0.07); }
.task-title-copy { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.task-card-name { overflow-wrap: anywhere; font-size: 14px; font-weight: 800; }
.task-state-text { color: var(--text-secondary); font-size: 10px; }
.task-type-tag { max-width: 100%; padding: 4px 9px; border: 1px solid rgba(201,106,69,0.1); border-radius: 999px; background: var(--accent-soft); color: var(--accent-strong); font-size: 10px; font-weight: 700; overflow-wrap: anywhere; }
.task-card-actions { display: flex; flex: 0 0 auto; gap: 7px; }
.btn-sm { min-height: 32px; padding: 6px 11px; font-size: 11px; white-space: nowrap; }
.btn-sm-run { border-color: rgba(201,106,69,0.15); background: var(--accent-soft); color: var(--accent-strong); }
.btn-sm-run:hover { background: #efe2cf; }
.btn-sm-start { border-color: rgba(90,110,72,0.14); background: var(--success-soft); color: var(--success); }
.btn-sm-stop { border-color: rgba(197,122,22,0.14); background: var(--warning-soft); color: var(--warning); }
.btn-sm-del { border-color: rgba(213,82,82,0.14); background: var(--danger-soft); color: var(--danger); }
.task-card-body { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px 24px; padding: 17px 20px 18px 22px; }
.task-info-row { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.task-info-label { color: var(--text-secondary); font-size: 10px; font-weight: 650; }
.task-info-value { min-width: 0; color: var(--text-primary); font-size: 12px; font-weight: 600; overflow-wrap: anywhere; }
.task-interval { width: fit-content; padding: 3px 8px; border-radius: 6px; background: #f6f1e7; color: var(--accent-strong); font-family: var(--font-mono); }
.task-result-row { grid-column: span 2; }
.task-result { max-width: 100%; }
.task-result-err { color: var(--danger); }
@media (max-width: 900px) {
  .task-card-header { align-items: flex-start; flex-direction: column; }
  .task-card-actions { width: 100%; }
  .task-card-body { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 700px) {
  .task-page { padding: 0 14px 28px; }
  .page-header { align-items: flex-start; gap: 14px; }
  .page-header h2 { font-size: 21px; }
  .header-signal { margin-top: 3px; }
  .create-grid { grid-template-columns: 1fr; }
  .task-actions-copy { display: none; }
  .task-actions-buttons { width: 100%; }
  .task-actions-buttons .btn-action { flex: 1; }
  .task-card-actions .btn-sm { flex: 1; }
}
@media (max-width: 480px) {
  .task-page { padding-inline: 10px; }
  .page-header { display: block; }
  .header-signal { margin-top: 13px; }
  .page-kicker { font-size: 9px; letter-spacing: 1.3px; }
  .task-actions-bar { padding: 12px; }
  .create-card { padding: 18px 15px; border-radius: var(--radius); }
  .create-card-header, .create-footer { align-items: flex-start; flex-direction: column; }
  .create-footer .btn-submit { width: 100%; }
  .cron-row { grid-template-columns: 1fr; }
  .cron-or { display: none; }
  .task-card-header { padding: 16px; }
  .task-card-title-row { align-items: flex-start; }
  .task-type-tag { margin-left: 20px; }
  .task-card-actions { gap: 5px; }
  .btn-sm { padding-inline: 7px; }
  .task-card-body { grid-template-columns: 1fr; padding: 16px; }
  .task-result-row { grid-column: auto; }
}
</style>
