<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { fetchSystemInfo, fetchRedisInfo, fetchRedisKeys, fetchLogTail, fetchLogSearch, terminalExec } from '../api/index.js'

const emit = defineEmits(['error'])

const activeTab = ref('server')

const sysInfo = ref(null)
const sysLoading = ref(false)

const logLines = ref([])
const logLoading = ref(false)
const logLevel = ref('ALL')
const logAutoScroll = ref(true)
const logPaused = ref(false)
const logTailCount = ref(300)
let logTimer = null
const logContainer = ref(null)

const logSearchMode = ref(false)
const logSearchLoading = ref(false)
const logSearchRecords = ref([])
const logSearchTotal = ref(0)
const logSearchQueried = ref(false)
const logSearchFilters = ref({
  level: 'ALL',
  user: '',
  function: '',
  clazz: '',
  method: '',
  status: '',
  minDuration: '',
  maxDuration: '',
  lines: 500
})


const activeFilterCount = computed(() => {
  const f = logSearchFilters.value
  let count = 0
  if (f.level !== 'ALL') count++
  if (f.user.trim()) count++
  if (f.function.trim()) count++
  if (f.clazz.trim()) count++
  if (f.method.trim()) count++
  if (f.status) count++
  if (f.minDuration !== '' && f.minDuration !== null) count++
  if (f.maxDuration !== '' && f.maxDuration !== null) count++
  return count
})

const redisInfo = ref(null)
const redisLoading = ref(false)
const selectedDb = ref(-1)
const redisKeys = ref([])
const redisKeysLoading = ref(false)

const termCommand = ref('')
const termOutput = ref('')
const termExecuting = ref(false)
const termHistory = ref([])
const termHistoryIndex = ref(-1)
const termExitCode = ref(null)
const termElapsed = ref(null)
const termContainer = ref(null)
const termInput = ref(null)

async function loadSystemInfo() {
  sysLoading.value = true
  try {
    const json = await fetchSystemInfo()
    if (json.success) sysInfo.value = json.data
  } catch (e) { /* silent */ }
  finally { sysLoading.value = false }
}

async function loadRedisInfo() {
  redisLoading.value = true
  try {
    const json = await fetchRedisInfo()
    if (json.success) redisInfo.value = json.data
  } catch (e) { emit('error', 'Redis信息获取失败: ' + e.message) }
  finally { redisLoading.value = false }
}

async function loadRedisKeys(db) {
  selectedDb.value = db
  redisKeysLoading.value = true
  redisKeys.value = []
  try {
    const json = await fetchRedisKeys(db, 200)
    if (json.success) redisKeys.value = json.data?.keys || []
  } catch (e) { emit('error', 'Redis键查询失败: ' + e.message) }
  finally { redisKeysLoading.value = false }
}

function closeKeyViewer() {
  selectedDb.value = -1
  redisKeys.value = []
}


function toggleLogSearchMode() {
  logSearchMode.value = !logSearchMode.value
  if (!logSearchMode.value) {
    logSearchRecords.value = []
    logSearchTotal.value = 0
    logSearchQueried.value = false
  }
}


function resetLogSearchFilters() {
  logSearchFilters.value = {
    level: 'ALL',
    user: '',
    function: '',
    clazz: '',
    method: '',
    status: '',
    minDuration: '',
    maxDuration: '',
    lines: 500
  }
  logSearchRecords.value = []
  logSearchTotal.value = 0
  logSearchQueried.value = false
}

async function doLogSearch() {
  logSearchLoading.value = true
  logSearchQueried.value = true
  try {
    const json = await fetchLogSearch(logSearchFilters.value)
    if (json.success) {
      logSearchRecords.value = json.data?.records || []
      logSearchTotal.value = json.data?.total || 0
    }
  } catch (e) { /* silent */ }
  finally { logSearchLoading.value = false }
}

async function loadLogs() {
  if (logPaused.value) return
  logLoading.value = true
  try {
    const json = await fetchLogTail(logTailCount.value, logLevel.value)
    if (json.success) {
      logLines.value = json.data || []
      if (logAutoScroll.value) {
        nextTick(() => {
          if (logContainer.value) {
            logContainer.value.scrollTop = logContainer.value.scrollHeight
          }
        })
      }
    }
  } catch (e) { /* silent */ }
  finally { logLoading.value = false }
}

function startLogPolling() {
  stopLogPolling()
  loadLogs()
  logTimer = setInterval(loadLogs, 3000)
}

function stopLogPolling() {
  if (logTimer) { clearInterval(logTimer); logTimer = null }
}

function togglePause() {
  logPaused.value = !logPaused.value
}

function getLogLineClass(line) {
  if (line.includes('ERROR') || line.includes('Exception')) return 'log-error'
  if (line.includes('WARN')) return 'log-warn'
  if (line.includes('DEBUG')) return 'log-debug'
  if (line.includes('INFO')) return 'log-info'
  return ''
}

function formatUptime(seconds) {
  const s = parseInt(seconds)
  if (isNaN(s)) return 'N/A'
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  return d + '天 ' + h + '小时 ' + m + '分钟'
}

function getDbKeyCount(dbIndex) {
  if (!redisInfo.value?.databases) return 0
  return redisInfo.value.databases[dbIndex]?.keys || 0
}

async function executeTerminalCommand() {
  const cmd = termCommand.value.trim()
  if (!cmd || termExecuting.value) return
  termExecuting.value = true
  termExitCode.value = null
  termElapsed.value = null
  termOutput.value = ''

  termHistory.value.unshift(cmd)
  if (termHistory.value.length > 50) termHistory.value.pop()
  termHistoryIndex.value = -1

  try {
    const json = await terminalExec(cmd)
    if (json.success && json.data) {
      const d = json.data
      let result = ''
      if (d.output) result += d.output
      if (d.error) result += (result ? '\n' : '') + '[STDERR] ' + d.error
      if (!result) result = '(无输出)'
      termOutput.value = result
      termExitCode.value = d.exitCode
      termElapsed.value = d.elapsed
    } else {
      termOutput.value = '[ERROR] ' + (json.message || '命令执行失败')
      termExitCode.value = -1
    }
  } catch (e) {
    termOutput.value = '[ERROR] 请求失败: ' + e.message
    termExitCode.value = -1
  } finally {
    termExecuting.value = false
    termCommand.value = ''
    nextTick(() => {
      if (termContainer.value) {
        termContainer.value.scrollTop = termContainer.value.scrollHeight
      }
      if (termInput.value) termInput.value.focus()
    })
  }
}



function handleTermKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    executeTerminalCommand()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (termHistory.value.length > 0) {
      if (termHistoryIndex.value < termHistory.value.length - 1) {
        termHistoryIndex.value++
      }
      termCommand.value = termHistory.value[termHistoryIndex.value]
    }
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (termHistoryIndex.value > 0) {
      termHistoryIndex.value--
      termCommand.value = termHistory.value[termHistoryIndex.value]
    } else {
      termHistoryIndex.value = -1
      termCommand.value = ''
    }
  }
}

function clearTerminal() {
  termOutput.value = ''
  termExitCode.value = null
  termElapsed.value = null
}

onMounted(() => {
  loadSystemInfo()
  loadRedisInfo()
  startLogPolling()
})

onUnmounted(() => {
  stopLogPolling()
})
</script>

<template>
  <div class="monitor-page">
    <div class="page-header">
      <div class="page-heading">
        <span class="page-kicker">SYSTEM / OBSERVABILITY</span>
        <h2>系统监控面板</h2>
        <div class="page-header-sub">统一观测主机指标、实时日志、Redis 状态与远程终端</div>
      </div>
      <span class="header-signal"><i></i>LIVE TELEMETRY</span>
    </div>

    <div class="monitor-tabs">
      <button class="monitor-tab" :class="{ active: activeTab === 'server' }" @click="activeTab = 'server'; loadSystemInfo()">
        <span class="tab-icon">SV</span><span>服务器状态</span>
      </button>
      <button class="monitor-tab" :class="{ active: activeTab === 'logs' }" @click="activeTab = 'logs'">
        <span class="tab-icon">LG</span><span>实时日志</span>
      </button>
      <button class="monitor-tab" :class="{ active: activeTab === 'redis' }" @click="activeTab = 'redis'; loadRedisInfo()">
        <span class="tab-icon">RD</span><span>Redis 监控</span>
      </button>
      <button class="monitor-tab" :class="{ active: activeTab === 'terminal' }" @click="activeTab = 'terminal'">
        <span class="tab-icon">CLI</span><span>远程终端</span>
      </button>
    </div>

    <div v-if="activeTab === 'server'" class="monitor-section">
      <div class="card">
        <div class="card-header card-header-between">
          <div class="card-header-left">
            <span class="card-icon">SV</span>
            <div class="card-title-copy"><span class="card-kicker">HOST / RUNTIME</span><h3>服务器运行状态</h3></div>
            <span v-if="sysInfo" class="live-badge"><i></i>ONLINE</span>
          </div>
          <button class="btn-action btn-refresh" @click="loadSystemInfo" :disabled="sysLoading">
            {{ sysLoading ? '加载中...' : '刷新指标' }}
          </button>
        </div>
        <div class="card-body">
          <div v-if="sysInfo" class="sys-body">
            <div class="sys-grid">
              <div class="sys-item">
                <span class="sys-label">操作系统</span>
                <span class="sys-val">{{ sysInfo.osName }}</span>
                <span class="sys-code">PLATFORM</span>
              </div>
              <div class="sys-item">
                <span class="sys-label">系统架构</span>
                <span class="sys-val sys-val-mono">{{ sysInfo.osArch }}</span>
                <span class="sys-code">ARCHITECTURE</span>
              </div>
              <div class="sys-item sys-item-wide">
                <span class="sys-label">CPU</span>
                <span class="sys-val">{{ sysInfo.cpuName }} ({{ sysInfo.cpuCores }}核)</span>
                <span class="sys-code">PROCESSOR</span>
              </div>
              <div class="sys-item">
                <span class="sys-label">CPU 占用</span>
                <span class="sys-val sys-val-metric" :class="{ 'val-warn': sysInfo.cpuUsagePercent > 80 }">{{ sysInfo.cpuUsagePercent }}<small>%</small></span>
                <span class="sys-code">UTILIZATION</span>
              </div>
              <div class="sys-item">
                <span class="sys-label">内存总量</span>
                <span class="sys-val sys-val-metric">{{ sysInfo.totalMemoryGB }}<small>GB</small></span>
                <span class="sys-code">TOTAL MEMORY</span>
              </div>
              <div class="sys-item">
                <span class="sys-label">已用内存</span>
                <span class="sys-val sys-val-metric" :class="{ 'val-warn': sysInfo.memoryUsagePercent > 85 }">{{ sysInfo.usedMemoryGB }}<small>GB</small></span>
                <span class="sys-code">USED / {{ sysInfo.memoryUsagePercent }}%</span>
              </div>
              <div class="sys-item">
                <span class="sys-label">可用内存</span>
                <span class="sys-val sys-val-metric">{{ sysInfo.availableMemoryGB }}<small>GB</small></span>
                <span class="sys-code">AVAILABLE</span>
              </div>
              <div class="sys-item">
                <span class="sys-label">服务运行时长</span>
                <span class="sys-val">{{ sysInfo.javaUptime }}</span>
                <span class="sys-code">SERVICE UPTIME</span>
              </div>
            </div>
            <div class="meter-grid">
              <div class="meter">
                <div class="meter-header">
                  <span class="meter-label">内存使用率</span>
                  <span class="meter-value">{{ sysInfo.memoryUsagePercent }}%</span>
                </div>
                <div class="meter-track">
                  <div class="meter-fill" :style="{ width: sysInfo.memoryUsagePercent + '%' }" :class="{ 'meter-danger': sysInfo.memoryUsagePercent > 85 }"></div>
                </div>
                <div class="meter-caption"><span>MEMORY PRESSURE</span><span>{{ sysInfo.usedMemoryGB }} / {{ sysInfo.totalMemoryGB }} GB</span></div>
              </div>
              <div class="meter">
                <div class="meter-header">
                  <span class="meter-label">CPU 使用率</span>
                  <span class="meter-value">{{ sysInfo.cpuUsagePercent }}%</span>
                </div>
                <div class="meter-track">
                  <div class="meter-fill" :style="{ width: sysInfo.cpuUsagePercent + '%' }" :class="{ 'meter-danger': sysInfo.cpuUsagePercent > 80 }"></div>
                </div>
                <div class="meter-caption"><span>COMPUTE LOAD</span><span>{{ sysInfo.cpuCores }} CORES</span></div>
              </div>
            </div>
          </div>
          <div v-else-if="sysLoading" class="state-block">
            <div class="state-spinner"></div>
            <div class="state-text">正在获取系统信息...</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'logs'" class="monitor-section">
      <div class="card">
        <div class="card-header card-header-between">
          <div class="card-header-left">
            <span class="card-icon">LG</span>
            <div class="card-title-copy"><span class="card-kicker">LOG / STREAM</span><h3>实时日志</h3></div>
            <div class="log-stream-state">
              <span class="log-status-dot" :class="{ 'log-paused': logPaused || logSearchMode }"></span>
              <span class="log-status-text">{{ logSearchMode ? '结构化查询模式' : (logPaused ? '已暂停' : '实时监控中') }}</span>
            </div>
          </div>
          <div class="card-header-right">
            <button class="btn-sm-action" :class="{ 'btn-sm-active': logSearchMode }" @click="toggleLogSearchMode">
              {{ logSearchMode ? '切换实时' : '结构化查询' }}
            </button>
            <template v-if="!logSearchMode">
              <select v-model="logLevel" class="log-level-select" @change="loadLogs()">
                <option value="ALL">全部级别</option>
                <option value="INFO">INFO</option>
                <option value="WARN">WARN</option>
                <option value="ERROR">ERROR</option>
                <option value="DEBUG">DEBUG</option>
              </select>
              <button class="btn-action btn-sm-action" @click="togglePause">
                {{ logPaused ? '恢复流' : '暂停流' }}
              </button>
              <button class="btn-action btn-refresh" @click="loadLogs" :disabled="logLoading">
                {{ logLoading ? '刷新中...' : '刷新' }}
              </button>
            </template>
          </div>
        </div>

        <template v-if="!logSearchMode">
          <div class="card-body log-card-body">
            <div class="log-console-bar">
              <span>APPLICATION.LOG</span>
              <span>TAIL {{ logTailCount }} / LEVEL {{ logLevel }}</span>
            </div>
            <div class="log-viewer" ref="logContainer">
              <div v-if="logLines.length === 0 && !logLoading" class="log-empty">暂无日志数据...</div>
              <div v-for="(line, i) in logLines" :key="i" class="log-line" :class="getLogLineClass(line)">{{ line }}</div>
              <div v-if="logLoading && logLines.length === 0" class="log-empty">
                <div class="state-spinner"></div>
                <div>正在加载日志...</div>
              </div>
            </div>
            <div class="log-footer">
              <label class="log-auto-scroll-label">
                <input type="checkbox" v-model="logAutoScroll" />
                <span>自动滚动</span>
              </label>
              <span class="log-count">共 {{ logLines.length }} 行</span>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="card-body log-filter-body">
            <div class="log-search-filters">
              <div class="lsf-hint">
                <span class="lsf-hint-icon">Q</span>
                <span>填写任意一个或多个条件均可查询，留空的条件不参与过滤。支持输入框回车键直接发起查询。</span>
                <span v-if="activeFilterCount > 0" class="lsf-active-badge">已启用 {{ activeFilterCount }} 个条件</span>
                <span v-else class="lsf-active-badge lsf-active-badge-none">未设置条件，将返回全部结构化日志</span>
              </div>
              <div class="lsf-row">
                <div class="lsf-item">
                  <label>日志级别</label>
                  <select v-model="logSearchFilters.level" class="lsf-input" @change="doLogSearch">
                    <option value="ALL">全部级别</option>
                    <option value="INFO">INFO</option>
                    <option value="WARN">WARN</option>
                    <option value="ERROR">ERROR</option>
                    <option value="DEBUG">DEBUG</option>
                  </select>
                </div>
                <div class="lsf-item">
                  <label>用户 <span class="lsf-optional">可选</span></label>
                  <input v-model="logSearchFilters.user" class="lsf-input" placeholder="模糊匹配用户名" @keyup.enter="doLogSearch" />
                </div>
                <div class="lsf-item">
                  <label>功能 <span class="lsf-optional">可选</span></label>
                  <input v-model="logSearchFilters.function" class="lsf-input" placeholder="模糊匹配功能名" @keyup.enter="doLogSearch" />
                </div>
              </div>
              <div class="lsf-row">
                <div class="lsf-item">
                  <label>方法所属类 <span class="lsf-optional">可选</span></label>
                  <input v-model="logSearchFilters.clazz" class="lsf-input" placeholder="如 AdminRecordController" @keyup.enter="doLogSearch" />
                </div>
                <div class="lsf-item">
                  <label>方法 <span class="lsf-optional">可选</span></label>
                  <input v-model="logSearchFilters.method" class="lsf-input" placeholder="如 search" @keyup.enter="doLogSearch" />
                </div>
                <div class="lsf-item">
                  <label>状态</label>
                  <select v-model="logSearchFilters.status" class="lsf-input" @change="doLogSearch">
                    <option value="">全部</option>
                    <option value="SUCCESS">SUCCESS</option>
                    <option value="FAIL">FAIL</option>
                  </select>
                </div>
              </div>
              <div class="lsf-row">
                <div class="lsf-item">
                  <label>耗时 >= ms <span class="lsf-optional">可选</span></label>
                  <input v-model="logSearchFilters.minDuration" type="number" class="lsf-input" placeholder="如 500" min="0" @keyup.enter="doLogSearch" />
                </div>
                <div class="lsf-item">
                  <label>耗时 <= ms <span class="lsf-optional">可选</span></label>
                  <input v-model="logSearchFilters.maxDuration" type="number" class="lsf-input" placeholder="如 2000" min="0" @keyup.enter="doLogSearch" />
                </div>
                <div class="lsf-item">
                  <label>扫描行数</label>
                  <input v-model="logSearchFilters.lines" type="number" class="lsf-input" placeholder="500" min="100" max="5000" @keyup.enter="doLogSearch" />
                </div>
              </div>
              <div class="lsf-actions">
                <span class="lsf-action-note">查询范围由扫描行数决定</span>
                <button class="btn-sm-action" @click="resetLogSearchFilters">重置条件</button>
                <button class="btn-action btn-refresh" @click="doLogSearch" :disabled="logSearchLoading">
                  {{ logSearchLoading ? '查询中...' : '执行查询' }}
                </button>
              </div>
            </div>
          </div>

          <div class="card-body log-card-body log-result-body">
            <div v-if="logSearchLoading && logSearchRecords.length === 0" class="log-empty">
              <div class="state-spinner"></div>
              <div>正在查询日志...</div>
            </div>
            <div v-else-if="!logSearchQueried" class="log-empty">
              <div class="log-search-init-hint">
                <div class="lsih-icon">Q</div>
                <div class="lsih-text">点击“执行查询”或在任意输入框按 Enter 发起查询</div>
                <div class="lsih-sub">无需填写全部条件，填写单个字段即可检索</div>
              </div>
            </div>
            <div v-else-if="logSearchRecords.length > 0" class="log-search-result">
              <table class="lsr-table">
                <thead>
                <tr>
                  <th>时间</th>
                  <th>级别</th>
                  <th>功能</th>
                  <th>用户</th>
                  <th>类</th>
                  <th>方法</th>
                  <th>状态</th>
                  <th>耗时(ms)</th>
                  <th>IP</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(r, i) in logSearchRecords" :key="i" :class="'lsr-status-' + r.status?.toLowerCase()">
                  <td class="lsr-time">{{ r.timestamp }}</td>
                  <td><span class="lsr-level" :class="'lsr-level-' + r.level?.toLowerCase()">{{ r.level }}</span></td>
                  <td>{{ r.function }}</td>
                  <td>{{ r.user }}</td>
                  <td class="lsr-clazz">{{ r.clazz }}</td>
                  <td class="lsr-method">{{ r.method }}</td>
                  <td><span class="lsr-status" :class="r.status === 'SUCCESS' ? 'lsr-status-ok' : 'lsr-status-fail'">{{ r.status }}</span></td>
                  <td class="lsr-duration" :class="{ 'lsr-duration-slow': r.duration >= 1000, 'lsr-duration-warn': r.duration >= 500 && r.duration < 1000 }">{{ r.duration }}</td>
                  <td class="lsr-ip">{{ r.ip }}</td>
                </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="log-empty">
              <div class="log-search-empty-result">
                <div class="lser-icon">0</div>
                <div class="lser-text">未找到符合条件的日志记录</div>
                <div class="lser-hint">
                  <template v-if="activeFilterCount > 0">当前已启用 {{ activeFilterCount }} 个条件，尝试减少条件或扩大扫描行数</template>
                  <template v-else>结构化日志仅包含由 LogExecutionTimeAspect 记录的接口调用，请确认日志文件中存在相关记录</template>
                </div>
              </div>
            </div>
            <div class="log-footer" v-if="logSearchRecords.length > 0">
              <span class="log-footer-label">STRUCTURED RESULT</span>
              <span class="log-count">共 {{ logSearchTotal }} 条记录</span>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div v-if="activeTab === 'redis'" class="monitor-section">
      <div class="card">
        <div class="card-header card-header-between">
          <div class="card-header-left">
            <span class="card-icon">RD</span>
            <div class="card-title-copy"><span class="card-kicker">REDIS / KEYSPACE</span><h3>Redis 数据库监控</h3></div>
            <span v-if="redisInfo" class="redis-host-badge">{{ redisInfo.host }}</span>
          </div>
          <button class="btn-action btn-refresh" @click="loadRedisInfo" :disabled="redisLoading">
            {{ redisLoading ? '加载中...' : '刷新状态' }}
          </button>
        </div>
        <div class="card-body">
          <div v-if="redisInfo" class="redis-body">
            <div class="redis-server-info">
              <div class="redis-si-item">
                <span class="redis-si-label">版本</span>
                <span class="redis-si-val">{{ redisInfo.redisVersion }}</span>
                <span class="redis-si-code">ENGINE</span>
              </div>
              <div class="redis-si-item">
                <span class="redis-si-label">已用内存</span>
                <span class="redis-si-val">{{ redisInfo.usedMemory }}</span>
                <span class="redis-si-code">MEMORY</span>
              </div>
              <div class="redis-si-item">
                <span class="redis-si-label">峰值内存</span>
                <span class="redis-si-val">{{ redisInfo.usedMemoryPeak }}</span>
                <span class="redis-si-code">PEAK</span>
              </div>
              <div class="redis-si-item">
                <span class="redis-si-label">连接数</span>
                <span class="redis-si-val">{{ redisInfo.connectedClients }}</span>
                <span class="redis-si-code">CLIENTS</span>
              </div>
              <div class="redis-si-item">
                <span class="redis-si-label">运行时长</span>
                <span class="redis-si-val">{{ formatUptime(redisInfo.uptimeSeconds) }}</span>
                <span class="redis-si-code">UPTIME</span>
              </div>
            </div>

            <div class="redis-section-heading"><span>数据库键空间</span><span>选择 DB 查看键详情</span></div>
            <div class="redis-db-grid">
              <div v-for="dbInfo in redisInfo.databases" :key="dbInfo.db"
                   class="redis-db-card"
                   :class="{ 'redis-db-card-active': selectedDb === dbInfo.db, 'redis-db-empty': dbInfo.keys === 0 }"
                   @click="loadRedisKeys(dbInfo.db)">
                <div class="redis-db-num">DB {{ dbInfo.db }}</div>
                <div class="redis-db-keys">{{ dbInfo.keys }}</div>
                <div class="redis-db-keys-label">KEYS</div>
              </div>
            </div>

            <div v-if="selectedDb >= 0" class="redis-key-viewer">
              <div class="rkv-header">
                <div><span class="rkv-kicker">KEYSPACE INSPECTOR</span><h4>DB{{ selectedDb }} 的键</h4></div>
                <button class="btn-sm-action" @click="closeKeyViewer">关闭</button>
              </div>
              <div v-if="redisKeysLoading" class="rkv-loading">
                <div class="state-spinner"></div>
                <span>正在查询...</span>
              </div>
              <div v-else-if="redisKeys.length === 0" class="rkv-empty">该数据库暂无数据</div>
              <div v-else class="rkv-table-wrap">
                <table class="rkv-table">
                  <thead>
                  <tr>
                    <th>Key</th>
                    <th>类型</th>
                    <th>TTL(秒)</th>
                  </tr>
                  </thead>
                  <tbody>
                  <tr v-for="k in redisKeys" :key="k.key">
                    <td class="rkv-key" :title="k.key">{{ k.key }}</td>
                    <td><span class="rkv-type" :class="'rkv-type-' + k.type">{{ k.type }}</span></td>
                    <td class="rkv-ttl">{{ k.ttl === -1 ? '永久' : k.ttl }}</td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
          <div v-else-if="redisLoading" class="state-block">
            <div class="state-spinner"></div>
            <div class="state-text">正在获取 Redis 信息...</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'terminal'" class="monitor-section">
      <div class="card">
        <div class="card-header card-header-between">
          <div class="card-header-left">
            <span class="card-icon">CLI</span>
            <div class="card-title-copy"><span class="card-kicker">REMOTE / SHELL</span><h3>远程终端</h3></div>
            <span class="term-warning-badge">PRIVILEGED SESSION</span>
          </div>
          <div class="card-header-right">
            <button class="btn-action btn-sm-action" @click="clearTerminal">清屏</button>
          </div>
        </div>
        <div class="card-body term-card-body">
          <div class="term-console-bar"><span>REMOTE SHELL</span><span>ENTER TO EXECUTE</span></div>
          <div class="term-output-area" ref="termContainer">
            <div v-if="!termOutput && !termExecuting" class="term-placeholder">
              <div class="term-placeholder-icon">$_</div>
              <div class="term-placeholder-text">在下方输入命令，按 Enter 执行</div>
              <div class="term-placeholder-hint">支持上下方向键浏览历史命令，Shift+Enter 换行</div>
            </div>
            <div v-if="termOutput" class="term-output-content">
              <pre class="term-pre">{{ termOutput }}</pre>
            </div>
            <div v-if="termExecuting" class="term-executing">
              <div class="state-spinner term-spinner"></div>
              正在执行命令...
            </div>
          </div>
          <div class="term-status-bar" v-if="termExitCode !== null">
            <span class="term-exit-code" :class="{ 'term-exit-ok': termExitCode === 0, 'term-exit-err': termExitCode !== 0 }">
              EXIT CODE {{ termExitCode }}
            </span>
            <span class="term-elapsed" v-if="termElapsed !== null">ELAPSED {{ termElapsed }} ms</span>
          </div>
          <div class="term-input-area">
            <span class="term-prompt">$</span>
            <textarea
                ref="termInput"
                v-model="termCommand"
                class="term-input"
                placeholder="输入命令... Enter 执行，上下方向键浏览历史"
                rows="1"
                @keydown="handleTermKeydown"
                :disabled="termExecuting"
            ></textarea>
            <button class="btn-action term-exec-btn" @click="executeTerminalCommand" :disabled="termExecuting || !termCommand.trim()">
              {{ termExecuting ? '执行中...' : '执行命令' }}
            </button>
          </div>
          <div class="term-history-bar" v-if="termHistory.length > 0">
            <span class="term-history-label">最近命令</span>
            <div class="term-history-items">
              <span v-for="(cmd, i) in termHistory.slice(0, 8)" :key="i" class="term-history-item" @click="termCommand = cmd" :title="cmd">
                {{ cmd.length > 40 ? cmd.substring(0, 40) + '...' : cmd }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.monitor-page { width: 100%; max-width: 1600px; min-width: 0; margin: 0 auto; padding: 0 20px 40px; color: var(--text-primary); font-family: var(--font-sans); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 22px; padding: 4px 0 18px; border-bottom: 1px solid var(--border-color); }
.page-heading { min-width: 0; }
.page-kicker, .card-kicker, .rkv-kicker { display: block; margin-bottom: 7px; color: var(--accent); font-size: 10px; font-weight: 800; letter-spacing: 1.7px; }
.page-header h2 { margin: 0; font-size: 24px; line-height: 1.25; letter-spacing: -0.6px; font-weight: 800; }
.page-header-sub { margin-top: 7px; color: var(--text-secondary); font-size: 13px; }
.header-signal { display: inline-flex; align-items: center; gap: 7px; flex: 0 0 auto; padding: 7px 11px; border: 1px solid rgba(201,106,69,0.15); border-radius: 999px; background: rgba(255,255,255,0.62); color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.85); font-size: 9px; font-weight: 800; letter-spacing: 1px; }
.header-signal i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px var(--accent-glow); animation: pulse 2.4s ease-in-out infinite; }
.monitor-tabs { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px; margin-bottom: 20px; padding: 7px; border: 1px solid rgba(221,211,193,0.85); border-radius: var(--radius); background: rgba(255,255,255,0.66); box-shadow: var(--glass-shadow, var(--shadow-sm)); backdrop-filter: blur(18px); }
.monitor-tab { display: flex; min-width: 0; min-height: 44px; align-items: center; justify-content: center; gap: 9px; padding: 8px 12px; border: 1px solid transparent; border-radius: var(--radius-sm); background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 12px; font-weight: 700; transition: var(--transition); }
.monitor-tab:hover { background: rgba(248,233,223,0.58); color: var(--text-primary); }
.monitor-tab.active { border-color: rgba(201,106,69,0.13); background: var(--accent-soft); color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.76); }
.tab-icon { display: grid; min-width: 28px; height: 28px; padding: 0 5px; place-items: center; border: 1px solid var(--border-color); border-radius: 8px; background: rgba(255,255,255,0.62); color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; font-weight: 800; }
.monitor-tab.active .tab-icon { border-color: rgba(201,106,69,0.15); color: var(--accent); }
.monitor-section { min-width: 0; animation: reveal 260ms ease-out; }
@keyframes reveal { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.45; } }
.card { min-width: 0; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: rgba(255,255,255,0.9); box-shadow: var(--shadow-sm); }
.card-header { display: flex; min-height: 76px; align-items: center; gap: 12px; padding: 15px 18px; border-bottom: 1px solid var(--border-color); background: linear-gradient(110deg, rgba(247,251,250,0.9), rgba(255,255,255,0.68)); }
.card-header-between { justify-content: space-between; }
.card-header-left, .card-header-right { display: flex; min-width: 0; align-items: center; gap: 10px; flex-wrap: wrap; }
.card-header-right { justify-content: flex-end; }
.card-icon { display: grid; min-width: 34px; height: 34px; padding: 0 6px; place-items: center; border: 1px solid rgba(201,106,69,0.14); border-radius: 10px; background: var(--accent-soft); color: var(--accent-strong); font-family: var(--font-mono); font-size: 9px; font-weight: 800; }
.card-title-copy { min-width: 0; }
.card-kicker { margin-bottom: 3px; font-size: 8px; letter-spacing: 1.1px; }
.card-header h3 { margin: 0; font-size: 14px; font-weight: 800; }
.card-body { min-width: 0; padding: 20px; }
.live-badge { display: inline-flex; align-items: center; gap: 6px; padding: 4px 8px; border-radius: 999px; background: var(--success-soft); color: var(--success); font-family: var(--font-mono); font-size: 8px; font-weight: 800; }
.live-badge i { width: 5px; height: 5px; border-radius: 50%; background: var(--success); }
.btn-action, .btn-sm-action { min-height: 34px; padding: 7px 13px; border: 1px solid transparent; border-radius: var(--radius-sm); cursor: pointer; font-size: 11px; font-weight: 750; white-space: nowrap; transition: var(--transition); }
.btn-action:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }
.btn-refresh { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: 0 6px 16px rgba(201,106,69,0.15), inset 0 1px 0 rgba(255,255,255,0.2); }
.btn-refresh:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(201,106,69,0.22); }
.btn-sm-action { border-color: var(--border-color); background: rgba(255,255,255,0.75); color: var(--text-secondary); }
.btn-sm-action:hover { border-color: var(--accent-light); color: var(--accent-strong); }
.btn-sm-active { border-color: rgba(201,106,69,0.18); background: var(--accent-soft); color: var(--accent-strong); }
.sys-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin-bottom: 20px; }
.sys-item { position: relative; display: flex; min-width: 0; min-height: 116px; flex-direction: column; gap: 7px; padding: 15px; overflow: hidden; border: 1px solid #e6ddcb; border-radius: var(--radius); background: linear-gradient(135deg, #fafaf9, rgba(255,255,255,0.78)); }
.sys-item::after { content: ''; position: absolute; right: -20px; bottom: -28px; width: 80px; height: 80px; border-radius: 50%; background: radial-gradient(circle, rgba(190,160,105,0.09), transparent 70%); }
.sys-label { color: var(--text-secondary); font-size: 11px; font-weight: 650; }
.sys-val { position: relative; z-index: 1; color: var(--text-primary); font-size: 13px; line-height: 1.5; font-weight: 750; overflow-wrap: anywhere; }
.sys-val-mono, .sys-val-metric { font-family: var(--font-mono); }
.sys-val-metric { font-size: 24px; line-height: 1.2; letter-spacing: -0.7px; }
.sys-val-metric small { margin-left: 4px; color: var(--text-secondary); font-size: 10px; font-weight: 600; letter-spacing: 0; }
.sys-code { margin-top: auto; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.75px; }
.val-warn { color: var(--danger); }
.meter-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.meter { padding: 16px; border: 1px solid var(--border-color); border-radius: var(--radius); background: rgba(248,251,250,0.76); }
.meter-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 11px; }
.meter-label { color: var(--text-secondary); font-size: 12px; font-weight: 650; }
.meter-value { color: var(--accent-strong); font-family: var(--font-mono); font-size: 17px; font-weight: 750; }
.meter-track { height: 8px; overflow: hidden; border-radius: 999px; background: #eae8e5; box-shadow: inset 0 1px 2px rgba(70,55,35,0.06); }
.meter-fill { height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--accent-strong), var(--accent-light)); box-shadow: 0 0 12px rgba(190,160,105,0.25); transition: width 500ms ease; }
.meter-fill.meter-danger { background: linear-gradient(90deg, #b04b45, #e68178); box-shadow: none; }
.meter-caption { display: flex; justify-content: space-between; gap: 10px; margin-top: 9px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.5px; }
.state-block { padding: 64px 20px; text-align: center; }
.state-text { color: var(--text-secondary); font-size: 13px; }
.state-spinner { width: 28px; height: 28px; margin: 0 auto 12px; border: 2px solid var(--accent-soft); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.log-stream-state { display: flex; align-items: center; gap: 7px; }
.log-status-dot { width: 7px; height: 7px; flex: 0 0 7px; border-radius: 50%; background: var(--success); box-shadow: 0 0 0 4px rgba(90,110,72,0.09); animation: pulse 2s ease-in-out infinite; }
.log-status-dot.log-paused { background: var(--warning); box-shadow: 0 0 0 4px rgba(197,122,22,0.09); animation: none; }
.log-status-text { color: var(--text-secondary); font-size: 10px; }
.log-level-select, .lsf-input { min-height: 36px; padding: 7px 11px; border: 1px solid var(--border-color); border-radius: var(--radius-sm); outline: none; background: rgba(255,255,255,0.82); color: var(--text-primary); font-size: 11px; transition: border-color var(--transition), box-shadow var(--transition), background var(--transition); }
.log-level-select:focus, .lsf-input:focus { border-color: var(--accent-light); background: #fff; box-shadow: 0 0 0 3px var(--accent-glow); }
.log-card-body, .term-card-body { padding: 0; }
.log-console-bar, .term-console-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 18px; border-bottom: 1px solid rgba(220,190,150,0.09); background: #241d12; color: #827a6b; font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.9px; }
.log-viewer { width: 100%; max-width: 100%; min-height: 420px; max-height: 62vh; padding: 16px 18px; overflow: auto; overscroll-behavior: contain; background: radial-gradient(circle at 100% 0%, rgba(120,95,60,0.08), transparent 35%), #1b160c; color: #ddd0bb; font-family: var(--font-mono); font-size: 11px; line-height: 1.75; }
.log-line { max-width: 100%; padding: 2px 5px; border-left: 2px solid transparent; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.log-line:hover { border-left-color: rgba(201,106,69,0.24); background: rgba(255,255,255,0.025); }
.log-error { color: #ff9b93; }
.log-warn { color: #e8bb74; }
.log-info { color: #c1b298; }
.log-debug { color: #a39e95; }
.log-empty { padding: 58px 20px; color: var(--text-secondary); text-align: center; font-size: 13px; }
.log-viewer .log-empty { color: #7d766b; }
.log-viewer .state-spinner, .term-output-area .state-spinner { border-color: rgba(201,106,69,0.12); border-top-color: #e8a87c; }
.log-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 18px; border-top: 1px solid var(--border-color); background: #faf6ee; }
.log-auto-scroll-label { display: flex; align-items: center; gap: 7px; color: var(--text-secondary); cursor: pointer; font-size: 11px; }
.log-auto-scroll-label input { accent-color: var(--accent); }
.log-count { color: var(--text-secondary); font-family: var(--font-mono); font-size: 10px; }
.log-footer-label { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.8px; }
.log-filter-body { border-bottom: 1px solid var(--border-color); background: linear-gradient(130deg, rgba(248,252,250,0.72), rgba(248,233,223,0.3)); }
.log-search-filters { min-width: 0; }
.lsf-hint { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; margin-bottom: 16px; padding: 11px 13px; border: 1px solid rgba(201,106,69,0.13); border-radius: var(--radius-sm); background: rgba(248,233,223,0.68); color: var(--text-secondary); font-size: 11px; line-height: 1.6; }
.lsf-hint-icon { display: grid; width: 24px; height: 24px; flex: 0 0 24px; place-items: center; border-radius: 7px; background: #fff; color: var(--accent); font-family: var(--font-mono); font-size: 10px; font-weight: 800; }
.lsf-active-badge { margin-left: auto; padding: 4px 8px; border-radius: 999px; background: var(--accent); color: #fff; font-size: 9px; font-weight: 700; white-space: nowrap; }
.lsf-active-badge-none { background: #e6ddcb; color: var(--text-secondary); }
.lsf-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 13px; }
.lsf-item { display: flex; min-width: 0; flex-direction: column; gap: 6px; }
.lsf-item label { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 10px; font-weight: 700; }
.lsf-optional { padding: 1px 5px; border-radius: 4px; background: #f3ede0; color: var(--text-tertiary); font-size: 8px; font-weight: 500; }
.lsf-input { width: 100%; min-width: 0; min-height: 40px; font-size: 12px; }
.lsf-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 4px; }
.lsf-action-note { margin-right: auto; color: var(--text-tertiary); font-size: 10px; }
.log-result-body { min-height: 280px; }
.log-search-init-hint, .log-search-empty-result { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; }
.lsih-icon, .lser-icon { display: grid; width: 46px; height: 46px; margin-bottom: 5px; place-items: center; border: 1px solid var(--border-color); border-radius: 14px; background: var(--accent-soft); color: var(--accent); font-family: var(--font-mono); font-size: 17px; font-weight: 750; }
.lsih-text, .lser-text { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.lsih-sub, .lser-hint { max-width: 520px; color: var(--text-tertiary); font-size: 11px; line-height: 1.65; }
.log-search-result { width: 100%; max-width: 100%; max-height: 560px; overflow: auto; overscroll-behavior: contain; border: 1px solid var(--border-color); border-radius: var(--radius); }
.lsr-table { width: 100%; min-width: 1080px; border-collapse: separate; border-spacing: 0; font-size: 11px; text-align: left; }
.lsr-table th { position: sticky; top: 0; z-index: 1; padding: 12px; border-bottom: 1px solid var(--border-color); background: #f6f1e7; color: var(--text-secondary); white-space: nowrap; font-size: 9px; font-weight: 800; letter-spacing: 0.5px; }
.lsr-table td { padding: 11px 12px; border-bottom: 1px solid #f6f1e7; color: var(--text-primary); white-space: nowrap; }
.lsr-table tbody tr:last-child td { border-bottom: 0; }
.lsr-table tbody tr:hover td { background: #faf6ee; }
.lsr-time, .lsr-clazz, .lsr-method, .lsr-duration, .lsr-ip { font-family: var(--font-mono); font-size: 10px; }
.lsr-time, .lsr-clazz, .lsr-ip { color: var(--text-secondary); }
.lsr-method { color: var(--accent-strong); font-weight: 700; }
.lsr-level, .lsr-status { display: inline-flex; padding: 3px 8px; border-radius: 999px; font-family: var(--font-mono); font-size: 9px; font-weight: 750; }
.lsr-level-info { background: var(--info-soft); color: var(--info); }
.lsr-level-warn { background: var(--warning-soft); color: var(--warning); }
.lsr-level-error { background: var(--danger-soft); color: var(--danger); }
.lsr-level-debug { background: #f6f1e7; color: var(--text-secondary); }
.lsr-status-ok { background: var(--success-soft); color: var(--success); }
.lsr-status-fail { background: var(--danger-soft); color: var(--danger); }
.lsr-duration { font-weight: 700; }
.lsr-duration-warn { color: var(--warning); }
.lsr-duration-slow { color: var(--danger); }
.redis-host-badge { max-width: 220px; overflow: hidden; padding: 4px 9px; border-radius: 999px; background: var(--accent-soft); color: var(--accent-strong); font-family: var(--font-mono); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.redis-server-info { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 24px; }
.redis-si-item { display: flex; min-width: 0; min-height: 105px; flex-direction: column; gap: 7px; padding: 14px; border: 1px solid #e6ddcb; border-radius: var(--radius); background: linear-gradient(135deg, var(--accent-soft), rgba(255,255,255,0.76)); }
.redis-si-label { color: var(--text-secondary); font-size: 10px; }
.redis-si-val { font-family: var(--font-mono); font-size: 14px; line-height: 1.45; font-weight: 750; overflow-wrap: anywhere; }
.redis-si-code { margin-top: auto; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.8px; }
.redis-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 2px 12px; }
.redis-section-heading span:first-child { font-size: 13px; font-weight: 800; }
.redis-section-heading span:last-child { color: var(--text-secondary); font-size: 10px; }
.redis-db-grid { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 10px; }
.redis-db-card { display: flex; min-width: 0; min-height: 112px; flex-direction: column; align-items: center; justify-content: center; padding: 13px 7px; border: 1px solid var(--border-color); border-radius: var(--radius); background: #fafaf9; cursor: pointer; transition: var(--transition); }
.redis-db-card:hover { border-color: var(--accent-light); background: #fff; box-shadow: var(--shadow-sm); transform: translateY(-2px); }
.redis-db-card-active { border-color: var(--accent); background: var(--accent-soft); box-shadow: 0 0 0 3px var(--accent-glow); }
.redis-db-empty { opacity: 0.5; }
.redis-db-empty:hover { opacity: 0.85; }
.redis-db-num { color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; font-weight: 750; }
.redis-db-keys { margin: 6px 0 3px; font-family: var(--font-mono); font-size: 24px; line-height: 1; font-weight: 700; }
.redis-db-keys-label { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.7px; }
.redis-key-viewer { margin-top: 20px; overflow: hidden; border: 1px solid var(--border-color); border-radius: var(--radius-lg); background: #fff; }
.rkv-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 17px; border-bottom: 1px solid var(--border-color); background: #faf6ee; }
.rkv-kicker { margin-bottom: 3px; font-size: 8px; letter-spacing: 1px; }
.rkv-header h4 { margin: 0; font-size: 13px; }
.rkv-loading { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 45px 20px; color: var(--text-secondary); font-size: 12px; }
.rkv-loading .state-spinner { width: 22px; height: 22px; margin: 0; }
.rkv-empty { padding: 45px 20px; color: var(--text-secondary); text-align: center; font-size: 12px; }
.rkv-table-wrap { max-width: 100%; max-height: 430px; overflow: auto; overscroll-behavior: contain; }
.rkv-table { width: 100%; min-width: 620px; border-collapse: separate; border-spacing: 0; font-size: 11px; text-align: left; }
.rkv-table th { position: sticky; top: 0; z-index: 1; padding: 11px 14px; border-bottom: 1px solid var(--border-color); background: #f6f1e7; color: var(--text-secondary); font-size: 9px; font-weight: 800; }
.rkv-table td { padding: 11px 14px; border-bottom: 1px solid #f6f1e7; }
.rkv-table tbody tr:last-child td { border-bottom: 0; }
.rkv-table tbody tr:hover td { background: #faf6ee; }
.rkv-key { max-width: 480px; overflow: hidden; font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.rkv-type { display: inline-flex; padding: 3px 8px; border-radius: 999px; font-family: var(--font-mono); font-size: 9px; font-weight: 750; }
.rkv-type-string { background: var(--success-soft); color: var(--success); }
.rkv-type-list { background: var(--info-soft); color: var(--info); }
.rkv-type-set { background: var(--warning-soft); color: var(--warning); }
.rkv-type-zset { background: #f3eee4; color: #8c643b; }
.rkv-type-hash { background: var(--accent-soft); color: var(--accent-strong); }
.rkv-ttl { font-family: var(--font-mono); font-weight: 650; }
.term-warning-badge { padding: 4px 8px; border: 1px solid rgba(197,122,22,0.15); border-radius: 999px; background: var(--warning-soft); color: var(--warning); font-family: var(--font-mono); font-size: 8px; font-weight: 800; letter-spacing: 0.6px; }
.term-output-area { width: 100%; max-width: 100%; min-height: 380px; max-height: 62vh; padding: 17px 18px; overflow: auto; overscroll-behavior: contain; background: radial-gradient(circle at 100% 0%, rgba(120,95,60,0.1), transparent 36%), #1b160c; color: #ddd0bb; font-family: var(--font-mono); font-size: 12px; line-height: 1.7; }
.term-placeholder { display: flex; min-height: 330px; flex-direction: column; align-items: center; justify-content: center; color: #7e776b; text-align: center; }
.term-placeholder-icon { display: grid; width: 62px; height: 52px; margin-bottom: 16px; place-items: center; border: 1px solid rgba(201,106,69,0.13); border-radius: 13px; background: rgba(201,106,69,0.04); color: #e8a87c; font-family: var(--font-mono); font-size: 17px; box-shadow: inset 0 1px 0 rgba(255,255,255,0.04); }
.term-placeholder-text { margin-bottom: 7px; color: #b8b2a8; font-size: 13px; }
.term-placeholder-hint { font-size: 10px; }
.term-output-content { max-width: 100%; }
.term-pre { max-width: 100%; margin: 0; color: inherit; font-family: inherit; font-size: inherit; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.term-executing { display: flex; align-items: center; gap: 9px; padding: 9px 0; color: #e8a87c; }
.term-spinner { width: 17px; height: 17px; margin: 0; }
.term-status-bar { display: flex; align-items: center; gap: 18px; padding: 9px 18px; border-top: 1px solid rgba(220,190,150,0.09); background: #241d12; font-family: var(--font-mono); font-size: 9px; }
.term-exit-code { font-weight: 750; }
.term-exit-ok { color: #bfad8f; }
.term-exit-err { color: #ff9b93; }
.term-elapsed { color: #8c8477; }
.term-input-area { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 9px; padding: 13px 18px; border-top: 1px solid rgba(220,190,150,0.09); background: #241d12; }
.term-prompt { color: #e8a87c; font-family: var(--font-mono); font-size: 15px; font-weight: 750; line-height: 38px; }
.term-input { width: 100%; min-width: 0; max-width: 100%; min-height: 38px; padding: 8px 11px; resize: vertical; border: 1px solid rgba(220,190,150,0.14); border-radius: 9px; outline: none; background: #1b160c; color: #e0d5c2; font-family: var(--font-mono); font-size: 11px; line-height: 1.6; transition: border-color var(--transition), box-shadow var(--transition); }
.term-input:focus { border-color: #ae966d; box-shadow: 0 0 0 3px rgba(190,160,105,0.1); }
.term-input:disabled { opacity: 0.45; }
.term-exec-btn { min-height: 38px; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; box-shadow: inset 0 1px 0 rgba(255,255,255,0.18); }
.term-exec-btn:hover:not(:disabled) { box-shadow: 0 6px 18px rgba(201,106,69,0.22); }
.term-history-bar { display: flex; align-items: flex-start; gap: 10px; padding: 12px 18px; border-top: 1px solid var(--border-color); background: #faf6ee; }
.term-history-label { flex: 0 0 auto; color: var(--text-secondary); font-size: 10px; line-height: 25px; }
.term-history-items { display: flex; min-width: 0; gap: 6px; flex-wrap: wrap; }
.term-history-item { max-width: 250px; overflow: hidden; padding: 5px 8px; border: 1px solid var(--border-color); border-radius: 7px; background: #fff; color: var(--text-secondary); cursor: pointer; font-family: var(--font-mono); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; transition: var(--transition); }
.term-history-item:hover { border-color: var(--accent-light); background: var(--accent-soft); color: var(--accent-strong); }
.log-viewer::-webkit-scrollbar-thumb, .term-output-area::-webkit-scrollbar-thumb { background-color: #423b2f; }
@media (max-width: 1100px) {
  .sys-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .redis-server-info { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .redis-db-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .lsf-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 700px) {
  .monitor-page { padding: 0 14px 28px; }
  .page-header { align-items: flex-start; gap: 14px; }
  .page-header h2 { font-size: 21px; }
  .header-signal { margin-top: 3px; }
  .monitor-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .card-header { align-items: flex-start; flex-direction: column; }
  .card-header-right { width: 100%; justify-content: flex-start; }
  .card-body { padding: 15px; }
  .log-card-body, .term-card-body { padding: 0; }
  .meter-grid { grid-template-columns: 1fr; }
  .lsf-row { grid-template-columns: 1fr; }
  .lsf-hint { align-items: flex-start; }
  .lsf-active-badge { margin-left: 0; }
  .lsf-actions { flex-wrap: wrap; }
  .lsf-action-note { width: 100%; margin: 0 0 3px; }
  .log-viewer, .term-output-area { min-height: 360px; max-height: 64vh; }
  .redis-server-info { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .term-history-item { max-width: 100%; }
}
@media (max-width: 480px) {
  .monitor-page { padding-inline: 10px; }
  .page-header { display: block; }
  .header-signal { margin-top: 13px; }
  .page-kicker { font-size: 9px; letter-spacing: 1.3px; }
  .monitor-tab { padding-inline: 7px; font-size: 11px; }
  .tab-icon { min-width: 25px; height: 25px; }
  .card-header { padding: 14px; }
  .card-header-left { gap: 8px; }
  .log-stream-state { width: 100%; margin-left: 42px; }
  .card-header-right .btn-sm-action, .card-header-right .btn-action { flex: 1; }
  .log-level-select { flex: 1; min-width: 110px; }
  .sys-grid { grid-template-columns: 1fr; }
  .sys-item { min-height: 105px; }
  .meter { padding: 14px; }
  .meter-caption { align-items: flex-start; flex-direction: column; }
  .log-console-bar, .term-console-bar { padding-inline: 13px; font-size: 7px; }
  .log-viewer, .term-output-area { min-height: 340px; padding: 13px; font-size: 10px; }
  .log-footer { padding-inline: 13px; }
  .lsf-actions .btn-action, .lsf-actions .btn-sm-action { flex: 1; }
  .redis-server-info { gap: 9px; }
  .redis-si-item { min-height: 100px; padding: 12px; }
  .redis-db-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .redis-section-heading span:last-child { display: none; }
  .redis-host-badge { max-width: 150px; }
  .term-warning-badge { margin-left: 42px; }
  .term-placeholder-hint { max-width: 250px; line-height: 1.6; }
  .term-input-area { grid-template-columns: auto minmax(0, 1fr); padding: 12px; }
  .term-exec-btn { grid-column: 1 / -1; width: 100%; }
  .term-history-bar { flex-direction: column; padding-inline: 13px; }
  .term-history-label { line-height: 1; }
  .term-history-items { width: 100%; }
  .term-history-item { max-width: 100%; }
}
</style>
