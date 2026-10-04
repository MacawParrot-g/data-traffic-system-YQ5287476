<template>
  <div class="ds-container">
    <div class="ds-header">
      <h3>数据源切换</h3>
      <div class="ds-status-badge" :class="status.useCustom ? 'custom' : 'default'">
        当前：{{ status.useCustom ? '自定义数据源' : '默认数据源' }}
      </div>
    </div>

    <div class="ds-card">
      <h4>自定义 MySQL 数据源</h4>
      <p class="ds-desc">配置您自己的 MySQL 数据库连接信息，切换后将使用您的数据源进行数据操作。</p>

      <div class="ds-form">
        <div class="ds-field">
          <label>JDBC URL</label>
          <input v-model="form.url" placeholder="jdbc:mysql://host:port/database?useSSL=false&serverTimezone=Asia/Shanghai" />
        </div>
        <div class="ds-field">
          <label>用户名</label>
          <input v-model="form.username" placeholder="数据库用户名" />
        </div>
        <div class="ds-field">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="数据库密码" />
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

      <div v-if="testResult" class="ds-result" :class="testResult.success ? 'success' : 'fail'">
        {{ testResult.message }}
      </div>
    </div>

    <div class="ds-card ds-switch-card">
      <h4>快速切换</h4>
      <div class="ds-switch-row">
        <button
            class="ds-btn ds-btn-switch"
            :class="{ active: !status.useCustom }"
            @click="doSwitch(false)"
            :disabled="switching"
        >
          默认数据源
        </button>
        <button
            class="ds-btn ds-btn-switch"
            :class="{ active: status.useCustom }"
            @click="doSwitch(true)"
            :disabled="switching || !status.hasConfig"
        >
          自定义数据源
        </button>
      </div>
      <p v-if="!status.hasConfig" class="ds-hint">请先配置并保存自定义数据源后再切换</p>
    </div>
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
    const r = await fetch('/api/datasource/status')
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
    const r = await fetch('/api/datasource/config')
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
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || '')
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
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || '')
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
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || '')
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
  switching.value = true
  try {
    const r = await fetch('/api/datasource/switch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Name': encodeURIComponent(localStorage.getItem('userName') || '')
      },
      body: JSON.stringify({ useCustom })
    })
    const json = await r.json()
    if (json.success) {
      await fetchStatus()
    }
    testResult.value = {
      success: json.success,
      message: json.success ? (useCustom ? '已切换到自定义数据源' : '已切换到默认数据源') : (json.message || '切换失败')
    }
  } catch (e) {
    testResult.value = { success: false, message: '请求失败: ' + e.message }
  } finally {
    switching.value = false
  }
}

onMounted(() => {
  fetchStatus()
  fetchConfig()
})
</script>

<style scoped>
.ds-container {
  max-width: 680px;
  margin: 0 auto;
}
.ds-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.ds-header h3 {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.ds-status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 6px 16px;
  border-radius: 20px;
}
.ds-status-badge.default {
  background: #f1f5f9;
  color: #64748b;
}
.ds-status-badge.custom {
  background: #e0e7ff;
  color: #3730a3;
}
.ds-card {
  background: var(--bg-card);
  border-radius: var(--radius);
  padding: 24px 28px;
  border: 1px solid var(--border-color);
  margin-bottom: 20px;
}
.ds-card h4 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}
.ds-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 20px;
  line-height: 1.6;
}
.ds-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;
}
.ds-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ds-field label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ds-field input {
  padding: 11px 14px;
  font-size: 14px;
  border: 2px solid var(--border-color);
  border-radius: 10px;
  outline: none;
  transition: var(--transition);
  font-family: inherit;
  background: var(--bg-card);
}
.ds-field input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-glow);
}
.ds-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.ds-btn {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: var(--transition);
}
.ds-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ds-btn-test {
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  color: #fff;
}
.ds-btn-test:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(59,130,246,0.35);
}
.ds-btn-save {
  background: linear-gradient(135deg, #22c55e, #10b981);
  color: #fff;
}
.ds-btn-save:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(34,197,94,0.35);
}
.ds-btn-delete {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #fff;
}
.ds-btn-delete:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(239,68,68,0.35);
}
.ds-result {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
}
.ds-result.success {
  background: #f0fdf4;
  color: #166534;
  border: 1px solid #bbf7d0;
}
.ds-result.fail {
  background: #fef2f2;
  color: #991b1b;
  border: 1px solid #fecaca;
}
.ds-switch-card {
  text-align: center;
}
.ds-switch-row {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 16px;
  margin-bottom: 8px;
}
.ds-btn-switch {
  padding: 12px 32px;
  font-size: 15px;
  background: #f1f5f9;
  color: var(--text-secondary);
  border: 2px solid var(--border-color);
}
.ds-btn-switch.active {
  background: linear-gradient(135deg, var(--accent), #a855f7);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 4px 16px rgba(99,102,241,0.3);
}
.ds-btn-switch:hover:not(:disabled):not(.active) {
  border-color: var(--accent);
  color: var(--accent);
}
.ds-hint {
  font-size: 12px;
  color: var(--warning);
  margin-top: 8px;
}
</style>