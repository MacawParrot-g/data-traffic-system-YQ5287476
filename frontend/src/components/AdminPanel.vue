<script setup>import { ref, computed, onMounted, reactive, onUnmounted, nextTick, watch } from 'vue'
import {
  fetchUserList,
  createUser,
  deleteUser,
  fetchSystemInfo,
  resetUserPassword,
  kickUser,
  fetchRecordListByDate,
  fetchDailyReport,
  adminRecordSearch,
  adminRecordStats,
  adminBatchDelete,
  adminRecordSummary,
  adminRecordSummaryByRecorder,
  adminBatchImport,
  executeExportByDate,
  executeExportByHashes,
  executeExportAll,
  fetchUnexportedByUser,
  fetchExportStatus,
  getExportDownloadUrl,
  updateRecord,
  adminAppIdLookup,
  adminAppIdSave,
  fetchEvent,
  fetchRetestCacheList,
  warmupRetestCache,
  addRetestBundle,
  updateRetestBundle,
  deleteRetestBundle,
} from '../api/index.js'

import { Chart, registerables } from 'chart.js'
Chart.register(...registerables)
const emit = defineEmits(['error'])
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const advFilters = reactive({
  dateFrom: getTodayDateStr(),
  dateTo: getTodayDateStr(),
  bundleId: '',
  keyword: '',
  exceptionType: '',
  isOutput: null,
  recorder: ''
})

const recorderSummary = ref([])
const editingRow = ref(null)
const editBuffer = reactive({})
const savingEdit = ref(false)
let chartInstances = []
const ATTR_COLORS = ['#2f6b46', '#b5651d', '#3a6ea5', '#6b4fa6']
const ATTR_COLORS_LIGHT = ['rgba(47,107,70,0.7)', 'rgba(181,101,29,0.7)', 'rgba(58,110,165,0.7)', 'rgba(107,79,166,0.7)']
const RECORDER_COLORS = ['#6366f1', '#ec4899', '#14b8a6', '#f97316', '#8b5cf6', '#06b6d4', '#ef4444', '#84cc16']
// ... existing code ...

const appIdMode = ref('local')
const appIdBundleId = ref('')
const appIdLoading = ref(false)
const appIdResult = ref(null)
const appIdOnlineResult = ref(null)
const appIdSaving = ref(false)
const appIdSaveMsg = ref('')
let attrPieChart = null
let recorderBarChart = null
const advExpanded = ref(true)
const statsData = ref(null)
const selectedUrls = ref([])
const batchDeleting = ref(false)
const exceptionOptions = ref([])
const recorderOptions = ref([])
const ATTR_OPTIONS = ['appflyer', 'adjust', 'singular', 'tenjin']
const pageSize = ref(15)
const pageSizeInput = ref(15)
const total = ref(0)
const activeTab = ref('report')
const selectedHashes = ref([])
const selectedAscribe = ref('')
const frozenOnly = ref(false)
const list = ref([])
const loading = ref(false)
const queried = ref(false)
const viewType = ref('ALL')
const currentPage = ref(1)
const recorderSearch = ref('')
const dateSearch = ref('')
const summaryData = ref(null)
const userList = ref([])
const userLoading = ref(false)
const createForm = reactive({ name: '', pwd: '', type: 'USER' })
const creating = ref(false)
const createMsg = ref('')
const todayUnexportedLoading = ref(false)
const exporting = ref(false)
const exportPolling = ref(false)
const exportFileReady = ref(false)
const exportFileName = ref('')
const exportResultMsg = ref('')
const exportResultSuccess = ref(false)
const currentUser = ref(localStorage.getItem('userName') || '')
let exportPollTimer = null
const unexportedTotal = ref(0)
let unexportedTimer = null
const chartFilter = ref('all')
const CHART_FILTER_OPTIONS = [
  { value: 'attrPie', label: '整体归因占比' },
  { value: 'recorderBar', label: '记录人合格率 vs 工作量' },
  { value: 'stackedAttr', label: '记录人归因构成对比' },
  { value: 'recorderPies', label: '各记录人归因明细' },
  { value: 'all', label: '全部图表' }
]
const hasSelection = computed(() => selectedHashes.value.length > 0)

const sysInfo = ref(null)
const sysLoading = ref(false)

const resetPwdUid = ref('')
const resetPwdNew = ref('')
const resetting = ref(false)
const resetMsg = ref('')
const kickBanSeconds = ref(300)
let userPollTimer = null

const importRawText = ref('')
const importing = ref(false)
const importMsg = ref('')
const importResult = ref(null)
const importExpanded = ref(false)

const reportVisible = ref(false)
const reportDate = ref('')
const reportLoading = ref(false)
const reportData = ref(null)

const currentUserRole = ref(localStorage.getItem('accType') || '')
const dateSortOrder = ref(null)

const retestList = ref([])
const retestLoading = ref(false)
const retestTotal = ref(0)
const retestTtl = ref(0)
const retestWarming = ref(false)
const retestMsg = ref('')
const retestAddForm = reactive({ bundleId: '', grade: 'B' })
const retestEditing = ref(null)
const retestEditGrade = ref('B')

const viewTypeLabel = {
  ALL: '全部数据', APPFLYER: 'appflyer', ADJUST: 'adjust',
  SINGULAR: 'singular', TENJIN: 'tenjin', FROZEN: '已冻结数据',
  APPFLYER_FROZEN: 'appflyer · 已冻结', ADJUST_FROZEN: 'adjust · 已冻结',
  SINGULAR_FROZEN: 'singular · 已冻结', TENJIN_FROZEN: 'tenjin · 已冻结'
}

async function loadRetestCache() {
  retestLoading.value = true
  try {
    const json = await fetchRetestCacheList()
    if (json.success && json.data) {
      retestList.value = json.data.list || []
      retestTotal.value = json.data.total || 0
      retestTtl.value = json.data.ttlSeconds || 0
    } else {
      emit('error', json.message || '查询复测缓存失败')
    }
  } catch (e) {
    emit('error', '查询复测缓存请求失败：' + e.message)
  } finally {
    retestLoading.value = false
  }
}

async function doRetestWarmup() {
  retestWarming.value = true
  retestMsg.value = ''
  try {
    const json = await warmupRetestCache()
    retestMsg.value = (json.success ? '✅ ' : '❌ ') + (json.message || '')
    await loadRetestCache()
  } catch (e) {
    retestMsg.value = '❌ 预热请求失败：' + e.message
  } finally {
    retestWarming.value = false
  }
}

async function doRetestAdd() {
  const bid = retestAddForm.bundleId.trim()
  if (!bid) { retestMsg.value = '❌ 请输入 Bundle ID'; return }
  try {
    const json = await addRetestBundle(bid, retestAddForm.grade)
    retestMsg.value = (json.success ? '✅ ' : '❌ ') + (json.message || '')
    if (json.success) { retestAddForm.bundleId = ''; await loadRetestCache() }
  } catch (e) {
    retestMsg.value = '❌ 新增请求失败：' + e.message
  }
}

function startRetestEdit(item) {
  retestEditing.value = item.bundleId
  retestEditGrade.value = item.grade || 'B'
}

async function saveRetestEdit() {
  try {
    const json = await updateRetestBundle(retestEditing.value, retestEditGrade.value)
    retestMsg.value = (json.success ? '✅ ' : '❌ ') + (json.message || '')
    retestEditing.value = null
    if (json.success) await loadRetestCache()
  } catch (e) {
    retestMsg.value = '❌ 更新请求失败：' + e.message
  }
}

async function doRetestDelete(bundleId) {
  if (!confirm('确认从复测缓存中删除 ' + bundleId + ' ？')) return
  try {
    const json = await deleteRetestBundle(bundleId)
    retestMsg.value = (json.success ? '✅ ' : '❌ ') + (json.message || '')
    if (json.success) await loadRetestCache()
  } catch (e) {
    retestMsg.value = '❌ 删除请求失败：' + e.message
  }
}

async function doRetestClear() {
  if (!confirm('确认清空全部复测缓存数据？')) return
  try {
    const json = await clearRetestCache()
    retestMsg.value = (json.success ? '✅ ' : '❌ ') + (json.message || '')
    if (json.success) await loadRetestCache()
  } catch (e) {
    retestMsg.value = '❌ 清空请求失败：' + e.message
  }
}

function formatRetestTtl(sec) {
  if (!sec || sec <= 0) return '已过期 / 无'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return h + ' 小时 ' + m + ' 分'
}

function toggleDateSort() {
  if (dateSortOrder.value === null) dateSortOrder.value = 'asc'
  else if (dateSortOrder.value === 'asc') dateSortOrder.value = 'desc'
  else dateSortOrder.value = null
  fetchData(true)
}

function normalizeRecordData(recordData) {
  if (!recordData) return ''
  let str = String(recordData).trim()
  str = str.replace(/\//g, '-')
  if (!str.includes('T') && !str.includes(' ')) {
    const parts = str.split('-')
    if (parts.length === 3) {
      return parts[0].padStart(4, '0') + '-' + (parts[1] || '01').padStart(2, '0') + '-' + (parts[2] || '01').padStart(2, '0') + 'T00:00:00'
    }
    return str
  }
  let [datePart, timePart] = str.includes('T') ? str.split('T') : str.split(' ')
  const dp = datePart.split('-')
  const y = dp[0].padStart(4, '0')
  const m = (dp[1] || '01').padStart(2, '0')
  const d = (dp[2] || '01').padStart(2, '0')
  const tp = (timePart || '00:00:00').substring(0, 8)
  return y + '-' + m + '-' + d + 'T' + tp
}

function extractTimePart(recordData) {
  const normalized = normalizeRecordData(recordData)
  const tIdx = normalized.indexOf('T')
  if (tIdx === -1) return ''
  return normalized.substring(tIdx + 1)
}

const sortedList = computed(() => list.value)

function resetFilters() {
  advFilters.dateFrom = getTodayDateStr()
  advFilters.dateTo = getTodayDateStr()
  advFilters.bundleId = ''
  advFilters.keyword = ''
  advFilters.exceptionType = ''
  advFilters.isOutput = null
  advFilters.recorder = ''
  selectedAscribe.value = ''
  frozenOnly.value = false
  recorderSearch.value = ''
  dateSearch.value = ''
  fetchData(true)
}

async function fetchData(resetPage = false) {
  if (resetPage) currentPage.value = 1
  list.value = []
  viewType.value = ''
  total.value = 0
  loading.value = true
  queried.value = true
  selectedUrls.value = []
  selectedHashes.value = []
  const filterParams = {
    dateFrom: advFilters.dateFrom,
    dateTo: advFilters.dateTo,
    bundleId: advFilters.bundleId.trim(),
    keyword: advFilters.keyword.trim(),
    exceptionType: advFilters.exceptionType,
    ascribe: selectedAscribe.value,
    frozenOnly: frozenOnly.value,
    recorder: advFilters.recorder.trim() || recorderSearch.value.trim(),
    isOutput: advFilters.isOutput
  }
  try {
    const promises = [
      adminRecordSearch({ ...filterParams, page: currentPage.value, size: pageSize.value, dateSort: dateSortOrder.value }),
      adminRecordSummary(filterParams)
    ]
    if (activeTab.value === 'report') {
      promises.push(adminRecordSummaryByRecorder(filterParams))
    }
    const results = await Promise.all(promises)
    const searchJson = results[0]
    const summaryJson = results[1]
    if (searchJson.success) {
      list.value = searchJson.data || []
      viewType.value = searchJson.viewType || 'ALL'
      total.value = searchJson.total || 0
    } else {
      emit('error', searchJson.message || '查询失败')
    }
    if (summaryJson.success) {
      summaryData.value = summaryJson.data
    }
    if (activeTab.value === 'report' && results[2]) {
      const recorderJson = results[2]
      if (recorderJson.success) {
        recorderSummary.value = recorderJson.data || []
        await nextTick()
        renderCharts()
      }
    }
  } catch (e) {
    emit('error', '查询请求失败：' + e.message)
  } finally {
    loading.value = false
  }
}

function getSummaryAttrPercent(count) {
  if (!summaryData.value || summaryData.value.qualifiedCount === 0) return '0.0'
  return (count * 100 / summaryData.value.qualifiedCount).toFixed(1)
}

function startUnexportedPolling() {
  loadUnexportedCount()
  unexportedTimer = setInterval(loadUnexportedCount, 30000)
}

function stopUnexportedPolling() {
  if (unexportedTimer) { clearInterval(unexportedTimer); unexportedTimer = null }
}


function toggleSelectAll(event) {
  if (event.target.checked) {
    selectedHashes.value = list.value.map(item => item.hash)
  } else {
    selectedHashes.value = []
  }
}

function getTodayDateStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

function formatDateForQuery(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  return parts[0] + '/' + parseInt(parts[1]) + '/' + parseInt(parts[2])
}

function renderCharts() {
  destroyCharts()
  if (!summaryData.value) return

  const mainPie = document.getElementById('attrPieChart')
  if (mainPie) {
    const attrs = summaryData.value.attributions
    chartInstances.push(new Chart(mainPie, {
      type: 'doughnut',
      data: {
        labels: ['appflyer', 'adjust', 'singular', 'tenjin'],
        datasets: [{
          data: [attrs.appflyer, attrs.adjust, attrs.singular, attrs.tenjin],
          backgroundColor: ATTR_COLORS,
          borderWidth: 3, borderColor: '#fff', hoverOffset: 8
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false, cutout: '55%',
        plugins: {
          legend: { position: 'bottom', labels: { padding: 14, font: { size: 11, weight: '600' }, usePointStyle: true, pointStyle: 'circle' } },
          title: { display: true, text: '整体归因占比', font: { size: 14, weight: '700' }, color: '#1a1a2e', padding: { bottom: 8 } },
          tooltip: { callbacks: { label: ctx => `${ctx.label}: ${ctx.raw} 条 (${summaryData.value.qualifiedCount > 0 ? (ctx.raw * 100 / summaryData.value.qualifiedCount).toFixed(1) : 0}%)` } }
        }
      }
    }))
  }

  // const exportPie = document.getElementById('exportPieChart')
  // if (exportPie) {
  //   const exported = summaryData.value.totalCount - (summaryData.value.totalCount - (summaryData.value.totalCount > 0 ? Math.round(summaryData.value.totalCount * (statsData.value?.exportedCount || 0) / (statsData.value?.totalCount || 1)) : 0))
  //   chartInstances.push(new Chart(exportPie, {
  //     type: 'doughnut',
  //     data: {
  //       labels: ['已导出', '未导出'],
  //       datasets: [{
  //         data: [summaryData.value.totalCount > 0 ? Math.round(summaryData.value.totalCount * ((statsData.value?.exportedCount || 0) / Math.max(statsData.value?.totalCount || 1, 1))) : 0,
  //           summaryData.value.totalCount - Math.round(summaryData.value.totalCount * ((statsData.value?.exportedCount || 0) / Math.max(statsData.value?.totalCount || 1, 1)))],
  //         backgroundColor: ['#22c55e', '#fbbf24'],
  //         borderWidth: 3, borderColor: '#fff', hoverOffset: 8
  //       }]
  //     },
  //     options: {
  //       responsive: true, maintainAspectRatio: false, cutout: '55%',
  //       plugins: {
  //         legend: { position: 'bottom', labels: { padding: 14, font: { size: 11, weight: '600' }, usePointStyle: true, pointStyle: 'circle' } },
  //         title: { display: true, text: '导出状态分布', font: { size: 14, weight: '700' }, color: '#1a1a2e', padding: { bottom: 8 } }
  //       }
  //     }
  //   }))
  // }

  if (recorderSummary.value.length > 0) {
    const barCtx = document.getElementById('recorderBarChart')
    if (barCtx) {
      const labels = recorderSummary.value.map(r => r.recorder)
      chartInstances.push(new Chart(barCtx, {
        type: 'bar',
        data: {
          labels,
          datasets: [
            { label: '合格率 (%)', data: recorderSummary.value.map(r => r.qualifyRate), backgroundColor: 'rgba(99,102,241,0.75)', borderColor: '#6366f1', borderWidth: 1, borderRadius: 6, yAxisID: 'y' },
            { label: '总记录数', data: recorderSummary.value.map(r => r.totalCount), backgroundColor: 'rgba(203,213,225,0.5)', borderColor: '#94a3b8', borderWidth: 1, borderRadius: 6, yAxisID: 'y1' }
          ]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: {
            legend: { position: 'top', labels: { font: { size: 11, weight: '600' }, usePointStyle: true } },
            title: { display: true, text: '各记录人合格率 vs 工作量', font: { size: 14, weight: '700' }, color: '#1a1a2e', padding: { bottom: 8 } }
          },
          scales: {
            y: { type: 'linear', position: 'left', min: 0, max: 100, title: { display: true, text: '合格率 (%)', font: { size: 11 } }, ticks: { callback: v => v + '%' } },
            y1: { type: 'linear', position: 'right', grid: { drawOnChartArea: false }, title: { display: true, text: '记录数', font: { size: 11 } } }
          }
        }
      }))
    }

    const stackedCtx = document.getElementById('stackedAttrChart')
    if (stackedCtx) {
      chartInstances.push(new Chart(stackedCtx, {
        type: 'bar',
        data: {
          labels: recorderSummary.value.map(r => r.recorder),
          datasets: ATTR_OPTIONS.map((attr, i) => ({
            label: attr,
            data: recorderSummary.value.map(r => r.attributions[attr]),
            backgroundColor: ATTR_COLORS_LIGHT[i],
            borderColor: ATTR_COLORS[i],
            borderWidth: 1, borderRadius: 4
          }))
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: {
            legend: { position: 'top', labels: { font: { size: 11, weight: '600' }, usePointStyle: true, pointStyle: 'rectRounded' } },
            title: { display: true, text: '各记录人归因构成对比', font: { size: 14, weight: '700' }, color: '#1a1a2e', padding: { bottom: 8 } }
          },
          scales: {
            x: { stacked: true, grid: { display: false } },
            y: { stacked: true, title: { display: true, text: '记录数', font: { size: 11 } } }
          }
        }
      }))
    }

    const radarCtx = document.getElementById('radarChart')
    if (radarCtx && recorderSummary.value.length >= 3) {
      const maxTotal = Math.max(...recorderSummary.value.map(r => r.totalCount))
      const maxQualified = Math.max(...recorderSummary.value.map(r => r.qualifiedCount))
      chartInstances.push(new Chart(radarCtx, {
        type: 'radar',
        data: {
          labels: ['工作量', '合格数', '合格率', 'appflyer', 'adjust', 'singular', 'tenjin'],
          datasets: recorderSummary.value.slice(0, 6).map((r, i) => {
            const maxAttr = Math.max(...ATTR_OPTIONS.map(a => r.attributions[a])) || 1
            return {
              label: r.recorder,
              data: [
                maxTotal > 0 ? Math.round(r.totalCount * 100 / maxTotal) : 0,
                maxQualified > 0 ? Math.round(r.qualifiedCount * 100 / maxQualified) : 0,
                r.qualifyRate,
                maxAttr > 0 ? Math.round(r.attributions.appflyer * 100 / maxAttr) : 0,
                maxAttr > 0 ? Math.round(r.attributions.adjust * 100 / maxAttr) : 0,
                maxAttr > 0 ? Math.round(r.attributions.singular * 100 / maxAttr) : 0,
                maxAttr > 0 ? Math.round(r.attributions.tenjin * 100 / maxAttr) : 0
              ],
              backgroundColor: RECORDER_COLORS[i] + '20',
              borderColor: RECORDER_COLORS[i],
              borderWidth: 2, pointBackgroundColor: RECORDER_COLORS[i], pointRadius: 3
            }
          })
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: {
            legend: { position: 'bottom', labels: { font: { size: 11, weight: '600' }, usePointStyle: true, padding: 12 } },
            title: { display: true, text: '记录人综合能力雷达', font: { size: 14, weight: '700' }, color: '#1a1a2e', padding: { bottom: 8 } }
          },
          scales: { r: { min: 0, max: 100, ticks: { stepSize: 25, font: { size: 9 }, backdropColor: 'transparent' }, grid: { color: '#ece2d3' }, pointLabels: { font: { size: 10, weight: '600' } } } }
        }
      }))
    }

    recorderSummary.value.forEach((r, idx) => {
      const canvas = document.getElementById(`recorderPie_${idx}`)
      if (canvas) {
        const attrs = r.attributions
        const total = r.qualifiedCount || 1
        chartInstances.push(new Chart(canvas, {
          type: 'doughnut',
          data: {
            labels: ['appflyer', 'adjust', 'singular', 'tenjin', '无归因'],
            datasets: [{
              data: [attrs.appflyer, attrs.adjust, attrs.singular, attrs.tenjin, r.totalCount - r.qualifiedCount],
              backgroundColor: [...ATTR_COLORS, '#ece2d3'],
              borderWidth: 2, borderColor: '#fff', hoverOffset: 6
            }]
          },
          options: {
            responsive: true, maintainAspectRatio: false, cutout: '50%',
            plugins: {
              legend: { display: false },
              title: { display: true, text: `${r.recorder}（${r.totalCount}条 / 合格率${r.qualifyRate}%）`, font: { size: 12, weight: '700' }, color: '#333', padding: { bottom: 4 } },
              tooltip: { callbacks: { label: ctx => `${ctx.label}: ${ctx.raw} 条 (${total > 0 ? (ctx.raw * 100 / r.totalCount).toFixed(1) : 0}%)` } }
            }
          }
        }))
      }
    })
  }
}

function destroyCharts() {
  chartInstances.forEach(c => { try { c.destroy() } catch(e) {} })
  chartInstances = []
}

async function doAppIdLookup() {
  const bid = appIdBundleId.value.trim()
  if (!bid) { emit('error', '请输入 Bundle ID'); return }
  appIdLoading.value = true
  appIdResult.value = null
  appIdOnlineResult.value = null
  appIdSaveMsg.value = ''
  try {
    if (appIdMode.value === 'local') {
      const json = await adminAppIdLookup(bid)
      if (json.success) {
        appIdResult.value = json.data
      } else {
        emit('error', json.message || '查询失败')
      }
    } else {
      const json = await fetchEvent(bid)
      if (json && json.success && json.data) {
        appIdOnlineResult.value = json.data
        if (json.data.bundleId && json.data.appId) {
          const saveJson = await adminAppIdSave(json.data.bundleId, json.data.appId)
          appIdSaveMsg.value = saveJson.message || (saveJson.success ? '已同步到本地数据库' : '同步失败')
        }
      } else {
        emit('error', (json && json.resultMsg) || '联网搜索未返回有效数据')
      }
    }
  } catch (e) {
    emit('error', '查询失败：' + e.message)
  } finally {
    appIdLoading.value = false
  }
}

function startEditRow(index) {
  editingRow.value = index
  const item = list.value[index]
  editBuffer.URL = item.URL || ''
  editBuffer.bundleId = item.bundleId || ''
  editBuffer.ascribe = item.ascribe || ''
  editBuffer.event_number = item.event_number ?? ''
  editBuffer.exception_type = item.exception_type || ''
  editBuffer.record_data = item.record_data || ''
  editBuffer.recorder = item.recorder || ''
  editBuffer.remark = item.remark || ''
}

function cancelEditRow() {
  editingRow.value = null
}

async function saveEditRow(index) {
  savingEdit.value = true
  const item = list.value[index]
  const updated = {
    hash: item.hash,
    URL: editBuffer.URL,
    bundleId: editBuffer.bundleId,
    ascribe: editBuffer.ascribe,
    event_number: editBuffer.event_number === '' ? null : parseInt(editBuffer.event_number),
    exception_type: editBuffer.exception_type,
    record_data: editBuffer.record_data,
    recorder: editBuffer.recorder,
    remark: editBuffer.remark,
    isOutput: item.isOutput
  }
  try {
    const json = await updateRecord(updated)
    if (json.success) {
      Object.assign(list.value[index], {
        URL: editBuffer.URL,
        bundleId: editBuffer.bundleId,
        ascribe: editBuffer.ascribe,
        event_number: updated.event_number,
        exception_type: editBuffer.exception_type,
        record_data: editBuffer.record_data,
        recorder: editBuffer.recorder,
        remark: editBuffer.remark
      })
      editingRow.value = null
      await loadStats()
    } else {
      emit('error', json.message || '更新失败')
    }
  } catch (e) {
    emit('error', '更新请求失败：' + e.message)
  } finally {
    savingEdit.value = false
  }
}

function sup(){
  alert('谢谢你，成都。谢谢你，我的同桌：吴雨芹。谢谢我自己：完整的完成了这一切！！！')
}

async function refreshTodayUnexported() {
  todayUnexportedLoading.value = true
  try {
    const json = await fetchUnexportedByUser(currentUser.value)
    if (json.success && json.data) {
      unexportedTotal.value = json.data.total || 0
    }
  } catch (e) { /* silent */ }
  finally { todayUnexportedLoading.value = false }
}

async function doExportAll() {
  if (exporting.value || exportPolling.value) return
  exporting.value = true
  exportResultMsg.value = ''
  exportResultSuccess.value = false
  exportFileReady.value = false
  exportFileName.value = ''
  try {
    const json = await executeExportAll(currentUser.value)
    exportResultSuccess.value = json.success
    exportResultMsg.value = json.message || ''
    if (json.success) {
      exportFileName.value = json.data?.fileName || ''
      startExportPolling()
    }
  } catch (e) {
    exportResultSuccess.value = false
    exportResultMsg.value = '导出请求失败：' + e.message
  } finally {
    exporting.value = false
  }
}

async function doExportByDate() {
  if (exporting.value || exportPolling.value) return
  exporting.value = true
  exportResultMsg.value = ''
  exportResultSuccess.value = false
  exportFileReady.value = false
  exportFileName.value = ''
  try {
    const dateVal = advFilters.dateFrom || getTodayDateStr()
    const json = await executeExportByDate(currentUser.value, formatDateForQuery(dateVal))
    exportResultSuccess.value = json.success
    exportResultMsg.value = json.message || ''
    if (json.success) {
      exportFileName.value = json.data?.fileName || ''
      startExportPolling()
    }
  } catch (e) {
    exportResultSuccess.value = false
    exportResultMsg.value = '导出请求失败：' + e.message
  } finally {
    exporting.value = false
  }
}

async function doExportByHashes() {
  if (selectedHashes.value.length === 0) {
    emit('error', '请先选择要导出的数据')
    return
  }
  if (exporting.value || exportPolling.value) return
  exporting.value = true
  exportResultMsg.value = ''
  exportResultSuccess.value = false
  exportFileReady.value = false
  exportFileName.value = ''
  try {
    const json = await executeExportByHashes(currentUser.value, selectedHashes.value)
    exportResultSuccess.value = json.success
    exportResultMsg.value = json.message || ''
    if (json.success) {
      exportFileName.value = json.data?.fileName || ''
      startExportPolling()
    }
  } catch (e) {
    exportResultSuccess.value = false
    exportResultMsg.value = '导出请求失败：' + e.message
  } finally {
    exporting.value = false
  }
}

function startExportPolling() {
  exportPolling.value = true
  exportPollTimer = setInterval(async () => {
    try {
      const json = await fetchExportStatus(currentUser.value)
      if (json.success && json.data?.ready) {
        stopExportPolling()
        exportFileReady.value = true
        exportFileName.value = json.data.fileName || exportFileName.value
      }
    } catch (e) { /* ignore */ }
  }, 2000)
}

function stopExportPolling() {
  exportPolling.value = false
  if (exportPollTimer) { clearInterval(exportPollTimer); exportPollTimer = null }
}

function doExportDownload() {
  const url = getExportDownloadUrl(currentUser.value)
  const a = document.createElement('a')
  a.href = url
  a.download = exportFileName.value
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => {
    exportFileReady.value = false
    exportFileName.value = ''
    exportResultMsg.value = '文件已下载'
    exportResultSuccess.value = true
  }, 1500)
}

function isAllSelected() {
  return list.value.length > 0 && selectedHashes.value.length === list.value.length
}

async function loadStats() {
  try {
    const json = await adminRecordStats()
    if (json.success) {
      statsData.value = json.data
      exceptionOptions.value = json.data.exceptionTypes || []
      recorderOptions.value = json.data.recorders || []
    }
  } catch (e) { /* silent */ }
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
      alert('' + json.message)
      selectedHashes.value = []
      await fetchData()
      await loadStats()
    } else {
      emit('error', json.message || '批量删除失败')
    }
  } catch (e) {
    emit('error', '批量删除请求失败：' + e.message)
  } finally {
    batchDeleting.value = false
  }
}


async function queryDailyReport() {
  if (!reportDate.value) {
    alert('请选择日期')
    return
  }
  reportLoading.value = true
  reportData.value = null
  try {
    const json = await fetchDailyReport(reportDate.value)
    if (json.success) {
      reportData.value = json.data
    } else {
      emit('error', json.message || '查询日报失败')
    }
  } catch (e) {
    emit('error', '查询日报失败：' + e.message)
  } finally {
    reportLoading.value = false
  }
}

function closeReport() {
  reportVisible.value = false
  reportData.value = null
}
async function loadUnexportedCount() {
  try {
    const json = await fetchUnexportedByUser(currentUser.value)
    if (json.success && json.data) {
      unexportedTotal.value = json.data.total || 0
    }
  } catch (e) { /* silent */ }
}
function getAttrPercent(count) {
  if (!reportData.value || reportData.value.qualifiedCount === 0) return '0.0'
  return (count * 100 / reportData.value.qualifiedCount).toFixed(1)
}
function prevPage() { if (currentPage.value > 1) { currentPage.value--; fetchData() } }
function nextPage() { if (currentPage.value < totalPages.value) { currentPage.value++; fetchData() } }

function applyPageSize() {
  const v = parseInt(pageSizeInput.value)
  if (v > 0) {
    pageSize.value = v
    currentPage.value = 1
    fetchData(true)
  }
}

async function loadUsers() {
  userLoading.value = true
  try {
    const json = await fetchUserList()
    if (json.success) {
      userList.value = json.data || []
    } else {
      emit('error', json.message || '获取用户列表失败')
    }
  } catch (e) {
    emit('error', '获取用户列表失败：' + e.message)
  } finally {
    userLoading.value = false
  }
}

async function handleBatchImport() {
  if (!importRawText.value.trim()) {
    importMsg.value = '请粘贴要导入的数据'
    return
  }
  const lineCount = importRawText.value.split('\n').filter(l => l.trim()).length
  if (!confirm(`确定要导入 ${lineCount} 条数据吗？\n所有数据的 isOutput 将设置为 1（已导出）。`)) return
  importing.value = true
  importMsg.value = ''
  importResult.value = null
  try {
    const json = await adminBatchImport(importRawText.value)
    if (json.success) {
      importMsg.value = ' ' + json.message
      importResult.value = json.data
      importRawText.value = ''
      await fetchData()
      await loadStats()
    } else {
      importMsg.value = ' ' + (json.message || '导入失败')
    }
  } catch (e) {
    importMsg.value = ' 导入请求失败：' + e.message
  } finally {
    importing.value = false
  }
}

async function handleCreateUser() {
  if (!createForm.name.trim() || !createForm.pwd.trim()) {
    emit('error', '姓名和密码不能为空')
    return
  }
  creating.value = true
  createMsg.value = ''
  try {
    const json = await createUser(createForm.name.trim(), createForm.pwd.trim(), createForm.type)
    if (json.success) {
      createMsg.value = '' + json.message + '，账号UID：' + (json.data?.uid || '')
      createForm.name = ''
      createForm.pwd = ''
      createForm.type = 'USER'
      await loadUsers()
    } else {
      createMsg.value = '' + (json.message || '创建失败')
    }
  } catch (e) {
    createMsg.value = ' 创建请求失败：' + e.message
  } finally {
    creating.value = false
  }
}

async function handleDeleteUser(uid) {
  if (!confirm('确定要删除用户 ' + uid + ' 吗？此操作不可撤销！')) return
  try {
    const json = await deleteUser(uid)
    if (json.success) {
      await loadUsers()
    } else {
      emit('error', json.message || '删除失败')
    }
  } catch (e) {
    emit('error', '删除请求失败：' + e.message)
  }
}

async function handleResetPassword() {
  if (!resetPwdUid.value.trim()) {
    resetMsg.value = '请输入要重置的UID'
    return
  }
  if (!resetPwdNew.value.trim()) {
    resetMsg.value = '请输入新密码'
    return
  }
  resetting.value = true
  resetMsg.value = ''
  try {
    const json = await resetUserPassword(resetPwdUid.value.trim(), resetPwdNew.value.trim())
    if (json.success) {
      resetMsg.value = '' + json.message
      resetPwdUid.value = ''
      resetPwdNew.value = ''
      await loadUsers()
    } else {
      resetMsg.value = '' + (json.message || '重置失败')
    }
  } catch (e) {
    resetMsg.value = '重置请求失败：' + e.message
  } finally {
    resetting.value = false
  }
}

async function handleKickUser(uid) {
  const ban = kickBanSeconds.value || 0
  if (!confirm(`确定要踢用户 ${uid} 下线吗？${ban > 0 ? '封禁 ' + ban + ' 秒' : '不封禁'}`)) return
  try {
    const json = await kickUser(uid, ban)
    if (json.success) {
      alert('' + json.message)
      await loadUsers()
    } else {
      emit('error', json.message || '踢人失败')
    }
  } catch (e) {
    emit('error', '踢人请求失败：' + e.message)
  }
}

function startUserPolling() {
  stopUserPolling()
  userPollTimer = setInterval(() => {
    if (activeTab.value === 'users') loadUsers()
  }, 15000)
}

function stopUserPolling() {
  if (userPollTimer) {
    clearInterval(userPollTimer)
    userPollTimer = null
  }
}

function copyUrl(url, event) {
  const showTip = () => {
    const el = event.target
    const tip = document.createElement('span')
    tip.className = 'copy-tip'
    tip.textContent = '已复制'
    el.parentElement.appendChild(tip)
    setTimeout(() => tip.remove(), 1200)
  }
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(url).then(showTip).catch(() => {
      fallbackCopy(url) && showTip()
    })
  } else {
    fallbackCopy(url) && showTip()
  }
}

function fallbackCopy(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  document.body.appendChild(ta)
  ta.select()
  try { document.execCommand('copy') } catch (e) { return false }
  finally { document.body.removeChild(ta) }
  return true
}

function formatAppIdTime(timeStr) {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

async function loadSystemInfo() {
  sysLoading.value = true
  try {
    const json = await fetchSystemInfo()
    if (json.success) sysInfo.value = json.data
  } catch (e) { /* silent */ }
  finally { sysLoading.value = false }
}
onMounted(() => {
  fetchData()
  loadUsers()
  loadSystemInfo()
  loadStats()
  startUserPolling()
  startUnexportedPolling()
})

watch(activeTab, async (newTab) => {
  if (newTab === 'report') {
    await nextTick()
    if (summaryData.value || recorderSummary.value.length > 0) {
      renderCharts()
    } else {
      fetchData(true)
    }
  }
})

watch(chartFilter, async () => {
  await nextTick()
  renderCharts()
  if (newTab === 'retest') {
    loadRetestCache()
  }
})

onUnmounted(() => {
  stopUserPolling()
  stopExportPolling()
  stopUnexportedPolling()
  destroyCharts()
})

watch(activeTab, async (newTab) => {
  if (newTab === 'report') {
    await nextTick()
    if (summaryData.value || recorderSummary.value.length > 0) {
      renderCharts()
    } else {
      fetchData(true)
    }
  }
  if (newTab === 'retest') {
    loadRetestCache()
  }
})
</script>

<template>
  <div class="admin-page">
    <header class="page-header">
      <div class="page-heading">
        <span class="page-eyebrow">ADMINISTRATION / OPERATIONS</span>
        <h2>管理员后台管理系统</h2>
        <div class="page-header-sub">自动化测试数据管理平台</div>
      </div>
      <div class="page-header-meta">
        <span class="system-state"><i aria-hidden="true"></i>管理工作区</span>
        <span class="operator">当前操作员 <strong>{{ currentUser }}</strong></span>
      </div>
    </header>

    <div class="admin-tabs" role="navigation" aria-label="管理工作区">
      <button class="admin-tab" :class="{ active: activeTab === 'report' }" @click="activeTab = 'report'">
        <span class="tab-icon">01</span><span class="tab-text">数据报表</span>
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'management' }" @click="activeTab = 'management'">
        <span class="tab-icon">02</span><span class="tab-text">数据管理</span>
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'appid' }" @click="activeTab = 'appid'">
        <span class="tab-icon">03</span><span class="tab-text">App信息</span>
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'retest' }" @click="activeTab = 'retest'">
        <span class="tab-icon">04</span><span class="tab-text">查看复测数据</span>
      </button>
      <button class="admin-tab" :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">
        <span class="tab-icon">06</span><span class="tab-text">用户管理</span>
      </button>
    </div>

    <!-- ==================== 数据报表 + 数据管理 共享区域 ==================== -->
    <div v-if="activeTab === 'report' || activeTab === 'management'" class="admin-section">

      <section class="filter-card" aria-label="高级数据筛选">
        <div class="section-caption">
          <span>查询条件</span>
          <small>组合筛选当前报表与数据管理范围</small>
        </div>
        <div class="filter-top-row">
          <div class="filter-section">
            <div class="filter-section-title">归因筛选</div>
            <div class="radio-group">
              <label class="radio-tag radio-all" :class="{ active: selectedAscribe === '' }">
                <input type="radio" name="adminAscribe" value="" :checked="selectedAscribe === ''" @change="selectedAscribe = ''" />
                <span>全部</span>
              </label>
              <label v-for="opt in ATTR_OPTIONS" :key="opt" class="radio-tag" :class="[opt, { active: selectedAscribe === opt }]">
                <input type="radio" name="adminAscribe" :value="opt" :checked="selectedAscribe === opt" @change="selectedAscribe = opt" />
                <span>{{ opt }}</span>
              </label>
            </div>
          </div>
          <div class="filter-top-actions">
            <label class="checkbox-tag" :class="{ active: frozenOnly }">
              <input type="checkbox" v-model="frozenOnly" />
              <span>仅冻结</span>
            </label>
            <button class="btn-link" @click="advExpanded = !advExpanded">
              {{ advExpanded ? '收起筛选' : '展开筛选' }}
            </button>
          </div>
        </div>

        <div v-if="advExpanded" class="filter-advanced">
          <div class="filter-grid">
            <div class="filter-field">
              <label class="field-label">起始日期</label>
              <input v-model="advFilters.dateFrom" type="date" class="filter-input filter-date" />
            </div>
            <div class="filter-field">
              <label class="field-label">结束日期</label>
              <input v-model="advFilters.dateTo" type="date" class="filter-input filter-date" />
            </div>
            <div class="filter-field">
              <label class="field-label">Bundle ID</label>
              <input v-model="advFilters.bundleId" class="filter-input" placeholder="精确匹配" @keyup.enter="fetchData(true)" />
            </div>
            <div class="filter-field">
              <label class="field-label">关键词</label>
              <input v-model="advFilters.keyword" class="filter-input" placeholder="URL/Bundle/备注" @keyup.enter="fetchData(true)" />
            </div>
            <div class="filter-field">
              <label class="field-label">异常类型</label>
              <select v-model="advFilters.exceptionType" class="filter-input">
                <option value="">全部</option>
                <option v-for="opt in exceptionOptions" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </div>
            <div class="filter-field">
              <label class="field-label">记录人</label>
              <select v-model="advFilters.recorder" class="filter-input">
                <option value="">全部</option>
                <option v-for="opt in recorderOptions" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </div>
            <div class="filter-field">
              <label class="field-label">导出状态</label>
              <select v-model="advFilters.isOutput" class="filter-input">
                <option :value="null">全部</option>
                <option :value="1">已导出</option>
                <option :value="0">未导出</option>
              </select>
            </div>
          </div>
          <div class="filter-action-row">
            <button class="btn-query" @click="fetchData(true)" :disabled="loading">
              {{ loading ? '查询中...' : '查询' }}
            </button>
            <button class="btn-reset" @click="resetFilters">重置</button>
            <span class="filter-tip">支持回车键快速查询</span>
          </div>
        </div>
      </section>

      <!-- 顶部统计卡片 -->
      <!--      <div class="stats-row" v-if="statsData">-->
      <!--        <div class="stat-card">-->
      <!--          <div class="stat-card-icon">T</div>-->
      <!--          <div class="stat-card-body">-->
      <!--            <div class="stat-card-value">{{ statsData.totalCount }}</div>-->
      <!--            <div class="stat-card-label">总记录数</div>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--        <div class="stat-card stat-card-exported">-->
      <!--          <div class="stat-card-icon">HE</div>-->
      <!--          <div class="stat-card-body">-->
      <!--            <div class="stat-card-value">{{ statsData.exportedCount }}</div>-->
      <!--            <div class="stat-card-label">已导出</div>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--        <div class="stat-card stat-card-pending">-->
      <!--          <div class="stat-card-icon">UE</div>-->
      <!--          <div class="stat-card-body">-->
      <!--            <div class="stat-card-value">{{ statsData.unexportedCount }}</div>-->
      <!--            <div class="stat-card-label">未导出</div>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--        <div class="stat-card stat-card-frozen">-->
      <!--          <div class="stat-card-icon">FR</div>-->
      <!--          <div class="stat-card-body">-->
      <!--            <div class="stat-card-value">{{ statsData.frozenCount }}</div>-->
      <!--            <div class="stat-card-label">已冻结</div>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->

      <!-- ==================== 数据报表 Tab 内容 ==================== -->
      <template v-if="activeTab === 'report'">

        <div v-if="summaryData" class="quality-panel">
          <div class="quality-header">
            <span class="quality-title">Q 质量分析</span>
            <span class="quality-scope">{{ total > 0 ? '当前筛选范围' : '全量数据' }}</span>
          </div>
          <div class="quality-summary">
            <div class="quality-stat">
              <div class="quality-stat-value">{{ summaryData.totalCount }}</div>
              <div class="quality-stat-label">总记录数</div>
            </div>
            <div class="quality-stat quality-stat-ok">
              <div class="quality-stat-value">{{ summaryData.qualifiedCount }}</div>
              <div class="quality-stat-label">合格数（有归因）</div>
            </div>
            <div class="quality-stat quality-stat-bad">
              <div class="quality-stat-value">{{ summaryData.unqualifiedCount }}</div>
              <div class="quality-stat-label">不合格数</div>
            </div>
            <div class="quality-stat quality-stat-rate">
              <div class="quality-stat-value">{{ summaryData.qualifyRate }}%</div>
              <div class="quality-stat-label">合格率</div>
            </div>
          </div>
          <div class="quality-attr-section">
            <div class="quality-attr-title">归因占比（基于合格数据 {{ summaryData.qualifiedCount }} 条）</div>
            <div class="quality-attr-bars">
              <div class="quality-attr-bar-item" v-for="type in ['appflyer', 'adjust', 'singular', 'tenjin']" :key="type">
                <div class="quality-attr-bar-header">
                  <span class="quality-attr-bar-name" :class="type">{{ type }}</span>
                  <span class="quality-attr-bar-count">{{ summaryData.attributions[type] }} 条</span>
                  <span class="quality-attr-bar-percent">{{ getSummaryAttrPercent(summaryData.attributions[type]) }}%</span>
                </div>
                <div class="quality-attr-bar-track">
                  <div class="quality-attr-bar-fill" :class="type" :style="{ width: (summaryData.qualifiedCount > 0 ? summaryData.attributions[type] * 100 / summaryData.qualifiedCount : 0) + '%' }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="summaryData" class="chart-filter-bar">
          <span class="chart-filter-icon">VIS</span>
          <label class="chart-filter-label">图表显示：</label>
          <select v-model="chartFilter" class="chart-filter-select">
            <option v-for="opt in CHART_FILTER_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <span class="chart-filter-hint">{{ CHART_FILTER_OPTIONS.find(o => o.value === chartFilter)?.label }}</span>
        </div>

        <div v-if="summaryData && (chartFilter === 'attrPie' || chartFilter === 'all')" class="charts-row charts-row-single">
          <div class="chart-card">
            <div class="chart-canvas-wrap"><canvas id="attrPieChart"></canvas></div>
          </div>
          <!--          <div class="chart-card">-->
          <!--            <div class="chart-canvas-wrap"><canvas id="exportPieChart"></canvas></div>-->
          <!--          </div>-->
        </div>

        <div v-if="recorderSummary.length > 0 && (chartFilter === 'recorderBar' || chartFilter === 'all')" :class="['charts-row', { 'charts-row-single': chartFilter === 'recorderBar' }]">
          <div class="chart-card">
            <div class="chart-canvas-wrap"><canvas id="recorderBarChart"></canvas></div>
          </div>
          <div class="chart-card" v-if="chartFilter === 'all'">
            <div class="chart-canvas-wrap"><canvas id="stackedAttrChart"></canvas></div>
          </div>
        </div>

        <div v-if="recorderSummary.length > 0 && chartFilter === 'stackedAttr'" class="charts-row charts-row-single">
          <div class="chart-card">
            <div class="chart-canvas-wrap"><canvas id="stackedAttrChart"></canvas></div>
          </div>
        </div>

        <!--        <div v-if="recorderSummary.length >= 3 && (chartFilter === 'radar' || chartFilter === 'all')" class="charts-row charts-row-single">-->
        <!--          <div class="chart-card">-->
        <!--            <div class="chart-canvas-wrap chart-canvas-tall"><canvas id="radarChart"></canvas></div>-->
        <!--          </div>-->
        <!--        </div>-->

        <div v-if="recorderSummary.length > 0 && (chartFilter === 'recorderPies' || chartFilter === 'all')" class="recorder-pies-section">

          <!--        <div v-if="recorderSummary.length > 0" class="recorder-pies-section">-->
          <div class="recorder-pies-header">
            <span class="recorder-pies-title">各记录人归因明细</span>
            <span class="recorder-pies-sub">共 {{ recorderSummary.length }} 位记录人</span>
          </div>
          <div class="recorder-pies-grid">
            <div v-for="(r, idx) in recorderSummary" :key="r.recorder" class="recorder-pie-card">
              <div class="chart-canvas-wrap recorder-pie-wrap">
                <canvas :id="'recorderPie_' + idx"></canvas>
              </div>
              <div class="recorder-pie-stats">
                <div class="recorder-pie-stat">
                  <span class="recorder-pie-stat-val">{{ r.totalCount }}</span>
                  <span class="recorder-pie-stat-lbl">总数</span>
                </div>
                <div class="recorder-pie-stat">
                  <span class="recorder-pie-stat-val">{{ r.qualifiedCount }}</span>
                  <span class="recorder-pie-stat-lbl">合格</span>
                </div>
                <div class="recorder-pie-stat">
                  <span class="recorder-pie-stat-val recorder-pie-stat-rate">{{ r.qualifyRate }}%</span>
                  <span class="recorder-pie-stat-lbl">合格率</span>
                </div>
              </div>
              <div class="recorder-pie-attr-list">
                <div v-for="attr in ['appflyer', 'adjust', 'singular', 'tenjin']" :key="attr" class="recorder-pie-attr-row">
                  <span class="recorder-pie-attr-dot" :style="{ background: ATTR_COLORS[['appflyer','adjust','singular','tenjin'].indexOf(attr)] }"></span>
                  <span class="recorder-pie-attr-name">{{ attr }}</span>
                  <span class="recorder-pie-attr-count">{{ r.attributions[attr] }}</span>
                  <span class="recorder-pie-attr-pct">{{ r.totalCount > 0 ? (r.attributions[attr] * 100 / r.totalCount).toFixed(1) : '0.0' }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="loading && !queried" class="state-block">
          <div class="state-spinner"></div>
          <div class="state-text">正在查询数据...</div>
        </div>

      </template>

      <!-- ==================== 数据管理 Tab 内容 ==================== -->
      <template v-if="activeTab === 'management'">

        <div class="export-action-bar">
          <div class="export-action-left">
            <button class="btn-action btn-export-date" @click="doExportByDate" :disabled="exporting || exportPolling || hasSelection">
              {{ exporting ? '导出中...' : '按日期导出' + (advFilters.dateFrom ? '(' + advFilters.dateFrom + ')' : '(今日)') }}
            </button>
            <button class="btn-action btn-export-hash" @click="doExportByHashes" :disabled="exporting || exportPolling || !hasSelection">
              {{ exporting ? '导出中...' : '导出选中行 (' + selectedHashes.length + ')' }}
            </button>
            <button class="btn-action btn-export-all" @click="doExportAll" :disabled="exporting || exportPolling || hasSelection || unexportedTotal === 0">
              {{ exporting ? '导出中...' : '导出全部未导出' }}
            </button>
            <span class="export-tip" v-if="hasSelection">注意：已切换为选中行导出模式</span>
          </div>
          <div class="export-action-right">
            <div class="unexported-badge" :class="{ 'unexported-zero': unexportedTotal === 0 }">
              <span class="unexported-badge-dot"></span>
              <span class="unexported-badge-text">未导出: <strong>{{ unexportedTotal }}</strong> 条</span>
            </div>
            <button class="btn-action btn-refresh-unexported" @click="refreshTodayUnexported" :disabled="todayUnexportedLoading">
              {{ todayUnexportedLoading ? '查询中...' : '刷新导出情况' }}
            </button>
            <div class="download-section-inline" v-if="exportFileReady">
              <span class="download-file-name">{{ exportFileName }}</span>
              <button class="btn-action btn-download-sm" @click="doExportDownload">下载文件</button>
            </div>
          </div>
        </div>

        <div v-if="exportResultMsg" class="feedback" :class="{ 'feedback-ok': exportResultSuccess, 'feedback-err': !exportResultSuccess }">
          {{ exportResultMsg }}
        </div>

        <div v-if="exportPolling" class="polling-tip">
          <div class="state-spinner" style="width:18px;height:18px;border-width:2px;margin:0;display:inline-block;vertical-align:middle;margin-right:8px;"></div>
          文件生成中，请稍候...
        </div>

        <!-- 结果工具栏 -->
        <div v-if="queried" class="result-toolbar">
          <div class="result-toolbar-left">
            <span class="result-count">共 <strong>{{ total }}</strong> 条 · 第 {{ currentPage }}/{{ totalPages }} 页</span>
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
          <div class="state-icon" aria-hidden="true">EMPTY</div>
          <div class="state-text">没有符合条件的数据</div>
        </div>

        <div v-if="total > 0" class="pagination pagination-top">
          <button class="page-btn" :disabled="currentPage <= 1" @click="prevPage">‹ 上一页</button>
          <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页，共 {{ total }} 条</span>
          <button class="page-btn" :disabled="currentPage >= totalPages" @click="nextPage">下一页 ›</button>
          <label class="page-size-label">
            每页
            <input class="page-size-input" type="number" v-model="pageSizeInput" @keydown.enter="applyPageSize" @blur="applyPageSize" min="1" max="500" />
            条
          </label>
        </div>

        <div v-if="list.length > 0" class="table-wrapper table-wrapper-records">
          <table class="data-table">
            <thead>
            <tr>
              <th class="th-check"><input type="checkbox" :checked="isAllSelected()" @change="toggleSelectAll" /></th>
              <th>#</th>
              <th>URL</th>
              <th>Bundle ID</th>
              <th>归因</th>
              <th>事件数</th>
              <th>异常类型</th>
              <th class="th-sortable" @click="toggleDateSort">
                <span>记录日期</span>
                <span class="sort-indicator">
                  <span class="sort-arrow sort-arrow-up" :class="{ active: dateSortOrder === 'asc' }" aria-hidden="true"></span>
                  <span class="sort-arrow sort-arrow-down" :class="{ active: dateSortOrder === 'desc' }" aria-hidden="true"></span>
                </span></th>
              <th>记录人</th>
              <th>备注</th>
              <!--              <th>导出</th>-->
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <!--            <template v-for="(item, index) in list" :key="item.hash">-->
            <template v-for="(item, index) in sortedList" :key="item.hash">
              <tr :class="{ 'row-selected': selectedHashes.includes(item.hash), 'row-editing': editingRow === index }">
                <td class="td-check"><input type="checkbox" :value="item.hash" v-model="selectedHashes" /></td>
                <td class="cell-index">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
                <td :title="item.URL" class="cell-url">
                  <template v-if="editingRow === index"><input v-model="editBuffer.URL" class="cell-edit-input cell-edit-input-wide" /></template>
                  <template v-else>
                    <span class="url-text" @contextmenu.prevent="copyUrl(item.URL, $event)">{{ item.URL }}</span>
                  </template>
                </td>
                <td class="cell-mono">
                  <template v-if="editingRow === index"><input v-model="editBuffer.bundleId" class="cell-edit-input" /></template>
                  <template v-else>{{ item.bundleId }}</template>
                </td>
                <td>
                  <template v-if="editingRow === index">
                    <select v-model="editBuffer.ascribe" class="cell-edit-input">
                      <option value="">无</option>
                      <option v-for="opt in ATTR_OPTIONS" :key="opt" :value="opt">{{ opt }}</option>
                    </select>
                  </template>
                  <template v-else>{{ item.ascribe || '-' }}</template>
                </td>
                <td class="cell-num">
                  <template v-if="editingRow === index"><input v-model.number="editBuffer.event_number" type="number" class="cell-edit-input cell-edit-input-sm" /></template>
                  <template v-else>{{ item.event_number }}</template>
                </td>
                <td>
                  <template v-if="editingRow === index"><input v-model="editBuffer.exception_type" class="cell-edit-input" /></template>
                  <template v-else>
                    <span v-if="item.exception_type" class="exception-tag" :class="'ex-' + item.exception_type">{{ item.exception_type }}</span>
                    <span v-else>-</span>
                  </template>
                </td>
                <td>
                  <template v-if="editingRow === index"><input v-model="editBuffer.record_data" class="cell-edit-input" /></template>
                  <template v-else>{{ normalizeRecordData(item.record_data) || '-' }}</template>
                </td>
                <td>
                  <template v-if="editingRow === index"><input v-model="editBuffer.recorder" class="cell-edit-input" /></template>
                  <template v-else>{{ item.recorder || '-' }}</template>
                </td>
                <td :title="item.remark" class="cell-remark">
                  <template v-if="editingRow === index"><input v-model="editBuffer.remark" class="cell-edit-input" /></template>
                  <template v-else>{{ item.remark || '-' }}</template>
                </td>
                <!--                <td v-if="currentUserRole==='DEVELOPER'"><span :class="item.isOutput === 1 ? 'tag-exported' : 'tag-unexported'">{{ item.isOutput === 1 ? '已导出' : '未导出' }}</span></td>-->
                <td class="action-cell">
                  <template v-if="editingRow === index">
                    <button class="btn-sm btn-save" @click="saveEditRow(index)" :disabled="savingEdit">{{ savingEdit ? '...' : '保存' }}</button>
                    <button class="btn-sm btn-cancel" @click="cancelEditRow">取消</button>
                  </template>
                  <template v-else>
                    <button class="btn-sm btn-edit" @click="startEditRow(index)" title="编辑此行">编辑</button>
                  </template>
                </td>
              </tr>
            </template>
            </tbody>
          </table>
        </div>
        <div class="import-section" v-if="currentUserRole === 'DEVELOPER'">
          <div class="import-toggle" @click="importExpanded = !importExpanded">
            <span class="import-toggle-icon">{{ importExpanded ? '收起' : '展开' }}</span>
            <span class="import-toggle-icon-emoji">I</span>
            <span class="import-toggle-text">一键导入数据（从WPS表格粘贴）</span>
          </div>
          <div v-if="importExpanded" class="import-body">
            <div class="import-help">
              <p>将 WPS 表格中的数据直接粘贴到下方输入框，系统会自动按 Tab 分隔解析为多条记录。</p>
              <p>数据格式（Tab分隔）：<code>URL · bundleId · 归因 · 事件数 · 异常类型 · 日期 · 记录人 · 已冻结(可选)</code></p>
              <p>导入的数据将自动设置 <code>isOutput = 1</code>（已导出），并自动生成唯一 hash。</p>
            </div>
            <textarea
                v-model="importRawText"
                class="import-textarea"
                placeholder="在此粘贴WPS表格数据...&#10;例如：&#10;https://apps.apple.com/...	com.example	appflyer	35	正常	2026/8/27	张三&#10;https://apps.apple.com/...	com.example2		5	超过10分钟0上报	2026/8/27	李四	已冻结"
                rows="8"
            ></textarea>
            <div class="import-actions">
              <div class="import-hint" v-if="importRawText.trim()">
                已检测到 <strong>{{ importRawText.split('\n').filter(l => l.trim()).length }}</strong> 行数据
              </div>
              <div class="import-btns">
                <button class="btn-action btn-import-clear" @click="importRawText = ''; importMsg = ''; importResult = null" v-if="importRawText">
                  清空
                </button>
                <button class="btn-action btn-import" @click="handleBatchImport" :disabled="importing || !importRawText.trim()">
                  {{ importing ? '导入中...' : '一键导入' }}
                </button>
              </div>
            </div>
            <div v-if="importMsg" class="feedback" :class="{ 'feedback-ok': importMsg.charCodeAt(0) === 9989, 'feedback-err': importMsg.charCodeAt(0) === 10060 }">
              {{ importMsg }}
            </div>
            <div v-if="importResult" class="import-result">
              <div class="import-result-row">
                <span class="import-result-label">总行数</span>
                <span class="import-result-value">{{ importResult.totalLines }}</span>
              </div>
              <div class="import-result-row">
                <span class="import-result-label">成功解析</span>
                <span class="import-result-value import-result-ok">{{ importResult.parsedCount }} 条</span>
              </div>
              <div class="import-result-row">
                <span class="import-result-label">入库成功</span>
                <span class="import-result-value import-result-ok">{{ importResult.successCount }} 条</span>
              </div>
              <div v-if="importResult.errorCount > 0" class="import-result-row">
                <span class="import-result-label">解析失败</span>
                <span class="import-result-value import-result-bad">{{ importResult.errorCount }} 条</span>
              </div>
              <div v-if="importResult.errors && importResult.errors.length > 0" class="import-errors">
                <div class="import-error-title">失败详情：</div>
                <div v-for="(err, idx) in importResult.errors" :key="idx" class="import-error-item">{{ err }}</div>
              </div>
            </div>
          </div>
        </div>
        <br/>
        <br/>
        <div @click="sup" class="sup-area">点我试试</div>
      </template>
    </div>


    <!-- ==================== App信息 Tab ==================== -->
    <template v-if="activeTab === 'appid'">
      <div class="admin-section">
        <div class="appid-lookup-card">
          <div class="appid-lookup-header">
            <span class="appid-lookup-icon">APP</span>
            <div>
              <h3 class="appid-lookup-title">App 信息查询</h3>
              <p class="appid-lookup-desc">通过 Bundle ID 查询 App 信息，支持本地库和联网两种模式</p>
            </div>
          </div>

          <div class="appid-mode-switch">
            <button class="appid-mode-btn" :class="{ active: appIdMode === 'local' }" @click="appIdMode = 'local'">
              <span class="appid-mode-icon">D</span>
              <div class="appid-mode-text">
                <span class="appid-mode-name">本地库获取</span>
                <span class="appid-mode-desc">仅返回 AppId · 优先 Redis → MySQL</span>
              </div>
            </button>
            <button class="appid-mode-btn" :class="{ active: appIdMode === 'online' }" @click="appIdMode = 'online'">
              <span class="appid-mode-icon">I</span>
              <div class="appid-mode-text">
                <span class="appid-mode-name">联网搜索</span>
                <span class="appid-mode-desc">返回完整信息 · 自动同步到本地库</span>
              </div>
            </button>
          </div>

          <div class="appid-search-row">
            <input v-model="appIdBundleId" class="appid-search-input" placeholder="输入 Bundle ID，例如 com.game.hero.survival" @keyup.enter="doAppIdLookup" />
            <button class="appid-search-btn" @click="doAppIdLookup" :disabled="appIdLoading">
              {{ appIdLoading ? '查询中...' : '查询' }}
            </button>
          </div>

          <div v-if="appIdLoading" class="state-block">
            <div class="state-spinner"></div>
            <div class="state-text">正在查询...</div>
          </div>

          <div v-if="appIdMode === 'local' && appIdResult" class="appid-result-card">
            <div v-if="appIdResult.found" class="appid-result-found">
              <div class="appid-result-badge" :class="appIdResult.source === 'Redis' ? 'badge-redis' : 'badge-mysql'">
                {{ appIdResult.source === 'Redis' ? 'Redis 命中' : 'MySQL 查询' }}
              </div>
              <div class="appid-result-grid">
                <div class="appid-result-field">
                  <span class="appid-field-label">Bundle ID</span>
                  <span class="appid-field-value appid-field-mono">{{ appIdResult.bundleId }}</span>
                </div>
                <div class="appid-result-field">
                  <span class="appid-field-label">App ID</span>
                  <span class="appid-field-value appid-field-highlight">{{ appIdResult.appId }}</span>
                </div>
                <div class="appid-result-field">
                  <span class="appid-field-label">数据来源</span>
                  <span class="appid-field-value">{{ appIdResult.source }}</span>
                </div>
              </div>
              <div v-if="appIdResult.source === 'MySQL'" class="appid-cache-hint">
                已将此条 AppId 缓存到 Redis，下次查询将直接命中 Redis
              </div>
            </div>
            <div v-else class="appid-result-notfound">
              <div class="appid-notfound-icon">Q</div>
              <div class="appid-notfound-text">本地库中未找到该 Bundle ID 对应的 AppId</div>
              <div class="appid-notfound-hint">请切换到「联网搜索」模式从远端获取</div>
            </div>
          </div>

          <div v-if="appIdMode === 'online' && appIdOnlineResult" class="appid-online-card">
            <div class="appid-online-header">
            <span class="appid-online-status" :class="appIdOnlineResult.status === 0 ? 'status-active' : 'status-inactive'">
              {{ appIdOnlineResult.status === 0 ? '活跃' : '非活跃' }}
            </span>
              <span class="appid-online-id">ID: {{ appIdOnlineResult.id }}</span>
            </div>
            <div class="appid-online-grid">
              <div class="appid-online-field">
                <span class="appid-online-label">Bundle ID</span>
                <span class="appid-online-value appid-field-mono">{{ appIdOnlineResult.bundleId }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">App ID</span>
                <span class="appid-online-value appid-field-highlight">{{ appIdOnlineResult.appId }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">平台</span>
                <span class="appid-online-value">{{ appIdOnlineResult.platform || '未指定' }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">类型</span>
                <span class="appid-online-value">{{ appIdOnlineResult.type === 1 ? 'iOS' : appIdOnlineResult.type === 2 ? 'Android' : '未知' }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">事件目标数</span>
                <span class="appid-online-value">{{ appIdOnlineResult.eventTokenTargetNum }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">当前事件数</span>
                <span class="appid-online-value">{{ appIdOnlineResult.currentTargetNum }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">添加次数</span>
                <span class="appid-online-value">{{ appIdOnlineResult.addTimes }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">事件类型</span>
                <span class="appid-online-value">{{ appIdOnlineResult.playEventType }}</span>
              </div>
              <div class="appid-online-field appid-online-field-wide">
                <span class="appid-online-label">下载链接</span>
                <span class="appid-online-value appid-field-mono appid-field-link">
                <a v-if="appIdOnlineResult.downloadUrl" :href="appIdOnlineResult.downloadUrl" target="_blank" rel="noopener">{{ appIdOnlineResult.downloadUrl }}</a>
                <span v-else>-</span>
              </span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">创建时间</span>
                <span class="appid-online-value">{{ formatAppIdTime(appIdOnlineResult.createTime) }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">更新时间</span>
                <span class="appid-online-value">{{ formatAppIdTime(appIdOnlineResult.updateTime) }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">申请时间</span>
                <span class="appid-online-value">{{ formatAppIdTime(appIdOnlineResult.applyTime) }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">过期时间</span>
                <span class="appid-online-value">{{ formatAppIdTime(appIdOnlineResult.expireTime) }}</span>
              </div>
              <div class="appid-online-field">
                <span class="appid-online-label">冻结时间</span>
                <span class="appid-online-value">{{ formatAppIdTime(appIdOnlineResult.frozenTime) }}</span>
              </div>
              <div class="appid-online-field appid-online-field-wide">
                <span class="appid-online-label">冻结原因</span>
                <span class="appid-online-value">{{ appIdOnlineResult.remark || '无' }}</span>
              </div>
            </div>
            <div v-if="appIdSaveMsg" class="appid-sync-msg" :class="{ 'appid-sync-ok': appIdSaveMsg.startsWith('') || appIdSaveMsg.includes('已存在'), 'appid-sync-err': appIdSaveMsg.startsWith('') }">
              {{ appIdSaveMsg }}
            </div>
          </div>
        </div>
      </div>
    </template>

    <template v-if="activeTab === 'retest'">
      <div class="admin-section">
        <div class="appid-lookup-card">
          <div class="appid-lookup-header">
            <span class="appid-lookup-icon">R</span>
            <div>
              <h3 class="appid-lookup-title">复测数据缓存（Redis DB12）</h3>
              <p class="appid-lookup-desc">仅缓存过去三天、评级 B 级以上的 bundleId，每条 TTL 24 小时，过期后自动重新预热</p>
            </div>
          </div>

          <div class="retest-stat-row">
            <div class="retest-stat-box">
              <div class="retest-stat-value">{{ retestTotal }}</div>
              <div class="retest-stat-label">缓存条目数</div>
            </div>
            <div class="retest-stat-box">
              <div class="retest-stat-value">{{ formatRetestTtl(retestTtl) }}</div>
              <div class="retest-stat-label">剩余存活时间</div>
            </div>
            <div class="retest-stat-actions">
              <button class="btn-action btn-create" @click="doRetestWarmup" :disabled="retestWarming">
                {{ retestWarming ? '预热中...' : '手动预热' }}
              </button>
              <button class="btn-action" @click="loadRetestCache" :disabled="retestLoading">
                {{ retestLoading ? '刷新中...' : '刷新' }}
              </button>
              <button class="btn-action btn-danger-solid" @click="doRetestClear">清空缓存</button>
            </div>
          </div>

          <div v-if="retestMsg" class="feedback" :class="{ 'feedback-ok': retestMsg.charCodeAt(0) === 9989, 'feedback-err': retestMsg.charCodeAt(0) === 10060 }" style="margin:12px 0">
            {{ retestMsg }}
          </div>

          <div class="retest-add-row">
            <input v-model="retestAddForm.bundleId" class="appid-search-input" placeholder="手动新增 Bundle ID" @keyup.enter="doRetestAdd" />
            <select v-model="retestAddForm.grade" class="filter-input" style="width:120px">
              <option value="A">A级</option>
              <option value="B">B级</option>
            </select>
            <button class="appid-search-btn" @click="doRetestAdd">新增</button>
          </div>

          <div v-if="retestLoading" class="state-block">
            <div class="state-spinner"></div>
            <div class="state-text">正在加载...</div>
          </div>

          <div v-else-if="retestList.length > 0" class="table-wrapper" style="margin-top:16px">
            <table class="data-table">
              <thead>
              <tr>
                <th style="width:60px">#</th>
                <th>Bundle ID</th>
                <th style="width:160px">评级</th>
                <th style="width:180px">操作</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="(item, idx) in retestList" :key="item.bundleId">
                <td>{{ idx + 1 }}</td>
                <td class="appid-field-mono" style="word-break:break-all">{{ item.bundleId }}</td>
                <td>
                  <template v-if="retestEditing === item.bundleId">
                    <select v-model="retestEditGrade" class="filter-input" style="width:90px">
                      <option value="A">A级</option>
                      <option value="B">B级</option>
                    </select>
                  </template>
                  <template v-else>
                    <span class="grade-badge" :class="'grade-' + (item.grade || '').toLowerCase()">{{ item.grade }}级</span>
                  </template>
                </td>
                <td>
                  <template v-if="retestEditing === item.bundleId">
                    <button class="btn-link" @click="saveRetestEdit">保存</button>
                    <button class="btn-link" @click="retestEditing = null">取消</button>
                  </template>
                  <template v-else>
                    <button class="btn-link" @click="startRetestEdit(item)">编辑</button>
                    <button class="btn-link btn-link-danger" @click="doRetestDelete(item.bundleId)">删除</button>
                  </template>
                </td>
              </tr>
              </tbody>
            </table>
          </div>

          <div v-else class="appid-result-notfound" style="margin-top:16px">
            <div class="appid-notfound-icon">Q</div>
            <div class="appid-notfound-text">复测缓存为空</div>
            <div class="appid-notfound-hint">点击「手动预热」从数据库加载过去三天 B 级以上的 bundleId</div>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 用户管理 Tab ==================== -->
    <div v-if="activeTab === 'users'" class="admin-section">

      <div class="user-top-grid">
        <!-- 创建新用户 -->
        <div class="card">
          <div class="card-header">
            <span class="card-icon">A</span>
            <h3>创建新用户</h3>
          </div>
          <div class="card-body">
            <div class="form-row">
              <div class="form-group">
                <label>姓名</label>
                <input v-model="createForm.name" placeholder="用户姓名" class="form-input" />
              </div>
              <div class="form-group">
                <label>密码</label>
                <input v-model="createForm.pwd" type="password" placeholder="登录密码" class="form-input" />
              </div>
            </div>
            <div class="form-row">
              <div class="form-group form-group-stretch">
                <label>账户类型</label>
                <select v-model="createForm.type" class="form-input">
                  <option value="USER">普通用户 (USER)</option>
                  <option value="ADMIN">管理员 (ADMIN)</option>
                </select>
              </div>
            </div>
            <button class="btn-action btn-create" @click="handleCreateUser" :disabled="creating">
              {{ creating ? '创建中...' : '创建用户' }}
            </button>
            <div v-if="createMsg" class="feedback" :class="{ 'feedback-ok': createMsg.startsWith(''), 'feedback-err': createMsg.startsWith('') }">
              {{ createMsg }}
            </div>
          </div>
        </div>

        <!-- 重置密码 -->
        <div class="card">
          <div class="card-header">
            <span class="card-icon">R</span>
            <h3>重置账户密码</h3>
          </div>
          <div class="card-body">
            <div class="form-row">
              <div class="form-group">
                <label>用户 UID</label>
                <input v-model="resetPwdUid" placeholder="输入UID" class="form-input" />
              </div>
              <div class="form-group">
                <label>新密码</label>
                <input v-model="resetPwdNew" type="password" placeholder="输入新密码" class="form-input" />
              </div>
            </div>
            <div class="form-row">
              <div class="form-group form-group-stretch">
                <label>&nbsp;</label>
                <button class="btn-action btn-reset" @click="handleResetPassword" :disabled="resetting">
                  {{ resetting ? '重置中...' : '重置密码并踢下线' }}
                </button>
              </div>
            </div>
            <div v-if="resetMsg" class="feedback" :class="{ 'feedback-ok': resetMsg.startsWith(''), 'feedback-err': resetMsg.startsWith('') }">
              {{ resetMsg }}
            </div>
          </div>
        </div>
      </div>

      <!-- 用户列表 -->
      <div class="card">
        <div class="card-header card-header-between">
          <div class="card-header-left">
            <span class="card-icon">T</span>
            <h3>用户列表</h3>
            <span class="user-count" v-if="userList.length > 0">{{ userList.length }} 个账户</span>
          </div>
          <div class="card-header-right">
            <span class="kick-label">踢下线封禁</span>
            <div class="kick-input-wrap">
              <input v-model.number="kickBanSeconds" type="number" min="0" class="kick-input" />
              <span class="kick-unit">秒</span>
            </div>
          </div>
        </div>
        <div class="card-body">
          <div v-if="userLoading && userList.length === 0" class="state-block">
            <div class="state-spinner"></div>
            <div class="state-text">加载中...</div>
          </div>
          <div v-if="!userLoading && userList.length === 0" class="state-block">
            <div class="state-icon" aria-hidden="true">EMPTY</div>
            <div class="state-text">暂无用户</div>
          </div>
          <div v-if="userList.length > 0" class="table-wrapper table-wrapper-user">
            <table class="data-table user-table">
              <thead>
              <tr>
                <th>UID</th>
                <th>姓名</th>
                <th>类型</th>
                <th>在线状态</th>
                <th>操作</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="u in userList" :key="u.uid" :class="{ 'row-online': u.online }">
                <td class="uid-cell">{{ u.uid }}</td>
                <td class="name-cell">{{ u.name }}</td>
                <td>
                  <span class="role-tag" :class="u.type === 'ADMIN' ? 'role-admin' : u.type === 'DEVELOPER' ? 'role-developer' : 'role-user'">
                    {{ u.type === 'ADMIN' ? '管理员' : u.type === 'DEVELOPER' ? 'root账户' : '用户' }}
                  </span>
                </td>
                <td>
                  <span class="online-tag" :class="u.online ? 'online-yes' : 'online-no'">
                    <span class="online-dot"></span>
                    {{ u.online ? '在线' : '离线' }}
                  </span>
                </td>
                <td class="action-cell">
                  <button class="btn-sm btn-kick" @click="handleKickUser(u.uid)" :disabled="!u.online" :title="u.online ? '踢下线' : '用户不在线'">
                    踢下线
                  </button>
                  <button class="btn-sm btn-del" @click="handleDeleteUser(u.uid)" title="删除用户">
                    删除
                  </button>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.admin-page,
.admin-page * { box-sizing: border-box; }
.admin-page { width: 100%; max-width: 1600px; margin: 0 auto; padding: 0 20px 38px; color: var(--text-primary); font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Segoe UI', 'Noto Sans SC', system-ui, sans-serif; }
.admin-page button,
.admin-page input,
.admin-page select,
.admin-page textarea { font-family: inherit; }
.admin-page button { -webkit-tap-highlight-color: transparent; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 15px; padding-bottom: 17px; border-bottom: 1px solid var(--border-color); }
.page-heading { min-width: 0; }
.page-eyebrow { display: block; margin-bottom: 7px; color: var(--accent); font-family: inherit; font-size: 11px; font-weight: 700; letter-spacing: .12em; }
.page-header h2 { margin: 0; color: var(--text-primary); font-size: 24px; font-weight: 700; letter-spacing: -.025em; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 13px; }
.page-header-meta { display: flex; flex: 0 0 auto; align-items: flex-end; flex-direction: column; gap: 7px; }
.system-state { display: inline-flex; align-items: center; gap: 6px; color: var(--accent-strong); font-size: 11px; font-weight: 650; }
.system-state i { width: 6px; height: 6px; background: var(--accent); border-radius: 50%; box-shadow: 0 0 0 3px var(--accent-soft); }
.operator { max-width: 260px; overflow: hidden; color: var(--text-tertiary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.operator strong { margin-left: 5px; color: #1c1a16; font-family: inherit; font-weight: 600; }
.admin-tabs { display: flex; gap: 4px; margin-bottom: 16px; padding: 4px; overflow-x: auto; overscroll-behavior-x: contain; background: #f1f0ee; border: 1px solid var(--border-color); border-radius: var(--radius-lg); scrollbar-width: thin; }
.admin-tab { display: inline-flex; flex: 1 0 116px; min-width: 0; min-height: 38px; align-items: center; justify-content: center; gap: 7px; padding: 7px 10px; color: var(--text-secondary); background: transparent; border: 1px solid transparent; border-radius: var(--radius); outline: none; font-size: 12px; font-weight: 600; white-space: nowrap; cursor: pointer; transition: color .16s ease, background .16s ease, border-color .16s ease, box-shadow .16s ease; }
.admin-tab:hover:not(.active) { color: var(--text-primary); background: rgba(255,255,255,.55); }
.admin-tab.active { color: var(--accent-strong); background: #fff; border-color: rgba(215,227,225,.85); box-shadow: var(--shadow-sm), inset 0 1px 0 #fff; }
.admin-tab:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.tab-icon { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; color: var(--text-tertiary); background: rgba(255,255,255,.55); border: 1px solid var(--border-color); border-radius: 6px; font-family: inherit; font-size: 9px; font-weight: 700; }
.admin-tab.active .tab-icon { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.admin-section { min-width: 0; animation: admin-fade-up .22s ease both; }
@keyframes admin-fade-up { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
.filter-card,
.chart-filter-bar { position: relative; isolation: isolate; background: rgba(255,255,255,.78); border: 1px solid rgba(215,227,225,.9); box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.95); backdrop-filter: blur(14px) saturate(120%); -webkit-backdrop-filter: blur(14px) saturate(120%); }
.filter-card { margin-bottom: 14px; padding: 16px; border-radius: var(--radius-lg); }
.section-caption { display: flex; align-items: baseline; gap: 10px; margin-bottom: 13px; }
.section-caption > span { color: var(--text-primary); font-size: 13px; font-weight: 700; }
.section-caption small { color: var(--text-tertiary); font-size: 11px; }
.filter-top-row { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.filter-section { min-width: 0; }
.filter-section-title,
.field-label { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.filter-top-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.radio-group { display: flex; flex-wrap: wrap; gap: 6px; }
.radio-tag { position: relative; display: inline-flex; min-height: 30px; align-items: center; gap: 6px; padding: 5px 10px; color: var(--text-secondary); background: rgba(255,255,255,.66); border: 1px solid var(--border-color); border-radius: 7px; font-family: inherit; font-size: 11px; font-weight: 600; cursor: pointer; transition: color .15s ease, background .15s ease, border-color .15s ease; }
.radio-tag::before { width: 5px; height: 5px; background: #a99f8c; border-radius: 50%; content: ""; }
.radio-tag.appflyer::before { background: #2f6b46; }
.radio-tag.adjust::before { background: #b5651d; }
.radio-tag.singular::before { background: #3a6ea5; }
.radio-tag.tenjin::before { background: #6b4fa6; }
.radio-tag input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.radio-tag:hover { color: var(--accent-strong); border-color: var(--accent-light); }
.radio-tag.active { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent); }
.radio-tag:has(input:focus-visible) { box-shadow: 0 0 0 3px var(--accent-soft); }
.checkbox-tag { display: inline-flex; min-height: 30px; align-items: center; gap: 6px; padding: 5px 10px; color: var(--text-secondary); background: #fff; border: 1px solid var(--border-color); border-radius: 7px; font-size: 11px; font-weight: 600; cursor: pointer; }
.checkbox-tag input { width: 13px; height: 13px; margin: 0; accent-color: var(--accent); }
.checkbox-tag.active { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent); }
.btn-link { display: inline-flex; align-items: center; min-height: 28px; padding: 3px 5px; color: var(--accent-strong); background: transparent; border: 0; border-radius: 5px; outline: none; font-size: 12px; font-weight: 600; cursor: pointer; }
.btn-link:hover { color: var(--accent); background: var(--accent-soft); }
.btn-link:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.btn-link-danger { color: var(--danger); }
.btn-link-danger:hover { color: var(--danger); background: #fbeee7; }
.filter-advanced { margin-top: 14px; padding-top: 14px; border-top: 1px solid #ecebe9; animation: admin-fade-up .2s ease both; }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 11px; margin-bottom: 13px; }
.filter-field { display: flex; min-width: 0; flex-direction: column; }
.filter-input,
.form-input,
.kick-input,
.page-size-input { width: 100%; min-width: 0; color: var(--text-primary); background: rgba(251,253,253,.96); border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; font-size: 12px; transition: border-color .16s ease, box-shadow .16s ease, background .16s ease; }
.filter-input { height: 36px; padding: 0 10px; }
.filter-input::placeholder,
.form-input::placeholder { color: var(--text-tertiary); }
.filter-input:hover,
.form-input:hover { border-color: var(--accent-light); }
.filter-input:focus,
.form-input:focus,
.kick-input:focus,
.page-size-input:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.filter-date { color-scheme: light; cursor: pointer; }
.filter-date::-webkit-calendar-picker-indicator { opacity: .55; cursor: pointer; }
select.filter-input,
select.form-input { cursor: pointer; }
.filter-action-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.filter-tip { margin-left: auto; color: var(--text-tertiary); font-size: 11px; }
.btn-action,
.btn-query,
.btn-reset,
.appid-search-btn,
.page-btn,
.btn-sm,
.btn-batch-del { display: inline-flex; align-items: center; justify-content: center; min-height: 34px; padding: 7px 12px; border: 1px solid transparent; border-radius: var(--radius); outline: none; font-size: 12px; font-weight: 600; line-height: 1.35; white-space: nowrap; cursor: pointer; transition: color .16s ease, background .16s ease, border-color .16s ease, box-shadow .16s ease; }
.btn-action:focus-visible,
.btn-query:focus-visible,
.btn-reset:focus-visible,
.appid-search-btn:focus-visible,
.page-btn:focus-visible,
.btn-sm:focus-visible,
.btn-batch-del:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.btn-action:disabled,
.btn-query:disabled,
.appid-search-btn:disabled,
.page-btn:disabled,
.btn-sm:disabled,
.btn-batch-del:disabled { opacity: .43; cursor: not-allowed; }
.btn-query,
.btn-create,
.btn-backup,
.appid-search-btn { color: #fff; background: var(--accent); border-color: var(--accent); box-shadow: inset 0 1px 0 rgba(255,255,255,.2); }
.btn-query:hover:not(:disabled),
.btn-create:hover:not(:disabled),
.btn-backup:hover:not(:disabled),
.appid-search-btn:hover:not(:disabled) { background: var(--accent-strong); border-color: var(--accent-strong); }
.btn-reset { width: auto; color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-reset:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-danger-solid { color: #fff; background: var(--danger); border-color: var(--danger); }
.btn-danger-solid:hover:not(:disabled) { background: #b94039; }
.quality-panel { margin-bottom: 14px; padding: 17px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.8); }
.quality-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.quality-title { color: var(--text-primary); font-size: 14px; font-weight: 700; }
.quality-scope { padding: 3px 8px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 999px; font-size: 10px; font-weight: 600; }
.quality-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }
.quality-stat { position: relative; min-width: 0; padding: 13px 14px; overflow: hidden; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); }
.quality-stat::before { position: absolute; top: 0; left: 0; width: 100%; height: 2px; background: #b4b1ac; content: ""; }
.quality-stat-value { overflow: hidden; color: var(--text-primary); font-family: inherit; font-size: 24px; font-weight: 650; letter-spacing: -.04em; line-height: 1.15; text-overflow: ellipsis; }
.quality-stat-label { margin-top: 6px; color: #1c1a16; font-size: 11px; line-height: 1.4; }
.quality-stat-ok { background: #f8f7f4; border-color: #e5e1da; }
.quality-stat-ok::before { background: var(--success); }
.quality-stat-ok .quality-stat-value { color: #4a7c59; }
.quality-stat-bad { background: #fff6f5; border-color: #f0d8d4; }
.quality-stat-bad::before { background: var(--danger); }
.quality-stat-bad .quality-stat-value { color: var(--danger); }
.quality-stat-rate { background: var(--accent-soft); border-color: var(--accent-light); }
.quality-stat-rate::before { background: var(--accent); }
.quality-stat-rate .quality-stat-value { color: var(--accent-strong); }
.quality-attr-section { padding-top: 13px; border-top: 1px solid #efe9de; }
.quality-attr-title { margin-bottom: 12px; color: var(--text-secondary); font-size: 12px; font-weight: 600; }
.quality-attr-bars { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 22px; }
.quality-attr-bar-item { min-width: 0; }
.quality-attr-bar-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 11px; }
.quality-attr-bar-name { display: inline-flex; align-items: center; gap: 5px; color: var(--text-secondary); font-family: inherit; font-weight: 600; }
.quality-attr-bar-name::before { width: 6px; height: 6px; background: #2f6b46; border-radius: 2px; content: ""; }
.quality-attr-bar-name.adjust::before { background: #b5651d; }
.quality-attr-bar-name.singular::before { background: #3a6ea5; }
.quality-attr-bar-name.tenjin::before { background: #6b4fa6; }
.quality-attr-bar-count { color: var(--text-secondary); }
.quality-attr-bar-percent { margin-left: auto; color: var(--text-primary); font-family: inherit; font-weight: 650; }
.quality-attr-bar-track { height: 5px; overflow: hidden; background: #efedeb; border-radius: 2px; }
.quality-attr-bar-fill { height: 100%; min-width: 0; background: #2f6b46; border-radius: 2px; transition: width .45s ease; }
.quality-attr-bar-fill.adjust { background: #b5651d; }
.quality-attr-bar-fill.singular { background: #3a6ea5; }
.quality-attr-bar-fill.tenjin { background: #6b4fa6; }
.chart-filter-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin-bottom: 14px; padding: 10px 12px; border-radius: var(--radius-lg); }
.chart-filter-icon { display: inline-flex; width: 27px; height: 27px; align-items: center; justify-content: center; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 7px; font-family: inherit; font-size: 9px; font-weight: 700; }
.chart-filter-label { color: var(--text-primary); font-size: 12px; font-weight: 650; white-space: nowrap; }
.chart-filter-select { min-width: 200px; height: 34px; padding: 0 10px; color: var(--text-primary); background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; font-size: 12px; cursor: pointer; }
.chart-filter-select:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.chart-filter-hint { margin-left: auto; color: var(--text-tertiary); font-size: 11px; }
.charts-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-bottom: 14px; }
.charts-row-single { width: 100%; max-width: 720px; grid-template-columns: minmax(0, 1fr); margin-right: auto; margin-left: auto; }
.chart-card { min-width: 0; padding: 15px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.chart-canvas-wrap { position: relative; width: 100%; height: 300px; }
.chart-canvas-wrap canvas { max-width: 100%; }
.chart-canvas-tall { height: 380px; }
.recorder-pies-section { margin-bottom: 18px; }
.recorder-pies-header { display: flex; align-items: baseline; gap: 10px; margin-bottom: 12px; padding-bottom: 10px; border-bottom: 1px solid var(--border-color); }
.recorder-pies-title { color: var(--text-primary); font-size: 14px; font-weight: 700; }
.recorder-pies-sub { color: var(--text-tertiary); font-size: 11px; }
.recorder-pies-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.recorder-pie-card { min-width: 0; padding: 13px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.recorder-pie-wrap { height: 200px; }
.recorder-pie-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 8px 0; padding: 9px 0; border-top: 1px solid #efe9de; border-bottom: 1px solid #efe9de; }
.recorder-pie-stat { display: flex; min-width: 0; align-items: center; flex-direction: column; gap: 3px; }
.recorder-pie-stat-val { max-width: 100%; overflow: hidden; color: var(--text-primary); font-family: inherit; font-size: 16px; font-weight: 650; text-overflow: ellipsis; }
.recorder-pie-stat-lbl { color: var(--text-tertiary); font-size: 10px; }
.recorder-pie-stat-rate { color: var(--accent-strong); }
.recorder-pie-attr-list { display: flex; flex-direction: column; gap: 5px; }
.recorder-pie-attr-row { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.recorder-pie-attr-dot { flex: 0 0 auto; width: 6px; height: 6px; border-radius: 2px; }
.recorder-pie-attr-name { flex: 1; color: var(--text-secondary); font-family: inherit; }
.recorder-pie-attr-count { min-width: 25px; color: var(--text-primary); font-family: inherit; font-weight: 650; text-align: right; }
.recorder-pie-attr-pct { min-width: 42px; color: var(--text-tertiary); font-family: inherit; text-align: right; }
.export-action-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 9px; margin-bottom: 11px; padding: 11px 12px; background: rgba(247,251,250,.86); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: inset 0 1px 0 rgba(255,255,255,.9); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); }
.export-action-left,
.export-action-right { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: 7px; }
.export-action-left { flex: 1 1 650px; }
.export-action-right { flex: 0 1 auto; }
.btn-export-date { color: #fff; background: var(--accent); border-color: var(--accent); }
.btn-export-date:hover:not(:disabled) { background: var(--accent-strong); }
.btn-export-hash,
.btn-download-sm { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-export-hash:hover:not(:disabled),
.btn-download-sm:hover:not(:disabled) { background: var(--accent-light); }
.btn-export-all { color: #1c1a16; background: #fbf3e2; border-color: #eeddb8; }
.btn-export-all:hover:not(:disabled) { background: #fbf3e2; }
.btn-refresh-unexported { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-refresh-unexported:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.export-tip { padding: 5px 8px; color: #1c1a16; background: #fbf3e2; border: 1px solid #eeddb8; border-radius: 6px; font-size: 11px; }
.unexported-badge { display: inline-flex; min-height: 29px; align-items: center; gap: 6px; padding: 4px 9px; color: #1c1a16; background: #fbf3e2; border: 1px solid #eeddb8; border-radius: 999px; font-size: 11px; white-space: nowrap; }
.unexported-badge-dot { width: 5px; height: 5px; background: var(--warning); border-radius: 50%; }
.unexported-badge strong { font-family: inherit; font-size: 13px; }
.unexported-badge.unexported-zero { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.unexported-zero .unexported-badge-dot { background: var(--success); }
.download-section-inline { display: flex; min-width: 0; align-items: center; gap: 8px; padding: 5px 6px 5px 10px; background: #f8f1e4; border: 1px solid #d9e4d0; border-radius: var(--radius); }
.download-file-name { max-width: 220px; overflow: hidden; color: #4a7c59; font-family: inherit; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.feedback,
.polling-tip { margin: 0 0 10px; padding: 10px 12px; border: 1px solid; border-radius: var(--radius); font-size: 12px; font-weight: 550; line-height: 1.55; overflow-wrap: anywhere; }
.feedback-ok { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.feedback-err { color: var(--danger); background: #fbeee7; border-color: #f2cfcc; }
.polling-tip { display: flex; align-items: center; gap: 8px; color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.result-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 9px; margin-bottom: 10px; padding: 8px 11px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); }
.result-toolbar-left,
.result-toolbar-right { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.result-count { color: var(--text-secondary); font-size: 12px; }
.result-count strong { color: var(--accent-strong); font-family: inherit; font-size: 14px; }
.batch-select-label { display: inline-flex; align-items: center; gap: 5px; color: var(--text-secondary); font-size: 12px; cursor: pointer; }
.batch-select-label input,
.data-table input[type="checkbox"] { width: 14px; height: 14px; margin: 0; accent-color: var(--accent); cursor: pointer; }
.selected-count { padding: 3px 8px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 999px; font-family: inherit; font-size: 10px; }
.btn-batch-del { min-height: 29px; padding: 5px 10px; color: var(--danger); background: #fbeee7; border-color: #f2cfcc; }
.btn-batch-del:hover:not(:disabled) { background: #fbeee7; }
.state-block { display: flex; min-height: 210px; align-items: center; justify-content: center; flex-direction: column; padding: 36px 20px; background: var(--bg-card); border: 1px dashed var(--border-color); border-radius: var(--radius-lg); text-align: center; }
.state-spinner { width: 28px; height: 28px; margin: 0 auto 12px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: admin-spin .75s linear infinite; }
@keyframes admin-spin { to { transform: rotate(360deg); } }
.state-icon { margin-bottom: 10px; color: var(--accent); font-family: inherit; font-size: 12px; font-weight: 700; letter-spacing: .12em; }
.state-text { color: var(--text-tertiary); font-size: 13px; line-height: 1.55; }
.table-wrapper { max-width: 100%; margin-bottom: 13px; overflow-x: auto; overscroll-behavior-x: contain; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.data-table { width: 100%; min-width: 640px; border-collapse: separate; border-spacing: 0; color: var(--text-primary); font-size: 12px; text-align: left; }
.table-wrapper-records .data-table { min-width: 1180px; }
.data-table th { height: 38px; padding: 9px 11px; color: #1c1a16; background: #efe9de; border-bottom: 1px solid var(--border-color); font-size: 10px; font-weight: 650; letter-spacing: .035em; white-space: nowrap; }
.data-table td { height: 44px; padding: 8px 11px; color: var(--text-primary); background: var(--bg-card); border-bottom: 1px solid #efe9de; vertical-align: middle; transition: background .14s ease; }
.data-table tbody tr:last-child td { border-bottom: 0; }
.data-table tbody tr:hover td { background: #faf6ee; }
.data-table tbody tr.row-selected td { background: var(--accent-soft); }
.data-table tbody tr.row-editing td { background: #fff9ea; }
.th-check { width: 38px; text-align: center; }
.td-check { text-align: center; }
.th-sortable { cursor: pointer; user-select: none; }
.th-sortable:hover { background: #efedea; }
.sort-indicator { display: inline-flex; flex-direction: column; gap: 2px; margin-left: 6px; vertical-align: middle; }
.sort-arrow { width: 0; height: 0; border-right: 3px solid transparent; border-left: 3px solid transparent; opacity: .55; }
.sort-arrow-up { border-bottom: 4px solid var(--text-tertiary); }
.sort-arrow-down { border-top: 4px solid var(--text-tertiary); }
.sort-arrow-up.active { border-bottom-color: var(--accent); opacity: 1; }
.sort-arrow-down.active { border-top-color: var(--accent); opacity: 1; }
.cell-index { width: 42px; color: var(--text-tertiary) !important; font-family: inherit; font-size: 10px; text-align: center; }
.cell-url { max-width: 240px; overflow: hidden; color: var(--text-secondary) !important; text-overflow: ellipsis; white-space: nowrap; }
.cell-url .url-text { padding: 2px 3px; border-radius: 4px; cursor: context-menu; }
.cell-url .url-text:hover { color: var(--accent-strong); background: var(--accent-soft); }
.cell-mono { max-width: 190px; overflow: hidden; color: var(--text-secondary) !important; font-family: inherit; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.cell-remark { max-width: 190px; overflow: hidden; color: var(--text-secondary) !important; text-overflow: ellipsis; white-space: nowrap; }
.cell-num { font-family: inherit; font-weight: 650; text-align: right; }
.exception-tag,
.tag-exported,
.tag-unexported { display: inline-flex; align-items: center; padding: 3px 7px; border: 1px solid; border-radius: 5px; font-size: 10px; font-weight: 650; white-space: nowrap; }
.exception-tag { color: #1c1a16; background: #fbf3e2; border-color: #eeddb8; }
.exception-tag.ex-正常,
.exception-tag.ex-验证已解决 { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.exception-tag.ex-测试 { color: #276b83; background: #f6f1e7; border-color: #dbe4ea; }
.exception-tag.ex-iOS16闪退,
.exception-tag.ex-iOS13\/14\/16均闪退,
.exception-tag.ex-禁止入库 { color: var(--danger); background: #fbeee7; border-color: #f0d6cc; }
.tag-exported { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.tag-unexported { color: #1c1a16; background: #fbf3e2; border-color: #eeddb8; }
.cell-edit-input { width: 100%; min-width: 80px; height: 30px; padding: 4px 7px; color: var(--text-primary); background: #fff; border: 1px solid var(--accent); border-radius: 6px; outline: none; font-size: 11px; }
.cell-edit-input:focus { box-shadow: 0 0 0 3px rgba(255,255,255,.8); }
.cell-edit-input-wide { min-width: 190px; }
.cell-edit-input-sm { min-width: 60px; width: 65px; }
.action-cell { min-width: 70px; white-space: nowrap; }
.action-cell .btn-sm { margin-right: 5px; }
.action-cell .btn-sm:last-child { margin-right: 0; }
.btn-sm { min-height: 29px; padding: 5px 9px; font-size: 11px; }
.btn-edit { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.btn-edit:hover:not(:disabled) { background: var(--accent-light); }
.btn-save { color: #fff; background: var(--accent); border-color: var(--accent); }
.btn-cancel { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-del { color: var(--danger); background: #fbeee7; border-color: #f2cfcc; }
.btn-del:hover:not(:disabled) { background: #fbeee7; }
.btn-kick { color: #1c1a16; background: #fbf3e2; border-color: #eeddb8; }
.btn-kick:hover:not(:disabled) { background: #fbf3e2; }
.btn-refresh { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-refresh:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.copy-tip { position: absolute; z-index: 4; margin-left: 4px; padding: 3px 6px; color: #fff; background: var(--accent-strong); border-radius: 4px; font-size: 10px; animation: copy-tip-life 1.2s ease both; }
@keyframes copy-tip-life { 0% { opacity: 0; } 15%,80% { opacity: 1; } 100% { opacity: 0; } }
.pagination { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px; padding: 5px 0 12px; }
.pagination-top { padding: 3px 0 11px; }
.page-btn { color: var(--text-secondary); background: var(--bg-card); border-color: var(--border-color); }
.page-btn:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.page-info { color: var(--text-secondary); font-family: inherit; font-size: 11px; }
.page-size-label { display: inline-flex; align-items: center; gap: 5px; margin-left: 6px; color: var(--text-secondary); font-size: 11px; }
.page-size-input { width: 55px; height: 32px; padding: 0 6px; font-family: inherit; font-size: 11px; text-align: center; }
.import-section { margin-top: 15px; overflow: hidden; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.import-toggle { display: flex; align-items: center; gap: 9px; min-height: 45px; padding: 11px 14px; cursor: pointer; user-select: none; transition: background .15s ease; }
.import-toggle:hover { background: #f9f8f7; }
.import-toggle-icon { min-width: 24px; color: var(--accent); font-size: 11px; font-weight: 650; }
.import-toggle-icon-emoji { display: inline-flex; width: 24px; height: 24px; align-items: center; justify-content: center; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 6px; font-family: inherit; font-size: 10px; font-weight: 700; }
.import-toggle-text { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.import-body { padding: 0 14px 14px; border-top: 1px solid #efe9de; }
.import-help { margin-top: 13px; padding: 11px 12px; color: #1c1a16; background: #f9f7f5; border: 1px solid #e6e2dc; border-radius: var(--radius); }
.import-help p { margin: 0 0 5px; font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; }
.import-help p:last-child { margin-bottom: 0; }
.import-help code { padding: 1px 4px; color: var(--accent-strong); background: #f0ede7; border-radius: 4px; font-family: inherit; font-size: 11px; overflow-wrap: anywhere; }
.import-textarea { width: 100%; min-height: 160px; margin-top: 12px; padding: 11px 12px; resize: vertical; color: var(--text-primary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; font-family: inherit; font-size: 12px; line-height: 1.6; }
.import-textarea:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.import-textarea::placeholder { color: var(--text-tertiary); }
.import-actions { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-top: 11px; }
.import-hint { color: var(--text-secondary); font-size: 12px; }
.import-hint strong { color: var(--accent-strong); font-family: inherit; }
.import-btns { display: flex; gap: 7px; margin-left: auto; }
.btn-import { color: #fff; background: var(--accent); border-color: var(--accent); }
.btn-import:hover:not(:disabled) { background: var(--accent-strong); }
.btn-import-clear { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.import-result { margin-top: 12px; padding: 10px 12px; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); }
.import-result-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px dashed #e5e4e1; font-size: 12px; }
.import-result-row:last-child { border-bottom: 0; }
.import-result-label { color: #1c1a16; }
.import-result-value { color: var(--text-primary); font-family: inherit; font-weight: 650; }
.import-result-ok { color: #4a7c59; }
.import-result-bad { color: var(--danger); }
.import-errors { max-height: 210px; margin-top: 10px; padding: 10px; overflow-y: auto; color: var(--danger); background: #fbeee7; border: 1px solid #f2cfcc; border-radius: 8px; }
.import-error-title { margin-bottom: 5px; font-size: 12px; font-weight: 650; }
.import-error-item { padding: 2px 0; font-size: 11px; line-height: 1.6; overflow-wrap: anywhere; }
.appid-lookup-card { min-width: 0; padding: 20px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.8); }
.appid-lookup-header { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 18px; }
.appid-lookup-icon { display: inline-flex; flex: 0 0 auto; width: 34px; height: 34px; align-items: center; justify-content: center; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 9px; font-family: inherit; font-size: 10px; font-weight: 700; }
.appid-lookup-header > div { min-width: 0; }
.appid-lookup-title { margin: 0; color: var(--text-primary); font-size: 16px; font-weight: 700; }
.appid-lookup-desc { margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }
.appid-mode-switch { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.appid-mode-btn { display: flex; min-width: 0; align-items: center; gap: 11px; padding: 12px 13px; color: var(--text-primary); background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; text-align: left; cursor: pointer; transition: background .16s ease, border-color .16s ease, box-shadow .16s ease; }
.appid-mode-btn:hover { background: var(--accent-soft); border-color: var(--accent-light); }
.appid-mode-btn.active { background: var(--accent-soft); border-color: var(--accent); box-shadow: inset 3px 0 0 var(--accent); }
.appid-mode-btn:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.appid-mode-icon { display: inline-flex; flex: 0 0 auto; width: 29px; height: 29px; align-items: center; justify-content: center; color: var(--text-secondary); background: #fff; border: 1px solid var(--border-color); border-radius: 7px; font-family: inherit; font-size: 11px; font-weight: 700; }
.appid-mode-btn.active .appid-mode-icon { color: var(--accent-strong); border-color: var(--accent-light); }
.appid-mode-text { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.appid-mode-name { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.appid-mode-desc { color: var(--text-tertiary); font-size: 11px; line-height: 1.45; overflow-wrap: anywhere; }
.appid-mode-btn.active .appid-mode-name { color: var(--accent-strong); }
.appid-search-row { display: flex; gap: 9px; margin-bottom: 14px; }
.appid-search-input { flex: 1; min-width: 0; height: 38px; padding: 0 11px; color: var(--text-primary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: var(--radius); outline: none; font-family: inherit; font-size: 12px; transition: border-color .16s ease, box-shadow .16s ease, background .16s ease; }
.appid-search-input::placeholder { color: var(--text-tertiary); }
.appid-search-input:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.appid-search-btn { min-height: 38px; padding-right: 20px; padding-left: 20px; }
.appid-result-card,
.appid-online-card { animation: admin-fade-up .22s ease both; }
.appid-result-found { padding: 14px; background: #f8f7f5; border: 1px solid #e4e0d9; border-radius: var(--radius-lg); }
.appid-result-badge { display: inline-flex; align-items: center; margin-bottom: 12px; padding: 4px 8px; border: 1px solid; border-radius: 6px; font-size: 11px; font-weight: 650; }
.badge-redis { color: #1c1a16; background: #fbf3e2; border-color: #eeddb8; }
.badge-mysql { color: #276b83; background: #f6f1e7; border-color: #dbe4ea; }
.appid-result-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.appid-result-field { display: flex; min-width: 0; flex-direction: column; gap: 5px; padding: 11px 12px; background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius); }
.appid-field-label,
.appid-online-label { color: var(--text-tertiary); font-size: 10px; font-weight: 600; letter-spacing: .035em; }
.appid-field-value { color: var(--text-primary); font-size: 15px; font-weight: 650; overflow-wrap: anywhere; }
.appid-field-mono { font-family: inherit; font-size: 12px !important; }
.appid-field-highlight { color: var(--accent-strong); font-family: inherit; font-size: 18px !important; }
.appid-cache-hint { margin-top: 11px; padding: 8px 10px; color: #1c1a16; background: #fff9e9; border: 1px solid #eee0bb; border-radius: 8px; font-size: 11px; line-height: 1.55; }
.appid-result-notfound { padding: 28px 18px; background: #faf9f8; border: 1px dashed var(--border-color); border-radius: var(--radius-lg); text-align: center; }
.appid-notfound-icon { margin-bottom: 8px; color: var(--accent); font-family: inherit; font-size: 21px; font-weight: 500; }
.appid-notfound-text { margin-bottom: 5px; color: var(--text-primary); font-size: 13px; font-weight: 650; }
.appid-notfound-hint { color: var(--text-tertiary); font-size: 11px; line-height: 1.55; }
.appid-online-card { padding: 15px; background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-lg); }
.appid-online-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; padding-bottom: 10px; border-bottom: 1px solid #efe9de; }
.appid-online-status { display: inline-flex; align-items: center; gap: 6px; padding: 3px 8px; border: 1px solid; border-radius: 999px; font-size: 11px; font-weight: 650; }
.appid-online-status::before { width: 5px; height: 5px; background: currentColor; border-radius: 50%; content: ""; }
.status-active { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.status-inactive { color: var(--text-secondary); background: #f5f5f4; border-color: var(--border-color); }
.appid-online-id { max-width: 60%; overflow: hidden; color: var(--text-tertiary); font-family: inherit; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.appid-online-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; }
.appid-online-field { display: flex; min-width: 0; flex-direction: column; gap: 5px; padding: 10px 11px; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); }
.appid-online-field-wide { grid-column: span 2; }
.appid-online-value { color: var(--text-primary); font-size: 12px; font-weight: 550; line-height: 1.5; overflow-wrap: anywhere; }
.appid-field-link a { color: var(--accent-strong); text-decoration: none; overflow-wrap: anywhere; }
.appid-field-link a:hover { text-decoration: underline; }
.appid-sync-msg { margin-top: 12px; padding: 9px 11px; border: 1px solid; border-radius: var(--radius); font-size: 12px; text-align: center; overflow-wrap: anywhere; }
.appid-sync-ok { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.appid-sync-err { color: var(--danger); background: #fbeee7; border-color: #f2cfcc; }
.retest-stat-row { display: flex; align-items: stretch; flex-wrap: wrap; gap: 10px; margin: 14px 0; }
.retest-stat-box { min-width: 135px; padding: 12px 15px; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); }
.retest-stat-value { color: var(--text-primary); font-family: inherit; font-size: 20px; font-weight: 650; line-height: 1.25; }
.retest-stat-label { margin-top: 5px; color: #1c1a16; font-size: 11px; }
.retest-stat-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin-left: auto; }
.retest-add-row { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin: 12px 0; }
.retest-add-row .appid-search-input { min-width: 220px; }
.grade-badge { display: inline-flex; min-width: 43px; align-items: center; justify-content: center; padding: 3px 9px; border: 1px solid; border-radius: 6px; font-family: inherit; font-size: 11px; font-weight: 700; }
.grade-a { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.grade-b { color: #276b83; background: #f6f1e7; border-color: #dbe4ea; }
.card { min-width: 0; margin-bottom: 14px; overflow: hidden; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.card-header { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; min-height: 52px; padding: 12px 15px; background: #fcfcfb; border-bottom: 1px solid var(--border-color); }
.card-header h3 { margin: 0; color: var(--text-primary); font-size: 14px; font-weight: 700; }
.card-header-between { justify-content: space-between; }
.card-header-left,
.card-header-right { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-icon { display: inline-flex; width: 26px; height: 26px; align-items: center; justify-content: center; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid var(--accent-light); border-radius: 7px; font-family: inherit; font-size: 10px; font-weight: 700; }
.card-body { min-width: 0; padding: 15px; }
.card-body .table-wrapper { margin-bottom: 0; box-shadow: none; }
.card-body .state-block { min-height: 180px; background: #fcfbfa; }
.db-metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 13px; }
.db-metric-card { position: relative; min-width: 0; padding: 13px; overflow: hidden; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); }
.db-metric-card::before { position: absolute; top: 0; left: 0; width: 100%; height: 2px; background: var(--accent); content: ""; }
.db-metric-label { color: #1c1a16; font-size: 11px; font-weight: 600; }
.db-metric-value { margin: 8px 0 5px; overflow: hidden; color: var(--text-primary); font-family: inherit; font-size: 26px; font-weight: 650; letter-spacing: -.04em; line-height: 1; text-overflow: ellipsis; }
.db-metric-sub { overflow: hidden; color: #1c1a16; font-family: inherit; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.db-metric-card.level-normal .db-metric-value { color: var(--accent-strong); }
.db-metric-card.level-warn { background: #fff9eb; border-color: #eddfbd; }
.db-metric-card.level-warn::before { background: var(--warning); }
.db-metric-card.level-warn .db-metric-value { color: #1c1a16; }
.db-metric-card.level-danger { background: #fbeee7; border-color: #f0d3cf; }
.db-metric-card.level-danger::before { background: var(--danger); }
.db-metric-card.level-danger .db-metric-value { color: var(--danger); }
.db-var-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
.db-var-item { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 11px; background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius); }
.db-var-label { flex: 0 0 auto; color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.db-var-value { min-width: 0; color: var(--text-primary); font-size: 12px; font-weight: 550; text-align: right; overflow-wrap: anywhere; }
.db-var-mono { font-family: inherit; font-size: 11px; }
.var-on { color: #4a7c59; }
.var-off { color: var(--danger); }
.db-backup-meta { display: flex; flex-wrap: wrap; gap: 6px 20px; margin-bottom: 12px; padding: 9px 11px; color: #1c1a16; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); font-size: 11px; line-height: 1.55; }
.db-backup-meta span { min-width: 0; overflow-wrap: anywhere; }
.db-backup-file { max-width: 420px; overflow: hidden; font-family: inherit; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.user-top-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.form-row { display: flex; gap: 10px; margin-bottom: 11px; }
.form-group { flex: 1; min-width: 0; }
.form-group-stretch { flex-basis: 100%; }
.form-group label { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.form-input { height: 37px; padding: 0 10px; }
.user-count { padding: 3px 7px; color: #1c1a16; background: #f6f1e7; border: 1px solid var(--border-color); border-radius: 999px; font-family: inherit; font-size: 10px; }
.kick-label { color: var(--text-secondary); font-size: 11px; }
.kick-input-wrap { display: flex; align-items: center; gap: 5px; }
.kick-input { width: 64px; height: 30px; padding: 0 6px; font-family: inherit; text-align: center; }
.kick-unit { color: var(--text-tertiary); font-size: 11px; }
.table-wrapper-user { min-width: 0; }
.user-table { min-width: 660px; }
.uid-cell { color: var(--accent-strong) !important; font-family: inherit; font-size: 11px; font-weight: 650; }
.name-cell { font-weight: 600; }
.row-online td { background: #fdfdfc; }
.role-tag,
.online-tag { display: inline-flex; align-items: center; padding: 3px 8px; border: 1px solid; border-radius: 999px; font-size: 10px; font-weight: 650; white-space: nowrap; }
.role-admin { color: #1c1a16; background: #fbf3e2; border-color: #eeddb8; }
.role-developer { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.role-user { color: #276b83; background: #f6f1e7; border-color: #dbe4ea; }
.online-tag { gap: 5px; }
.online-yes { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.online-no { color: var(--text-tertiary); background: #f5f5f4; border-color: var(--border-color); }
.online-dot { width: 5px; height: 5px; background: currentColor; border-radius: 50%; }
.sup-area { width: 20px; height: 15px; color: transparent; font-size: 11px;}
.stats-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
.stat-card { display: flex; min-width: 0; align-items: center; gap: 12px; padding: 14px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); }
.stat-card-icon { color: var(--accent); font-family: inherit; }
.stat-card-value { color: var(--text-primary); font-family: inherit; font-size: 24px; font-weight: 650; }
.stat-card-label { color: var(--text-tertiary); font-size: 11px; }
.report-overlay { position: fixed; z-index: 999; inset: 0; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(45,35,22,.32); backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px); animation: report-fade .18s ease both; }
@keyframes report-fade { from { opacity: 0; } to { opacity: 1; } }
.report-modal { width: min(640px, 100%); max-height: calc(100dvh - 40px); overflow-y: auto; overscroll-behavior: contain; background: rgba(255,255,255,.96); border: 1px solid rgba(255,255,255,.8); border-radius: var(--radius-lg); box-shadow: var(--shadow-lg), inset 0 1px 0 #fff; }
.report-modal-header { position: sticky; z-index: 2; top: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 18px; background: rgba(255,255,255,.92); border-bottom: 1px solid var(--border-color); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); }
.report-modal-title { display: flex; align-items: center; gap: 8px; }
.report-modal-title h3 { margin: 0; color: var(--text-primary); font-size: 16px; }
.report-modal-icon { color: var(--accent); }
.report-close-btn { width: 30px; height: 30px; color: var(--text-secondary); background: transparent; border: 1px solid transparent; border-radius: 7px; cursor: pointer; }
.report-close-btn:hover { color: var(--text-primary); background: var(--accent-soft); border-color: var(--accent-light); }
.report-modal-body { padding: 18px; }
.report-date-picker { margin-bottom: 16px; }
.report-date-label { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.report-date-row { display: flex; gap: 9px; }
.report-date-input { flex: 1; min-width: 0; color-scheme: light; }
.report-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 9px; margin-bottom: 18px; }
.report-stat { padding: 12px; background: #faf6ee; border: 1px solid var(--border-color); border-radius: var(--radius); text-align: center; }
.report-stat .stat-number { color: var(--text-primary); font-family: inherit; font-size: 22px; font-weight: 650; }
.report-stat .stat-label { margin-top: 4px; color: var(--text-tertiary); font-size: 10px; }
.stat-qualified { color: #4a7c59; background: #f8f1e4; border-color: #d9e4d0; }
.stat-unqualified { color: var(--danger); background: #fbeee7; border-color: #f2cfcc; }
.stat-rate { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.attr-section-title { margin-bottom: 12px; padding-bottom: 8px; color: var(--text-primary); border-bottom: 1px solid var(--border-color); font-size: 12px; font-weight: 650; }
.attr-bar-list { display: flex; flex-direction: column; gap: 11px; }
.attr-bar-header { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; font-size: 11px; }
.attr-bar-name { color: var(--text-secondary); font-family: inherit; }
.attr-bar-count { color: var(--text-secondary); }
.attr-bar-percent { margin-left: auto; font-family: inherit; }
.attr-bar-track { height: 5px; overflow: hidden; background: #efedeb; border-radius: 2px; }
.attr-bar-fill { height: 100%; background: var(--accent); }
.report-placeholder,
.report-loading { padding: 36px 18px; text-align: center; }
.placeholder-icon { margin-bottom: 10px; color: var(--accent); font-family: inherit; }
.placeholder-text { color: var(--text-tertiary); font-size: 12px; }
@media (max-width: 1200px) {
  .filter-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .recorder-pies-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .appid-online-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1000px) {
  .charts-row { grid-template-columns: minmax(0, 1fr); }
  .charts-row-single { max-width: 100%; }
  .db-metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .user-top-grid { grid-template-columns: minmax(0, 1fr); }
  .export-action-left { flex-basis: 100%; }
  .export-action-right { width: 100%; }
}
@media (max-width: 900px) {
  .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .appid-result-grid { grid-template-columns: minmax(0, 1fr); }
  .appid-online-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .stats-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 700px) {
  .admin-page { padding: 0 0 30px; }
  .page-header { align-items: flex-start; }
  .page-header-meta { align-items: flex-end; }
  .admin-tabs { border-radius: var(--radius); }
  .admin-tab { flex-basis: 108px; }
  .filter-card { padding: 14px; }
  .section-caption { align-items: flex-start; flex-direction: column; gap: 3px; }
  .quality-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quality-attr-bars { grid-template-columns: minmax(0, 1fr); }
  .recorder-pies-grid { grid-template-columns: minmax(0, 1fr); }
  .chart-canvas-wrap { height: 270px; }
  .chart-filter-hint { width: 100%; margin-left: 0; }
  .download-section-inline { width: 100%; justify-content: space-between; }
  .download-file-name { max-width: 55vw; }
  .appid-lookup-card { padding: 16px; }
  .retest-stat-actions { width: 100%; margin-left: 0; }
  .db-var-grid { grid-template-columns: minmax(0, 1fr); }
  .report-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .card-header-between { align-items: flex-start; }
}
@media (max-width: 520px) {
  .page-header { flex-direction: column; gap: 9px; }
  .page-header h2 { font-size: 22px; }
  .page-header-meta { align-items: flex-start; }
  .operator { max-width: 88vw; }
  .filter-grid { grid-template-columns: minmax(0, 1fr); }
  .filter-top-row { flex-direction: column; }
  .filter-top-actions { width: 100%; justify-content: space-between; }
  .radio-group { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .radio-tag { justify-content: center; }
  .filter-action-row .btn-query { flex: 1; }
  .filter-tip { width: 100%; margin-left: 0; }
  .quality-panel { padding: 13px; }
  .quality-header { align-items: flex-start; flex-direction: column; gap: 8px; }
  .quality-stat { padding: 11px; }
  .quality-stat-value { font-size: 21px; }
  .chart-filter-bar { align-items: flex-start; }
  .chart-filter-select { flex: 1; min-width: 0; width: 100%; }
  .chart-filter-label { align-self: center; }
  .chart-canvas-wrap { height: 250px; }
  .export-action-bar { padding: 10px; }
  .export-action-left .btn-action { flex: 1 1 calc(50% - 5px); }
  .export-action-left .btn-refresh-unexported { flex-basis: 100%; }
  .export-tip { width: 100%; }
  .result-toolbar { align-items: flex-start; flex-direction: column; }
  .page-info { width: 100%; order: -1; text-align: center; }
  .page-size-label { margin-left: 0; }
  .import-actions { align-items: flex-start; flex-direction: column; }
  .import-btns { width: 100%; margin-left: 0; }
  .import-btns .btn-action { flex: 1; }
  .appid-mode-switch { grid-template-columns: minmax(0, 1fr); }
  .appid-search-row { flex-direction: column; }
  .appid-search-btn { width: 100%; }
  .appid-online-grid { grid-template-columns: minmax(0, 1fr); }
  .appid-online-field-wide { grid-column: auto; }
  .appid-online-id { max-width: 100%; }
  .retest-stat-box { flex: 1 1 calc(50% - 6px); min-width: 0; }
  .retest-add-row { align-items: stretch; flex-direction: column; }
  .retest-add-row .appid-search-input { width: 100%; min-width: 0; }
  .retest-add-row .filter-input { width: 100% !important; }
  .form-row { flex-direction: column; }
  .db-metric-grid { gap: 8px; }
  .db-metric-card { padding: 11px; }
  .db-metric-value { font-size: 23px; }
  .card-header-right { width: 100%; }
  .card-header-between .card-header-right .btn-action { width: 100%; }
  .report-overlay { padding: 10px; }
  .report-modal { max-height: calc(100dvh - 20px); }
  .report-date-row { flex-direction: column; }
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .filter-card,
  .chart-filter-bar,
  .export-action-bar { background: var(--bg-card); }
}
@media (prefers-reduced-motion: reduce) {
  .admin-page * { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; scroll-behavior: auto !important; }
}
</style>
