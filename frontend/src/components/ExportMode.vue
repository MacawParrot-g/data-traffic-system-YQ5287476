<template>
  <div class="export-mode">
    <header class="export-heading">
      <div>
        <span class="export-kicker">DATA PIPELINE / EXPORT</span>
        <h2>数据导出</h2>
        <p>查询待导出记录，生成并下载当前账号的数据文件。</p>
      </div>
      <span class="account-chip"><i></i>{{ currentUser || '未登录用户' }}</span>
    </header>

    <div class="export-toolbar">
      <div class="toolbar-copy">
        <span class="toolbar-label">任务控制</span>
        <strong>待导出数据</strong>
      </div>
      <button class="btn-refresh" @click="fetchList" :disabled="loading">
        <span class="refresh-icon" aria-hidden="true"></span>
        {{ loading ? '查询中...' : '刷新未导出数据' }}
      </button>
    </div>

    <div v-if="loading && !queried" class="loading"><span class="loading-spinner" aria-hidden="true"></span>正在查询数据库...</div>

    <div v-if="!loading && queried && list.length === 0 && !fileReady" class="export-empty">
      <div class="empty-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none"><path d="M7 3.5h7l4 4V20.5H7z"/><path d="M14 3.5v4h4M10 12h5M10 15.5h5"/></svg>
      </div>
      <strong>暂无待导出数据</strong>
      <div>当前用户所有数据均已导出并下载，暂无未下载文件</div>
    </div>

    <div v-if="list.length > 0" class="export-list-panel">
      <div class="export-header">
        <div class="export-count">
          <span class="count-label">待处理记录</span>
          <div>用户 <strong>{{ currentUser }}</strong> 共 <span>{{ list.length }}</span> 条未导出数据</div>
        </div>
        <button class="btn-export-exec" @click="doExport" :disabled="executing">
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 3v9m0 0 3.5-3.5M10 12 6.5 8.5M4 14.5v2h12v-2"/></svg>
          {{ executing ? '导出中...' : '一键导出' }}
        </button>
      </div>
      <div class="export-flow" aria-hidden="true">
        <span class="flow-step active">01 查询记录</span><i></i><span class="flow-step">02 生成文件</span><i></i><span class="flow-step">03 下载完成</span>
      </div>
    </div>

    <div v-if="resultMsg" class="export-result-box" :class="resultSuccess ? 'success' : 'fail'" role="status">
      <span class="result-mark" aria-hidden="true">{{ resultSuccess ? 'OK' : 'ERR' }}</span>
      <div>
        <div class="result-title">{{ resultSuccess ? '导出成功' : '导出失败' }}</div>
        <div>{{ resultMsg }}</div>
      </div>
    </div>

    <div v-if="fileReady" class="download-section">
      <div class="file-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none"><path d="M7 3.5h7l4 4V20.5H7z"/><path d="M14 3.5v4h4M10 13l2 2 2-2M12 10v5"/></svg>
      </div>
      <div class="download-copy">
        <span>文件已生成</span>
        <div class="download-info">{{ fileName }}</div>
      </div>
      <button class="btn-download" @click="doDownload" :disabled="downloading">
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 3v9m0 0 3.5-3.5M10 12 6.5 8.5M4 14.5v2h12v-2"/></svg>
        {{ downloading ? '下载中...' : '下载文件' }}
      </button>
    </div>

    <div v-if="polling" class="polling-tip"><span class="loading-spinner" aria-hidden="true"></span><span>文件生成中，请稍候...</span><i></i></div>
  </div>
</template>


<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { fetchUnexportedByUser, executeExportByUser, fetchExportStatus, getExportDownloadUrl } from '../api/index.js'

const emit = defineEmits(['error'])
const list = ref([])
const loading = ref(false)
const queried = ref(false)
const executing = ref(false)
const downloading = ref(false)
const resultMsg = ref('')
const resultSuccess = ref(false)
const fileReady = ref(false)
const fileName = ref('')
const polling = ref(false)
const currentUser = ref(localStorage.getItem('userName') || '')

let pollTimer = null

async function fetchList() {
  loading.value = true; queried.value = false; resultMsg.value = ''
  try {
    const json = await fetchUnexportedByUser(currentUser.value)
    if (json.success) {
      list.value = json.data?.list || []
    } else {
      emit('error', json.message || '查询失败'); list.value = []
    }
  } catch (e) {
    emit('error', '查询请求失败：' + e.message); list.value = []
  } finally {
    loading.value = false; queried.value = true
  }
}

async function doExport() {
  executing.value = true; resultMsg.value = ''; resultSuccess.value = false
  fileReady.value = false; fileName.value = ''
  try {
    const json = await executeExportByUser(currentUser.value)
    resultSuccess.value = json.success
    resultMsg.value = json.message || ''
    if (json.success) {
      fileName.value = json.data?.fileName || ''
      startPolling()
    }
  } catch (e) {
    resultSuccess.value = false; resultMsg.value = '请求失败：' + e.message
  } finally {
    executing.value = false
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
        await fetchList()
      }
    } catch (e) { /* ignore */ }
  }, 2000)
}

function stopPolling() {
  polling.value = false
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function doDownload() {
  downloading.value = true
  const url = getExportDownloadUrl(currentUser.value)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName.value
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => {
    downloading.value = false
    fileReady.value = false
    fileName.value = ''
    resultMsg.value = '文件已下载，服务器端文件已自动删除'
    resultSuccess.value = true
  }, 1500)
}

onMounted(() => { fetchList() })
onUnmounted(() => { stopPolling() })
</script>

<style scoped>
.export-mode {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.export-mode *, .export-mode *::before, .export-mode *::after { box-sizing: border-box; }
.export-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 16px; padding: 0 2px; }
.export-kicker { display: block; margin-bottom: 5px; color: var(--accent); font-family: var(--font-mono); font-size: 9px; line-height: 1.5; font-weight: 700; letter-spacing: 1.25px; }
.export-heading h2 { margin: 0; font-size: 20px; line-height: 1.4; font-weight: 750; letter-spacing: -0.35px; }
.export-heading p { margin: 5px 0 0; color: var(--text-secondary); font-size: 11px; line-height: 1.7; }
.account-chip { display: inline-flex; align-items: center; gap: 7px; max-width: 220px; padding: 6px 10px; overflow: hidden; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #cde7df; border-radius: 999px; font-size: 10px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.account-chip i { flex: none; width: 6px; height: 6px; background: var(--accent); border-radius: 50%; box-shadow: 0 0 0 3px rgba(15,143,131,0.09); }
.export-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 14px; padding: 13px 15px; background: rgba(255,255,255,0.76); border: 1px solid var(--border-color); border-radius: var(--radius); box-shadow: var(--shadow-sm), inset 0 1px 0 #fff; backdrop-filter: blur(18px) saturate(125%); -webkit-backdrop-filter: blur(18px) saturate(125%); }
.toolbar-copy { display: flex; align-items: center; gap: 11px; }
.toolbar-label { padding-right: 11px; color: var(--text-tertiary); border-right: 1px solid var(--border-color); font-family: var(--font-mono); font-size: 8px; font-weight: 700; letter-spacing: 0.8px; }
.toolbar-copy strong { font-size: 12px; font-weight: 700; }
.btn-refresh, .btn-export-exec, .btn-download { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 38px; padding: 8px 15px; color: #fff; background: var(--accent); border: 1px solid var(--accent-strong); border-radius: 10px; box-shadow: inset 0 1px 0 rgba(255,255,255,0.22), 0 5px 14px rgba(15,143,131,0.14); font-size: 11px; line-height: 1.4; font-weight: 650; white-space: nowrap; cursor: pointer; transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease; }
.btn-refresh:hover:not(:disabled), .btn-export-exec:hover:not(:disabled), .btn-download:hover:not(:disabled) { transform: translateY(-1px); background: var(--accent-strong); box-shadow: var(--shadow-md); }
.export-mode button:disabled { opacity: 0.48; cursor: not-allowed; transform: none; box-shadow: none; }
.refresh-icon { position: relative; width: 13px; height: 13px; border: 1.5px solid currentColor; border-left-color: transparent; border-radius: 50%; }
.refresh-icon::after { content: ''; position: absolute; left: -2px; top: -2px; width: 0; height: 0; border-top: 3px solid transparent; border-bottom: 3px solid transparent; border-right: 4px solid currentColor; transform: rotate(35deg); }
.loading { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 90px; padding: 20px; color: var(--text-secondary); background: rgba(255,255,255,0.64); border: 1px solid var(--border-color); border-radius: var(--radius); font-size: 11px; }
.loading-spinner { width: 14px; height: 14px; flex: none; border: 2px solid #cce2dc; border-top-color: var(--accent); border-radius: 50%; animation: export-spin 700ms linear infinite; }
@keyframes export-spin { to { transform: rotate(360deg); } }
.export-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 260px; padding: 42px 20px; color: var(--text-secondary); text-align: center; background-color: rgba(255,255,255,0.66); background-image: linear-gradient(rgba(15,143,131,0.025) 1px, transparent 1px), linear-gradient(90deg, rgba(15,143,131,0.025) 1px, transparent 1px); background-size: 24px 24px; border: 1px dashed #bfd5ce; border-radius: var(--radius-lg); font-size: 11px; line-height: 1.8; }
.export-empty .empty-icon { display: grid; place-items: center; width: 58px; height: 58px; margin-bottom: 14px; color: var(--accent); background: var(--accent-soft); border: 1px solid #cde7df; border-radius: 17px; box-shadow: inset 0 1px 0 #fff; }
.empty-icon svg { width: 26px; height: 26px; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.export-empty strong { margin-bottom: 5px; color: var(--text-primary); font-size: 13px; }
.export-list-panel { margin-bottom: 14px; padding: 17px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.export-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin: 0; }
.export-count { min-width: 0; color: var(--text-secondary); font-size: 11px; line-height: 1.8; }
.count-label { display: block; margin-bottom: 2px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; font-weight: 700; letter-spacing: 0.7px; }
.export-count strong { color: var(--text-primary); font-weight: 650; overflow-wrap: anywhere; }
.export-count div > span { display: inline-block; margin: 0 3px; color: var(--accent-strong); font-family: var(--font-mono); font-size: 21px; line-height: 1; font-weight: 750; vertical-align: -2px; }
.btn-export-exec svg, .btn-download svg { width: 16px; height: 16px; flex: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.export-flow { display: flex; align-items: center; gap: 9px; margin-top: 15px; padding-top: 13px; border-top: 1px solid var(--border-color); }
.flow-step { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; white-space: nowrap; }
.flow-step.active { color: var(--accent-strong); font-weight: 700; }
.export-flow i { flex: 1; height: 1px; background: var(--border-color); }
.export-result-box { display: flex; align-items: flex-start; gap: 11px; margin-top: 14px; padding: 12px 14px; border: 1px solid; border-radius: 11px; font-size: 11px; line-height: 1.7; }
.export-result-box.success { color: #176e52; background: var(--success-soft); border-color: #cde8da; }
.export-result-box.fail { color: #a94444; background: var(--danger-soft); border-color: #efd2cf; }
.result-mark { display: inline-grid; place-items: center; flex: none; min-width: 30px; height: 22px; padding: 0 5px; color: currentColor; background: rgba(255,255,255,0.55); border: 1px solid currentColor; border-radius: 6px; font-family: var(--font-mono); font-size: 8px; font-weight: 800; opacity: 0.82; }
.result-title { margin-bottom: 2px; color: currentColor; font-weight: 750; }
.download-section { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 13px; margin-top: 14px; padding: 15px; background: linear-gradient(135deg, rgba(255,255,255,0.9), rgba(233,247,240,0.78)); border: 1px solid #c7e2d5; border-radius: var(--radius); box-shadow: inset 0 1px 0 #fff, var(--shadow-md); backdrop-filter: blur(20px) saturate(130%); -webkit-backdrop-filter: blur(20px) saturate(130%); }
.file-icon { display: grid; place-items: center; width: 40px; height: 40px; color: var(--success); background: rgba(255,255,255,0.72); border: 1px solid #cde8da; border-radius: 11px; }
.file-icon svg { width: 22px; height: 22px; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.download-copy { min-width: 0; }
.download-copy > span { display: block; margin-bottom: 3px; color: var(--success); font-size: 9px; font-weight: 700; }
.download-info { margin: 0; overflow: hidden; color: var(--text-primary); font-family: var(--font-mono); font-size: 11px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.polling-tip { display: flex; align-items: center; gap: 9px; margin-top: 14px; padding: 11px 13px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #cde7df; border-radius: 10px; font-size: 10px; font-weight: 600; }
.polling-tip i { flex: 1; height: 2px; overflow: hidden; background: #cce5dd; border-radius: 2px; }
.polling-tip i::after { content: ''; display: block; width: 35%; height: 100%; background: var(--accent); animation: export-progress 1.4s ease-in-out infinite; }
@keyframes export-progress { from { transform: translateX(-100%); } to { transform: translateX(390%); } }
@media (max-width: 700px) {
  .export-header { align-items: flex-start; }
  .export-flow { overflow-x: auto; padding-bottom: 3px; }
  .export-flow i { min-width: 24px; }
  .download-section { grid-template-columns: auto minmax(0, 1fr); }
  .download-section .btn-download { grid-column: 1 / -1; width: 100%; }
}
@media (max-width: 480px) {
  .export-heading { gap: 10px; }
  .export-heading h2 { font-size: 18px; }
  .account-chip { max-width: 120px; padding: 5px 8px; font-size: 9px; }
  .export-toolbar { padding: 12px; }
  .toolbar-label { display: none; }
  .export-toolbar .btn-refresh { padding-inline: 11px; }
  .export-list-panel { padding: 14px; border-radius: 14px; }
  .export-header { flex-direction: column; gap: 12px; }
  .btn-export-exec { width: 100%; }
  .export-flow { gap: 7px; }
  .flow-step { font-size: 7px; }
  .download-section { padding: 13px; }
}
@media (prefers-reduced-motion: reduce) {
  .export-mode *, .export-mode *::before, .export-mode *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}
</style>
