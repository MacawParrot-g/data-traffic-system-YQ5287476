<template>
  <div class="app-layout" v-if="loggedIn">
    <aside class="sidebar" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="sidebar-logo">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none"><path d="M4 16s4.4-7 12-7 12 7 12 7-4.4 7-12 7S4 16 4 16Z" stroke="currentColor" stroke-width="1.8"/><circle cx="16" cy="16" r="4" stroke="currentColor" stroke-width="1.8"/><circle cx="16" cy="16" r="1.5" fill="currentColor"/></svg>
          </span>
          <span class="brand-copy">
            <span class="brand-name">DataEye</span>
            <span class="brand-product">TDM CONSOLE</span>
          </span>
        </div>
        <button class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed" :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 7h14M5 12h14M5 17h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        </button>
      </div>

      <nav class="sidebar-nav">
        <span class="nav-eyebrow">WORKSPACE</span>
        <button
            v-for="tab in filteredTabs"
            :key="tab.key"
            class="nav-item"
            :class="{ active: mode === tab.key, 'nav-item-alert': tab.key === 'ncenter' && notifCount > 0 }"
            @click="switchMode(tab.key)"
        >
          <span class="nav-icon" :class="'nav-icon-' + tab.key" aria-hidden="true"></span>
          <span class="nav-label">{{ tab.label }}</span>
          <span class="nav-badge" v-if="tab.key === 'ncenter' && notifCount > 0">{{ notifCount }}</span>
        </button>
      </nav>

      <div class="sidebar-stats">
        <div class="stat-card">
          <div class="stat-card-topline">
            <span class="stat-label">今日入库</span>
            <span class="stat-live"><i></i>LIVE</span>
          </div>
          <div class="stat-value">{{ todayCount }} <span class="stat-unit">条记录</span></div>
          <div class="stat-track"><span></span></div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="sidebar-user">
          <div class="user-avatar">{{ displayName ? displayName.charAt(0) : 'U' }}</div>
          <div class="user-info">
            <div class="user-name">{{ displayName }}</div>
            <div class="user-role">{{ accType }}</div>
          </div>
          <span class="user-presence" title="当前在线"></span>
        </div>
        <div class="sidebar-actions">
          <button type="button" class="action-link" @click="showGuide = true">新人指南</button>
          <button type="button" class="action-link logout-link" @click="comfirmLogout()">{{ loggingOut ? '退出中...' : '退出登录' }}</button>
        </div>
      </div>
    </aside>

    <main class="main-content">
      <header class="main-header">
        <div class="header-left">
          <button class="mobile-menu-btn" @click="sidebarCollapsed = !sidebarCollapsed" aria-label="打开导航">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 7h14M5 12h14M5 17h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
          </button>
          <div class="header-heading">
            <span class="header-eyebrow">TEST DATA OPERATIONS</span>
            <h2 class="header-title">{{ currentTabLabel }}</h2>
          </div>
        </div>
        <div class="header-right">
          <span class="header-environment"><i></i>TEST ENVIRONMENT</span>
          <span class="header-badge" v-if="errorMsg">{{ errorMsg }}</span>
        </div>
      </header>

      <div class="content-area">
        <keep-alive>
          <component :is="currentComponent" @error="setError" @record-saved="fetchTodayCount" />
        </keep-alive>
      </div>
    </main>
  </div>

  <NewbieGuide v-if="showGuide" @close="showGuide = false" />

  <div class="alert-forced-overlay" v-if="forcedAlerts.length > 0">
    <div class="alert-forced-modal" v-for="(alert, idx) in forcedAlerts" :key="'fa-' + idx">
      <div class="alert-forced-icon" aria-hidden="true">NOTICE</div>
      <span class="alert-forced-kicker">IMPORTANT MESSAGE</span>
      <h2 class="alert-forced-title">{{ alert.title }}</h2>
      <p class="alert-forced-content">{{ alert.content || '（无附加内容）' }}</p>
      <button class="alert-forced-btn" @click="ackForcedAlert(idx)">我已了解</button>
    </div>
  </div>

  <div class="gn-overlay" v-if="globalNotifs.length > 0">
    <div class="gn-modal" v-for="(notif, idx) in globalNotifs" :key="'gn-' + notif.id + '-' + idx" :class="getNotifTypeClass(notif.type)">
      <div class="gn-header">
        <span class="gn-type-label" :class="getNotifTypeClass(notif.type)">{{ getNotifTypeLabel(notif.type) }}</span>
        <button class="gn-close" @click="dismissGlobalNotif(idx)" aria-label="关闭通知">×</button>
      </div>
      <h3 class="gn-title">{{ notif.title }}</h3>
      <p class="gn-content" v-if="notif.content">{{ notif.content }}</p>
      <div class="gn-footer">
        <span class="gn-sender">发送者：{{ notif.sender }}</span>
        <span class="gn-time">{{ notif.createdAt }}</span>
      </div>
      <button class="gn-dismiss" @click="dismissGlobalNotif(idx)">关闭通知</button>
    </div>
  </div>

  <div class="login-overlay" v-if="!loggedIn">
    <div class="login-ambient login-ambient-one"></div>
    <div class="login-ambient login-ambient-two"></div>
    <div class="login-grid" aria-hidden="true"></div>
    <div class="login-card">
      <section class="login-brand">
        <div class="login-brand-lockup">
          <span class="login-brand-mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none"><path d="M4 16s4.4-7 12-7 12 7 12 7-4.4 7-12 7S4 16 4 16Z" stroke="currentColor" stroke-width="1.8"/><circle cx="16" cy="16" r="4" stroke="currentColor" stroke-width="1.8"/><circle cx="16" cy="16" r="1.5" fill="currentColor"/></svg>
          </span>
          <span>DataEye / TDM</span>
        </div>
        <div class="login-brand-copy">
          <span class="login-brand-kicker">DATA, MADE TRACEABLE</span>
          <h2>让每一条测试数据<br>都有迹可循。</h2>
          <p>面向测试与研发的一站式数据工作台，让任务、事件、归因与入库保持清晰同步。</p>
        </div>
        <div class="login-brand-meta">
          <div><span>01</span><p>任务编排<br><small>Task orchestration</small></p></div>
          <div><span>02</span><p>事件洞察<br><small>Event intelligence</small></p></div>
          <div><span>03</span><p>数据治理<br><small>Data governance</small></p></div>
        </div>
      </section>
      <section class="login-panel">
        <img src="/icon.png" alt="公司 Logo" class="login-logo" />
        <div class="login-heading">
          <span class="login-kicker">SECURE ACCESS</span>
          <h1>广告事件归因 TDM 系统</h1>
          <div class="login-subtitle">测试服 · 内部工作台</div>
        </div>
        <div class="login-form">
          <div class="login-field">
            <label>账号</label>
            <input v-model="loginUid" placeholder="请输入账号" @keyup.enter="doLogin" class="login-input" autocomplete="username" />
          </div>
          <div class="login-field">
            <label>密码</label>
            <input v-model="loginPwd" type="password" placeholder="请输入密码" @keyup.enter="doLogin" class="login-input" autocomplete="current-password" />
          </div>
          <button class="login-btn" @click="doLogin" :disabled="!loginUid.trim() || !loginPwd.trim() || loginLoading">
            <span>{{ loginLoading ? '登录中...' : '进入系统' }}</span>
            <svg v-if="!loginLoading" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M4 10h11m-4-4 4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
          <div v-if="loginError" class="login-error">{{ loginError }}</div>
        </div>
        <div class="login-footer">由深圳市慧动创想科技有限公司 · 蓝黄金刚鹦鹉开发</div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { authLogin, authLogout, authStatus, fetchCountByRecorder, ackAlert, pollGlobalNotifications,fetchMyNotifications } from './api/index.js'
import AutoMode from './components/AutoMode.vue'
import ManualMode from './components/ManualMode.vue'
import DataVisitable from "./components/DataVisitable.vue";
import QRCodeBuilderByMan from "./components/QRCodeBuilderByMan.vue";
import NewbieGuide from "./components/NewbieGuide.vue";
import AdminPanel from "./components/AdminPanel.vue";
import DevMode from "./components/DevMode.vue";
import MonitorPanel from "./components/MonitorPanel.vue";
import GradeManage from "./components/GradeManage.vue";
import AuditLogPanel from "./components/AuditLogPanel.vue";
import ScheduledTaskPanel from "./components/ScheduledTaskPanel.vue";
import NotificationCenter from "./components/NotificationCenter.vue";
import NotificationManager from "./components/NotificationManager.vue";
import MQPanel from "./components/MQPanel.vue";
import DataSource from "./components/DataSource.vue";
import PyScriptPanel from "./components/PyScriptPanel.vue";
import TemplateManage from "./components/TemplateManage.vue";
import DeepSeekChat from "./components/DeepSeekChat.vue";
const notifCount = ref(0)
let bellPollTimer = null
const mode = ref('auto')
const errorMsg = ref('')
const loggedIn = ref(false)
const loginUid = ref('')
const loginPwd = ref('')
const lastPollTime = ref(Date.now())
let pollTimer = null
const loginLoading = ref(false)
const loginError = ref('')
const loggingOut = ref(false)
const forcedAlerts = ref([])
const showGuide = ref(false)
const accType = ref('USER')
const globalNotifs = ref([])
const displayName = ref('')
const sidebarCollapsed = ref(false)
const todayCount = ref(0)
const tabIcons = {
  auto: 'A',
  manual: 'M',
  develop: '🛠',
  qrcode: 'Q',
  data: 'D',
  mon: 'M',
  DS: 'C',
  grade: 'G',
  remark: 'R',
  audit: 'U',
  task: 'T',
  ncenter: 'H',
  notify: 'N',
  mq: 'L',
  source: 'S',
  py: 'P'
}

const componentMap = computed(() => {
  const map = {
    auto: AutoMode,
    manual: ManualMode,
    qrcode: QRCodeBuilderByMan,
    ncenter: NotificationCenter,
    source: DataSource,
  }
  if (accType.value === 'ADMIN' || accType.value === 'DEVELOPER') {
    map.data = AdminPanel
    map.audit = AuditLogPanel
    map.task = ScheduledTaskPanel
    map.notify = NotificationManager
  }
  if (accType.value === 'DEVELOPER') {
    map.develop = DevMode
    map.mon = MonitorPanel
    map.mq = MQPanel
    map.py = PyScriptPanel
    map.DS = DeepSeekChat
  }
  if (accType.value === 'USER' || accType.value === 'ADMIN' || accType.value === 'DEVELOPER') {
    map.grade = GradeManage
    map.remark = TemplateManage
  }
  if (accType.value === 'USER') {
    map.data = DataVisitable
  }
  return map
})


const currentComponent = computed(() => componentMap.value[mode.value])


function dismissGlobalNotif(index) {
  globalNotifs.value.splice(index, 1)
}

const tabs = [
  { key: 'auto', label: '自动模式' },
  { key: 'manual', label: '手动模式' },
  { key: 'develop', label: '开发者模式' },
  { key: 'qrcode', label: '二维码生成' },
  { key: 'data', label: '数据看板&导出' },
  { key: 'mon', label: '监控面板' },
  { key: 'DS', label: 'DeepSeek助手' },
  { key: 'grade', label: '评级管理' },
  { key: 'remark', label: '备注管理' },
  { key: 'audit', label: '审计日志' },
  { key: 'task', label: '定时任务' },
  { key: 'ncenter', label: '通知中心' },
  { key: 'notify', label: '通知管理' },
  { key: 'mq', label: 'MQ监控' },
  { key: 'source', label: '数据源切换' },
  { key: 'py', label: 'Python脚本' },
]


const filteredTabs = computed(() => {
  let result = [...tabs]
  if (accType.value !== 'DEVELOPER') {
    result = result.filter(t => t.key !== 'develop' && t.key !== 'mon' && t.key !== 'mq' && t.key !== 'DS')
  }
  if (accType.value === 'USER') {
    result = result.filter(t => t.key !== 'audit' && t.key !== 'task' && t.key !== 'notify')
  }
  if (accType.value === 'ADMIN' || accType.value === 'DEVELOPER') {
    result = result.map(t => {
      if (t.key === 'data') return { ...t, label: '管理员看板' }
      return t
    })
  }
  return result
})


const currentTabLabel = computed(() => {
  const found = filteredTabs.value.find(t => t.key === mode.value)
  return found ? found.label : ''
})

function startBellPolling() {
  stopBellPolling()
  pollBell()
  bellPollTimer = setInterval(pollBell, 5000)
}

function stopBellPolling() {
  if (bellPollTimer) {
    clearInterval(bellPollTimer)
    bellPollTimer = null
  }
}

async function pollBell() {
  try {
    const json = await fetchMyNotifications(1, 50)
    if (json.success) {
      const list = json.data || []
      notifCount.value = list.filter(n => {
        if (!n.expireAt) return true
        return new Date(n.expireAt) > new Date()
      }).length
    }
  } catch (e) { /* silent */ }
}

onMounted(async () => {
  try {
    const json = await authStatus()
    if (json.success && json.data && json.data.loggedIn) {
      loggedIn.value = true
      accType.value = json.data.data.type || 'USER'
      displayName.value = json.data.data.name || ''
      localStorage.setItem('userName', json.data.data.name || '')
      localStorage.setItem('accType', json.data.data.type || 'USER')
      startBellPolling()
    }
  } catch (e) {
    console.warn('检查登录状态失败:', e)
  }
})

onMounted(() => {
  fetchTodayCount()
})

watch(sidebarCollapsed, (newVal) => {
  if (!newVal) {
    fetchTodayCount()
  }
})

async function doLogin() {
  if (!loginUid.value.trim() || !loginPwd.value.trim()) return
  loginLoading.value = true
  loginError.value = ''
  try {
    const json = await authLogin(loginUid.value.trim(), loginPwd.value.trim())
    if (json.success) {
      const data = json.data
      localStorage.setItem('userName', data.name)
      localStorage.setItem('accType', data.type)
      accType.value = data.type
      displayName.value = data.name
      loggedIn.value = true
      mode.value = 'auto'
      startBellPolling()
    } else {
      loginError.value = json.message || '登录失败'
    }
  } catch (e) {
    loginError.value = '登录请求失败：' + e.message
  } finally {
    loginLoading.value = false
  }
}

function comfirmLogout() {
  let logout = confirm("确定要退出登录吗")
  if (logout) {
    doLogout()
  } else {
    return
  }
}

async function doLogout() {
  loggingOut.value = true
  try {
    await authLogout()
  } catch (e) { /* silent */ }
  finally {
    localStorage.removeItem('userName')
    localStorage.removeItem('accType')
    loggedIn.value = false
    accType.value = 'USER'
    displayName.value = ''
    loginUid.value = ''
    loginPwd.value = ''
    loginError.value = ''
    mode.value = 'auto'
    loggingOut.value = false
    notifCount.value = 0
    stopBellPolling()
  }
}

function setError(msg) { errorMsg.value = msg }

function switchMode(newMode) {
  mode.value = newMode
  errorMsg.value = ''
}

function getTodayStrLocal() {
  const d = new Date()
  return d.getFullYear() + '/' + (d.getMonth() + 1) + '/' + d.getDate()
}

async function fetchTodayCount() {
  try {
    const params = new URLSearchParams({
      recorder: localStorage.getItem('userName') || '',
      recordData: getTodayStrLocal()
    })
    const r = await fetch('/api/record/querybyname?' + params.toString())
    const json = await r.json()
    if (json.success) {
      todayCount.value = json.data || 0
    }
  } catch (e) { /* silent */ }
}

</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Noto+Sans+SC:wght@400;500;600;700&display=swap');

* { margin: 0; padding: 0; box-sizing: border-box; }

:root {
  --sidebar-bg: #c15b34;
  --sidebar-hover: rgba(255, 255, 255, 0.12);
  --sidebar-active: rgba(255, 255, 255, 0.18);
  --sidebar-text: rgba(255, 247, 240, 0.90);
  --sidebar-text-active: #ffffff;
  --sidebar-width: 248px;
  --sidebar-collapsed-width: 76px;
  --accent: #c96a45;
  --accent-strong: #a85638;
  --accent-light: #e0875f;
  --accent-bright: #eda77f;
  --accent-glow: rgba(201, 106, 69, 0.13);
  --accent-soft: #f8e9df;
  --cyan: #4e7f93;
  --bg-primary: #faf9f5;
  --bg-elevated: #fdfcf8;
  --bg-card: #fffefb;
  --glass: rgba(255, 255, 255, 0.72);
  --glass-strong: rgba(255, 255, 255, 0.88);
  --text-primary: #2d2a24;
  --text-secondary: #6c665c;
  --text-tertiary: #9a9388;
  --border-color: #e6e1d5;
  --border-strong: #d8cfbd;
  --success: #4a7c59;
  --success-soft: #e9f1e7;
  --warning: #b0791e;
  --warning-soft: #f8efdb;
  --danger: #c0564a;
  --danger-soft: #f9ece7;
  --info: #4f7fa3;
  --info-soft: #eaf2f8;
  --radius-xs: 8px;
  --radius-sm: 10px;
  --radius: 14px;
  --radius-lg: 20px;
  --radius-xl: 28px;
  --shadow-sm: 0 1px 2px rgba(70, 55, 35, 0.03), 0 5px 18px rgba(70, 55, 35, 0.04);
  --shadow-md: 0 14px 40px rgba(70, 55, 35, 0.08), 0 2px 8px rgba(70, 55, 35, 0.04);
  --shadow-lg: 0 28px 80px rgba(45, 35, 22, 0.16), 0 4px 18px rgba(45, 35, 22, 0.07);
  --glass-shadow: inset 0 1px 0 rgba(255,255,255,0.82), inset 0 -1px 0 rgba(201,106,69,0.05), 0 12px 36px rgba(70,55,35,0.08);
  --transition: 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
  --font-sans: 'Manrope', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  --font-mono: 'Cascadia Code', 'SFMono-Regular', Consolas, monospace;
}

html { min-width: 320px; background: var(--bg-primary); }
body {
  font-family: var(--font-sans);
  background: var(--bg-primary);
  min-height: 100vh;
  color: var(--text-primary);
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
button, input, textarea, select { font: inherit; }
button { -webkit-tap-highlight-color: transparent; }
button:focus-visible, a:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible {
  outline: 2px solid var(--accent-light);
  outline-offset: 3px;
}

::selection { color: #2d2a24; background: #f3ddcb; }
::-webkit-scrollbar { width: 8px; height: 8px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #d8d0bf; border: 2px solid transparent; border-radius: 10px; background-clip: padding-box; }
::-webkit-scrollbar-thumb:hover { background: #bcae97; border: 2px solid transparent; background-clip: padding-box; }
/* ... existing code ... */
.app-layout { display: flex; min-height: 100vh; }

.sidebar {
  width: var(--sidebar-width);
  background:
      radial-gradient(circle at 18% 0%, rgba(217, 150, 95, 0.12), transparent 28%),
      linear-gradient(165deg, #f2efe6 0%, var(--sidebar-bg) 54%, #e8e2d4 100%);
  border-right: 1px solid rgba(90, 75, 55, 0.10);
  display: flex;
  flex-direction: column;
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 100;
  transition: width 240ms cubic-bezier(0.2, 0.8, 0.2, 1), transform 240ms cubic-bezier(0.2, 0.8, 0.2, 1);
  overflow: hidden;
}
.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.16;
  background-image: linear-gradient(rgba(90,75,55,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(90,75,55,0.03) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: linear-gradient(to bottom, black, transparent 65%);
}
.sidebar > * { position: relative; z-index: 1; }
.sidebar-collapsed { width: var(--sidebar-collapsed-width); }
.sidebar-collapsed .sidebar-logo,
.sidebar-collapsed .nav-eyebrow,
.sidebar-collapsed .nav-label,
.sidebar-collapsed .sidebar-stats,
.sidebar-collapsed .sidebar-footer { display: none; }
.sidebar-header {
  min-height: 86px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 18px 16px 18px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.065);
}
.sidebar-collapsed .sidebar-header { justify-content: center; padding-inline: 12px; }
.sidebar-logo { display: flex; align-items: center; gap: 11px; min-width: 0; }
  .brand-mark {
    width: 38px;
    height: 38px;
    flex: 0 0 38px;
    display: grid;
    place-items: center;
    color: var(--accent-strong);
    border: 1px solid rgba(168, 86, 56, 0.22);
    border-radius: 12px;
    background: linear-gradient(145deg, rgba(255,255,255,0.75), rgba(255,255,255,0.3));
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.8), 0 8px 20px rgba(70,55,35,0.10);
  }
.brand-mark svg { width: 24px; height: 24px; }
.brand-copy { display: flex; flex-direction: column; line-height: 1; }
  .brand-name { color: #2d2a24; font-size: 17px; font-weight: 800; letter-spacing: -0.45px; }
  .brand-product { color: #8a8272; font-size: 8px; font-weight: 700; letter-spacing: 1.8px; margin-top: 7px; }
.sidebar-toggle, .mobile-menu-btn {
  border: 1px solid transparent;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: var(--transition);
}
  .sidebar-toggle {
    width: 32px;
    height: 32px;
    flex: 0 0 32px;
    color: #7d7666;
    background: rgba(90,75,55,0.04);
    border-color: rgba(90,75,55,0.08);
    border-radius: 10px;
  }
.sidebar-toggle svg, .mobile-menu-btn svg { width: 19px; height: 19px; }
.sidebar-toggle:hover { color: #e9faf6; background: rgba(255,255,255,0.09); border-color: rgba(255,255,255,0.12); }

.sidebar-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 22px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
  .sidebar-nav::-webkit-scrollbar-thumb { background-color: rgba(90,75,55,0.18); }
.nav-eyebrow {
  padding: 0 12px 10px;
  color: #948b78;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.8px;
}
.nav-item {
  position: relative;
  width: 100%;
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--sidebar-text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: var(--transition);
  text-align: left;
  white-space: nowrap;
}
.nav-item::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  opacity: 0;
  background: linear-gradient(115deg, rgba(255,255,255,0.1), transparent 44%);
  transition: opacity var(--transition);
}
.nav-item:hover { background: var(--sidebar-hover); color: #dff5f0; transform: translateX(2px); }
.nav-item.active {
  background: linear-gradient(110deg, rgba(88, 196, 176, 0.17), rgba(88, 196, 176, 0.07));
  color: var(--sidebar-text-active);
  border-color: rgba(136, 225, 207, 0.14);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.07), 0 8px 24px rgba(0,0,0,0.12);
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -13px;
  width: 3px;
  height: 22px;
  border-radius: 0 4px 4px 0;
  background: var(--accent-bright);
  box-shadow: 0 0 14px rgba(128,229,210,0.5);
}
.nav-item.active::after { opacity: 1; }
.sidebar-collapsed .nav-item { justify-content: center; padding-inline: 8px; }
.sidebar-collapsed .nav-item:hover { transform: none; }
  .nav-icon {
    position: relative;
    width: 28px;
    height: 28px;
    flex: 0 0 28px;
    display: grid;
    place-items: center;
    border-radius: 8px;
    color: #8a8272;
    background: rgba(255,255,255,0.55);
    border: 1px solid rgba(90,75,55,0.07);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: -0.2px;
  transition: var(--transition);
}
  .nav-item.active .nav-icon { color: var(--accent-strong); background: rgba(201,106,69,0.12); border-color: rgba(201,106,69,0.18); }
.nav-icon-auto::before { content: 'AU'; }
.nav-icon-manual::before { content: 'MN'; }
.nav-icon-develop::before { content: 'DV'; }
.nav-icon-qrcode::before { content: 'QR'; }
.nav-icon-data::before { content: 'DB'; }
.nav-icon-mon::before { content: 'MO'; }
.nav-icon-DS::before { content: 'AI'; }
.nav-icon-grade::before { content: 'GR'; }
.nav-icon-remark::before { content: 'RM'; }
.nav-icon-audit::before { content: 'AD'; }
.nav-icon-task::before { content: 'TM'; }
.nav-icon-ncenter::before { content: 'NT'; }
.nav-icon-notify::before { content: 'NS'; }
.nav-icon-mq::before { content: 'MQ'; }
.nav-icon-source::before { content: 'DS'; }
.nav-icon-py::before { content: 'PY'; }
.nav-label { overflow: hidden; text-overflow: ellipsis; }
.nav-badge {
  background: #c0564a;
  color: #fff;
  font-size: 9px;
  font-weight: 800;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  margin-left: auto;
  line-height: 1;
  box-shadow: 0 0 0 3px rgba(192,86,74,0.1);
}
  .nav-item-alert { color: #b3554a; }
  .nav-item-alert.active { color: #8f3f35; border-color: rgba(192,86,74,0.25); background: rgba(192,86,74,0.10); }
  .nav-item-alert.active::before { background: #c0564a; box-shadow: 0 0 14px rgba(192,86,74,0.3); }

  .sidebar-stats { padding: 10px 14px 16px; }
  .stat-card {
    padding: 15px 16px 14px;
    border: 1px solid rgba(90, 75, 55, 0.10);
    border-radius: 15px;
    background: linear-gradient(135deg, rgba(255,255,255,0.8), rgba(255,255,255,0.35));
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.7);
  }
.stat-card-topline { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
  .stat-label { font-size: 11px; color: #8a8272; font-weight: 500; }
  .stat-live { display: inline-flex; align-items: center; gap: 4px; color: #4a7c59; font-size: 8px; font-weight: 800; letter-spacing: 1px; }
.stat-live i, .header-environment i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; box-shadow: 0 0 8px currentColor; }
  .stat-value { font-size: 26px; font-weight: 700; color: #2d2a24; line-height: 1.2; margin-top: 8px; letter-spacing: -0.8px; }
  .stat-unit { font-size: 10px; font-weight: 500; color: #9a9388; margin-left: 4px; letter-spacing: 0; }
  .stat-track { height: 2px; margin-top: 13px; background: rgba(90,75,55,0.10); overflow: hidden; border-radius: 2px; }
  .stat-track span { display: block; width: 62%; height: 100%; background: linear-gradient(90deg, #c96a45, #eda77f); box-shadow: 0 0 8px #d97757; }


  .sidebar-footer { padding: 16px 18px 18px; border-top: 1px solid rgba(90,75,55,0.10); background: rgba(255,255,255,0.35); }
  /* ... existing code ... */
.sidebar-user { display: flex; align-items: center; gap: 10px; margin-bottom: 15px; }
.user-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 11px;
  background: linear-gradient(145deg, #c96a45, #a85638);
  border: 1px solid rgba(255,255,255,0.6);
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 12px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.35);
}
  .user-info { overflow: hidden; flex: 1; }
  .user-name { font-size: 12px; font-weight: 600; color: #2d2a24; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .user-role { font-size: 8px; color: #948b78; letter-spacing: 1px; margin-top: 3px; }
  .user-presence { width: 7px; height: 7px; border-radius: 50%; background: #4a7c59; box-shadow: 0 0 0 3px rgba(74,124,89,0.12); }
  .sidebar-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .action-link { padding: 0; border: 0; background: none; font-size: 10px; color: #8a8272; cursor: pointer; transition: color var(--transition); }
  .action-link:hover { color: var(--accent-strong); }
  .logout-link:hover { color: #b3554a; }

.main-content {
  flex: 1;
  min-width: 0;
  margin-left: var(--sidebar-width);
  transition: margin-left 240ms cubic-bezier(0.2, 0.8, 0.2, 1);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.sidebar-collapsed ~ .main-content, .sidebar-collapsed + .main-content { margin-left: var(--sidebar-collapsed-width); }
  .main-header {
    min-height: 86px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    padding: 15px 32px;
    background: rgba(251, 250, 245, 0.8);
    backdrop-filter: blur(22px) saturate(150%);
    -webkit-backdrop-filter: blur(22px) saturate(150%);
    border-bottom: 1px solid rgba(214, 204, 188, 0.75);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.8);
  position: sticky;
  top: 0;
  z-index: 50;
}
.header-left, .header-right { display: flex; align-items: center; gap: 14px; min-width: 0; }
.header-heading { display: flex; flex-direction: column; gap: 3px; }
  .header-eyebrow { color: #9a9388; font-size: 8px; font-weight: 800; letter-spacing: 1.7px; }
.header-title { font-size: 19px; line-height: 1.3; font-weight: 700; color: var(--text-primary); letter-spacing: -0.35px; }
.header-environment {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid #e0d9c9;
  border-radius: 8px;
  background: rgba(255,255,255,0.56);
  color: #7d7666;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 1px;
  white-space: nowrap;
}
  .header-environment i { color: #4a7c59; animation: status-pulse 2.4s ease-in-out infinite; }
  .header-badge { max-width: 460px; font-size: 11px; line-height: 1.5; color: #9e4438; background: var(--danger-soft); border: 1px solid #ecd2cb; padding: 7px 12px; border-radius: 9px; font-weight: 500; }
.mobile-menu-btn { display: none; width: 36px; height: 36px; color: var(--text-primary); background: var(--glass); border-color: var(--border-color); border-radius: 10px; }
.content-area {
  flex: 1;
  min-width: 0;
  padding: 28px 32px 40px;
  background:
    radial-gradient(circle at 100% 0%, rgba(96, 183, 168, 0.07), transparent 25%),
    linear-gradient(180deg, rgba(255,255,255,0.25), transparent 240px);
}

.login-overlay {
  position: relative;
  isolation: isolate;
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 36px;
  overflow: hidden;
  background: #071416;
}
.login-grid {
  position: absolute;
  inset: 0;
  z-index: -2;
  opacity: 0.28;
  background-image: linear-gradient(rgba(138,210,195,0.055) 1px, transparent 1px), linear-gradient(90deg, rgba(138,210,195,0.055) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at 45% 50%, black, transparent 72%);
}
.login-ambient { position: absolute; z-index: -1; border-radius: 50%; filter: blur(10px); pointer-events: none; }
.login-ambient-one { width: 620px; height: 620px; top: -300px; left: -180px; background: radial-gradient(circle, rgba(39,157,137,0.24), transparent 68%); }
.login-ambient-two { width: 560px; height: 560px; right: -220px; bottom: -280px; background: radial-gradient(circle, rgba(39,125,157,0.2), transparent 68%); }
.login-card {
  width: min(100%, 980px);
  min-height: 600px;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  overflow: hidden;
  border: 1px solid rgba(196, 236, 227, 0.16);
  border-radius: 28px;
  background: rgba(14, 35, 35, 0.68);
  backdrop-filter: blur(28px) saturate(130%);
  -webkit-backdrop-filter: blur(28px) saturate(130%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.1), 0 40px 120px rgba(0,0,0,0.42);
  animation: login-enter 650ms cubic-bezier(0.16, 1, 0.3, 1) both;
}
.login-brand { position: relative; display: flex; flex-direction: column; padding: 42px 44px 38px; color: #effbf8; overflow: hidden; }
.login-brand::after {
  content: '';
  position: absolute;
  width: 360px;
  height: 360px;
  right: -180px;
  top: 100px;
  border: 1px solid rgba(130,222,201,0.1);
  border-radius: 50%;
  box-shadow: 0 0 0 60px rgba(130,222,201,0.025), 0 0 0 120px rgba(130,222,201,0.018);
  pointer-events: none;
}
.login-brand-lockup { display: flex; align-items: center; gap: 11px; color: #c7e8e0; font-size: 12px; font-weight: 700; letter-spacing: 0.2px; }
.login-brand-mark { width: 36px; height: 36px; display: grid; place-items: center; color: #86e3d0; }
.login-brand-mark svg { width: 32px; height: 32px; }
.login-brand-copy { margin: auto 0; padding: 56px 0; position: relative; z-index: 1; }
.login-brand-kicker, .login-kicker { display: block; font-size: 9px; font-weight: 800; letter-spacing: 2.2px; }
.login-brand-kicker { color: #6ea89d; margin-bottom: 20px; }
.login-brand-copy h2 { font-size: clamp(32px, 3.2vw, 44px); line-height: 1.3; letter-spacing: -1.5px; font-weight: 600; }
.login-brand-copy p { max-width: 360px; color: #88a7a2; font-size: 13px; line-height: 1.9; margin-top: 22px; }
.login-brand-meta { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; position: relative; z-index: 1; }
.login-brand-meta > div { padding-top: 14px; border-top: 1px solid rgba(178,224,214,0.12); }
.login-brand-meta span { color: #4f7c74; font-size: 9px; font-weight: 700; }
.login-brand-meta p { color: #b4d1ca; font-size: 10px; line-height: 1.6; margin-top: 6px; }
.login-brand-meta small { color: #4f716c; font-size: 7px; white-space: nowrap; }
.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 38px 44px 30px;
  color: var(--text-primary);
  background: rgba(249, 252, 251, 0.94);
  border-left: 1px solid rgba(255,255,255,0.5);
  box-shadow: inset 1px 0 0 rgba(255,255,255,0.42);
}
.login-logo { width: 72px; height: 42px; object-fit: contain; object-position: left center; margin-bottom: 28px; }
.login-heading { margin-bottom: 28px; }
.login-kicker { color: var(--accent); margin-bottom: 10px; }
.login-card h1 { font-size: 20px; line-height: 1.45; color: var(--text-primary); font-weight: 700; letter-spacing: -0.35px; }
.login-subtitle { font-size: 11px; color: var(--text-secondary); margin-top: 7px; }
.login-form { display: flex; flex-direction: column; gap: 17px; }
.login-field label { display: block; font-size: 10px; font-weight: 700; color: #58716e; margin-bottom: 7px; letter-spacing: 0.5px; }
.login-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  font-size: 13px;
  color: var(--text-primary);
  border: 1px solid #d6e3df;
  border-radius: 11px;
  outline: none;
  transition: var(--transition);
  background: rgba(255,255,255,0.72);
  box-shadow: inset 0 1px 2px rgba(14,55,49,0.025);
}
.login-input::placeholder { color: #a2b3af; }
.login-input:hover { border-color: #bdd3cd; }
.login-input:focus { border-color: var(--accent-light); background: #fff; box-shadow: 0 0 0 4px var(--accent-glow), inset 0 1px 0 #fff; }
.login-btn {
  width: 100%;
  height: 47px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: linear-gradient(120deg, #0c8075, #0f9687);
  color: #fff;
  border: 1px solid rgba(4, 99, 89, 0.5);
  padding: 0 18px;
  font-size: 13px;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 600;
  transition: var(--transition);
  margin-top: 4px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.22), 0 8px 20px rgba(15,143,131,0.2);
}
.login-btn svg { width: 18px; height: 18px; transition: transform var(--transition); }
.login-btn:hover:not(:disabled) { transform: translateY(-1px); background: linear-gradient(120deg, #08756b, #0b887b); box-shadow: inset 0 1px 0 rgba(255,255,255,0.24), 0 12px 28px rgba(15,143,131,0.27); }
.login-btn:hover:not(:disabled) svg { transform: translateX(3px); }
.login-btn:active:not(:disabled) { transform: translateY(0); }
.login-btn:disabled { opacity: 0.45; cursor: not-allowed; box-shadow: none; }
.login-error { color: #a84545; font-size: 11px; line-height: 1.6; background: var(--danger-soft); border: 1px solid #f1d5d2; padding: 9px 12px; border-radius: 9px; }
.login-footer { font-size: 8px; line-height: 1.6; color: #94a7a2; margin-top: 28px; text-align: center; }

.gn-overlay, .alert-forced-overlay, .modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(4, 18, 18, 0.54);
  backdrop-filter: blur(12px) saturate(120%);
  -webkit-backdrop-filter: blur(12px) saturate(120%);
}
.gn-overlay { flex-direction: column; gap: 16px; overflow-y: auto; }
.gn-modal, .alert-forced-modal, .modal-box {
  position: relative;
  width: min(100%, 440px);
  padding: 28px;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,0.78);
  border-radius: 22px;
  background: linear-gradient(145deg, rgba(255,255,255,0.94), rgba(244,250,248,0.86));
  backdrop-filter: blur(28px) saturate(150%);
  -webkit-backdrop-filter: blur(28px) saturate(150%);
  box-shadow: var(--glass-shadow), var(--shadow-lg);
  animation: modal-enter 260ms cubic-bezier(0.16, 1, 0.3, 1) both;
}
.gn-modal::before, .alert-forced-modal::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent-light), transparent);
}
.gn-modal.gn-warn::before { background: linear-gradient(90deg, transparent, #dda047, transparent); }
.gn-modal.gn-emergency::before { background: linear-gradient(90deg, transparent, #df6969, transparent); }
.gn-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px; }
.gn-type-label { font-size: 9px; font-weight: 800; padding: 5px 9px; border-radius: 7px; letter-spacing: 0.8px; }
.gn-type-label.gn-info { background: var(--accent-soft); color: var(--accent-strong); }
.gn-type-label.gn-warn { background: var(--warning-soft); color: #9a641c; }
.gn-type-label.gn-emergency { background: var(--danger-soft); color: #b44444; }
.gn-close { width: 30px; height: 30px; display: grid; place-items: center; border: 1px solid var(--border-color); border-radius: 9px; background: rgba(255,255,255,0.5); color: var(--text-secondary); cursor: pointer; font-size: 18px; line-height: 1; transition: var(--transition); }
.gn-close:hover { color: var(--text-primary); background: #fff; transform: rotate(4deg); }
.gn-title { font-size: 18px; line-height: 1.5; font-weight: 700; margin-bottom: 8px; }
.gn-content { font-size: 13px; color: var(--text-secondary); line-height: 1.75; margin-bottom: 16px; white-space: pre-wrap; }
.gn-footer { display: flex; justify-content: space-between; gap: 16px; font-size: 10px; color: var(--text-tertiary); margin-bottom: 18px; padding-top: 14px; border-top: 1px solid var(--border-color); }
.gn-dismiss, .alert-forced-btn { width: 100%; min-height: 40px; border: 1px solid var(--border-color); border-radius: 11px; background: rgba(255,255,255,0.62); color: var(--text-primary); font-size: 12px; font-weight: 600; cursor: pointer; transition: var(--transition); }
.gn-dismiss:hover, .alert-forced-btn:hover { border-color: #b5d6ce; background: var(--accent-soft); color: var(--accent-strong); }
.alert-forced-overlay { overflow-y: auto; }
.alert-forced-modal { max-height: calc(100vh - 48px); max-height: calc(100dvh - 48px); overflow-y: auto; text-align: center; }
.alert-forced-icon { width: 58px; height: 58px; display: grid; place-items: center; margin: 0 auto 16px; border-radius: 18px; border: 1px solid #cde7df; background: linear-gradient(145deg, #f3fcf8, #ddf2eb); color: var(--accent); font-size: 8px; font-weight: 800; letter-spacing: 1px; box-shadow: inset 0 1px 0 #fff; }
.alert-forced-kicker { color: var(--accent); font-size: 8px; font-weight: 800; letter-spacing: 1.8px; }
.alert-forced-title { margin-top: 8px; font-size: 20px; line-height: 1.5; }
.alert-forced-content { color: var(--text-secondary); font-size: 13px; line-height: 1.8; margin: 12px 0 22px; white-space: pre-wrap; }
.alert-forced-btn { background: var(--accent); color: #fff; border-color: var(--accent-strong); }
.alert-forced-btn:hover { background: var(--accent-strong); color: #fff; }

.error-msg, .duplicate-tip {
  padding: 11px 14px;
  border-radius: 11px;
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.6;
  font-weight: 500;
}
.error-msg { color: #a94444; background: var(--danger-soft); border: 1px solid #f1d3d0; }
.duplicate-tip { color: #91611c; background: var(--warning-soft); border: 1px solid #efdfbd; }
.loading { color: var(--text-secondary); font-size: 12px; padding: 14px 0; }

.btn-refresh, .btn-today-count, .btn-event, .btn-frozen, .btn-save, .btn-export-exec,
.btn-timer, .btn-timer-cancel, .btn-modal, .btn-cancel {
  min-height: 38px;
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 9px 18px;
  font-size: 12px;
  line-height: 1.4;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
}
.btn-refresh, .btn-today-count {
  color: #fff;
  background: linear-gradient(125deg, #0d8377, #129888);
  border-color: #0b766c;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.2), 0 6px 18px rgba(15,143,131,0.16);
}
.btn-event, .btn-export-exec { color: #fff; background: #246e82; border-color: #1b6274; box-shadow: inset 0 1px 0 rgba(255,255,255,0.18), 0 6px 18px rgba(36,110,130,0.14); }
.btn-frozen { color: #276b83; background: #eaf5f8; border-color: #cce3ea; margin-top: 10px; }
.btn-save, .btn-retest { color: #fff; background: #198664; border-color: #147556; box-shadow: inset 0 1px 0 rgba(255,255,255,0.18), 0 6px 18px rgba(25,134,100,0.14); }
.btn-timer { color: #8c5c17; background: var(--warning-soft); border-color: #eddbb7; }
.btn-timer-cancel { color: #a94b4b; background: var(--danger-soft); border-color: #efd2cf; }
.btn-cancel, .btn-modal { color: var(--text-secondary); background: rgba(255,255,255,0.7); border-color: var(--border-color); }
.btn-refresh:hover:not(:disabled), .btn-today-count:hover:not(:disabled), .btn-event:hover:not(:disabled),
.btn-frozen:hover:not(:disabled), .btn-save:hover:not(:disabled), .btn-export-exec:hover:not(:disabled),
.btn-timer:hover:not(:disabled), .btn-timer-cancel:hover:not(:disabled), .btn-modal:hover:not(:disabled), .btn-cancel:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.025);
  box-shadow: var(--shadow-md);
}
button:disabled, .btn-refresh:disabled, .btn-event:disabled, .btn-save:disabled, .btn-modal:disabled { opacity: 0.48; cursor: not-allowed; box-shadow: none; }

.timer-section { margin-bottom: 14px; }
.timer-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.timer-label { font-size: 12px; font-weight: 600; color: var(--text-secondary); white-space: nowrap; }
.timer-input, .input-group input, .form-group input, .form-group textarea, .form-group select {
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  outline: none;
  transition: var(--transition);
  background: rgba(255,255,255,0.72);
  box-shadow: inset 0 1px 2px rgba(15,45,42,0.025);
}
.timer-input { width: 80px; min-height: 38px; padding: 8px 10px; font-size: 12px; text-align: center; }
.timer-input:focus, .input-group input:focus, .form-group input:focus, .form-group textarea:focus, .form-group select:focus { border-color: var(--accent-light); background: #fff; box-shadow: 0 0 0 3px var(--accent-glow); }
.timer-status { margin-top: 8px; font-size: 11px; color: var(--accent); font-weight: 600; }

.info-section, .form-section {
  background: rgba(255,255,255,0.78);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,0.9);
}
.info-section { padding: 18px 20px; margin-bottom: 16px; }
.info-item { margin-bottom: 12px; word-break: break-word; }
.info-item:last-child { margin-bottom: 0; }
.info-label, .form-group label { display: block; font-weight: 600; color: var(--text-secondary); font-size: 10px; letter-spacing: 0.4px; margin-bottom: 6px; }
.info-value { color: var(--text-primary); font-size: 13px; line-height: 1.7; }
.input-group { display: flex; gap: 10px; margin-bottom: 20px; }
.input-group input { flex: 1; min-width: 0; min-height: 42px; padding: 10px 13px; font-size: 13px; }
.preview-url { background: var(--accent-soft); border: 1px solid #d2eae3; border-radius: 12px; padding: 11px 14px; margin-bottom: 16px; font-size: 12px; line-height: 1.7; color: var(--text-secondary); word-break: break-all; }
.preview-url strong { color: var(--accent-strong); }
.qrcode-container { display: flex; justify-content: center; margin-bottom: 20px; }
.qrcode-container canvas { display: block; padding: 10px; background: #fff; border: 1px solid var(--border-color); border-radius: 14px; box-shadow: var(--shadow-md); }

.event-result, .frozen-result, .save-success, .event-compare {
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.7;
}
.event-result { text-align: left; background: var(--success-soft); padding: 16px 18px; margin-top: 14px; border: 1px solid #cde8da; }
.event-result.no-event { background: var(--success-soft); border-color: #cde8da; }
.event-result .event-label { font-weight: 600; color: var(--text-secondary); font-size: 9px; letter-spacing: 0.6px; margin-bottom: 4px; }
.event-result .event-value { color: var(--text-primary); font-size: 13px; }
.event-result .event-value.highlight { color: var(--accent-strong); font-weight: 700; font-size: 22px; }
.event-compare { background: rgba(247,250,249,0.8); border: 1px solid var(--border-color); padding: 10px 13px; margin-top: 10px; color: var(--text-secondary); }
.event-compare .diff { color: var(--accent-strong); font-weight: 700; }
.attribution-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 11px; }
.attr-tag { display: inline-flex; align-items: center; min-height: 24px; padding: 3px 9px; border-radius: 7px; font-size: 10px; font-weight: 700; color: #fff; }
.attr-tag.appflyer { background: #25896b; }
.attr-tag.adjust { background: #b57b2b; }
.attr-tag.singular { background: #367caa; }
.attr-tag.tenjin { background: #6978a8; }
.no-attribution { color: #a05e20; font-weight: 600; font-size: 11px; margin-top: 10px; background: var(--warning-soft); border: 1px solid #efdcbb; padding: 6px 10px; border-radius: 8px; display: inline-block; }
.frozen-result { background: var(--info-soft); padding: 14px 16px; margin-top: 10px; border: 1px solid #cee3eb; }
.frozen-result .frozen-label { font-weight: 600; color: var(--info); font-size: 9px; letter-spacing: 0.6px; margin-bottom: 3px; }
.save-success { background: var(--success-soft); padding: 10px 13px; margin-top: 10px; color: #176e52; border: 1px solid #cde8da; }
.form-section { padding: 22px; margin-top: 18px; }
.form-section h4 { font-size: 15px; color: var(--text-primary); margin-bottom: 18px; font-weight: 700; }
.form-group { margin-bottom: 15px; }
.form-group input, .form-group textarea, .form-group select { width: 100%; min-height: 40px; padding: 9px 12px; font-size: 12px; }
.form-group textarea { resize: vertical; min-height: 76px; line-height: 1.7; }

.export-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; }
.export-count { font-size: 12px; color: var(--text-secondary); font-weight: 500; }
.export-count span { color: var(--accent); font-size: 20px; font-weight: 700; }
.export-table-wrapper { overflow-x: auto; margin-bottom: 20px; border-radius: var(--radius); border: 1px solid var(--border-color); }
.export-table { width: 100%; border-collapse: collapse; font-size: 11px; text-align: left; min-width: 700px; }
.export-table th { background: #f3f8f6; color: var(--text-secondary); padding: 11px 13px; white-space: nowrap; font-size: 10px; font-weight: 700; border-bottom: 1px solid var(--border-color); }
.export-table td { padding: 11px 13px; border-bottom: 1px solid #edf2f0; color: var(--text-primary); max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.export-table tr:hover td { background: #f7fbf9; }
.export-table tr:last-child td { border-bottom: none; }
.export-empty { text-align: center; padding: 50px 20px; color: var(--text-tertiary); font-size: 13px; }
.export-empty .empty-icon { font-size: 36px; margin-bottom: 12px; }
.export-result-box { border-radius: 12px; padding: 13px 16px; margin-top: 16px; font-size: 12px; line-height: 1.7; }
.export-result-box.success { color: #176e52; background: var(--success-soft); border: 1px solid #cde8da; }
.export-result-box.fail { color: #a94444; background: var(--danger-soft); border: 1px solid #f1d3d0; }

.modal-overlay { z-index: 1000; }
.modal-box { min-width: 0; text-align: center; }
.modal-title { font-size: 18px; font-weight: 700; margin-bottom: 10px; color: var(--text-primary); }
.modal-desc { font-size: 12px; line-height: 1.7; color: var(--text-secondary); margin-bottom: 22px; }
.modal-actions { display: flex; flex-direction: column; gap: 9px; }
.floating-text { padding-bottom: 17px; }

@keyframes login-enter { from { opacity: 0; transform: translateY(18px) scale(0.985); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes modal-enter { from { opacity: 0; transform: translateY(12px) scale(0.975); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes status-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

@media (max-width: 980px) {
  .login-card { grid-template-columns: 0.9fr 1.1fr; }
  .login-brand { padding-inline: 30px; }
  .login-panel { padding-inline: 32px; }
  .login-brand-meta { grid-template-columns: 1fr; gap: 8px; }
  .login-brand-meta > div { display: flex; align-items: center; gap: 10px; padding-top: 8px; }
  .login-brand-meta p { margin-top: 0; }
  .login-brand-meta small { display: none; }
  .header-environment { display: none; }
}

@media (max-width: 820px) {
  .sidebar { width: min(286px, 84vw); transform: translateX(-102%); box-shadow: 24px 0 70px rgba(0,0,0,0.32); }
  .sidebar-collapsed { width: min(286px, 84vw); transform: translateX(0); }
  .sidebar-collapsed .sidebar-header { justify-content: space-between; padding-inline: 20px 16px; }
  .sidebar-collapsed .sidebar-logo { display: flex; }
  .sidebar-collapsed .nav-label, .sidebar-collapsed .sidebar-stats, .sidebar-collapsed .sidebar-footer, .sidebar-collapsed .nav-eyebrow { display: initial; }
  .sidebar-collapsed .sidebar-footer { display: block; }
  .sidebar-collapsed .sidebar-stats { display: block; }
  .sidebar-collapsed .nav-item { justify-content: flex-start; padding-inline: 12px; }
  .main-content, .sidebar-collapsed ~ .main-content, .sidebar-collapsed + .main-content { margin-left: 0; }
  .mobile-menu-btn { display: grid; }
  .main-header { min-height: 74px; padding: 12px 18px; }
  .content-area { padding: 22px 18px 32px; }
  .login-overlay { padding: 22px; }
  .login-card { width: min(100%, 520px); min-height: 0; grid-template-columns: 1fr; }
  .login-brand { display: none; }
  .login-panel { padding: 36px; border-left: 0; }
}

@media (max-width: 520px) {
  .main-header { gap: 10px; padding-inline: 14px; }
  .header-title { font-size: 16px; }
  .header-eyebrow { font-size: 7px; letter-spacing: 1.2px; }
  .header-badge { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .content-area { padding: 18px 12px 28px; }
  .login-overlay { padding: 14px; }
  .login-card { border-radius: 22px; }
  .login-panel { padding: 30px 24px 24px; }
  .login-logo { margin-bottom: 22px; }
  .login-heading { margin-bottom: 24px; }
  .login-card h1 { font-size: 18px; }
  .gn-overlay, .alert-forced-overlay, .modal-overlay { padding: 14px; }
  .gn-modal, .alert-forced-modal, .modal-box { padding: 22px; border-radius: 18px; }
  .gn-footer { flex-direction: column; gap: 5px; }
  .input-group { flex-direction: column; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; }
}
</style>
