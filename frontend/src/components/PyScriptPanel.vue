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
      <h2>Python 脚本管理</h2>
      <div class="page-header-sub">上传 · 执行 · 查看运维脚本运行结果</div>
    </div>

    <div class="ps-toolbar">
      <button class="btn-action btn-primary" @click="triggerUpload" :disabled="uploading">
        {{ uploading ? '上传中...' : '+ 上传脚本' }}
      </button>
      <input ref="fileInput" type="file" accept=".py" style="display:none" @change="handleFileUpload" />
      <button class="btn-action btn-secondary" @click="loadScripts" :disabled="loading">
        {{ loading ? '刷新中...' : '刷新列表' }}
      </button>
      <span class="ps-toolbar-hint">脚本存放路径: /opt/pyscript/sc/ | 虚拟环境: /opt/pyscript/venv</span>
    </div>

    <div class="ps-layout">
      <div class="ps-left">
        <div class="card">
          <div class="card-header">
            <h3>脚本列表</h3>
            <span class="ps-count">{{ scripts.length }} 个脚本</span>
          </div>
          <div class="card-body">
            <div v-if="loading && scripts.length === 0" class="ps-empty">
              <div class="state-spinner"></div>
              <div>正在加载脚本列表...</div>
            </div>
            <div v-else-if="scripts.length === 0" class="ps-empty">
              <div class="ps-empty-icon">P</div>
              <div class="ps-empty-text">暂无Python脚本</div>
              <div class="ps-empty-hint">点击上方「上传脚本」按钮上传 .py 文件</div>
            </div>
            <div v-else class="ps-script-list">
              <div v-for="s in scripts" :key="s.name" class="ps-script-item" :class="{ 'ps-script-running': executing === s.name }">
                <div class="psi-info">
                  <div class="psi-name">{{ s.name }}</div>
                  <div class="psi-meta">{{ formatSize(s.size) }} · {{ formatTime(s.lastModified) }}</div>
                </div>
                <div class="psi-actions">
                  <button class="btn-run" @click="runScript(s.name)" :disabled="executing !== ''">
                    {{ executing === s.name ? '执行中...' : '▶ 执行' }}
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
            <h3>执行结果</h3>
            <span v-if="lastResult" class="ps-result-badge" :class="getResultStatusClass()">
              {{ lastResult.success ? (lastResult.data?.status === 'error' ? 'ERROR' : lastResult.data?.status === 'warning' ? 'WARNING' : 'SUCCESS') : 'FAIL' }}
            </span>
            <span v-if="resultLoading" class="ps-executing-badge">
              <span class="state-spinner-sm"></span> 执行中
            </span>
          </div>
          <div class="card-body ps-result-body">
            <div v-if="resultLoading" class="ps-empty">
              <div class="state-spinner"></div>
              <div>正在执行脚本，请稍候...</div>
            </div>
            <div v-else-if="!lastResult" class="ps-empty">
              <div class="ps-empty-icon">R</div>
              <div class="ps-empty-text">暂无执行结果</div>
              <div class="ps-empty-hint">选择左侧脚本并点击「执行」按钮</div>
            </div>
            <template v-else>
              <div class="ps-result-summary" :class="getResultStatusClass()">
                <div class="prs-row">
                  <span class="prs-label">状态</span>
                  <span class="prs-val">{{ lastResult.message }}</span>
                </div>
                <div class="prs-row" v-if="lastResult.data?._elapsed">
                  <span class="prs-label">耗时</span>
                  <span class="prs-val">{{ lastResult.data._elapsed }} ms</span>
                </div>
                <div class="prs-row" v-if="lastResult.data?.status">
                  <span class="prs-label">脚本状态</span>
                  <span class="prs-val">{{ lastResult.data.status }}</span>
                </div>
                <div class="prs-row" v-if="lastResult.data?.message">
                  <span class="prs-label">脚本消息</span>
                  <span class="prs-val">{{ lastResult.data.message }}</span>
                </div>
              </div>
              <div class="ps-result-detail" ref="resultContainer">
                <div class="ps-result-tabs">
                  <span class="ps-result-tab-label">JSON 数据</span>
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
.pyscript-page { width: 100%; max-width: 1600px; margin: 0 auto; padding: 0 20px 40px; box-sizing: border-box; }
.page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 2px solid #f0f0f0; }
.page-header h2 { margin: 0; font-size: 22px; color: #1a1a2e; font-weight: 700; }
.page-header-sub { font-size: 13px; color: #999; }

.ps-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.ps-toolbar-hint { font-size: 12px; color: #999; margin-left: auto; }

.btn-action { border: none; padding: 10px 24px; font-size: 13px; border-radius: 10px; cursor: pointer; font-weight: 600; transition: transform 0.15s, box-shadow 0.15s; }
.btn-action:disabled { opacity: 0.5; cursor: not-allowed; transform: none !important; }
.btn-primary { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; }
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(102,126,234,0.35); }
.btn-secondary { background: #f4f5f7; color: #666; border: 1px solid #e0e0e0; padding: 8px 20px; font-size: 12px; }
.btn-secondary:hover:not(:disabled) { background: #e8e8e8; color: #333; }

.ps-layout { display: grid; grid-template-columns: 420px 1fr; gap: 20px; }
@media (max-width: 1100px) { .ps-layout { grid-template-columns: 1fr; } }

.card { background: #fff; border-radius: 14px; padding: 0; margin-bottom: 0; border: 1px solid #eaeaea; box-shadow: 0 1px 4px rgba(0,0,0,0.04); overflow: hidden; }
.card-header { display: flex; align-items: center; gap: 8px; padding: 16px 20px; border-bottom: 1px solid #f0f0f0; }
.card-header h3 { margin: 0; font-size: 15px; color: #333; font-weight: 700; }
.card-body { padding: 20px; }

.ps-count { font-size: 12px; color: #999; margin-left: auto; }

.ps-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 50px 20px; gap: 8px; }
.ps-empty-icon { font-size: 40px; opacity: 0.25; font-weight: 800; color: #667eea; }
.ps-empty-text { font-size: 14px; color: #888; font-weight: 600; }
.ps-empty-hint { font-size: 12px; color: #bbb; }

.state-spinner { width: 28px; height: 28px; border: 3px solid #e0e0e0; border-top-color: #667eea; border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 10px; }
.state-spinner-sm { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff; border-radius: 50%; animation: spin 0.8s linear infinite; display: inline-block; vertical-align: middle; margin-right: 6px; }
@keyframes spin { to { transform: rotate(360deg); } }

.ps-script-list { display: flex; flex-direction: column; gap: 6px; max-height: 600px; overflow-y: auto; }
.ps-script-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: #f8f9fc; border-radius: 10px; border: 1px solid #eee; transition: all 0.2s; }
.ps-script-item:hover { border-color: #667eea; background: #f0f2ff; }
.ps-script-running { border-color: #667eea; background: #eef2ff; box-shadow: 0 0 0 2px rgba(102,126,234,0.15); }

.psi-info { flex: 1; min-width: 0; }
.psi-name { font-size: 13px; font-weight: 700; color: #333; font-family: 'SF Mono', 'Fira Code', monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.psi-meta { font-size: 11px; color: #999; margin-top: 3px; }

.psi-actions { display: flex; gap: 6px; flex-shrink: 0; margin-left: 12px; }
.btn-run { background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; border: none; padding: 6px 14px; font-size: 12px; border-radius: 8px; cursor: pointer; font-weight: 600; transition: all 0.15s; white-space: nowrap; }
.btn-run:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 2px 8px rgba(34,197,94,0.35); }
.btn-run:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-del { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; padding: 6px 12px; font-size: 12px; border-radius: 8px; cursor: pointer; font-weight: 600; transition: all 0.15s; white-space: nowrap; }
.btn-del:hover:not(:disabled) { background: #fee2e2; }
.btn-del:disabled { opacity: 0.5; cursor: not-allowed; }

.ps-result-body { padding: 0 !important; }
.ps-result-badge { font-size: 11px; font-weight: 700; padding: 3px 12px; border-radius: 20px; margin-left: auto; }
.ps-result-badge.result-ok { background: #dcfce7; color: #166534; }
.ps-result-badge.result-fail { background: #fee2e2; color: #991b1b; }
.ps-result-badge.result-error { background: #fee2e2; color: #991b1b; }
.ps-result-badge.result-warn { background: #fef3c7; color: #92400e; }
.ps-executing-badge { display: flex; align-items: center; font-size: 12px; color: #667eea; font-weight: 600; margin-left: auto; background: #eef2ff; padding: 4px 12px; border-radius: 20px; }

.ps-result-summary { padding: 16px 20px; border-bottom: 1px solid #f0f0f0; }
.ps-result-summary.result-ok { background: linear-gradient(135deg, #f0fdf4, #dcfce7); }
.ps-result-summary.result-fail { background: linear-gradient(135deg, #fef2f2, #fee2e2); }
.ps-result-summary.result-error { background: linear-gradient(135deg, #fef2f2, #fee2e2); }
.ps-result-summary.result-warn { background: linear-gradient(135deg, #fffbeb, #fef3c7); }
.prs-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.prs-row:last-child { margin-bottom: 0; }
.prs-label { font-size: 12px; color: #666; font-weight: 600; min-width: 70px; }
.prs-val { font-size: 13px; color: #333; font-weight: 600; }

.ps-result-detail { }
.ps-result-tabs { padding: 10px 20px; background: #f8f9fc; border-bottom: 1px solid #f0f0f0; }
.ps-result-tab-label { font-size: 12px; font-weight: 700; color: #667eea; text-transform: uppercase; letter-spacing: 0.3px; }
.ps-result-pre { margin: 0; padding: 16px 20px; background: #1a1a2e; color: #d4d4d8; font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace; font-size: 12px; line-height: 1.7; max-height: 450px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; }
</style>