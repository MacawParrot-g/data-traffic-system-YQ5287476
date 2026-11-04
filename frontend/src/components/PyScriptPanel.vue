<!-- 文件路径: frontend/src/components/PyScriptPanel.vue -->
<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { fetchPyScriptList, executePyScript, uploadPyScript, deletePyScript } from '../api/index.js'

const emit = defineEmits(['error'])

const scripts = ref([])
const loading = ref(false)
const executing = ref('')
const lastResult = ref(null)
const resultLoading = ref(false)
const resultRaw = ref('')
const fileInput = ref(null)
const uploading = ref(false)
const resultContainer = ref(null)

async function loadScripts() {
  loading.value = true
  try {
    const json = await fetchPyScriptList()
    if (json.success) scripts.value = json.data || []
  } catch (e) { emit('error', '脚本列表获取失败: ' + e.message) }
  finally { loading.value = false }
}

async function runScript(name) {
  if (executing.value) return
  executing.value = name
  resultLoading.value = true
  lastResult.value = null
  resultRaw.value = ''
  try {
    const json = await executePyScript(name)
    lastResult.value = json
    if (json.success && json.data) {
      resultRaw.value = JSON.stringify(json.data, null, 2)
    } else if (json.data) {
      resultRaw.value = JSON.stringify(json.data, null, 2)
    }
    nextTick(() => {
      if (resultContainer.value) resultContainer.value.scrollTop = 0
    })
  } catch (e) {
    lastResult.value = { success: false, message: '请求失败: ' + e.message }
  } finally {
    executing.value = ''
    resultLoading.value = false
  }
}

function triggerUpload() {
  if (fileInput.value) fileInput.value.click()
}

async function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  if (!file.name.endsWith('.py')) {
    emit('error', '仅支持 .py 文件')
    return
  }
  uploading.value = true
  try {
    const json = await uploadPyScript(file)
    if (json.success) {
      await loadScripts()
    } else {
      emit('error', json.message || '上传失败')
    }
  } catch (e) { emit('error', '上传失败: ' + e.message) }
  finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function removeScript(name) {
  if (!confirm('确定要删除脚本 "' + name + '" 吗？')) return
  try {
    const json = await deletePyScript(name)
    if (json.success) {
      await loadScripts()
      if (lastResult.value && lastResult.value.data && lastResult.value.data._scriptName === name) {
        lastResult.value = null
        resultRaw.value = ''
      }
    } else {
      emit('error', json.message || '删除失败')
    }
  } catch (e) { emit('error', '删除失败: ' + e.message) }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}

function getResultStatusClass() {
  if (!lastResult.value) return ''
  if (!lastResult.value.success) return 'result-fail'
  const d = lastResult.value.data
  if (d && d.status === 'error') return 'result-error'
  if (d && d.status === 'warning') return 'result-warn'
  return 'result-ok'
}

onMounted(() => { loadScripts() })
</script>

<template>
  <div class="pyscript-page">
    <div class="page-header">
      <div class="page-heading">
        <span class="page-kicker">PYTHON / OPS RUNNER</span>
        <h2>Python 脚本管理</h2>
        <div class="page-header-sub">上传、执行并检查运维脚本的结构化运行结果</div>
      </div>
      <span class="header-signal"><i></i>RUNNER READY</span>
    </div>

    <div class="ps-toolbar">
      <div class="ps-toolbar-actions">
        <button class="btn-action btn-primary" @click="triggerUpload" :disabled="uploading">
          {{ uploading ? '上传中...' : '上传脚本' }}
        </button>
        <input ref="fileInput" type="file" accept=".py" style="display:none" @change="handleFileUpload" />
        <button class="btn-action btn-secondary" @click="loadScripts" :disabled="loading">
          {{ loading ? '刷新中...' : '刷新列表' }}
        </button>
      </div>
      <span class="ps-toolbar-hint"><span>WORKSPACE</span>/opt/pyscript/sc/<i></i><span>VENV</span>/opt/pyscript/venv</span>
    </div>

    <div class="ps-layout">
      <div class="ps-left">
        <div class="card">
          <div class="card-header">
            <div class="card-title-group">
              <span class="card-code">PY</span>
              <div><span class="card-kicker">SCRIPT LIBRARY</span><h3>脚本列表</h3></div>
            </div>
            <span class="ps-count">{{ scripts.length }} 个脚本</span>
          </div>
          <div class="card-body">
            <div v-if="loading && scripts.length === 0" class="ps-empty">
              <div class="state-spinner"></div>
              <div>正在加载脚本列表...</div>
            </div>
            <div v-else-if="scripts.length === 0" class="ps-empty">
              <div class="ps-empty-icon">PY</div>
              <div class="ps-empty-text">暂无 Python 脚本</div>
              <div class="ps-empty-hint">点击上方“上传脚本”添加 .py 文件</div>
            </div>
            <div v-else class="ps-script-list">
              <div v-for="s in scripts" :key="s.name" class="ps-script-item" :class="{ 'ps-script-running': executing === s.name }">
                <div class="psi-file-mark">PY</div>
                <div class="psi-info">
                  <div class="psi-name" :title="s.name">{{ s.name }}</div>
                  <div class="psi-meta">{{ formatSize(s.size) }}<i></i>{{ formatTime(s.lastModified) }}</div>
                  <div v-if="executing === s.name" class="psi-running-label">PROCESS RUNNING</div>
                </div>
                <div class="psi-actions">
                  <button class="btn-run" @click="runScript(s.name)" :disabled="executing !== ''">
                    {{ executing === s.name ? '执行中...' : '执行' }}
                  </button>
                  <button class="btn-del" @click="removeScript(s.name)" :disabled="executing !== ''">删除</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="ps-right">
        <div class="card">
          <div class="card-header">
            <div class="card-title-group">
              <span class="card-code">JS</span>
              <div><span class="card-kicker">EXECUTION OUTPUT</span><h3>执行结果</h3></div>
            </div>
            <span v-if="lastResult" class="ps-result-badge" :class="getResultStatusClass()">
              {{ lastResult.success ? (lastResult.data?.status === 'error' ? 'ERROR' : lastResult.data?.status === 'warning' ? 'WARNING' : 'SUCCESS') : 'FAIL' }}
            </span>
            <span v-if="resultLoading" class="ps-executing-badge">
              <span class="state-spinner-sm"></span>执行中
            </span>
          </div>
          <div class="card-body ps-result-body">
            <div v-if="resultLoading" class="ps-empty">
              <div class="state-spinner"></div>
              <div>正在执行脚本，请稍候...</div>
              <div class="ps-empty-hint">运行完成后将在此展示结构化输出</div>
            </div>
            <div v-else-if="!lastResult" class="ps-empty">
              <div class="ps-empty-icon">{}</div>
              <div class="ps-empty-text">暂无执行结果</div>
              <div class="ps-empty-hint">选择左侧脚本并点击“执行”</div>
            </div>
            <template v-else>
              <div class="ps-result-summary" :class="getResultStatusClass()">
                <div class="prs-row">
                  <span class="prs-label">状态</span>
                  <span class="prs-val">{{ lastResult.message }}</span>
                </div>
                <div class="prs-row" v-if="lastResult.data?._elapsed">
                  <span class="prs-label">耗时</span>
                  <span class="prs-val prs-mono">{{ lastResult.data._elapsed }} ms</span>
                </div>
                <div class="prs-row" v-if="lastResult.data?.status">
                  <span class="prs-label">脚本状态</span>
                  <span class="prs-val prs-mono">{{ lastResult.data.status }}</span>
                </div>
                <div class="prs-row" v-if="lastResult.data?.message">
                  <span class="prs-label">脚本消息</span>
                  <span class="prs-val">{{ lastResult.data.message }}</span>
                </div>
              </div>
              <div class="ps-result-detail" ref="resultContainer">
                <div class="ps-result-tabs">
                  <span class="ps-result-tab-label">JSON 数据</span>
                  <span class="ps-result-tab-meta">READ ONLY / UTF-8</span>
                </div>
                <pre class="ps-result-pre">{{ resultRaw }}</pre>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pyscript-page { width: 100%; max-width: 1600px; min-width: 0; margin: 0 auto; padding: 0 20px 40px; color: var(--text-primary); font-family: var(--font-sans); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 22px; padding: 4px 0 18px; border-bottom: 1px solid var(--border-color); }
.page-heading { min-width: 0; }
.page-kicker, .card-kicker { display: block; margin-bottom: 7px; color: var(--accent); font-size: 10px; font-weight: 800; letter-spacing: 1.7px; }
.page-header h2 { margin: 0; font-size: 24px; line-height: 1.25; letter-spacing: -0.6px; font-weight: 800; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 13px; }
.header-signal { display: inline-flex; align-items: center; gap: 7px; flex: 0 0 auto; padding: 7px 11px; border: 1px solid rgba(201,106,69,0.15); border-radius: 999px; background: rgba(255,255,255,0.62); color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.85); font-size: 9px; font-weight: 800; letter-spacing: 1px; }
.header-signal i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px var(--accent-glow); }
.ps-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; padding: 10px; border: 1px solid rgba(221,211,193,0.85); border-radius: var(--radius); background: rgba(255,255,255,0.66); box-shadow: var(--glass-shadow, var(--shadow-sm)); backdrop-filter: blur(18px); }
.ps-toolbar-actions { display: flex; flex: 0 0 auto; gap: 8px; }
.btn-action { min-height: 38px; padding: 8px 16px; border: 1px solid transparent; border-radius: var(--radius-sm); cursor: pointer; font-size: 12px; font-weight: 750; transition: var(--transition); }
.btn-action:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }
.btn-primary { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: 0 6px 16px rgba(201,106,69,0.17), inset 0 1px 0 rgba(255,255,255,0.22); }
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(201,106,69,0.23); }
.btn-secondary { border-color: var(--border-color); background: rgba(255,255,255,0.76); color: var(--text-secondary); }
.btn-secondary:hover:not(:disabled) { border-color: var(--accent-light); color: var(--accent-strong); }
.ps-toolbar-hint { display: flex; min-width: 0; align-items: center; justify-content: flex-end; gap: 6px; flex-wrap: wrap; color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; overflow-wrap: anywhere; }
.ps-toolbar-hint span { color: var(--accent); font-size: 8px; font-weight: 800; letter-spacing: 0.7px; }
.ps-toolbar-hint i { width: 1px; height: 12px; margin: 0 4px; background: var(--border-color); }
.ps-layout { display: grid; grid-template-columns: minmax(340px, 410px) minmax(0, 1fr); align-items: start; gap: 20px; }
.ps-left, .ps-right { min-width: 0; }
.card { min-width: 0; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.9); box-shadow: var(--shadow-sm); }
.card-header { display: flex; min-height: 76px; align-items: center; justify-content: space-between; gap: 12px; padding: 15px 18px; border-bottom: 1px solid var(--border-color); background: linear-gradient(110deg, rgba(247,251,250,0.85), rgba(255,255,255,0.65)); }
.card-title-group { display: flex; min-width: 0; align-items: center; gap: 11px; }
.card-code { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; border: 1px solid rgba(201,106,69,0.14); border-radius: 10px; background: var(--accent-soft); color: var(--accent-strong); font-family: var(--font-mono); font-size: 10px; font-weight: 800; }
.card-kicker { margin-bottom: 3px; font-size: 8px; letter-spacing: 1.1px; }
.card-header h3 { margin: 0; font-size: 14px; font-weight: 800; }
.card-body { min-width: 0; padding: 14px; }
.ps-count { flex: 0 0 auto; padding: 4px 8px; border-radius: 999px; background: #f6f1e7; color: var(--text-secondary); font-size: 10px; }
.ps-empty { display: flex; min-height: 330px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 40px 18px; color: var(--text-secondary); text-align: center; font-size: 13px; }
.ps-empty-icon { display: grid; min-width: 52px; height: 52px; margin-bottom: 6px; padding: 0 10px; place-items: center; border: 1px solid var(--border-color); border-radius: 15px; background: var(--accent-soft); color: var(--accent); font-family: var(--font-mono); font-size: 16px; font-weight: 750; }
.ps-empty-text { color: var(--text-primary); font-size: 14px; font-weight: 700; }
.ps-empty-hint { color: var(--text-tertiary); font-size: 11px; }
.state-spinner { width: 28px; height: 28px; margin-bottom: 8px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
.state-spinner-sm { width: 12px; height: 12px; border: 2px solid rgba(201,106,69,0.17); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.ps-script-list { display: flex; max-height: 620px; flex-direction: column; gap: 8px; overflow-y: auto; }
.ps-script-item { display: flex; min-width: 0; align-items: center; gap: 11px; padding: 12px; border: 1px solid #f3ede0; border-radius: var(--radius); background: #fafaf9; transition: border-color var(--transition), background var(--transition), box-shadow var(--transition), transform var(--transition); }
.ps-script-item:hover { border-color: var(--border-strong); background: #fff; box-shadow: var(--shadow-sm); transform: translateY(-1px); }
.ps-script-running { border-color: var(--accent-light); background: var(--accent-soft); box-shadow: 0 0 0 3px var(--accent-glow); }
.psi-file-mark { display: grid; width: 32px; height: 38px; flex: 0 0 32px; place-items: center; border: 1px solid var(--border-color); border-radius: 7px 7px 9px 7px; background: #fff; color: var(--accent); font-family: var(--font-mono); font-size: 9px; font-weight: 800; }
.psi-info { flex: 1; min-width: 0; }
.psi-name { overflow: hidden; color: var(--text-primary); font-family: var(--font-mono); font-size: 12px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.psi-meta { display: flex; align-items: center; gap: 6px; margin-top: 5px; color: var(--text-secondary); font-size: 9px; white-space: nowrap; }
.psi-meta i { width: 3px; height: 3px; border-radius: 50%; background: var(--text-tertiary); }
.psi-running-label { margin-top: 5px; color: var(--accent); font-family: var(--font-mono); font-size: 8px; font-weight: 800; letter-spacing: 0.7px; }
.psi-actions { display: flex; flex: 0 0 auto; gap: 5px; }
.btn-run, .btn-del { min-height: 32px; padding: 6px 10px; border: 1px solid transparent; border-radius: 8px; cursor: pointer; font-size: 10px; font-weight: 750; white-space: nowrap; transition: var(--transition); }
.btn-run { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: inset 0 1px 0 rgba(255,255,255,0.2); }
.btn-run:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 5px 12px rgba(201,106,69,0.2); }
.btn-del { border-color: rgba(213,82,82,0.14); background: var(--danger-soft); color: var(--danger); }
.btn-del:hover:not(:disabled) { background: #fbe4dd; }
.btn-run:disabled, .btn-del:disabled { opacity: 0.45; cursor: not-allowed; }
.ps-result-body { padding: 0; }
.ps-result-badge { margin-left: auto; padding: 5px 9px; border-radius: 999px; font-family: var(--font-mono); font-size: 9px; font-weight: 800; letter-spacing: 0.5px; }
.ps-result-badge.result-ok { background: var(--success-soft); color: var(--success); }
.ps-result-badge.result-fail, .ps-result-badge.result-error { background: var(--danger-soft); color: var(--danger); }
.ps-result-badge.result-warn { background: var(--warning-soft); color: var(--warning); }
.ps-executing-badge { display: flex; align-items: center; gap: 7px; margin-left: auto; padding: 5px 9px; border-radius: 999px; background: var(--accent-soft); color: var(--accent-strong); font-size: 10px; font-weight: 700; }
.ps-result-summary { padding: 17px 19px; border-bottom: 1px solid var(--border-color); background: #fafaf9; }
.ps-result-summary.result-ok { background: linear-gradient(115deg, var(--success-soft), rgba(255,255,255,0.7)); }
.ps-result-summary.result-fail, .ps-result-summary.result-error { background: linear-gradient(115deg, var(--danger-soft), rgba(255,255,255,0.7)); }
.ps-result-summary.result-warn { background: linear-gradient(115deg, var(--warning-soft), rgba(255,255,255,0.7)); }
.prs-row { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: 10px; margin-bottom: 9px; }
.prs-row:last-child { margin-bottom: 0; }
.prs-label { color: var(--text-secondary); font-size: 11px; }
.prs-val { min-width: 0; color: var(--text-primary); font-size: 12px; font-weight: 650; overflow-wrap: anywhere; }
.prs-mono { font-family: var(--font-mono); }
.ps-result-detail { min-width: 0; max-width: 100%; overflow: hidden; background: #1b160c; }
.ps-result-tabs { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 18px; border-bottom: 1px solid rgba(220,190,150,0.1); background: #241d12; }
.ps-result-tab-label { color: #e8a87c; font-family: var(--font-mono); font-size: 10px; font-weight: 750; letter-spacing: 0.5px; }
.ps-result-tab-meta { color: #8a7f6a; font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.8px; }
.ps-result-pre { max-width: 100%; min-height: 260px; max-height: 480px; margin: 0; padding: 18px; overflow: auto; overscroll-behavior: contain; background: radial-gradient(circle at 100% 0%, rgba(120,95,60,0.09), transparent 38%), #1b160c; color: #ddd0bb; font-family: var(--font-mono); font-size: 11px; line-height: 1.75; tab-size: 2; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.ps-result-pre::-webkit-scrollbar-thumb { background-color: #423b2f; }
@media (max-width: 1100px) {
  .ps-layout { grid-template-columns: 1fr; }
  .ps-script-list { max-height: 430px; }
  .ps-toolbar { align-items: flex-start; flex-direction: column; }
  .ps-toolbar-hint { justify-content: flex-start; padding: 0 4px 4px; }
}
@media (max-width: 700px) {
  .pyscript-page { padding: 0 14px 28px; }
  .page-header { align-items: flex-start; gap: 14px; }
  .page-header h2 { font-size: 21px; }
  .header-signal { margin-top: 3px; }
  .ps-layout { gap: 15px; }
  .card-header { padding-inline: 15px; }
}
@media (max-width: 480px) {
  .pyscript-page { padding-inline: 10px; }
  .page-header { display: block; }
  .header-signal { margin-top: 13px; }
  .page-kicker { font-size: 9px; letter-spacing: 1.3px; }
  .ps-toolbar-actions { width: 100%; }
  .ps-toolbar-actions .btn-action { flex: 1; }
  .ps-toolbar-hint { display: block; line-height: 1.8; }
  .ps-toolbar-hint span { margin-right: 5px; }
  .ps-toolbar-hint i { display: block; width: 100%; height: 1px; margin: 5px 0; }
  .ps-script-item { align-items: flex-start; flex-wrap: wrap; }
  .psi-info { min-width: calc(100% - 48px); }
  .psi-actions { width: 100%; padding-left: 43px; }
  .psi-actions .btn-run { flex: 1; }
  .psi-meta { white-space: normal; flex-wrap: wrap; }
  .prs-row { grid-template-columns: 64px minmax(0, 1fr); }
  .ps-result-tab-meta { display: none; }
  .ps-result-pre { padding: 14px; font-size: 10px; }
}
</style>
