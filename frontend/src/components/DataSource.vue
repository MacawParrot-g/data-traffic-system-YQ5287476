<template>
  <div class="ds-container">
    <header class="ds-header">
      <div class="ds-heading">
        <span class="ds-eyebrow">CONNECTION SETTINGS</span>
        <h3>数据源切换</h3>
        <p>管理测试工作区的数据连接与运行上下文</p>
      </div>
      <div class="ds-status-badge" :class="status.useCustom ? 'custom' : 'default'">
        <span class="ds-status-dot" aria-hidden="true"></span>
        当前：{{ status.useCustom ? '自定义数据源' : '默认数据源' }}
      </div>
    </header>

    <section class="ds-card">
      <div class="ds-card-heading">
        <span class="ds-section-index" aria-hidden="true">01</span>
        <div>
          <h4>自定义 MySQL 数据源</h4>
          <p class="ds-desc">配置您自己的 MySQL 数据库连接信息，切换后将使用您的数据源进行数据操作。</p>
        </div>
      </div>

      <div class="ds-connection-context">
        <span>当前连接上下文</span>
        <strong>{{ status.currentSource }}</strong>
      </div>

      <div class="ds-form">
        <div class="ds-field ds-field-wide">
          <label>JDBC URL</label>
          <input v-model="form.url" placeholder="jdbc:mysql://host:port/database?useSSL=false&serverTimezone=Asia/Shanghai" />
        </div>
        <div class="ds-field">
          <label>用户名</label>
          <input v-model="form.username" placeholder="数据库用户名" autocomplete="username" />
        </div>
        <div class="ds-field">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="数据库密码" autocomplete="current-password" />
        </div>
      </div>

      <div class="ds-actions">
        <button class="ds-btn ds-btn-test" @click="testConnection" :disabled="testing || !canSubmit">
          {{ testing ? '测试中...' : '测试连接' }}
        </button>
        <button class="ds-btn ds-btn-save" @click="saveConfig" :disabled="saving || !canSubmit">
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
        <button class="ds-btn ds-btn-delete" @click="deleteConfig" :disabled="!status.hasConfig">
          删除配置
        </button>
      </div>

      <div v-if="testResult" class="ds-result" :class="testResult.success ? 'success' : 'fail'" role="status">
        <span class="ds-result-mark" aria-hidden="true"></span>
        <span>{{ testResult.message }}</span>
      </div>
    </section>

    <section class="ds-card ds-switch-card">
      <div class="ds-card-heading">
        <span class="ds-section-index" aria-hidden="true">02</span>
        <div>
          <h4>快速切换</h4>
          <p class="ds-desc">选择后续数据操作使用的连接，切换前请确认当前数据已保存。</p>
        </div>
      </div>
      <div class="ds-switch-row">
        <button
            class="ds-btn ds-btn-switch"
            :class="{ active: !status.useCustom }"
            @click="doSwitch(false)"
            :disabled="switching"
        >
          <span class="ds-switch-name">默认数据源</span>
          <span class="ds-switch-meta">系统内置连接</span>
        </button>
        <button
            class="ds-btn ds-btn-switch"
            :class="{ active: status.useCustom }"
            @click="doSwitch(true)"
            :disabled="switching || !status.hasConfig"
        >
          <span class="ds-switch-name">自定义数据源</span>
          <span class="ds-switch-meta">已保存的 MySQL 连接</span>
        </button>
      </div>
      <p v-if="!status.hasConfig" class="ds-hint">请先配置并保存自定义数据源后再切换</p>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'

const form = reactive({ url: '', username: '', password: '' })
const status = reactive({ hasConfig: false, useCustom: false, currentSource: '默认数据源' })
const testing = ref(false)
const saving = ref(false)
const switching = ref(false)
const testResult = ref(null)
const canSubmit = ref(true)

async function fetchStatus() {
  try {
    const r = await fetch('/api/datasource/status', {
      headers: {
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || 'anonymous')
      }
    })
    const json = await r.json()
    if (json.success && json.data) {
      status.hasConfig = json.data.hasConfig
      status.useCustom = json.data.useCustom
      status.currentSource = json.data.currentSource
    }
  } catch (e) { /* silent */ }
}

async function fetchConfig() {
  try {
    const r = await fetch('/api/datasource/config', {
      headers: {
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || 'anonymous')
      }
    })
    const json = await r.json()
    if (json.success && json.data) {
      form.url = json.data.url || ''
      form.username = json.data.username || ''
      if (json.data.hasPassword) {
        form.password = ''
      }
    }
  } catch (e) { /* silent */ }
}

async function testConnection() {
  if (!form.url.trim() || !form.username.trim()) return
  testing.value = true
  testResult.value = null
  try {
    const r = await fetch('/api/datasource/test', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || 'anonymous')
      },
      body: JSON.stringify({ url: form.url, username: form.username, password: form.password })
    })
    const json = await r.json()
    testResult.value = {
      success: json.success,
      message: json.success
          ? '连接成功! 数据库版本: ' + (json.data?.version || '未知')
          : (json.message || '连接失败')
    }
  } catch (e) {
    testResult.value = { success: false, message: '请求失败: ' + e.message }
  } finally {
    testing.value = false
  }
}

async function saveConfig() {
  if (!form.url.trim() || !form.username.trim()) return
  saving.value = true
  try {
    const r = await fetch('/api/datasource/config', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || 'anonymous')
      },
      body: JSON.stringify({ url: form.url, username: form.username, password: form.password })
    })
    const json = await r.json()
    if (json.success) {
      testResult.value = { success: true, message: '配置保存成功' }
      await fetchStatus()
    } else {
      testResult.value = { success: false, message: json.message || '保存失败' }
    }
  } catch (e) {
    testResult.value = { success: false, message: '请求失败: ' + e.message }
  } finally {
    saving.value = false
  }
}

async function deleteConfig() {
  if (!confirm('确定要删除自定义数据源配置吗？将恢复为默认数据源。')) return
  try {
    const r = await fetch('/api/datasource/config', {
      method: 'DELETE',
      headers: {
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || 'anonymous')
      }
    })
    const json = await r.json()
    if (json.success) {
      form.url = ''
      form.username = ''
      form.password = ''
      testResult.value = { success: true, message: '配置已删除，已恢复默认数据源' }
      await fetchStatus()
    }
  } catch (e) { /* silent */ }
}

async function doSwitch(useCustom) {
  if (status.useCustom === useCustom) return
  const confirmed = confirm('数据源切换会丢失当前未保存的数据，请确保数据已保存，是否继续？')
  if (!confirmed) return
  switching.value = true
  try {
    const r = await fetch('/api/datasource/switch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || 'anonymous')
      },
      body: JSON.stringify({ useCustom })
    })
    const json = await r.json()
    if (json.success) {
      await fetchStatus()
      alert('数据源已切换成功，页面即将刷新以应用新数据源')
      window.location.reload()
    } else {
      switching.value = false
      testResult.value = {
        success: false,
        message: json.message || '切换失败'
      }
    }
  } catch (e) {
    switching.value = false
    testResult.value = { success: false, message: '请求失败: ' + e.message }
  }
}

onMounted(() => {
  fetchStatus()
  fetchConfig()
})
</script>

<style scoped>
.ds-container,
.ds-container * { box-sizing: border-box; }
.ds-container {
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  padding-bottom: 36px;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.ds-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 0 0 18px;
  border-bottom: 1px solid var(--border-color);
}
.ds-heading { min-width: 0; }
.ds-eyebrow,
.ds-section-index {
  font-family: var(--font-mono);
  letter-spacing: .12em;
}
.ds-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
}
.ds-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -.025em;
}
.ds-heading p {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.ds-status-badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  min-height: 30px;
  padding: 5px 11px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.ds-status-badge.default { background: #f4f7f7; color: var(--text-secondary); }
.ds-status-badge.custom { background: var(--accent-soft); border-color: var(--accent-light); color: var(--accent-strong); }
.ds-status-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.ds-card {
  position: relative;
  margin-bottom: 16px;
  padding: 22px;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,.8);
}
.ds-card::before {
  position: absolute;
  top: 0;
  left: 22px;
  width: 42px;
  height: 2px;
  background: var(--accent);
  content: "";
}
.ds-card-heading { display: flex; align-items: flex-start; gap: 13px; margin-bottom: 18px; }
.ds-card-heading > div { min-width: 0; }
.ds-section-index {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-top: 1px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid var(--accent-light);
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
}
.ds-card h4 { margin: 0; color: var(--text-primary); font-size: 15px; font-weight: 700; }
.ds-desc { margin: 6px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.65; }
.ds-connection-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 10px 12px;
  color: var(--text-secondary);
  background: #f6f9f9;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-size: 11px;
}
.ds-connection-context strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  text-align: right;
}
.ds-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.ds-field { display: flex; min-width: 0; flex-direction: column; gap: 6px; }
.ds-field-wide { grid-column: 1 / -1; }
.ds-field label {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .025em;
}
.ds-field input {
  width: 100%;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  color: var(--text-primary);
  background: #fbfdfd;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  outline: none;
  font-family: var(--font-sans);
  font-size: 12px;
  transition: border-color .16s ease, box-shadow .16s ease, background .16s ease;
}
.ds-field-wide input { font-family: var(--font-mono); font-size: 11px; }
.ds-field input::placeholder { color: var(--text-tertiary); }
.ds-field input:hover { border-color: var(--accent-light); }
.ds-field input:focus { background: #fff; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.ds-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.ds-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 8px 15px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  outline: none;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background .16s ease, border-color .16s ease, color .16s ease, box-shadow .16s ease;
}
.ds-btn:focus-visible { box-shadow: 0 0 0 3px var(--accent-soft); }
.ds-btn:disabled { opacity: .45; cursor: not-allowed; }
.ds-btn-test { color: var(--text-primary); background: #fff; border-color: var(--border-color); }
.ds-btn-test:hover:not(:disabled) { color: var(--accent-strong); border-color: var(--accent-light); background: var(--accent-soft); }
.ds-btn-save { color: #fff; background: var(--accent); border-color: var(--accent); box-shadow: inset 0 1px 0 rgba(255,255,255,.2); }
.ds-btn-save:hover:not(:disabled) { background: var(--accent-strong); border-color: var(--accent-strong); }
.ds-btn-delete { margin-left: auto; color: var(--danger); background: transparent; border-color: transparent; }
.ds-btn-delete:hover:not(:disabled) { background: #fff5f4; border-color: #f2cfcc; }
.ds-result {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 16px;
  padding: 11px 12px;
  border: 1px solid;
  border-radius: var(--radius);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.ds-result-mark { flex: 0 0 auto; width: 7px; height: 7px; margin-top: 6px; border-radius: 50%; background: currentColor; }
.ds-result.success { color: #16715d; background: #f0faf6; border-color: #cce9df; }
.ds-result.fail { color: var(--danger); background: #fff5f4; border-color: #f2cfcc; }
.ds-switch-card { margin-bottom: 0; }
.ds-switch-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.ds-btn-switch {
  min-width: 0;
  min-height: 66px;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  color: var(--text-secondary);
  background: #f7f9f9;
  border-color: var(--border-color);
  text-align: left;
}
.ds-btn-switch:hover:not(:disabled):not(.active) { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent-light); }
.ds-btn-switch.active { color: var(--accent-strong); background: var(--accent-soft); border-color: var(--accent); box-shadow: inset 3px 0 0 var(--accent); }
.ds-switch-name { color: var(--text-primary); font-size: 13px; font-weight: 650; }
.ds-btn-switch.active .ds-switch-name { color: var(--accent-strong); }
.ds-switch-meta { color: var(--text-tertiary); font-size: 10px; font-weight: 500; }
.ds-hint { margin: 11px 0 0; color: var(--warning); font-size: 11px; line-height: 1.6; }
@media (max-width: 700px) {
  .ds-header { align-items: flex-start; flex-direction: column; gap: 12px; }
  .ds-card { padding: 18px; }
  .ds-card::before { left: 18px; }
  .ds-form { grid-template-columns: 1fr; }
  .ds-field-wide { grid-column: auto; }
  .ds-btn-delete { margin-left: 0; }
}
@media (max-width: 480px) {
  .ds-header h3 { font-size: 21px; }
  .ds-status-badge { white-space: normal; }
  .ds-card { padding: 16px; border-radius: var(--radius); }
  .ds-card::before { left: 16px; }
  .ds-connection-context { align-items: flex-start; flex-direction: column; gap: 5px; }
  .ds-connection-context strong { text-align: left; }
  .ds-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ds-actions .ds-btn { width: 100%; padding-right: 10px; padding-left: 10px; }
  .ds-btn-delete { grid-column: 1 / -1; }
  .ds-switch-row { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .ds-btn,
  .ds-field input { transition: none; }
}
</style>
