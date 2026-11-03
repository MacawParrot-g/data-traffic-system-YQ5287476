<template>
  <div class="manual-mode">
    <header class="workspace-heading">
      <div>
        <span class="workspace-kicker">MANUAL TEST / APP LOOKUP</span>
        <h2>手动测试</h2>
        <p>按 App ID 获取任务并完成事件验证</p>
      </div>
      <span class="workspace-status"><i class="status-dot"></i>指定应用查询</span>
    </header>
    <div class="mode-notice">
    </div>

    <div class="input-section">
      <div class="input-heading">
        <div><span class="section-kicker">TASK PARAMETERS</span><h3>指定应用任务</h3></div>
        <span class="section-meta">APP ID</span>
      </div>
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

    <div v-if="duplicateTip" class="duplicate-tip"><span class="notice-mark" aria-hidden="true">!</span><span>{{ duplicateTip }}</span></div>
    <div v-if="loading && !downloadUrl" class="loading"><span class="loading-spinner" aria-hidden="true"></span>正在获取数据...</div>

    <div class="empty-placeholder" v-if="!downloadUrl && !loading">
      <div class="empty-icon" aria-hidden="true">404</div>
      <div class="empty-text">当前没有任何测试条目，请输入 App ID 并点击生成按钮</div>
    </div>

    <div v-if="duplicateTip" class="duplicate-tip"><span class="notice-mark" aria-hidden="true">!</span><span>{{ duplicateTip }}</span></div>
    <div v-if="loading && !downloadUrl" class="loading"><span class="loading-spinner" aria-hidden="true"></span>正在获取数据...</div>

    <div class="qr-info-section" v-if="downloadUrl">
      <div class="qr-card">
        <div class="qr-card-header">
          <span class="qr-card-icon" aria-hidden="true"></span>
          <span class="qr-card-title">扫描二维码下载</span>
        </div>
        <div class="qrcode-container">
          <canvas ref="qrCanvas"></canvas>
        </div>
        <div class="app-status-row" :title="'上游 status = ' + (taskStatus === null ? '未返回' : taskStatus)">
          <span class="app-status-badge" :class="statusInfo.cls">{{ statusInfo.text }}</span>
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
            <div class="event-value">无事件</div>
            <button class="btn-frozen" @click="doFrozen" :disabled="frozenLoading || frozenDone || isTaskFrozen">
              {{ isTaskFrozen ? '该应用已被冻结' : frozenDone ? '已冻结' : frozenLoading ? '冻结中...' : '冻结应用' }}
            </button>
            <div class="frozen-remark-tip" v-if="!isTaskFrozen">备注请写在冻结理由输入框内</div>
            <div class="frozen-reason-box" v-if="!frozenDone && !isTaskFrozen">
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
            <div class="no-attribution" v-if="attributions.length === 0">无归因</div>
          </div>
        </template>
        <div class="event-placeholder" v-else>
          <span class="event-placeholder-icon" aria-hidden="true"></span>
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
      <div class="form-grid-2">
        <div class="form-group">
          <label>记录人</label>
          <input v-model="form.recorder" />
        </div>
        <div class="form-group">
          <label>记录日期</label>
          <template v-if="applyTime">
            <template v-if="currentUserRole === 'DEVELOPER'">
              <div class="date-source-toggle">
                <button type="button" class="date-source-btn" :class="{ active: dateSource === 'applyTime' }" @click="dateSource = 'applyTime'; form.record_data = applyTime">applyTime</button>
                <button type="button" class="date-source-btn" :class="{ active: dateSource === 'today' }" @click="dateSource = 'today'; form.record_data = getTodayStr()">今日日期</button>
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

<style scoped>
.manual-mode {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  color: var(--text-primary);
  font-family: var(--font-sans);
  --panel-muted: #f6faf8;
  --panel-hover: #f0f7f4;
}
.manual-mode *, .manual-mode *::before, .manual-mode *::after { box-sizing: border-box; }
.manual-mode button, .manual-mode input, .manual-mode textarea, .manual-mode select { font: inherit; }
.manual-mode button { -webkit-tap-highlight-color: transparent; }
.workspace-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin: 0 0 16px;
  padding: 0 2px;
}
.workspace-kicker, .section-kicker {
  display: block;
  margin-bottom: 5px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 9px;
  line-height: 1.4;
  font-weight: 700;
  letter-spacing: 1.35px;
}
.workspace-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
  line-height: 1.35;
  font-weight: 750;
  letter-spacing: -0.35px;
}
.workspace-heading p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.6;
}
.workspace-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: none;
  padding: 6px 10px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid #cee8e0;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 650;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(15,143,131,0.1);
}
.top-bar { margin-bottom: 14px; }
.top-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.combined-action-bar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  padding: 14px 16px;
  background: rgba(255,255,255,0.78);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm), inset 0 1px 0 rgba(255,255,255,0.9);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}
.action-bar-title { min-width: 108px; padding-right: 16px; border-right: 1px solid var(--border-color); }
.action-bar-title span {
  display: block;
  margin-bottom: 3px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 1px;
}
.action-bar-title strong { font-size: 13px; font-weight: 700; }
.combined-left { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-width: 0; }
.combined-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.input-section, .manual-section {
  margin-bottom: 16px;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
.input-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.input-heading h3 { margin: 0; font-size: 13px; line-height: 1.5; font-weight: 700; }
.input-heading .section-kicker { margin-bottom: 2px; }
.section-meta {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.8px;
}
.input-group { display: flex; gap: 9px; margin: 0; }
.input-group input {
  flex: 1;
  min-width: 0;
  min-height: 40px;
  padding: 9px 12px;
  color: var(--text-primary);
  background: #fbfdfc;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  outline: none;
  font-family: var(--font-mono);
  font-size: 12px;
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}
.input-group input::placeholder { color: var(--text-tertiary); font-family: var(--font-sans); }
.input-group input:focus { background: #fff; border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.preview-url {
  margin: 11px 0 0;
  padding: 9px 11px;
  color: var(--text-secondary);
  background: var(--panel-muted);
  border: 1px solid var(--border-color);
  border-radius: 9px;
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 1.7;
  word-break: break-all;
}
.preview-url strong { color: var(--accent-strong); font-family: var(--font-sans); font-weight: 650; }
.btn-refresh, .btn-event, .btn-save, .btn-frozen, .btn-timer, .btn-timer-cancel, .btn-modal, .btn-grade {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.4;
  font-weight: 650;
  white-space: nowrap;
  cursor: pointer;
  transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease, border-color 160ms ease;
}
.btn-refresh, .btn-save {
  color: #fff;
  background: var(--accent);
  border-color: var(--accent-strong);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.22), 0 5px 14px rgba(15,143,131,0.14);
}
.btn-event {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: #c9e5dd;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.7);
}
.btn-timer { color: var(--accent-strong); background: #fff; border-color: var(--border-color); }
.btn-timer-cancel, .btn-cancel { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-frozen { margin-top: 10px; color: #276b83; background: var(--info-soft); border-color: #cde3eb; }
.btn-refresh:hover:not(:disabled), .btn-event:hover:not(:disabled), .btn-save:hover:not(:disabled),
.btn-frozen:hover:not(:disabled), .btn-timer:hover:not(:disabled), .btn-timer-cancel:hover:not(:disabled),
.btn-modal:hover:not(:disabled), .btn-grade:hover:not(:disabled) { transform: translateY(-1px); box-shadow: var(--shadow-md); }
.btn-refresh:hover:not(:disabled), .btn-save:hover:not(:disabled) { background: var(--accent-strong); }
.btn-event:hover:not(:disabled), .btn-timer:hover:not(:disabled) { background: #dcefe9; border-color: #b9dbd2; }
.manual-mode button:disabled { opacity: 0.48; cursor: not-allowed; transform: none; box-shadow: none; }
.duplicate-tip {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 12px;
  padding: 10px 12px;
  color: #8c5c17;
  background: var(--warning-soft);
  border: 1px solid #eddbb7;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.6;
  font-weight: 550;
}
.notice-mark {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 17px;
  height: 17px;
  margin-top: 1px;
  color: #fff;
  background: var(--warning);
  border-radius: 50%;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
}
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin-bottom: 14px;
  padding: 13px;
  color: var(--text-secondary);
  background: rgba(255,255,255,0.62);
  border: 1px solid var(--border-color);
  border-radius: 11px;
  font-size: 11px;
}
.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #cce2dc;
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: task-spin 700ms linear infinite;
}
@keyframes task-spin { to { transform: rotate(360deg); } }
.empty-placeholder {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 230px;
  margin-bottom: 16px;
  padding: 42px 22px;
  overflow: hidden;
  text-align: center;
  background-color: rgba(255,255,255,0.66);
  background-image: linear-gradient(rgba(15,143,131,0.025) 1px, transparent 1px), linear-gradient(90deg, rgba(15,143,131,0.025) 1px, transparent 1px);
  background-size: 24px 24px;
  border: 1px dashed #bfd5ce;
  border-radius: var(--radius-lg);
}
.empty-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  margin-bottom: 15px;
  color: var(--accent-strong);
  background: rgba(231,246,242,0.86);
  border: 1px solid #c7e3db;
  border-radius: 18px;
  box-shadow: inset 0 1px 0 #fff, 0 8px 22px rgba(15,143,131,0.07);
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 750;
  letter-spacing: 0.5px;
}
.empty-text { max-width: 420px; color: var(--text-secondary); font-size: 12px; line-height: 1.8; }
.qr-info-section {
  display: grid;
  grid-template-columns: 258px minmax(0, 1fr);
  margin-bottom: 16px;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
.qr-card { min-width: 0; padding: 20px; text-align: center; background: var(--panel-muted); border-right: 1px solid var(--border-color); }
.qr-card-header { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 24px; margin-bottom: 14px; }
.qr-card-title { color: var(--text-primary); font-size: 12px; line-height: 1.6; font-weight: 700; }
.qr-card-icon { position: relative; width: 17px; height: 17px; flex: none; border: 1.5px solid var(--accent); border-radius: 4px; }
.qr-card-icon::before, .qr-card-icon::after { content: ''; position: absolute; background: var(--panel-muted); }
.qr-card-icon::before { inset: 3px -3px; border-top: 1px solid var(--accent); border-bottom: 1px solid var(--accent); }
.qr-card-icon::after { inset: -3px 3px; border-left: 1px solid var(--accent); border-right: 1px solid var(--accent); }
.qrcode-container { display: flex; justify-content: center; margin: 0; }
.qrcode-container canvas {
  display: block;
  width: min(100%, 182px);
  max-width: 100%;
  height: auto;
  padding: 8px;
  background: #fff;
  border: 1px solid #d7e5e0;
  border-radius: 12px;
  box-shadow: 0 7px 22px rgba(15,45,42,0.06), inset 0 1px 0 #fff;
}
.app-status-row { display: flex; justify-content: center; margin-top: 12px; }
.app-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 25px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1.4;
  font-weight: 700;
}
.app-status-badge::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.app-status-badge.normal { color: #176e52; background: var(--success-soft); border-color: #cce6d9; }
.app-status-badge.frozen { color: #276b83; background: var(--info-soft); border-color: #cde3eb; }
.app-status-badge.unknown { color: #a94444; background: var(--danger-soft); border-color: #efd2cf; }
.side-info { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: center; gap: 9px; min-width: 0; padding: 16px; }
.info-card {
  min-width: 0;
  padding: 11px 12px;
  background: #fbfdfc;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  transition: border-color 160ms ease, background 160ms ease;
}
.info-card:hover { background: #fff; border-color: #c5dcd5; }
.info-card-label {
  margin-bottom: 5px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 8px;
  line-height: 1.5;
  font-weight: 700;
  letter-spacing: 0.65px;
  text-transform: uppercase;
}
.info-card-value { color: var(--text-primary); font-family: var(--font-mono); font-size: 11px; line-height: 1.65; font-weight: 600; overflow-wrap: anywhere; }
.action-row { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 14px; }
.version-card { margin-bottom: 14px; background: var(--bg-card); }
.version-card .version-value { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; font-size: 11px; }
.version-card .ver-item { padding: 4px 8px; color: var(--text-secondary); background: var(--panel-muted); border: 1px solid var(--border-color); border-radius: 7px; font-family: var(--font-mono); }
.version-card .ver-mismatch { padding: 4px 8px; color: #a94444; background: var(--danger-soft); border: 1px solid #efd2cf; border-radius: 7px; font-weight: 700; }
.btn-stop-delivery { min-height: 32px; margin-top: 9px; padding: 6px 12px; color: #a94444; background: var(--danger-soft); border: 1px solid #efd2cf; border-radius: 8px; font-size: 10px; font-weight: 650; cursor: pointer; }
.stop-delivery-msg { margin-top: 7px; color: var(--success); font-size: 10px; font-weight: 600; }
.timer-panel {
  display: grid;
  grid-template-columns: minmax(210px, 0.72fr) 1px minmax(0, 1.28fr);
  gap: 16px;
  align-items: stretch;
  margin-bottom: 16px;
  padding: 15px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
.timer-left { min-width: 0; align-self: center; }
.timer-row { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.timer-label { color: var(--text-secondary); font-size: 10px; font-weight: 650; white-space: nowrap; }
.timer-input {
  width: 62px;
  min-height: 36px;
  padding: 7px 8px;
  color: var(--text-primary);
  text-align: center;
  background: #fbfdfc;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  outline: none;
  font-family: var(--font-mono);
  font-size: 11px;
}
.timer-input:focus { border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.timer-status { margin-top: 8px; color: var(--accent-strong); font-size: 10px; line-height: 1.6; }
.timer-divider { width: 1px; background: var(--border-color); }
.timer-right { min-width: 0; }
.event-result { padding: 12px 13px; background: var(--success-soft); border: 1px solid #cde8da; border-radius: 11px; font-size: 11px; line-height: 1.7; }
.event-result.no-event { text-align: left; background: #fbfdfc; border-color: var(--border-color); }
.event-label, .frozen-label { margin-bottom: 3px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; font-weight: 700; letter-spacing: 0.6px; text-transform: uppercase; }
.event-value { color: var(--text-primary); font-size: 12px; font-weight: 650; }
.event-value.highlight { color: var(--accent-strong); font-family: var(--font-mono); font-size: 22px; line-height: 1.3; }
.event-compare { margin-top: 8px; padding: 7px 9px; color: var(--text-secondary); background: rgba(255,255,255,0.72); border: 1px solid #d7e9e1; border-radius: 8px; font-size: 10px; }
.event-compare .diff { color: var(--accent-strong); font-family: var(--font-mono); font-weight: 700; }
.attribution-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.attr-tag { display: inline-flex; align-items: center; min-height: 22px; padding: 3px 8px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #cce6de; border-radius: 6px; font-family: var(--font-mono); font-size: 9px; font-weight: 700; }
.attr-tag.adjust { color: #8c5c17; background: var(--warning-soft); border-color: #eddbb7; }
.attr-tag.singular { color: #276b83; background: var(--info-soft); border-color: #cde3eb; }
.attr-tag.tenjin { color: #52637f; background: #eef2f7; border-color: #d9e0e9; }
.no-attribution { display: inline-block; margin-top: 8px; padding: 5px 8px; color: #8c5c17; background: var(--warning-soft); border: 1px solid #eddbb7; border-radius: 7px; font-size: 10px; font-weight: 650; }
.event-placeholder { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 70px; padding: 12px; color: var(--text-tertiary); background: var(--panel-muted); border: 1px dashed #cfdfda; border-radius: 10px; font-size: 10px; }
.event-placeholder-icon { position: relative; width: 15px; height: 15px; border: 1.5px solid #8eaaa3; border-radius: 50%; }
.event-placeholder-icon::after { content: ''; position: absolute; right: -4px; bottom: -3px; width: 6px; height: 1.5px; background: #8eaaa3; transform: rotate(45deg); border-radius: 2px; }
.frozen-reason-box { display: grid; gap: 7px; margin-top: 9px; }
.frozen-reason-input, .frozen-reason-select {
  width: 100%;
  min-height: 36px;
  padding: 8px 10px;
  color: var(--text-primary);
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  outline: none;
  font-size: 10px;
}
.frozen-reason-input:focus, .frozen-reason-select:focus { border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.frozen-reason-select:disabled { color: var(--text-tertiary); background: #f2f5f4; cursor: not-allowed; }
.frozen-remark-tip { margin-top: 8px; color: #8c5c17; font-size: 10px; line-height: 1.6; }
.frozen-result { margin-top: 9px; padding: 9px 11px; color: #276b83; background: var(--info-soft); border: 1px solid #cde3eb; border-radius: 9px; }
.frozen-value { font-size: 10px; line-height: 1.6; }
.retest-frozen-tip { margin-top: 8px; color: var(--text-tertiary); font-size: 10px; line-height: 1.6; }
.form-section {
  margin: 16px 0;
  padding: 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
.form-section h4 { display: flex; align-items: center; gap: 9px; margin: 0 0 16px; color: var(--text-primary); font-size: 13px; line-height: 1.5; font-weight: 700; }
.form-section h4::before { content: ''; width: 3px; height: 15px; background: var(--accent); border-radius: 3px; }
.form-grid-2, .form-row-2col { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.form-group { min-width: 0; margin-bottom: 13px; }
.form-group label { display: block; margin-bottom: 5px; color: var(--text-secondary); font-size: 9px; line-height: 1.5; font-weight: 650; letter-spacing: 0.3px; }
.form-group input, .form-group select, .form-group textarea {
  width: 100%;
  min-height: 38px;
  padding: 8px 11px;
  color: var(--text-primary);
  background: #fbfdfc;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  outline: none;
  font-size: 11px;
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}
.form-group textarea { min-height: 76px; resize: vertical; line-height: 1.7; }
.form-group input:focus, .form-group select:focus, .form-group textarea:focus { background: #fff; border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.form-group input:disabled { color: var(--text-secondary); background: #f1f5f3; cursor: not-allowed; }
.form-group br { display: none; }
.form-group textarea + select, .form-group br + select { margin-top: 8px; }
.date-source-toggle { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 7px; }
.date-source-btn { min-height: 28px; padding: 4px 9px; color: var(--text-secondary); background: #fff; border: 1px solid var(--border-color); border-radius: 7px; font-size: 9px; font-weight: 600; cursor: pointer; }
.date-source-btn.active { color: var(--accent-strong); background: var(--accent-soft); border-color: #bfdfd6; }
.date-readonly { font-family: var(--font-mono); }
.retest-date { color: var(--accent-strong) !important; background: var(--accent-soft) !important; border-color: #c8e3dc !important; }
.form-btn-row { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin-top: 4px; }
.save-success-tag { display: inline-flex; align-items: center; min-height: 30px; padding: 5px 10px; color: #176e52; background: var(--success-soft); border: 1px solid #cde8da; border-radius: 8px; font-size: 10px; font-weight: 650; }
.save-success { margin-top: 10px; padding: 9px 11px; color: #176e52; background: var(--success-soft); border: 1px solid #cde8da; border-radius: 9px; font-size: 11px; }
.auto-normal-tag { display: inline-flex; padding: 7px 12px; color: #176e52; background: var(--success-soft); border: 1px solid #cde8da; border-radius: 8px; font-size: 11px; font-weight: 650; }
.graded-warning { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 13px; padding: 9px 11px; color: #8c5c17; background: var(--warning-soft); border: 1px solid #eddbb7; border-radius: 9px; font-size: 10px; line-height: 1.7; }
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  overflow-y: auto;
  background: rgba(4,18,18,0.48);
  backdrop-filter: blur(12px) saturate(120%);
  -webkit-backdrop-filter: blur(12px) saturate(120%);
}
.modal-box {
  position: relative;
  width: min(100%, 440px);
  max-height: calc(100dvh - 40px);
  padding: 24px;
  overflow-y: auto;
  color: var(--text-primary);
  text-align: left;
  background: linear-gradient(145deg, rgba(255,255,255,0.96), rgba(244,250,248,0.9));
  border: 1px solid rgba(255,255,255,0.82);
  border-radius: 20px;
  box-shadow: inset 0 1px 0 #fff, var(--shadow-lg);
  backdrop-filter: blur(26px) saturate(140%);
  -webkit-backdrop-filter: blur(26px) saturate(140%);
}
.modal-box::before { content: ''; position: absolute; inset: 0 20% auto; height: 1px; background: var(--accent-light); opacity: 0.75; }
.modal-title { margin-bottom: 8px; color: var(--text-primary); font-size: 17px; line-height: 1.5; font-weight: 750; }
.modal-desc { margin-bottom: 18px; color: var(--text-secondary); font-size: 11px; line-height: 1.8; }
.modal-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.btn-modal.btn-retest { color: #fff; background: var(--accent); border-color: var(--accent-strong); }
.btn-modal.btn-cancel { color: var(--text-secondary); background: rgba(255,255,255,0.72); border-color: var(--border-color); }
.feedback { padding: 9px 11px; border-radius: 8px; font-size: 10px; line-height: 1.6; }
.feedback-ok { color: #176e52; background: var(--success-soft); border: 1px solid #cde8da; }
.feedback-err { color: #a94444; background: var(--danger-soft); border: 1px solid #efd2cf; }
@media (max-width: 700px) {
  .workspace-heading { align-items: flex-start; }
  .combined-action-bar { grid-template-columns: 1fr; gap: 12px; }
  .action-bar-title { padding: 0 0 10px; border-right: 0; border-bottom: 1px solid var(--border-color); }
  .qr-info-section { grid-template-columns: 1fr; }
  .qr-card { border-right: 0; border-bottom: 1px solid var(--border-color); }
  .timer-panel { grid-template-columns: 1fr; gap: 12px; }
  .timer-divider { width: 100%; height: 1px; }
  .form-grid-2, .form-row-2col { grid-template-columns: 1fr; gap: 0; }
  .modal-overlay { align-items: flex-start; padding: 14px; }
  .modal-box { max-height: calc(100dvh - 28px); margin: auto 0; }
}
@media (max-width: 480px) {
  .workspace-heading { gap: 10px; margin-bottom: 13px; }
  .workspace-heading h2 { font-size: 18px; }
  .workspace-heading p { max-width: 230px; }
  .workspace-status { padding: 5px 8px; font-size: 9px; }
  .combined-action-bar, .input-section, .manual-section, .form-section { padding: 13px; border-radius: 14px; }
  .combined-left { align-items: stretch; flex-direction: column; }
  .input-group { flex-direction: column; }
  .input-group .btn-refresh { width: 100%; }
  .side-info { grid-template-columns: 1fr; padding: 13px; }
  .qr-card { padding: 17px 13px; }
  .qr-info-section { border-radius: 14px; }
  .action-row .btn-event { flex: 1; }
  .timer-panel { padding: 12px; }
  .timer-row .timer-label { width: 100%; }
  .timer-row .btn-timer, .timer-row .btn-timer-cancel { flex: 1; }
  .form-btn-row .btn-save, .form-btn-row .btn-grade { flex: 1; }
  .modal-box { padding: 20px; border-radius: 17px; }
  .modal-actions { flex-direction: column; }
  .modal-actions .btn-modal { width: 100%; }
}
@media (prefers-reduced-motion: reduce) {
  .manual-mode *, .manual-mode *::before, .manual-mode *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}
</style>

<script setup>
import {ref, reactive, watch, nextTick, onUnmounted, onMounted} from 'vue'
import { renderQRToCanvas } from '../utils/qrcode.js'
import { useTemplateOptions } from '../utils/useTemplateOptions.js'
import { useAppStatus } from '../utils/useAppStatus.js'
import {
  fetchObtain,
  fetchEvent,
  fetchAllAttributions,
  fetchFrozen,
  insertRecord,
  uploadFrozenV2,
  fetchCountByRecorder
} from '../api/index.js'
const { taskStatus, statusInfo, isTaskFrozen, setTaskStatus, resetTaskStatus } = useAppStatus()
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
      setTaskStatus(json.data.status)
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
  if (isTaskFrozen.value) { frozenMsg.value = '该应用已被冻结，无法二次冻结'; return }
  if (frozenDone.value) return
  if (!taskId.value) return
  const remark = frozenReasonText.value.trim() || frozenReasonSelect.value
  if (!remark) { emit('error', '请选择或填写冻结理由'); return }
  frozenLoading.value = true; emit('error', '')
  frozenForm.bundleId = bundleId.value
  frozenForm.id = taskId.value != null ? Number(taskId.value) : 0
  frozenForm.remark = remark
  try {
    // 仅通过 /api/proxy/frozen/v2 上传冻结数据
    let v2Ok = false
    let v2Msg = ''
    try {
      const frozenJson = await uploadFrozenV2({
        bundleId: frozenForm.bundleId,
        id: frozenForm.id,
        remark: frozenForm.remark
      })
      v2Ok = !!frozenJson.success
      v2Msg = frozenJson.message || ''
    } catch (e) {
      v2Ok = false
    }
    if (v2Ok) {
      frozenDone.value = true
      frozenMsg.value = v2Msg || '冻结成功'
      isFrozen.value = ',已冻结'
    } else {
      frozenMsg.value = '冻结失败' + (v2Msg ? '：' + v2Msg : '')
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
  resetTaskStatus()
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
