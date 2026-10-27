<template>
  <div class="manual-mode">
    <div class="mode-notice">
      <span class="notice-icon">⚠️</span>
      <span>请注意！该模式下自动去重不生效</span>
    </div>

    <div class="input-section">
      <div class="input-group">
        <input type="text" v-model="appId" placeholder="请输入 App ID" @keyup.enter="fetchData" />
        <button class="btn-refresh" @click="fetchData" :disabled="loading || !appId.trim()">
          {{ loading ? '加载中...' : '生成' }}
        </button>
      </div>
      <div class="preview-url" v-if="appId.trim()">
        <strong>请求地址：</strong>https://[上游域名由后端配置]/ad/play/task?appleid={{ appId.trim() }}
      </div>
    </div>

    <div v-if="duplicateTip" class="duplicate-tip">⚠️ {{ duplicateTip }}</div>
    <div v-if="loading && !downloadUrl" class="loading">正在获取数据...</div>

    <div class="empty-placeholder" v-if="!downloadUrl && !loading">
      <div class="empty-icon">404 NO FOUND</div>
      <div class="empty-text">当前没有任何测试条目，请输入 App ID 并点击生成按钮</div>
    </div>

    <div v-if="duplicateTip" class="duplicate-tip">⚠️ {{ duplicateTip }}</div>
    <div v-if="loading && !downloadUrl" class="loading">正在获取数据...</div>

    <div class="qr-info-section" v-if="downloadUrl">
      <div class="qr-card">
        <div class="qr-card-header">
          <span class="qr-card-icon">📱</span>
          <span class="qr-card-title">扫描二维码下载</span>
        </div>
        <div class="qrcode-container">
          <canvas ref="qrCanvas"></canvas>
        </div>
      </div>
      <div class="side-info">
        <div class="info-card">
          <div class="info-card-label">Bundle ID</div>
          <div class="info-card-value">{{ bundleId }}</div>
        </div>
        <div class="info-card">
          <div class="info-card-label">原始CurrentTargetNum数</div>
          <div class="info-card-value">{{ originalCurrentTargetNum }}</div>
        </div>
      </div>
    </div>

    <div class="action-row" v-if="bundleId">
      <button class="btn-event" @click="queryEvent" :disabled="eventLoading">
        {{ eventLoading ? '查询中...' : '查询事件' }}
      </button>
      <button class="btn-event" @click="queryEventWithFullMsg()" :disabled="eventLoading">
        查看JSON
      </button>
    </div>

    <div class="timer-panel" v-if="bundleId">
      <div class="timer-left">
        <div class="timer-row">
          <label class="timer-label">定时查询</label>
          <input class="timer-input" type="number" v-model.number="timerSeconds" min="1" max="3600" placeholder="秒数" />
          <button class="btn-timer" @click="startTimer" :disabled="timerCountdown > 0 || !timerSeconds || timerSeconds < 1">
            {{ timerCountdown > 0 ? timerCountdown + 's' : '开始定时' }}
          </button>
          <button class="btn-timer-cancel" v-if="timerCountdown > 0" @click="cancelTimer">取消</button>
        </div>
        <div class="timer-status" v-if="timerMsg">{{ timerMsg }}</div>
      </div>
      <div class="timer-divider"></div>
      <div class="timer-right">
        <template v-if="eventResult">
          <div class="event-result no-event" v-if="eventResult === 'no_event'">
            <div class="event-label">查询结果</div>
            <div class="event-value">✅ 无事件</div>
            <button class="btn-frozen" @click="doFrozen" :disabled="frozenLoading || frozenDone">
              {{ frozenDone ? '已冻结' : frozenLoading ? '冻结中...' : '冻结应用' }}
            </button>
            <div class="frozen-remark-tip">备注请写在冻结理由输入框内</div>
            <div class="frozen-reason-box" v-if="!frozenDone">
              <input class="frozen-reason-input" v-model="frozenReasonText" maxlength="200" placeholder="自定义冻结理由/备注(也可用下方下拉选择，200字以内)" />
              <select class="frozen-reason-select" v-model="frozenReasonText" @focus="loadTemplateOptions('冻结理由')" @open="loadTemplateOptions('冻结理由')" :disabled="frozenReasonText.trim() !== '' && !frozenReasonOptions.includes(frozenReasonText)">
                <option value="" disabled>请选择冻结理由</option>
                <option v-for="opt in frozenReasonOptions" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </div>
            <div class="frozen-result" v-if="frozenMsg">
              <div class="frozen-label">冻结结果</div>
              <div class="frozen-value">{{ frozenMsg }}</div>
            </div>
          </div>
          <div class="event-result" v-if="eventResult === 'has_event'">
            <div class="event-label">最新 currentTargetNum</div>
            <div class="event-value highlight">{{ newCurrentTargetNum }}</div>
            <div class="event-compare">
              原始值：<span class="diff">{{ originalCurrentTargetNum }}</span> → 最新值：<span class="diff">{{ newCurrentTargetNum }}</span>
            </div>
            <div class="attribution-tags" v-if="attributions.length > 0">
              <span class="attr-tag" v-for="attr in attributions" :key="attr" :class="attr">{{ attr }}</span>
            </div>
            <div class="no-attribution" v-if="attributions.length === 0">⚠️ 无归因</div>
          </div>
        </template>
        <div class="event-placeholder" v-else>
          <span class="event-placeholder-icon">🔍</span>
          <span>查询的事件会在这里显示</span>
        </div>
      </div>
    </div>

    <div class="form-section" v-if="downloadUrl && eventResult">
      <h4>填写入库信息（直接写入数据库）</h4>
      <div class="form-group">
        <label>异常类型</label>
        <select v-model="form.exception_type" @focus="loadTemplateOptions('异常类型')" @open="loadTemplateOptions('异常类型')">
          <option value="" disabled>请选择异常类型</option>
          <option v-for="opt in exceptionOptions" :key="opt" :value="opt">{{ opt }}</option>
        </select>
      </div>
      <div class="form-group" v-if="eventResult !== 'no_event'">
        <label>备注</label>
        <textarea v-model="form.remark" placeholder="请输入备注信息"></textarea>
      </div>
      <div style="display:flex; gap:12px;">
        <div class="form-group" style="flex:1">
          <label>记录人</label>
          <input v-model="form.recorder" />
        </div>
        <div class="form-group" style="flex:1">
          <label>记录日期</label>
          <template v-if="applyTime">
            <template v-if="currentUserRole === 'DEVELOPER'">
              <div class="date-source-toggle">
                <button type="button" class="date-source-btn" :class="{ active: dateSource === 'applyTime' }" @click="dateSource = 'applyTime'; form.record_data = applyTime">📅 applyTime</button>
                <button type="button" class="date-source-btn" :class="{ active: dateSource === 'today' }" @click="dateSource = 'today'; form.record_data = getTodayStr()">📅 今日日期</button>
              </div>
              <input :value="form.record_data" disabled class="date-readonly" />
            </template>
            <template v-else>
              <input :value="applyTime" disabled class="date-readonly" title="使用任务申请时间" />
            </template>
          </template>
          <input v-else v-model="form.record_data" />
        </div>
      </div>
      <button class="btn-save" @click="saveToMySQL" :disabled="saving">
        {{ saving ? '入库中...' : '直接入库' }}
      </button>
      <div class="save-success" v-if="saveMsg">{{ saveMsg }}</div>
    </div>
  </div>
</template>

<style scoped>.manual-mode {
  max-width: 860px;
  margin: 0 auto;
}
.mode-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 12px;
  padding: 10px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  font-weight: 600;
  color: #92400e;
}
.notice-icon {
  font-size: 16px;
}
.input-section {
  margin-bottom: 20px;
}
.input-group {
  display: flex;
  justify-content: center;
  gap: 10px;
}
.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: var(--bg-card);
  border: 2px dashed var(--border-color);
  border-radius: 16px;
  margin-bottom: 20px;
}

.timer-panel .frozen-result {
  margin-top: 6px;
  padding: 8px 12px;
}
.frozen-reason-box { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.frozen-reason-input { width: 100%; box-sizing: border-box; padding: 8px 12px; border: 2px solid #e5e7eb; border-radius: 8px; font-size: 13px; font-family: inherit; transition: all 0.2s; }
.frozen-reason-input:focus { outline: none; border-color: #667eea; box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1); }
.frozen-reason-select { width: 100%; box-sizing: border-box; padding: 8px 12px; border: 2px solid #e5e7eb; border-radius: 8px; font-size: 13px; background: #fff; transition: all 0.2s; }
.frozen-reason-select:focus { outline: none; border-color: #667eea; }
.frozen-reason-select:disabled { background: #f3f4f6; color: #9ca3af; cursor: not-allowed; }
.frozen-remark-tip { margin-top: 10px; margin-bottom: 4px; font-size: 12px; font-weight: 700; color: #b45309; }

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.empty-text {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
  text-align: center;
  line-height: 1.6;
}
.qr-info-section {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 20px;
  margin-bottom: 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 24px;
  box-shadow: var(--shadow-sm);
}
.qr-card {
  text-align: center;
}
.qr-card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 16px;
}
.qr-card-icon {
  font-size: 20px;
}
.qr-card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}
.side-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: center;
}
.info-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px 20px;
  background: var(--bg-primary);
}
.info-card-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}
.info-card-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  word-break: break-all;
}
.action-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.timer-panel {
  display: flex;
  align-items: center;
  gap: 0;
  background: #fdf2f8;
  border: 1px solid #fbcfe8;
  border-radius: 14px;
  padding: 16px 20px;
  margin-bottom: 16px;
  min-height: 80px;
}
.timer-left {
  flex-shrink: 0;
  min-width: 200px;
}
.timer-divider {
  width: 1px;
  align-self: stretch;
  background: #f9a8d4;
  margin: 0 18px;
  flex-shrink: 0;
}
.timer-right {
  flex: 1;
  min-width: 0;
}
.event-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #9ca3af;
  font-size: 13px;
  font-weight: 500;
}
.event-placeholder-icon {
  font-size: 18px;
}
.timer-panel .event-result {
  padding: 10px 14px;
  margin: 0;
}
.timer-panel .event-label {
  margin-bottom: 2px;
}
.timer-panel .event-value {
  font-size: 14px;
}
.timer-panel .event-value.highlight {
  font-size: 16px;
}
.timer-panel .event-compare {
  margin-top: 6px;
  padding: 6px 10px;
}
.timer-panel .attribution-tags {
  margin-top: 6px;
}
.timer-panel .frozen-result {
  margin-top: 6px;
  padding: 8px 12px;
}
@media (max-width: 700px) {
  .qr-info-section {
    grid-template-columns: 1fr;
  }
  .qr-card {
    justify-self: center;
  }
  .timer-panel {
    flex-direction: column;
    align-items: stretch;
  }
  .timer-divider {
    width: 100%;
    height: 1px;
    margin: 12px 0;
  }
  .timer-left {
    min-width: auto;
  }
}
.date-source-toggle { display: flex; gap: 6px; margin-bottom: 6px; }
.date-source-btn { padding: 4px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; background: #fff; color: #6b7280; transition: all 0.2s; }
.date-source-btn.active { background: #667eea; color: #fff; border-color: #667eea; }
.date-source-btn:hover:not(.active) { background: #f3f4f6; }
.date-readonly { width: 100%; box-sizing: border-box; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 13px; background: #f0fdf4; color: #166534; font-weight: 600; }
</style>

<script setup>
import {ref, reactive, watch, nextTick, onUnmounted, onMounted} from 'vue'
import { renderQRToCanvas } from '../utils/qrcode.js'
import { useTemplateOptions } from '../utils/useTemplateOptions.js'
import {
  fetchObtain,
  fetchEvent,
  fetchAllAttributions,
  fetchFrozen,
  insertRecord,
  uploadFrozenV2,
  fetchCountByRecorder
} from '../api/index.js'

const emit = defineEmits(['error'])
// const exceptionOptions = ['正常','iOS16闪退','iOS13/14/16均闪退','需要iOS18以上','地区不支持','硬件版本过低','超过10分钟0上报','越狱检测','其他','验证已解决']
// const frozenReasonOptions = ['需要iOS17以上','需要登陆后使用，无法注册','卡死在加载页进不去','非英语汉语软件，看不懂','网络检测，无法进入','需要订阅后使用','事件机制成因不明','逻辑混乱，无法正常游玩或使用']
const { exceptionOptions, frozenReasonOptions, loadTemplateOptions, preloadAllTemplateOptions } = useTemplateOptions()
const softwareType=ref('')
const appId = ref('')
const frozenDone = ref(false)
const frozenReasonText = ref('')
const frozenReasonSelect = ref('')
const downloadUrl = ref('')
const frozenForm = reactive({ bundleId: '', id: null, remark: '' })
const bundleId = ref('')
const originalCurrentTargetNum = ref(null)
const eventResult = ref('')
const newCurrentTargetNum = ref(null)
let attributions = ref([])
const lastReportTime = ref('')
const applyTime = ref('')
const dateSource = ref('applyTime')
const eventId = ref(null)
const frozenMsg = ref('')
const duplicateTip = ref('')
const loading = ref(false)
const eventLoading = ref(false)
const frozenLoading = ref(false)
const saving = ref(false)
const saveMsg = ref('')
const taskId = ref(null)
const qrCanvas = ref(null)
const timerSeconds = ref(30)
const timerCountdown = ref(0)
let timerMsg = ref('')
let timerInterval = null
let isFrozen=ref('')
const MAX_RETRIES = 5
let retryCount = 0
const currentUserRole = ref(localStorage.getItem('accType') || '')
const form = reactive(
    {
      exception_type: '',
      remark: '',
      recorder: localStorage.getItem('userName') || '',
      record_data: getTodayStr()
    })

function getTodayStr() {
  const d = new Date();
  return d.getFullYear() + '/' + (d.getMonth() + 1) + '/' + d.getDate()
}

async function fetchDataByName(){
  const json = await fetchCountByRecorder()
  if(json.success){
    alert("用户"+localStorage.getItem('userName') +"已入库"+json.data+"条")
  }else{
    alert("获取事件数失败，请联系工作人员")
  }
}

function trimApplyTime(raw) {
  if (!raw) return ''
  const idx = raw.indexOf('.')
  return idx > -1 ? raw.substring(0, idx) : raw
}

// async function renderQR(url) {
//   await nextTick()
//   if (url && qrCanvas.value) {
//     try {
//       await QRCode.toCanvas(qrCanvas.value, url, {
//         width: 256,
//         margin: 2,
//         color: {
//           dark: '#000000',
//           light: '#ffffff'
//         }
//       })
//     } catch (e) { console.error('QR码生成失败:', e) }
//   }
// }

watch(downloadUrl, (val) => { renderQR(val) })

async function fetchData() {
  const id = appId.value.trim(); if (!id) return
  loading.value = true; emit('error', ''); resetState()
  try {
    const json = await fetchObtain(id)
    if (json.success && json.data) {
      downloadUrl.value = json.data.downloadUrl || '';
      bundleId.value = json.data.bundleId || ''
      taskId.value = json.data.id ?? null
      applyTime.value = trimApplyTime(json.data.applyTime || '')
      originalCurrentTargetNum.value = json.data.currentTargetNum ?? null
      if(originalCurrentTargetNum.value<=1||originalCurrentTargetNum.value===0){
        alert("初始originalCurrentTargetNum数小于等于1或等于0，后续测试期间可能会没有归因，请注意")
      }
    } else {
      emit('error', '接口返回异常：' + (json.resultMsg || '未知错误'))
    }
  } catch (e) {
    if (e.name === 'AbortError') {
      emit('error', '请求超时')
    }
    else {
      emit('error', '请求失败：' + e.message)
    }
  } finally { loading.value = false }
}
async function queryEventWithFullMsg() {
  try {
    const data = await fetchEvent(bundleId.value)
    if(data.success){
      alert(JSON.stringify(data, null, 2))
    }
  }catch (e) {
    alert('服务器无响应，请联系技术人员')
  }
}


function toStorageDate(isoDate) {
  if (!isoDate) return ''
  const parts = isoDate.split('-')
  return parts[0] + '/' + parseInt(parts[1]) + '/' + parseInt(parts[2])
}

async function queryEvent() {
  if (!bundleId.value) return
  eventLoading.value = true; emit('error', '')
  eventResult.value = ''; newCurrentTargetNum.value = null; attributions.value = []; eventId.value = null; frozenMsg.value = ''
  lastReportTime.value = ''
  try {
    const [eventJson, attrResults] = await Promise.all([fetchEvent(bundleId.value), fetchAllAttributions(bundleId.value)])
    if (eventJson.success && eventJson.data) {
      const newCurrent = eventJson.data.currentTargetNum; eventId.value = eventJson.data.id ?? null
      if (newCurrent !== originalCurrentTargetNum.value) {
        eventResult.value = 'has_event'; newCurrentTargetNum.value = newCurrent
      }
      else {
        eventResult.value = 'no_event'
      }
    } else {
      emit('error', '事件接口返回异常：' + (eventJson.resultMsg || '未知错误'))
    }
    const found = []
    for (const { type, json } of attrResults) {
      if (json.success && json.data) {
        if (Array.isArray(json.data) && json.data.length > 0) {
          found.push(type)
        } else if (!Array.isArray(json.data) && Object.keys(json.data).length > 0) {
          found.push(type)
        }
      }
    }
    attributions.value = found
    let lrt = ''
    const appflyerResult = attrResults.find(r => r.type === 'appflyer')
    if (appflyerResult && appflyerResult.json.success && Array.isArray(appflyerResult.json.data) && appflyerResult.json.data.length > 0) {
      lrt = appflyerResult.json.data[appflyerResult.json.data.length - 1].lastReportTime || ''
    }
    if (!lrt) {
      for (const { json } of attrResults) {
        if (json.success && Array.isArray(json.data) && json.data.length > 0) {
          lrt = json.data[json.data.length - 1].lastReportTime || ''
          if (lrt) break
        }
      }
    }
    lastReportTime.value = lrt
      if (currentUserRole.value !== 'DEVELOPER') {
        form.record_data = applyTime.value || lrt
      } else {
        if (dateSource.value === 'applyTime') form.record_data = applyTime.value || lrt
        else form.record_data = getTodayStr()
      }

  } catch (e) { emit('error', '事件查询失败：' + e.message) }
  finally { eventLoading.value = false }
}

async function doFrozen() {
  if (frozenDone.value) return
  if (!taskId.value) return
  const remark = frozenReasonText.value.trim() || frozenReasonSelect.value
  if (!remark) { emit('error', '请选择或填写冻结理由'); return }
  frozenLoading.value = true; emit('error', '')
  frozenForm.bundleId = bundleId.value
  frozenForm.id = appId.value && !isNaN(Number(appId.value)) ? Number(appId.value) : 0
  frozenForm.remark = remark
  try {
    // V2：上传冻结数据（新版），独立执行，失败不阻断 V1
    let v2Ok = false
    try {
      const frozenJson = await uploadFrozenV2({
        bundleId: frozenForm.bundleId,
        id: frozenForm.id,
        remark: frozenForm.remark
      })
      v2Ok = !!frozenJson.success
    } catch (e) {
      v2Ok = false
    }
    // V1：将任务 id 传入旧版冻结接口 /api/proxy/frozen
    let v1Ok = false
    try {
      const legacyJson = await fetchFrozen(taskId.value)
      v1Ok = !!legacyJson.success
    } catch (e) {
      v1Ok = false
    }

    if (v1Ok && v2Ok) {
      frozenDone.value = true
      frozenMsg.value = 'V1V2接口均冻结成功'
      isFrozen.value = ',已冻结'
    } else if (v2Ok && !v1Ok) {
      frozenMsg.value = 'V1接口冻结失败'
    } else if (v1Ok && !v2Ok) {
      frozenMsg.value = 'V2接口冻结失败'
    } else {
      frozenMsg.value = '上传失败'
    }
  } finally {
    frozenLoading.value = false
  }
}

function generateSubmissionId() {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes)
      .map(b => b.toString(16).padStart(2, '0'))
      .join('')
}

async function saveToMySQL() {
  emit('record-saved')
  if (!form.exception_type.trim()) { emit('error', '请选择异常类型'); return }
  saving.value = true; saveMsg.value = ''
  if(newCurrentTargetNum.value===0||newCurrentTargetNum.value===null){
    attributions.value=[]
    alert('无新增事件token，即使原本的token有归因，也不会被设置在字段内')
  }
  let finalRemark = eventResult.value === 'no_event' ? frozenReasonText.value.trim() : form.remark.trim()
  if (isFrozen.value) {
    finalRemark += isFrozen.value
  }
  if (newCurrentTargetNum.value > 0 && attributions.value.length === 0) {
    finalRemark += ',无事件归因'
  }
  const finalRecordData = form.record_data.includes('T')
      ? form.record_data
      : form.record_data.includes('/')
          ? form.record_data
          : toStorageDate(form.record_data)
  try {
    const json = await insertRecord({
      URL: downloadUrl.value,
      bundleId: bundleId.value,
      ascribe: (attributions.value || []).join(';'),
      appId: appId.value,
      event_number: newCurrentTargetNum.value,
      exception_type: form.exception_type.trim(),
      record_data: finalRecordData,
      recorder: form.recorder,
      remark: finalRemark,
      isOutput: 0,
      submissionId: generateSubmissionId()
    })
    if (json.success) {
      saveMsg.value = '✅ ' + (json.resultMsg || '入库成功')
    }
    else { emit('error', json.resultMsg || '入库失败') }
  } catch (e) { emit('error', '入库请求失败：' + e.message) }
  finally { saving.value = false }
}

function resetState() {
  downloadUrl.value = '';
  bundleId.value = '';
  originalCurrentTargetNum.value = null
  eventResult.value = '';
  newCurrentTargetNum.value = null;
  attributions.value = []
  eventId.value = null;
  frozenMsg.value = '';
  duplicateTip.value = '';
  saveMsg.value = ''
  taskId.value = null;
  retryCount = 0
  lastReportTime.value = ''
  applyTime.value = ''
  dateSource.value = 'applyTime'
  form.exception_type = '';
  form.remark = '';
  form.recorder = localStorage.getItem('userName') || '';
  form.record_data = getTodayStr()
  isFrozen.value=''
  frozenDone.value = false
  frozenReasonText.value = ''
  frozenReasonSelect.value = ''
}

function startTimer() {
  if (!timerSeconds.value || timerSeconds.value < 1) return
  timerCountdown.value = timerSeconds.value
  timerMsg.value = '将在 ' + timerCountdown.value + ' 秒后自动查询事件'
  timerInterval = setInterval(() => {
    timerCountdown.value--
    if (timerCountdown.value <= 0) {
      clearInterval(timerInterval)
      timerInterval = null
      timerCountdown.value = 0
      timerMsg.value = '定时已到，正在自动查询事件...'
      queryEvent().then(() => {
        timerMsg.value = '✅ 定时查询已完成'
        setTimeout(() => {
          timerMsg.value = '计时结束后内容会在这里显示'
        }, 5000);
      })
    }
  }, 1000)
}

function cancelTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
  timerCountdown.value = 0
  timerMsg.value = ''
}
onMounted(() => { preloadAllTemplateOptions() })
onUnmounted(() => { cancelTimer() })
onMounted(() => {
  timerMsg.value='计时结束后内容会在这里显示'
}
)
watch(downloadUrl, async (val) => { await nextTick(); renderQRToCanvas(qrCanvas.value, val) })
</script>
