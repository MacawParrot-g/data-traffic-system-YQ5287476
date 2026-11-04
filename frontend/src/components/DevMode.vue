
<template>
  <div class="dev-mode">
    <header class="dev-header">
      <div class="dev-heading">
        <div class="dev-badge">开发者模式</div>
        <h2 class="dev-title">专业测试与数据治理</h2>
        <p class="dev-subtitle">归因验证、测试归档与数据库操作</p>
      </div>
      <div class="dev-top-bar">
        <button class="dev-guide-btn" @click="guideVisible = true">
          <span>使用指南</span>
        </button>
      </div>
    </header>
    <div class="dev-tabs">
      <button class="dev-tab" :class="{ active: devActiveTab === 'test' }" @click="devActiveTab = 'test'">
        <span>专业测试</span>
      </button>
      <button class="dev-tab" :class="{ active: devActiveTab === 'governance' }" @click="devActiveTab = 'governance'; loadTableList()">
        <span>数据建表和治理</span>
      </button>
    </div>

    <!-- ==================== 专业测试 Tab ==================== -->
    <div v-if="devActiveTab === 'test'" class="test-tab">
      <div class="top-bar">
        <div v-if="duplicateTip" class="duplicate-tip">{{ duplicateTip }}</div>
        <div class="top-actions">
          <button class="btn-refresh" @click="confirmRefresh" :disabled="loading">
            {{ loading ? '加载中...' : '刷新数据' }}
          </button>
        </div>
      </div>

      <div class="manual-section">
        <div class="input-group">
          <input type="text" v-model="appId" placeholder="输入 App ID 获取下载链接" @keyup.enter="fetchByAppId" />
          <button class="btn-refresh" @click="fetchByAppId" :disabled="loading || !appId.trim()">
            {{ loading ? '获取中...' : '生成' }}
          </button>
        </div>
      </div>

      <div v-if="loading && !downloadUrl" class="loading">正在获取数据...</div>

      <div class="empty-placeholder" v-if="!downloadUrl && !loading">
        <div class="empty-icon" aria-hidden="true">404</div>
        <div class="empty-text">点击刷新数据获取任务，或输入 App ID 生成下载链接</div>
      </div>

      <div class="qr-info-section" v-if="downloadUrl">
        <div class="qr-card">
          <div class="qr-card-header">
            <span class="qr-card-title">扫描二维码下载APP进行测试</span>
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
            <div class="info-card-label">App ID</div>
            <div class="info-card-value">{{ appIds }}</div>
          </div>
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
        <button class="btn-event" @click="queryEventWithFullMsg" :disabled="eventLoading">
          查看JSON
        </button>
      </div>
      <div class="info-card version-card" v-if="currentVersion || checkVerion">
        <div class="info-card-label">版本比对</div>
        <div class="version-value">
          <span class="ver-item">current: {{ currentVersion || '-' }}</span>
          <span class="ver-item">check: {{ checkVerion || '-' }}</span>
          <span class="ver-mismatch" v-if="versionMismatch">版本不一致</span>
        </div>
        <button v-if="versionMismatch && !isRetestMode" class="btn-stop-delivery" @click="stopDelivery" :disabled="stopDeliveryLoading">
          {{ stopDeliveryLoading ? '停止中...' : '停止二次下发' }}
        </button>
        <div class="retest-frozen-tip" v-else-if="versionMismatch && isRetestMode">复测模式数据源为本地Redis，已禁用停止下发功能</div>
        <div class="stop-delivery-msg" v-if="stopDeliveryMsg">{{ stopDeliveryMsg }}</div>
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
              <button class="btn-frozen" @click="doFrozen" :disabled="frozenLoading || frozenDone" v-if="!isRetestMode">
                {{ frozenDone ? '已冻结' : frozenLoading ? '冻结中...' : '冻结应用' }}
              </button>
              <div class="retest-frozen-tip" v-else>复测模式下已禁用冻结功能</div>
              <div class="frozen-remark-tip" v-if="!isRetestMode">备注请写在冻结理由输入框内</div>
              <div class="frozen-reason-box" v-if="!isRetestMode && !frozenDone">
                <input class="frozen-reason-input" v-model="frozenReasonText" maxlength="200" placeholder="自定义冻结理由/备注(也可用下方下拉选择，200字以内)"  />
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
                原始值：<span class="diff">{{ originalCurrentTargetNum }}</span> -> 最新值：<span class="diff">{{ newCurrentTargetNum }}</span>
              </div>
              <div class="attribution-tags" v-if="attributions.length > 0">
                <span class="attr-tag" v-for="attr in attributions" :key="attr" :class="attr">{{ attr }}</span>
              </div>
              <div class="no-attribution" v-if="attributions.length === 0">无归因</div>
            </div>
          </template>
          <div class="event-placeholder" v-else>
            <span>查询的事件会在这里显示</span>
          </div>
        </div>
      </div>

      <!-- 四路归因查询 -->
      <div class="attr-section">
        <h3 class="section-title">归因查询</h3>
        <div class="attr-grid">
          <div v-for="type in ATTR_TYPES" :key="type" class="attr-panel" :class="type">
            <div class="attr-panel-header">
              <span class="attr-panel-title">{{ type.toUpperCase() }}</span>
              <span class="attr-count" v-if="attrStates[type].count !== null">({{ attrStates[type].count }}条)</span>
            </div>
            <div class="attr-input-row">
              <input v-model="attrStates[type].bundleId" placeholder="Bundle ID" @keyup.enter="querySingleAttr(type)" />
              <button class="btn-attr-query" @click="querySingleAttr(type)" :disabled="attrStates[type].loading || !attrStates[type].bundleId">
                {{ attrStates[type].loading ? '...' : '查询' }}
              </button>
            </div>
            <div v-if="attrStates[type].data.length > 0" class="attr-results">
              <div v-for="(record, rIdx) in attrStates[type].data" :key="rIdx" class="attr-record">
                <div v-for="(val, key) in record" :key="key" class="attr-field">
                  <span class="attr-field-key">{{ key }}</span>
                  <span class="attr-field-val">{{ formatAttrValue(val) }}</span>
                </div>
              </div>
            </div>
            <div v-if="attrStates[type].queried && attrStates[type].data.length === 0 && !attrStates[type].loading" class="attr-empty">
              无数据
            </div>
          </div>
        </div>
      </div>

      <!-- 入库表单 -->
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
          <textarea v-model="form.remark" placeholder="手动输入(如果用了模板此处禁填)"></textarea>
          <br/>
          <select v-model="form.remark" @focus="loadTemplateOptions('备注模板')" @open="loadTemplateOptions('备注模板')">
            <option value="" disabled>模板(可选)</option>
            <option v-for="opt in remarkTemplateOptions" :key="opt" :value="opt">{{ opt }}</option>
          </select>
        </div>
        <div class="form-row-2col">
          <div class="form-group">
            <label>记录人</label>
            <input v-model="form.recorder" />
          </div>
          <div class="form-group">
            <label>记录日期</label>
            <template v-if="isRetestMode">
              <input :value="form.record_data" disabled class="date-readonly retest-date" title="复测自动生成时间戳" />
            </template>
            <template v-else-if="applyTime">
              <div class="date-source-toggle">
                <button type="button" class="date-source-btn" :class="{ active: dateSource === 'applyTime' }" @click="dateSource = 'applyTime'; form.record_data = applyTime">applyTime</button>
                <button type="button" class="date-source-btn" :class="{ active: dateSource === 'today' }" @click="dateSource = 'today'; form.record_data = getTodayStr()">今日日期</button>
              </div>
              <input :value="form.record_data" disabled class="date-readonly" />
            </template>
            <input v-else v-model="form.record_data" />
          </div>
        </div>
        <div class="form-btn-row">
          <button class="btn-save" @click="saveToMySQL" :disabled="saving || recordSubmitted">
            {{ recordSubmitted ? '已入库' : saving ? '入库中...' : '直接入库' }}
          </button>
          <span class="save-success-tag" v-if="recordSubmitted">入库成功</span>
        </div>
      </div>

      <!-- 测试历史 -->
      <div class="history-section">
        <div class="history-header">
          <h3 class="section-title">7日测试历史</h3>
          <div class="history-actions">
            <span v-if="redisStatusMsg" class="redis-status" :class="redisOk ? 'ok' : 'warn'">{{ redisStatusMsg }}</span>
            <button class="btn-history-action" @click="loadHistory">刷新</button>
            <button class="btn-history-action btn-danger" @click="clearHistory" :disabled="historyList.length === 0">清空</button>
          </div>
        </div>
        <div v-if="historyLoading" class="loading">加载历史记录...</div>
        <div v-if="!historyLoading && historyList.length === 0" class="history-empty">暂无测试记录</div>
        <div v-if="historyList.length > 0" class="history-table-wrapper">
          <table class="history-table">
            <thead>
            <tr>
              <th>时间</th>
              <th>Bundle ID</th>
              <th>事件数</th>
              <th>归因</th>
              <th>异常类型</th>
              <th>备注</th>
              <th>记录人</th>
              <th>日期</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(item, idx) in historyList" :key="idx">
              <td>{{ item.timestamp || '-' }}</td>
              <td class="cell-mono">{{ item.bundleId || '-' }}</td>
              <td>{{ item.eventNumber ?? '-' }}</td>
              <td>
                <span v-if="item.ascribe" class="attr-tag-inline">{{ item.ascribe }}</span>
                <span v-else>-</span>
              </td>
              <td>{{ item.exceptionType || '-' }}</td>
              <td class="cell-remark" :title="item.remark">{{ item.remark || '-' }}</td>
              <td>{{ item.recorder || '-' }}</td>
              <td>{{ item.recordData || '-' }}</td>
              <td>
                <button class="btn-del-row" @click="deleteHistoryRecord(item.timestamp)" title="删除">删除</button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 复测弹窗 -->
      <div class="modal-overlay" v-if="showRetestModal" @click.self="showRetestModal = false">
        <div class="modal-box">
          <div class="modal-title">库存已耗尽</div>
          <div class="modal-desc">当前没有可用的下载任务，请选择操作：</div>
          <div class="modal-actions">
            <button class="btn-modal btn-retest" @click="retestFlow" :disabled="retestLoading">
              {{ retestLoading ? '获取中...' : '复测' }}
            </button>
            <button class="btn-modal btn-cancel" @click="showRetestModal = false">取消</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 数据建表和治理 Tab ==================== -->
    <div v-if="devActiveTab === 'governance'" class="gov-section">

      <!-- 表列表 -->
      <div class="gov-card">
        <div class="gov-card-hd">
          <div class="gov-card-hd-left">
            <span class="gov-icon"></span><h3>数据库表列表</h3>
            <span class="gov-badge" v-if="govTables.length">{{ govTables.length }} 张表</span>
          </div>
          <button class="gov-btn-ghost" @click="loadTableList" :disabled="govTableLoading">
            {{ govTableLoading ? '加载中...' : '刷新' }}
          </button>
        </div>
        <div class="gov-card-bd">
          <div v-if="govTableLoading && !govTables.length" class="gov-loading">正在加载...</div>
          <div v-else-if="!govTables.length" class="gov-empty">暂无数据表</div>
          <div v-else class="gov-tbl-list">
            <div v-for="t in govTables" :key="t" class="gov-tbl-item"
                 :class="{ 'gov-tbl-active': govSelectedTable === t }" @click="selectTable(t)">
              <span class="gov-tbl-icon"></span>
              <span class="gov-tbl-name">{{ t }}</span>
              <button class="gov-btn-icon-del" @click.stop="handleDropTable(t)" title="删除表">删除</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 建表 -->
      <div class="gov-card">
        <div class="gov-card-hd"><span class="gov-icon" aria-hidden="true"></span><h3>新建数据表</h3></div>
        <div class="gov-card-bd">
          <div class="gov-field">
            <label>表名</label>
            <input v-model="govCreateTableName" class="gov-input" placeholder="例如: my_new_table" />
          </div>
          <div class="gov-field">
            <label>字段定义（MySQL DDL语法）</label>
            <textarea v-model="govCreateColumns" class="gov-textarea" rows="4"
                      placeholder="示例：&#10;id INT AUTO_INCREMENT PRIMARY KEY,&#10;name VARCHAR(100) NOT NULL,&#10;created_at DATETIME DEFAULT CURRENT_TIMESTAMP"></textarea>
          </div>
          <button class="gov-btn-primary" @click="handleCreateTable" :disabled="govCreating">
            {{ govCreating ? '创建中...' : '创建表' }}
          </button>
          <div v-if="govCreateMsg" class="gov-fb" :class="govCreateMsg.charCodeAt(0) === 9989 ? 'gov-fb-ok' : 'gov-fb-err'">{{ govCreateMsg }}</div>
        </div>
      </div>

      <!-- 表结构 & 字段管理 -->
      <div class="gov-card" v-if="govSelectedTable">
        <div class="gov-card-hd">
          <div class="gov-card-hd-left">
            <span class="gov-icon"></span><h3>表结构：{{ govSelectedTable }}</h3>
            <span class="gov-badge" v-if="govColumns.length">{{ govColumns.length }} 个字段</span>
          </div>
          <button class="gov-btn-ghost" @click="describeTable(govSelectedTable)" :disabled="govDescLoading">
            {{ govDescLoading ? '加载中...' : ' 刷新' }}
          </button>
        </div>
        <div class="gov-card-bd">
          <div v-if="govDescLoading" class="gov-loading">正在加载表结构...</div>
          <div v-if="govColumns.length > 0" class="gov-tbl-wrap">
            <table class="gov-data-tbl">
              <thead><tr><th>字段名</th><th>类型</th><th>可空</th><th>键</th><th>默认值</th><th>额外</th><th>注释</th><th>操作</th></tr></thead>
              <tbody>
              <tr v-for="col in govColumns" :key="col.field">
                <td class="gov-mono">{{ col.field }}</td>
                <td class="gov-mono">{{ col.type }}</td>
                <td>{{ col.null }}</td>
                <td>{{ col.key || '-' }}</td>
                <td>{{ col.default || '-' }}</td>
                <td>{{ col.extra || '-' }}</td>
                <td>{{ col.comment || '-' }}</td>
                <td><button class="gov-btn-sm gov-btn-danger" @click="handleDropColumn(col.field)">删除</button></td>
              </tr>
              </tbody>
            </table>
          </div>

          <div class="gov-sub">
            <h4 class="gov-sub-title">新增字段</h4>
            <div class="gov-inline-row">
              <input v-model="govAddColDef" class="gov-input gov-input-flex" placeholder="字段定义，例如: age INT NOT NULL DEFAULT 0" />
              <button class="gov-btn-primary gov-btn-sm" @click="handleAddColumn" :disabled="govAddColLoading">{{ govAddColLoading ? '...' : '添加' }}</button>
            </div>
            <div v-if="govAddColMsg" class="gov-fb-sm" :class="govAddColMsg.charCodeAt(0) === 9989 ? 'gov-fb-ok' : 'gov-fb-err'">{{ govAddColMsg }}</div>
          </div>

          <div class="gov-sub">
            <h4 class="gov-sub-title">修改字段</h4>
            <div class="gov-inline-row">
              <input v-model="govModifyColDef" class="gov-input gov-input-flex" placeholder="完整字段定义，例如: name VARCHAR(200) NOT NULL COMMENT '名称'" />
              <button class="gov-btn-primary gov-btn-sm" @click="handleModifyColumn" :disabled="govModifyColLoading">{{ govModifyColLoading ? '...' : '修改' }}</button>
            </div>
            <div v-if="govModifyColMsg" class="gov-fb-sm" :class="govModifyColMsg.charCodeAt(0) === 9989 ? 'gov-fb-ok' : 'gov-fb-err'">{{ govModifyColMsg }}</div>
          </div>
        </div>
      </div>

      <!-- SQL 执行器 -->
      <div class="gov-card">
        <div class="gov-card-hd"><span class="gov-icon" aria-hidden="true"></span><h3>SQL 执行器</h3></div>
        <div class="gov-card-bd">
          <div class="gov-field">
            <label>目标表（可选，仅用于快捷填充）</label>
            <select v-model="govSqlTable" class="gov-input">
              <option value="">全局执行（不限表）</option>
              <option v-for="t in govTables" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>
          <div class="gov-field">
            <label>SQL 语句</label>
            <textarea v-model="govSqlText" class="gov-textarea gov-sql-ta" rows="5"
                      placeholder="输入SQL语句，例如：&#10;SELECT * FROM test_static LIMIT 10&#10;UPDATE test_static SET remark='test' WHERE URL='xxx'"></textarea>
          </div>
          <div class="gov-btn-row">
            <button class="gov-btn-primary" @click="handleExecuteSQL" :disabled="govSqlLoading">{{ govSqlLoading ? '执行中...' : '执行SQL' }}</button>
            <button class="gov-btn-ghost" @click="fillSqlTemplate('select')">SELECT模板</button>
            <button class="gov-btn-ghost" @click="fillSqlTemplate('update')">UPDATE模板</button>
            <button class="gov-btn-ghost" @click="fillSqlTemplate('insert')">INSERT模板</button>
          </div>
          <div v-if="govSqlMsg" class="gov-fb" :class="govSqlMsg.charCodeAt(0) === 9989 ? 'gov-fb-ok' : 'gov-fb-err'">{{ govSqlMsg }}</div>
          <div v-if="govSqlResult" class="gov-sql-result">
            <div class="gov-sql-result-hd">
              <span>查询结果</span>
              <span class="gov-sql-rows">{{ govSqlResult.rowCount ?? govSqlResult.affectedRows ?? 0 }} {{ govSqlResult.rowCount !== undefined ? '行' : '行受影响' }}</span>
            </div>
            <div v-if="govSqlResult.rows && govSqlResult.rows.length > 0" class="gov-tbl-wrap">
              <table class="gov-data-tbl gov-sql-tbl">
                <thead><tr><th v-for="key in Object.keys(govSqlResult.rows[0])" :key="key">{{ key }}</th></tr></thead>
                <tbody>
                <tr v-for="(row, idx) in govSqlResult.rows" :key="idx">
                  <td v-for="key in Object.keys(row)" :key="key" class="gov-mono">{{ formatSqlVal(row[key]) }}</td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- 批量导入 -->
      <div class="gov-card">
        <div class="gov-card-hd"><span class="gov-icon"></span><h3>从其他数据库批量导入</h3></div>
        <div class="gov-card-bd">
          <div class="gov-field">
            <label>源数据库 JDBC URL</label>
            <input v-model="govImport.sourceUrl" class="gov-input"
                   placeholder="例如: jdbc:mysql://10.1.14.107:3306/test_data?useSSL=false&serverTimezone=Asia/Shanghai" />
          </div>
          <div class="gov-grid-2">
            <div class="gov-field">
              <label>用户名</label>
              <input v-model="govImport.sourceUsername" class="gov-input" placeholder="root" />
            </div>
            <div class="gov-field">
              <label>密码</label>
              <input v-model="govImport.sourcePassword" type="password" class="gov-input" placeholder="密码" />
            </div>
          </div>
          <button class="gov-btn-ghost" @click="fetchSourceTables" :disabled="govImport.fetching || !govImport.sourceUrl">
            {{ govImport.fetching ? '连接中...' : '连接并获取表列表' }}
          </button>
          <div v-if="govImport.sourceTables.length > 0" class="gov-import-section">
            <label class="gov-import-label">选择要导入的表：</label>
            <div class="gov-cb-grid">
              <label v-for="t in govImport.sourceTables" :key="t" class="gov-cb-item">
                <input type="checkbox" :value="t" v-model="govImport.selectedTables" />
                <span>{{ t }}</span>
              </label>
            </div>
            <label class="gov-cb-item" style="margin-top:12px">
              <input type="checkbox" v-model="govImport.truncateBefore" />
              <span>导入前清空本地同名表数据</span>
            </label>
            <button class="gov-btn-primary gov-btn-import" @click="handleBatchImport"
                    :disabled="govImport.importing || govImport.selectedTables.length === 0">
              {{ govImport.importing ? '导入中...' : ' 开始批量导入' }}
            </button>
          </div>
          <div v-if="govImport.msg" class="gov-fb" :class="govImport.msg.startsWith('') ? 'gov-fb-ok' : 'gov-fb-err'">{{ govImport.msg }}</div>
          <div v-if="govImport.details.length > 0" class="gov-import-details">
            <div v-for="(d, idx) in govImport.details" :key="idx" class="gov-import-detail" :class="'gov-detail-' + d.status">
              <span class="gov-detail-tbl">{{ d.table }}</span>
              <span>{{ d.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="modal-overlay" v-if="dropConfirmVisible" @click.self="closeDropConfirm">
      <div class="modal-box">
        <div class="modal-title">危险操作：删除数据表</div>
        <div class="modal-desc">
          你即将删除表 <strong>{{ dropTargetTable }}</strong>，此操作不可撤销。<br />
          请输入确认密码以继续：
        </div>
        <input
            class="drop-pwd-input"
            type="password"
            v-model="dropPassword"
            placeholder="请输入确认密码"
            @keyup.enter="confirmDropTable"
        />
        <div v-if="dropError" class="drop-pwd-error">{{ dropError }}</div>
        <div class="modal-actions">
          <button class="btn-modal btn-retest" @click="confirmDropTable" :disabled="dropSubmitting">
            {{ dropSubmitting ? '验证并删除中...' : '确认删除' }}
          </button>
          <button class="btn-modal btn-cancel" @click="closeDropConfirm">取消</button>
        </div>
      </div>
    </div>
    <DevGuide :visible="guideVisible" @close="guideVisible = false" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, nextTick, onUnmounted } from 'vue'
import { renderQRToCanvas } from '../utils/qrcode.js'
import { useTemplateOptions } from '../utils/useTemplateOptions.js'
import {
  fetchTask, fetchObtain, fetchEvent, fetchAllAttributions, fetchAttribution, fetchRetestBundle,
  fetchFrozen, uploadFrozenV2, insertRecord, fetchRandomForRetest,
  fetchDevHistory, saveDevHistory, clearDevHistory, fetchDevRedisStatus, deleteDevHistoryRecord,
  fetchTableList, fetchTableDescribe, createTable, dropTable,
  addColumn, modifyColumn, dropColumn, executeSQL, batchImport,
  stopSecondDelivery
} from '../api/index.js'
import { useAppStatus } from '../utils/useAppStatus.js'
import { generateCurrentTimestamp, trimApplyTime, toStorageDate } from '../utils/date.js'
import DevGuide from './DevGuide.vue'
const { taskStatus, statusInfo, setTaskStatus, resetTaskStatus } = useAppStatus()
const emit = defineEmits(['error', 'record-saved'])
const currentVersion = ref('')
const checkVerion = ref('')
const versionMismatch = ref(false)
const versionAppId = ref('')
const stopDeliveryLoading = ref(false)
const stopDeliveryMsg = ref('')
const devActiveTab = ref('test')
const guideVisible = ref(false)
// const exceptionOptions = ['正常','iOS16闪退','iOS13/14/16均闪退','需要iOS18以上','地区不支持','硬件版本过低','超过10分钟0上报','越狱检测','其他','验证已解决','测试']
// const templateOptions = ['需要iOS17以上','需要登陆后使用，无法注册','卡死在加载页进不去','非英语汉语软件，看不懂','网络检测，无法进入','需要订阅后使用','禁止入库']
const ATTR_TYPES = ['appflyer', 'adjust', 'singular', 'tenjin']
// const frozenReasonOptions = ['需要iOS17以上','需要登陆后使用，无法注册','卡死在加载页进不去','非英语汉语软件，看不懂','网络检测，无法进入','需要订阅后使用','事件机制成因不明','逻辑混乱，无法正常游玩或使用']
const MAX_RETRIES = 5
const { exceptionOptions, remarkTemplateOptions, frozenReasonOptions, loadTemplateOptions, preloadAllTemplateOptions } = useTemplateOptions()
const downloadUrl = ref('')
const bundleId = ref('')
const originalCurrentTargetNum = ref(null)
const eventResult = ref('')
const newCurrentTargetNum = ref(null)
const attributions = ref([])
const eventId = ref(null)
const taskId = ref(null)
const frozenMsg = ref('')
const frozenDone = ref(false)
const frozenReasonText = ref('')
const frozenReasonSelect = ref('')
const duplicateTip = ref('')
const loading = ref(false)
const eventLoading = ref(false)
const frozenLoading = ref(false)
const saving = ref(false)
const appId = ref('')
const showRetestModal = ref(false)
const retestLoading = ref(false)
const qrCanvas = ref(null)
const timerSeconds = ref(60)
const timerCountdown = ref(0)
const timerMsg = ref('')
const applyTime = ref('')
const appIds = ref('')
const recordSubmitted = ref(false)
const isRetestMode = ref(false)

let timerInterval = null
let isFrozen = ref('')
let isSubmit = true
let retryCount = 0
let retryTimer = null

const attrStates = reactive({
  appflyer: { bundleId: '', data: [], loading: false, count: null, queried: false },
  adjust: { bundleId: '', data: [], loading: false, count: null, queried: false },
  singular: { bundleId: '', data: [], loading: false, count: null, queried: false },
  tenjin: { bundleId: '', data: [], loading: false, count: null, queried: false }
})

const form = reactive({
  exception_type: '',
  remark: '',
  recorder: localStorage.getItem('userName') || '',
  record_data: getTodayStr()
})
const frozenForm = reactive({ bundleId: '', id: null, remark: '' })
// ========== 删表密码确认 Modal 状态 ==========
const dropConfirmVisible = ref(false)
const dropTargetTable = ref('')
const dropPassword = ref('')
const dropSubmitting = ref(false)
const dropError = ref('')

const historyList = ref([])
const historyLoading = ref(false)
const redisOk = ref(true)
const redisStatusMsg = ref('')
const lastReportTime = ref('')
const dateSource = ref('applyTime')

// ========== 数据治理 Tab 状态 ==========
const govTables = ref([])
const govTableLoading = ref(false)
const govSelectedTable = ref('')
const govColumns = ref([])
const govDescLoading = ref(false)
const govCreateTableName = ref('')
const govCreateColumns = ref('')
const govCreating = ref(false)
const govCreateMsg = ref('')
const govAddColDef = ref('')
const govAddColLoading = ref(false)
const govAddColMsg = ref('')
const govModifyColDef = ref('')
const govModifyColLoading = ref(false)
const govModifyColMsg = ref('')
const govSqlTable = ref('')
const govSqlText = ref('')
const govSqlLoading = ref(false)
const govSqlMsg = ref('')
const govSqlResult = ref(null)
const govImport = reactive({
  sourceUrl: '', sourceUsername: 'root', sourcePassword: '',
  sourceTables: [], selectedTables: [], truncateBefore: false,
  fetching: false, importing: false, msg: '', details: []
})

// ========== 工具函数 ==========

function getTodayStr() {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return d.getFullYear() + '-' + mm + '-' + dd
}

function formatAttrValue(val) {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function generateSubmissionId() {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes)
      .map(b => b.toString(16).padStart(2, '0'))
      .join('')
}

// ========== Watch ==========

watch(downloadUrl, async (val) => { await nextTick(); renderQRToCanvas(qrCanvas.value, val) })

watch(devActiveTab, (val) => {
  if (val === 'test' && downloadUrl.value) {
    nextTick(() => renderQRToCanvas(qrCanvas.value, downloadUrl.value))
  }
})

// ========== 测试 Tab 函数 ==========

function fillAttrBundleIds(bid) {
  for (const type of ATTR_TYPES) {
    attrStates[type].bundleId = bid
  }
}

function confirmRefresh() {
  if (!isSubmit && downloadUrl.value) {
    if (confirm('请确认是否已入库该条测试数据，刷新会导致数据丢失！')) {
      fetchData()
    }
  } else {
    fetchData()
  }
}

async function fetchData() {
  loading.value = true
  emit('error', '')
  resetTaskState()
  if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
  retryCount = 0
  try {
    const json = await fetchTask()
    if (json.data) {
      if (json.duplicate) {
        retryCount++
        if (retryCount > MAX_RETRIES) {
          emit('error', '已连续 ' + MAX_RETRIES + ' 次检测到重复URL，请稍后再试')
          duplicateTip.value = ''
          return
        }
        duplicateTip.value = '检测到重复URL（第' + retryCount + '次），正在为您自动刷新...'
        retryTimer = setTimeout(() => fetchData(), 1500)
        return
      }
      retryCount = 0
      duplicateTip.value = ''
      downloadUrl.value = json.data.downloadUrl || ''
      bundleId.value = json.data.bundleId || ''
      appIds.value = json.data.appId || ''
      applyTime.value = trimApplyTime(json.data.applyTime || '')
      taskId.value = json.data.id ?? null
      originalCurrentTargetNum.value = json.data.currentTargetNum ?? null
      setTaskStatus(json.data.status)
      isSubmit = false
      fillAttrBundleIds(bundleId.value)
      if (originalCurrentTargetNum.value <= 1 || originalCurrentTargetNum.value === 0) {
        alert('初始originalCurrentTargetNum数小于等于1或等于0，后续测试期间可能会没有归因，请注意')
      }
    } else {
      showRetestModal.value = true
    }
  } catch (e) {
    if (e.name === 'AbortError') emit('error', '请求超时，远程服务器响应太慢')
    else emit('error', '请求失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function fetchByAppId() {
  const id = appId.value.trim()
  if (!id) return
  loading.value = true
  emit('error', '')
  resetTaskState()
  try {
    const json = await fetchObtain(id)
    if (json.success && json.data) {
      if (json.duplicate) {
        retryCount++
        if (retryCount > MAX_RETRIES) {
          emit('error', '已连续 ' + MAX_RETRIES + ' 次检测到重复URL，请稍后再试')
          duplicateTip.value = ''
          return
        }
        duplicateTip.value = '检测到重复URL（第' + retryCount + '次），正在为您自动刷新...'
        retryTimer = setTimeout(() => fetchByAppId(), 1500)
        return
      }
      retryCount = 0
      duplicateTip.value = ''
      downloadUrl.value = json.data.downloadUrl || ''
      bundleId.value = json.data.bundleId || ''
      appIds.value = json.data.appId || ''
      applyTime.value = trimApplyTime(json.data.applyTime || '')
      originalCurrentTargetNum.value = json.data.currentTargetNum ?? null
      setTaskStatus(json.data.status)
      isSubmit = false
      taskId.value = json.data.id ?? null
      fillAttrBundleIds(bundleId.value)
      if (originalCurrentTargetNum.value <= 1 || originalCurrentTargetNum.value === 0) {
        alert('初始originalCurrentTargetNum数小于等于1或等于0，后续测试期间可能会没有归因，请注意')
      }
    } else {
      emit('error', '接口返回异常：' + (json.resultMsg || '未知错误'))
    }
  } catch (e) {
    if (e.name === 'AbortError') emit('error', '请求超时')
    else emit('error', '请求失败：' + e.message)
  } finally {
    loading.value = false
  }
}

function resetState() {
  downloadUrl.value = ''
  bundleId.value = ''
  originalCurrentTargetNum.value = null
  eventResult.value = ''
  newCurrentTargetNum.value = null
  attributions.value = []
  eventId.value = null
  frozenMsg.value = ''
  taskId.value = null
  duplicateTip.value = ''
  dateSource.value = 'applyTime'
  applyTime.value = ''
  lastReportTime.value = ''
  recordSubmitted.value = false
  isRetestMode.value = false
  form.exception_type = ''
  form.remark = ''
  form.recorder = localStorage.getItem('userName') || ''
  form.record_data = getTodayStr()
  isFrozen.value = ''
  frozenDone.value = false
  resetTaskStatus()
  frozenReasonText.value = ''
  frozenReasonSelect.value = ''
  currentVersion.value = ''
  checkVerion.value = ''
  versionMismatch.value = false
  versionAppId.value = ''
  stopDeliveryMsg.value = ''
}
async function stopDelivery() {
  if (!versionAppId.value || !checkVerion.value) {
    emit('error', '缺少 appId 或待校验版本，无法停止二次下发')
    return
  }
  stopDeliveryLoading.value = true
  stopDeliveryMsg.value = ''
  emit('error', '')
  try {
    const json = await stopSecondDelivery(versionAppId.value, checkVerion.value)
    if (json.success) {
      versionMismatch.value = false
      stopDeliveryMsg.value = '已停止该应用二次下发'
    } else {
      emit('error', '停止二次下发失败：' + (json.message || '未知错误'))
    }
  } catch (e) {
    emit('error', '停止二次下发请求失败：' + e.message)
  } finally {
    stopDeliveryLoading.value = false
  }
}
function resetTaskState() {
  resetState()
  for (const type of ATTR_TYPES) {
    attrStates[type].data = []
    attrStates[type].count = null
    attrStates[type].queried = false
  }
}

async function retestFlow() {
  retestLoading.value = true
  resetState()
  isRetestMode.value = true
  try {
    const json = await fetchRetestBundle()
    if (json.success && json.data && json.data.bundleId) {
      showRetestModal.value = false
      bundleId.value = json.data.bundleId
      form.record_data = generateCurrentTimestamp()
      try {
        const jsons = await fetchEvent(bundleId.value)
        if (jsons.success && jsons.data) {
          originalCurrentTargetNum.value = jsons.data.currentTargetNum ?? null
          const rawAppId = jsons.data.appId || ''
          appId.value = rawAppId
        }
        if (appId.value && !isNaN(Number(appId.value))) {
          const obt = await fetchObtain(appId.value)
          if (obt.success && obt.data) {
            downloadUrl.value = obt.data.downloadUrl || ''
            setTaskStatus(obt.data.status)
          }
        }
      } catch (e) {
        appId.value = ''
        alert('服务器无响应，请联系技术人员')
      }
      isSubmit = false
    } else {
      isRetestMode.value = false
      emit('error', json.message || '获取复测数据失败，Redis中暂无可复测数据')
    }
  } catch (e) {
    isRetestMode.value = false
    emit('error', '复测请求失败：' + e.message)
  } finally {
    retestLoading.value = false
  }
}

async function queryEvent() {
  if (!bundleId.value) return
  eventLoading.value = true
  emit('error', '')
  eventResult.value = ''; newCurrentTargetNum.value = null; attributions.value = []; eventId.value = null; frozenMsg.value = ''
  lastReportTime.value = ''
  try {
    const [eventJson, attrResults] = await Promise.all([
      fetchEvent(bundleId.value),
      fetchAllAttributions(bundleId.value)
    ])
    if (eventJson.success && eventJson.data) {
      const newCurrent = eventJson.data.currentTargetNum
      eventId.value = eventJson.data.id ?? null
      currentVersion.value = eventJson.data.currentVersion || ''
      checkVerion.value = eventJson.data.checkVerion || ''
      if (currentVersion.value && checkVerion.value && currentVersion.value !== checkVerion.value) {
        versionMismatch.value = true
        versionAppId.value = eventJson.data.appId || appIds.value || appId.value || ''
      } else {
        versionMismatch.value = false
      }
      eventId.value = eventJson.data.id ?? null
      if (originalCurrentTargetNum.value !== null) {
        if (newCurrent !== originalCurrentTargetNum.value) {
          eventResult.value = 'has_event'
          newCurrentTargetNum.value = newCurrent
        } else {
          eventResult.value = 'no_event'
        }
      } else {
        newCurrentTargetNum.value = newCurrent
        eventResult.value = newCurrent != null && newCurrent > 0 ? 'has_event' : 'no_event'
      }
    } else {
      emit('error', '事件接口返回异常：' + (eventJson.resultMsg || '未知错误'))
    }
    const found = []
    for (const { type, json } of attrResults) {
      if (json.success && json.data) {
        if (Array.isArray(json.data) && json.data.length > 0) found.push(type)
        else if (!Array.isArray(json.data) && Object.keys(json.data).length > 0) found.push(type)
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
    if (!isRetestMode.value) {
      if (dateSource.value === 'applyTime') form.record_data = applyTime.value || lrt
      else form.record_data = getTodayStr()
    }
  } catch (e) {
    emit('error', '事件查询失败：' + e.message)
  } finally {
    if (isRetestMode.value) {
      form.record_data = generateCurrentTimestamp()
    }
    eventLoading.value = false
  }
}

async function queryEventWithFullMsg() {
  try {
    const data = await fetchEvent(bundleId.value)
    if (data.success) alert(JSON.stringify(data, null, 2))
  } catch (e) {
    alert('服务器无响应，请联系技术人员')
  }
}

async function querySingleAttr(type) {
  const bid = attrStates[type].bundleId
  if (!bid) return
  attrStates[type].loading = true
  attrStates[type].data = []
  attrStates[type].count = null
  attrStates[type].queried = false
  try {
    const json = await fetchAttribution(bid, type)
    if (json.success) {
      let records = []
      if (Array.isArray(json.data)) {
        records = json.data
      } else if (json.data && typeof json.data === 'object') {
        records = [json.data]
      }
      attrStates[type].data = records
      attrStates[type].count = records.length
    } else {
      attrStates[type].data = []
      attrStates[type].count = 0
    }
  } catch (e) {
    attrStates[type].data = []
    attrStates[type].count = 0
  } finally {
    attrStates[type].loading = false
    attrStates[type].queried = true
  }
}

async function doFrozen() {
  if (frozenDone.value) return
  if (!taskId.value) { emit('error', '当前任务缺少 taskId，请点“刷新数据”获取任务或用有效 App ID 生成后再冻结'); return }
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

async function saveToMySQL() {
  emit('record-saved')
  if (recordSubmitted.value) return
  if (!form.exception_type.trim()) { emit('error', '请选择异常类型'); return }
  saving.value = true
  if (newCurrentTargetNum.value === 0 || newCurrentTargetNum.value === null) {
    attributions.value = []
    alert('无新增事件token，即使原本的token有归因，也不会被设置在字段内')
  }
  let finalRemark = eventResult.value === 'no_event' ? frozenReasonText.value.trim() : form.remark.trim()
  if (isFrozen.value) {
    finalRemark += isFrozen.value
  }
  if (newCurrentTargetNum.value > 0 && attributions.value.length === 0) {
    finalRemark += ',无事件归因'
  }
  if (isRetestMode.value) {
    form.record_data = generateCurrentTimestamp()
  }
  const finalRecordData = form.record_data.includes('T')
      ? form.record_data
      : form.record_data.includes('/')
          ? toStorageDate(form.record_data.split('/').reverse().join('-'))
          : toStorageDate(form.record_data)
  const numericAppId = appId.value && !isNaN(Number(appId.value)) ? Number(appId.value) : null
  try {
    const json = await insertRecord({
      URL: downloadUrl.value,
      bundleId: bundleId.value,
      ascribe: (attributions.value || []).join(';'),
      appId: numericAppId,
      taskId: taskId.value,
      event_number: newCurrentTargetNum.value,
      exception_type: form.exception_type.trim(),
      record_data: finalRecordData,
      recorder: form.recorder,
      remark: finalRemark,
      isOutput: 0,
      submissionId: generateSubmissionId()
    })
    if (json.success) {
      recordSubmitted.value = true
      isSubmit = true
      await saveToHistory(finalRemark, finalRecordData)
      await loadHistory()
    } else {
      emit('error', json.resultMsg || '入库失败')
    }
  } catch (e) {
    emit('error', '入库请求失败：' + e.message)
  } finally {
    saving.value = false
  }
}

async function saveToHistory(remark, recordData) {
  try {
    await saveDevHistory({
      bundleId: bundleId.value,
      ascribe: (attributions.value || []).join(';'),
      eventNumber: newCurrentTargetNum.value,
      exceptionType: form.exception_type.trim(),
      recordData: recordData || form.record_data,
      recorder: form.recorder,
      remark: remark || form.remark.trim()
    })
  } catch (e) {
    console.warn('保存历史失败:', e)
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const json = await fetchDevHistory()
    if (json.success) {
      historyList.value = json.data || []
      if (json.message && json.message.includes('Redis不可用')) {
        redisOk.value = false
        redisStatusMsg.value = 'Redis不可用，使用本地缓存'
      } else {
        redisOk.value = true
        redisStatusMsg.value = 'Redis正常'
      }
    }
  } catch (e) {
    console.warn('加载历史失败:', e)
  } finally {
    historyLoading.value = false
  }
}

async function clearHistory() {
  if (!confirm('确定清空所有测试历史记录？')) return
  try {
    await clearDevHistory()
    historyList.value = []
  } catch (e) {
    console.warn('清空历史失败:', e)
  }
}

async function deleteHistoryRecord(timestamp) {
  if (!timestamp) return
  try {
    await deleteDevHistoryRecord(timestamp)
    historyList.value = historyList.value.filter(h => h.timestamp !== timestamp)
  } catch (e) {
    console.warn('删除记录失败:', e)
  }
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
        timerMsg.value = '定时查询已完成'
        setTimeout(() => { timerMsg.value = '计时结束后内容会在这里显示' }, 5000)
      })
    }
  }, 1000)
}

function cancelTimer() {
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
  timerCountdown.value = 0
  timerMsg.value = ''
}

onMounted(() => {
  timerMsg.value = '计时结束后内容会在这里显示'
  loadHistory()

})
onMounted(() => { preloadAllTemplateOptions() })
onUnmounted(() => {
  cancelTimer()
  if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
})

// ========== 数据治理 Tab 函数（不动） ==========

function formatSqlVal(val) {
  if (val === null || val === undefined) return 'NULL'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

async function loadTableList() {
  govTableLoading.value = true
  try {
    const json = await fetchTableList()
    if (json.success) {
      govTables.value = json.data?.tables || []
    } else {
      emit('error', json.message || '获取表列表失败')
    }
  } catch (e) {
    emit('error', '获取表列表失败：' + e.message)
  } finally {
    govTableLoading.value = false
  }
}

async function selectTable(tableName) {
  govSelectedTable.value = tableName
  govAddColMsg.value = ''
  govModifyColMsg.value = ''
  govAddColDef.value = ''
  govModifyColDef.value = ''
  await describeTable(tableName)
}

async function describeTable(tableName) {
  govDescLoading.value = true
  govColumns.value = []
  try {
    const json = await fetchTableDescribe(tableName)
    if (json.success) {
      govColumns.value = json.data?.columns || []
    } else {
      emit('error', json.message || '获取表结构失败')
    }
  } catch (e) {
    emit('error', '获取表结构失败：' + e.message)
  } finally {
    govDescLoading.value = false
  }
}

async function handleCreateTable() {
  if (!govCreateTableName.value.trim()) { emit('error', '请输入表名'); return }
  if (!govCreateColumns.value.trim()) { emit('error', '请输入字段定义'); return }
  govCreating.value = true
  govCreateMsg.value = ''
  try {
    const json = await createTable(govCreateTableName.value.trim(), govCreateColumns.value.trim())
    govCreateMsg.value = json.success ? '✅ ' + json.message : '❌ ' + json.message
    if (json.success) { govCreateTableName.value = ''; govCreateColumns.value = ''; await loadTableList() }
  } catch (e) {
    govCreateMsg.value = '❌ 建表请求失败：' + e.message
  } finally {
    govCreating.value = false
  }
}

function handleDropTable(tableName) {
  dropTargetTable.value = tableName
  dropPassword.value = ''
  dropError.value = ''
  dropConfirmVisible.value = true
}

function closeDropConfirm() {
  dropConfirmVisible.value = false
  dropTargetTable.value = ''
  dropPassword.value = ''
  dropError.value = ''
}

async function confirmDropTable() {
  if (dropSubmitting.value) return
  if (!dropPassword.value) { dropError.value = '请输入确认密码'; return }
  dropSubmitting.value = true
  dropError.value = ''
  try {
    const json = await dropTable(dropTargetTable.value, dropPassword.value)
    if (json.success) {
      const t = dropTargetTable.value
      if (govSelectedTable.value === t) { govSelectedTable.value = ''; govColumns.value = [] }
      closeDropConfirm()
      await loadTableList()
    } else {
      dropError.value = json.message || '删表失败'
    }
  } catch (e) {
    dropError.value = '删表请求失败：' + e.message
  } finally {
    dropSubmitting.value = false
  }
}

async function handleAddColumn() {
  if (!govSelectedTable.value) return
  if (!govAddColDef.value.trim()) { emit('error', '请输入字段定义'); return }
  govAddColLoading.value = true
  govAddColMsg.value = ''
  try {
    const json = await addColumn(govSelectedTable.value, govAddColDef.value.trim())
    govAddColMsg.value = json.success ? '✅ ' + json.message : '❌ ' + json.message
    if (json.success) { govAddColDef.value = ''; await describeTable(govSelectedTable.value) }
  } catch (e) {
    govAddColMsg.value = '❌ 新增字段失败：' + e.message
  } finally {
    govAddColLoading.value = false
  }
}

async function handleModifyColumn() {
  if (!govSelectedTable.value) return
  if (!govModifyColDef.value.trim()) { emit('error', '请输入字段定义'); return }
  govModifyColLoading.value = true
  govModifyColMsg.value = ''
  try {
    const json = await modifyColumn(govSelectedTable.value, govModifyColDef.value.trim())
    govModifyColMsg.value = json.success ? '✅ ' + json.message : '❌ ' + json.message
    if (json.success) { govModifyColDef.value = ''; await describeTable(govSelectedTable.value) }
  } catch (e) {
    govModifyColMsg.value = '❌ 修改字段失败：' + e.message
  } finally {
    govModifyColLoading.value = false
  }
}

async function handleDropColumn(colName) {
  if (!govSelectedTable.value) return
  if (!confirm(`确定要删除字段 "${colName}" 吗？`)) return
  try {
    const json = await dropColumn(govSelectedTable.value, colName)
    if (json.success) { await describeTable(govSelectedTable.value) }
    else { emit('error', json.message || '删除字段失败') }
  } catch (e) {
    emit('error', '删除字段请求失败：' + e.message)
  }
}

function fillSqlTemplate(type) {
  const t = govSqlTable.value || 'test_static'
  if (type === 'select') govSqlText.value = `SELECT * FROM ${t} LIMIT 20`
  else if (type === 'update') govSqlText.value = `UPDATE ${t} SET 字段名 = '新值' WHERE 条件字段 = '条件值'`
  else if (type === 'insert') govSqlText.value = `INSERT INTO ${t} (字段1, 字段2) VALUES ('值1', '值2')`
}

async function handleExecuteSQL() {
  if (!govSqlText.value.trim()) { emit('error', '请输入SQL语句'); return }
  govSqlLoading.value = true
  govSqlMsg.value = ''
  govSqlResult.value = null
  try {
    const json = await executeSQL(govSqlText.value.trim(), govSqlTable.value)
    govSqlMsg.value = json.success ? '✅ ' + json.message : '❌ ' + json.message
    if (json.success && json.data) { govSqlResult.value = json.data }
  } catch (e) {
    govSqlMsg.value = '❌ 执行SQL失败：' + e.message
  } finally {
    govSqlLoading.value = false
  }
}

async function fetchSourceTables() {
  govImport.fetching = true
  govImport.sourceTables = []
  govImport.selectedTables = []
  govImport.msg = ''
  govImport.details = []
  try {
    const json = await executeSQL('SHOW TABLES', '', govImport.sourceUrl, govImport.sourceUsername, govImport.sourcePassword)
    if (json.success && json.data?.rows) {
      govImport.sourceTables = json.data.rows.map(r => Object.values(r)[0])
    } else {
      govImport.msg = '❌ ' + (json.message || '无法获取源数据库表列表')
    }
  } catch (e) {
    govImport.msg = '❌ 连接源数据库失败：' + e.message
  } finally {
    govImport.fetching = false
  }
}

async function handleBatchImport() {
  if (govImport.selectedTables.length === 0) return
  govImport.importing = true
  govImport.msg = ''
  govImport.details = []
  try {
    const json = await batchImport({
      sourceUrl: govImport.sourceUrl,
      sourceUsername: govImport.sourceUsername,
      sourcePassword: govImport.sourcePassword,
      tables: govImport.selectedTables,
      truncateBefore: govImport.truncateBefore
    })
    govImport.msg = json.success ? '✅ ' + json.message : '❌ ' + json.message
    if (json.success && json.data?.details) { govImport.details = json.data.details }
    if (json.success) { await loadTableList() }
  } catch (e) {
    govImport.msg = '❌ 批量导入请求失败：' + e.message
  } finally {
    govImport.importing = false
  }
}
</script>


<style scoped>
.dev-mode {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  color: var(--text-primary);
  font-family: var(--font-sans);
  --panel-muted: #f6f1e7;
  --panel-hover: #f6f1e7;
}
.dev-mode *, .dev-mode *::before, .dev-mode *::after { box-sizing: border-box; }
.dev-mode button, .dev-mode input, .dev-mode textarea, .dev-mode select { font: inherit; }
.dev-mode button { -webkit-tap-highlight-color: transparent; }
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
  border: 1px solid #e2ddd4;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 650;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(201,106,69,0.1);
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
  background: #fffdf6;
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
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.22), 0 5px 14px rgba(201,106,69,0.14);
}
.btn-event {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: #e6d3c2;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.7);
}
.btn-timer { color: var(--accent-strong); background: #fff; border-color: var(--border-color); }
.btn-timer-cancel, .btn-cancel { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.btn-frozen { margin-top: 10px; color: #276b83; background: var(--info-soft); border-color: #dbe4ea; }
.btn-refresh:hover:not(:disabled), .btn-event:hover:not(:disabled), .btn-save:hover:not(:disabled),
.btn-frozen:hover:not(:disabled), .btn-timer:hover:not(:disabled), .btn-timer-cancel:hover:not(:disabled),
.btn-modal:hover:not(:disabled), .btn-grade:hover:not(:disabled) { transform: translateY(-1px); box-shadow: var(--shadow-md); }
.btn-refresh:hover:not(:disabled), .btn-save:hover:not(:disabled) { background: var(--accent-strong); }
.btn-event:hover:not(:disabled), .btn-timer:hover:not(:disabled) { background: #f1e3d5; border-color: #e0cbb4; }
.dev-mode button:disabled { opacity: 0.48; cursor: not-allowed; transform: none; box-shadow: none; }
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
  border: 2px solid #ddd0bb;
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
  background-image: linear-gradient(rgba(201,106,69,0.025) 1px, transparent 1px), linear-gradient(90deg, rgba(201,106,69,0.025) 1px, transparent 1px);
  background-size: 24px 24px;
  border: 1px dashed #ddd0bb;
  border-radius: var(--radius-lg);
}
.empty-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  margin-bottom: 15px;
  color: var(--accent-strong);
  background: rgba(248,233,223,0.86);
  border: 1px solid #e6d3c2;
  border-radius: 18px;
  box-shadow: inset 0 1px 0 #fff, 0 8px 22px rgba(201,106,69,0.07);
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
  border: 1px solid #e6ddcb;
  border-radius: 12px;
  box-shadow: 0 7px 22px rgba(70,55,35,0.06), inset 0 1px 0 #fff;
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
.app-status-badge.normal { color: #176e52; background: var(--success-soft); border-color: #d9e4d0; }
.app-status-badge.frozen { color: #276b83; background: var(--info-soft); border-color: #dbe4ea; }
.app-status-badge.unknown { color: #b04b45; background: var(--danger-soft); border-color: #f0d6cc; }
.side-info { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: center; gap: 9px; min-width: 0; padding: 16px; }
.info-card {
  min-width: 0;
  padding: 11px 12px;
  background: #fffdf6;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  transition: border-color 160ms ease, background 160ms ease;
}
.info-card:hover { background: #fff; border-color: #ddd0bb; }
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
.version-card .ver-mismatch { padding: 4px 8px; color: #b04b45; background: var(--danger-soft); border: 1px solid #f0d6cc; border-radius: 7px; font-weight: 700; }
.btn-stop-delivery { min-height: 32px; margin-top: 9px; padding: 6px 12px; color: #b04b45; background: var(--danger-soft); border: 1px solid #f0d6cc; border-radius: 8px; font-size: 10px; font-weight: 650; cursor: pointer; }
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
  background: #fffdf6;
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
.event-result { padding: 12px 13px; background: var(--success-soft); border: 1px solid #d9e4d0; border-radius: 11px; font-size: 11px; line-height: 1.7; }
.event-result.no-event { text-align: left; background: #fffdf6; border-color: var(--border-color); }
.event-label, .frozen-label { margin-bottom: 3px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; font-weight: 700; letter-spacing: 0.6px; text-transform: uppercase; }
.event-value { color: var(--text-primary); font-size: 12px; font-weight: 650; }
.event-value.highlight { color: var(--accent-strong); font-family: var(--font-mono); font-size: 22px; line-height: 1.3; }
.event-compare { margin-top: 8px; padding: 7px 9px; color: var(--text-secondary); background: rgba(255,255,255,0.72); border: 1px solid #e6ddcb; border-radius: 8px; font-size: 10px; }
.event-compare .diff { color: var(--accent-strong); font-family: var(--font-mono); font-weight: 700; }
.attribution-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.attr-tag { display: inline-flex; align-items: center; min-height: 22px; padding: 3px 8px; color: #2f6b46; background: #e8f1e6; border: 1px solid #cfe2c8; border-radius: 6px; font-family: var(--font-mono); font-size: 9px; font-weight: 700; }
.attr-tag.appflyer { color: #2f6b46; background: #e8f1e6; border-color: #cfe2c8; }
.attr-tag.adjust { color: #b5651d; background: #fbe6cf; border-color: #f0cfa8; }
.attr-tag.singular { color: #3a6ea5; background: #dbeafe; border-color: #bcd4f0; }
.attr-tag.tenjin { color: #6b4fa6; background: #ece0f7; border-color: #d7c4ee; }
.no-attribution { display: inline-block; margin-top: 8px; padding: 5px 8px; color: #8c5c17; background: var(--warning-soft); border: 1px solid #eddbb7; border-radius: 7px; font-size: 10px; font-weight: 650; }
.event-placeholder { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 70px; padding: 12px; color: var(--text-tertiary); background: var(--panel-muted); border: 1px dashed #ddd0bb; border-radius: 10px; font-size: 10px; }
.event-placeholder-icon { position: relative; width: 15px; height: 15px; border: 1.5px solid #a99f8c; border-radius: 50%; }
.event-placeholder-icon::after { content: ''; position: absolute; right: -4px; bottom: -3px; width: 6px; height: 1.5px; background: #a99f8c; transform: rotate(45deg); border-radius: 2px; }
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
.frozen-reason-select:disabled { color: var(--text-tertiary); background: #f3eee4; cursor: not-allowed; }
.frozen-remark-tip { margin-top: 8px; color: #8c5c17; font-size: 10px; line-height: 1.6; }
.frozen-result { margin-top: 9px; padding: 9px 11px; color: #276b83; background: var(--info-soft); border: 1px solid #dbe4ea; border-radius: 9px; }
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
  background: #fffdf6;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  outline: none;
  font-size: 11px;
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}
.form-group textarea { min-height: 76px; resize: vertical; line-height: 1.7; }
.form-group input:focus, .form-group select:focus, .form-group textarea:focus { background: #fff; border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.form-group input:disabled { color: var(--text-secondary); background: #f3eee4; cursor: not-allowed; }
.form-group br { display: none; }
.form-group textarea + select, .form-group br + select { margin-top: 8px; }
.date-source-toggle { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 7px; }
.date-source-btn { min-height: 28px; padding: 4px 9px; color: var(--text-secondary); background: #fff; border: 1px solid var(--border-color); border-radius: 7px; font-size: 9px; font-weight: 600; cursor: pointer; }
.date-source-btn.active { color: var(--accent-strong); background: var(--accent-soft); border-color: #e6d3c2; }
.date-readonly { font-family: var(--font-mono); }
.retest-date { color: var(--accent-strong) !important; background: var(--accent-soft) !important; border-color: #e6d6c0 !important; }
.form-btn-row { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin-top: 4px; }
.save-success-tag { display: inline-flex; align-items: center; min-height: 30px; padding: 5px 10px; color: #176e52; background: var(--success-soft); border: 1px solid #d9e4d0; border-radius: 8px; font-size: 10px; font-weight: 650; }
.save-success { margin-top: 10px; padding: 9px 11px; color: #176e52; background: var(--success-soft); border: 1px solid #d9e4d0; border-radius: 9px; font-size: 11px; }
.auto-normal-tag { display: inline-flex; padding: 7px 12px; color: #176e52; background: var(--success-soft); border: 1px solid #d9e4d0; border-radius: 8px; font-size: 11px; font-weight: 650; }
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
  background: rgba(45,35,22,0.48);
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
  background: linear-gradient(145deg, rgba(255,255,255,0.96), rgba(250,246,238,0.9));
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
.feedback-ok { color: #176e52; background: var(--success-soft); border: 1px solid #d9e4d0; }
.feedback-err { color: #b04b45; background: var(--danger-soft); border: 1px solid #f0d6cc; }
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
  .dev-mode *, .dev-mode *::before, .dev-mode *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}

.dev-mode { max-width: 1180px; }
.dev-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 16px; padding: 0 2px; }
.dev-heading { min-width: 0; }
.dev-badge { display: inline-flex; align-items: center; gap: 7px; margin-bottom: 6px; padding: 0; color: var(--accent); background: none; border: 0; font-family: var(--font-mono); font-size: 9px; line-height: 1.5; font-weight: 700; letter-spacing: 1.1px; text-transform: uppercase; }
.dev-badge::before { content: ''; width: 6px; height: 6px; background: var(--accent); border-radius: 2px; box-shadow: 0 0 0 3px rgba(201,106,69,0.09); }
.dev-title { margin: 0; color: var(--text-primary); font-size: 20px; line-height: 1.4; font-weight: 750; letter-spacing: -0.35px; }
.dev-subtitle { margin: 5px 0 0; color: var(--text-secondary); font-size: 11px; line-height: 1.7; }
.dev-top-bar { display: flex; justify-content: flex-end; margin: 0; }
.dev-guide-btn { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 36px; padding: 7px 13px; color: var(--accent-strong); background: rgba(255,255,255,0.7); border: 1px solid #dcd7cf; border-radius: 10px; box-shadow: inset 0 1px 0 #fff, var(--shadow-sm); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); font-size: 10px; font-weight: 650; cursor: pointer; transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease; }
.dev-guide-btn::before { content: '?'; display: grid; place-items: center; width: 16px; height: 16px; color: #fff; background: var(--accent); border-radius: 50%; font-family: var(--font-mono); font-size: 9px; }
.dev-guide-btn:hover { transform: translateY(-1px); background: #fff; box-shadow: var(--shadow-md); }
.dev-tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px; margin-bottom: 18px; padding: 4px; background: #f3ede0; border: 1px solid var(--border-color); border-radius: 12px; }
.dev-tab { min-height: 38px; padding: 8px 12px; color: var(--text-secondary); background: transparent; border: 1px solid transparent; border-radius: 9px; font-size: 11px; font-weight: 650; cursor: pointer; transition: color 160ms ease, background 160ms ease, box-shadow 160ms ease; }
.dev-tab.active { color: var(--accent-strong); background: rgba(255,255,255,0.94); border-color: rgba(255,255,255,0.9); box-shadow: 0 2px 8px rgba(70,55,35,0.06), inset 0 1px 0 #fff; }
.dev-tab:not(.active):hover { color: var(--text-primary); background: rgba(255,255,255,0.45); }
.test-tab, .gov-section { animation: dev-enter 240ms ease both; }
@keyframes dev-enter { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
.test-tab > .top-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
.test-tab > .top-bar:has(.duplicate-tip) { flex-wrap: wrap; }
.test-tab > .top-bar .duplicate-tip { flex: 1; margin: 0; }
.test-tab > .top-bar .top-actions { margin: 0 0 0 auto; }
.attr-section { margin: 20px 0; }
.section-title { display: flex; align-items: center; gap: 9px; margin: 0 0 12px; color: var(--text-primary); font-size: 13px; line-height: 1.5; font-weight: 700; }
.section-title::before { content: ''; width: 3px; height: 14px; background: var(--accent); border-radius: 3px; }
.attr-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.attr-panel { position: relative; min-width: 0; padding: 15px; overflow: hidden; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
.attr-panel::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 2px; background: #2f6b46; }
.attr-panel.adjust::before { background: #b5651d; }
.attr-panel.singular::before { background: #3a6ea5; }
.attr-panel.tenjin::before { background: #6b4fa6; }
.attr-panel-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.attr-panel-title { color: var(--text-primary); font-family: var(--font-mono); font-size: 11px; font-weight: 750; letter-spacing: 0.4px; }
.attr-count { margin-left: auto; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 9px; }
.attr-input-row { display: flex; gap: 7px; margin-bottom: 10px; }
.attr-input-row input { flex: 1; min-width: 0; min-height: 36px; padding: 8px 10px; color: var(--text-primary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: 8px; outline: none; font-family: var(--font-mono); font-size: 10px; }
.attr-input-row input:focus { background: #fff; border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.btn-attr-query { min-width: 54px; min-height: 36px; padding: 7px 11px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #e6d3c2; border-radius: 8px; font-size: 10px; font-weight: 650; cursor: pointer; }
.btn-attr-query:hover:not(:disabled) { background: #f1e3d5; border-color: #e0cbb4; }
.attr-results { max-height: 280px; padding-right: 3px; overflow-y: auto; overscroll-behavior: contain; }
.attr-record { margin-bottom: 7px; padding: 9px 10px; background: var(--panel-muted); border: 1px solid var(--border-color); border-radius: 9px; }
.attr-record:last-child { margin-bottom: 0; }
.attr-field { display: grid; grid-template-columns: minmax(86px, 0.35fr) minmax(0, 1fr); gap: 9px; padding: 4px 0; border-bottom: 1px solid #e6ddcb; font-size: 9px; line-height: 1.65; }
.attr-field:last-child { border-bottom: 0; }
.attr-field-key { color: var(--text-tertiary); font-family: var(--font-mono); font-weight: 650; overflow-wrap: anywhere; }
.attr-field-val { color: var(--text-primary); font-family: var(--font-mono); overflow-wrap: anywhere; }
.attr-empty { padding: 18px; color: var(--text-tertiary); text-align: center; background: var(--panel-muted); border: 1px dashed #ddd0bb; border-radius: 9px; font-size: 10px; }
.history-section { margin-top: 22px; overflow: hidden; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.history-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; padding: 15px 17px; }
.history-header .section-title { margin: 0; }
.history-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.redis-status { display: inline-flex; align-items: center; min-height: 26px; padding: 4px 8px; border: 1px solid; border-radius: 7px; font-size: 9px; font-weight: 650; }
.redis-status.ok { color: #176e52; background: var(--success-soft); border-color: #d9e4d0; }
.redis-status.warn { color: #8c5c17; background: var(--warning-soft); border-color: #eddbb7; }
.btn-history-action { min-height: 30px; padding: 5px 10px; color: var(--text-secondary); background: #fff; border: 1px solid var(--border-color); border-radius: 8px; font-size: 9px; font-weight: 650; cursor: pointer; }
.btn-history-action:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: #e6d3c2; }
.btn-history-action.btn-danger:hover:not(:disabled) { color: #b04b45; background: var(--danger-soft); border-color: #f0d6cc; }
.history-empty { padding: 38px 20px; color: var(--text-tertiary); text-align: center; border-top: 1px solid var(--border-color); font-size: 11px; }
.history-table-wrapper, .gov-tbl-wrap { max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; border: 1px solid var(--border-color); border-radius: 10px; }
.history-table-wrapper { border: 0; border-top: 1px solid var(--border-color); border-radius: 0; }
.history-table, .gov-data-tbl { width: 100%; min-width: 880px; border-collapse: collapse; color: var(--text-primary); text-align: left; font-size: 10px; }
.history-table th, .gov-data-tbl th { padding: 10px 12px; color: var(--text-secondary); background: #f6f1e7; border-bottom: 1px solid var(--border-color); font-family: var(--font-mono); font-size: 8px; font-weight: 700; letter-spacing: 0.45px; white-space: nowrap; }
.history-table td, .gov-data-tbl td { max-width: 190px; padding: 10px 12px; overflow: hidden; color: var(--text-primary); border-bottom: 1px solid #e6ddcb; text-overflow: ellipsis; white-space: nowrap; }
.history-table tbody tr:last-child td, .gov-data-tbl tbody tr:last-child td { border-bottom: 0; }
.history-table tbody tr:hover td, .gov-data-tbl tbody tr:hover td { background: #faf6ee; }
.cell-mono, .gov-mono { font-family: var(--font-mono); font-size: 9px; }
.cell-remark { max-width: 150px; }
.attr-tag-inline { color: var(--accent-strong); font-family: var(--font-mono); font-size: 9px; font-weight: 650; }
.btn-del-row { min-height: 26px; padding: 3px 7px; color: #b04b45; background: transparent; border: 1px solid transparent; border-radius: 6px; font-size: 9px; font-weight: 650; cursor: pointer; }
.btn-del-row:hover { background: var(--danger-soft); border-color: #f0d6cc; }
.drop-pwd-input { width: 100%; min-height: 40px; margin: 3px 0 15px; padding: 9px 11px; color: var(--text-primary); background: rgba(255,255,255,0.78); border: 1px solid var(--border-color); border-radius: 9px; outline: none; font-size: 11px; }
.drop-pwd-input:focus { border-color: var(--danger); box-shadow: 0 0 0 3px rgba(213,82,82,0.1); }
.drop-pwd-error { margin: -5px 0 13px; color: #b04b45; font-size: 10px; font-weight: 600; }
.gov-section { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; align-items: start; }
.gov-card { min-width: 0; margin: 0; overflow: hidden; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
.gov-card:first-child, .gov-card:nth-child(3), .gov-card:nth-child(4), .gov-card:nth-child(5) { grid-column: 1 / -1; }
.gov-card:nth-child(2) { grid-column: 1 / -1; }
.gov-card-hd { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; min-height: 56px; padding: 12px 15px; background: #fffdf6; border-bottom: 1px solid var(--border-color); }
.gov-card-hd-left { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; min-width: 0; }
.gov-card-hd h3 { margin: 0; color: var(--text-primary); font-size: 12px; line-height: 1.6; font-weight: 700; overflow-wrap: anywhere; }
.gov-icon { position: relative; display: inline-grid; place-items: center; flex: none; width: 27px; height: 27px; color: var(--accent); background: var(--accent-soft); border: 1px solid #e6ddcb; border-radius: 8px; font-size: 0; }
.gov-icon::before { content: ''; width: 10px; height: 10px; border: 1.5px solid currentColor; border-radius: 2px; box-shadow: inset 0 2px 0 rgba(201,106,69,0.12); }
.gov-badge { display: inline-flex; align-items: center; min-height: 22px; padding: 3px 8px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #e4e0d8; border-radius: 999px; font-family: var(--font-mono); font-size: 8px; font-weight: 650; }
.gov-card-bd { min-width: 0; padding: 15px; }
.gov-loading, .gov-empty { padding: 26px 16px; color: var(--text-tertiary); text-align: center; font-size: 10px; }
.gov-tbl-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 8px; }
.gov-tbl-item { display: flex; align-items: center; gap: 8px; min-width: 0; min-height: 40px; padding: 8px 10px; color: var(--text-secondary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: 9px; font-size: 10px; cursor: pointer; transition: border-color 160ms ease, background 160ms ease; }
.gov-tbl-item:hover { color: var(--text-primary); background: var(--panel-hover); border-color: #d3cdc3; }
.gov-tbl-item.gov-tbl-active { color: var(--accent-strong); background: var(--accent-soft); border-color: #c9c1b2; font-weight: 650; }
.gov-tbl-icon { flex: none; width: 11px; height: 13px; border: 1.5px solid currentColor; border-radius: 2px; opacity: 0.62; }
.gov-tbl-name { flex: 1; min-width: 0; overflow: hidden; font-family: var(--font-mono); text-overflow: ellipsis; white-space: nowrap; }
.gov-btn-icon-del { flex: none; min-height: 25px; padding: 3px 6px; color: var(--text-tertiary); background: transparent; border: 1px solid transparent; border-radius: 6px; font-size: 8px; cursor: pointer; }
.gov-btn-icon-del:hover { color: #b04b45; background: var(--danger-soft); border-color: #f0d6cc; }
.gov-field { min-width: 0; margin-bottom: 13px; }
.gov-field label, .gov-import-label { display: block; margin-bottom: 5px; color: var(--text-secondary); font-size: 9px; font-weight: 650; }
.gov-input, .gov-textarea { width: 100%; min-height: 38px; padding: 8px 11px; color: var(--text-primary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: 9px; outline: none; font-size: 11px; transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease; }
.gov-textarea { min-height: 96px; resize: vertical; font-family: var(--font-mono); font-size: 10px; line-height: 1.75; }
.gov-input:focus, .gov-textarea:focus { background: #fff; border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.gov-sql-ta { min-height: 132px; }
.gov-grid-2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.gov-btn-primary, .gov-btn-ghost { display: inline-flex; align-items: center; justify-content: center; min-height: 36px; padding: 7px 13px; border: 1px solid; border-radius: 9px; font-size: 10px; line-height: 1.4; font-weight: 650; cursor: pointer; transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease; }
.gov-btn-primary { color: #fff; background: var(--accent); border-color: var(--accent-strong); box-shadow: inset 0 1px 0 rgba(255,255,255,0.2); }
.gov-btn-ghost { color: var(--text-secondary); background: #fff; border-color: var(--border-color); }
.gov-btn-primary:hover:not(:disabled), .gov-btn-ghost:hover:not(:disabled) { transform: translateY(-1px); box-shadow: var(--shadow-sm); }
.gov-btn-primary:hover:not(:disabled) { background: var(--accent-strong); }
.gov-btn-ghost:hover:not(:disabled) { color: var(--accent-strong); background: var(--accent-soft); border-color: #e6d3c2; }
.gov-btn-sm { min-height: 34px; padding: 6px 12px; }
.gov-btn-danger { color: #b04b45; background: var(--danger-soft); border-color: #f0d6cc; }
.gov-btn-row { display: flex; flex-wrap: wrap; gap: 7px; margin-bottom: 12px; }
.gov-btn-import { width: 100%; margin-top: 13px; }
.gov-fb, .gov-fb-sm { margin-top: 9px; padding: 8px 10px; border: 1px solid; border-radius: 8px; font-size: 10px; line-height: 1.65; }
.gov-fb-sm { padding: 6px 9px; font-size: 9px; }
.gov-fb-ok { color: #176e52; background: var(--success-soft); border-color: #d9e4d0; }
.gov-fb-err { color: #b04b45; background: var(--danger-soft); border-color: #f0d6cc; }
.gov-data-tbl { min-width: 780px; }
.gov-sql-tbl { min-width: 640px; }
.gov-sub { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--border-color); }
.gov-sub-title { margin: 0 0 9px; color: var(--text-primary); font-size: 11px; font-weight: 700; }
.gov-inline-row { display: flex; align-items: center; gap: 8px; }
.gov-input-flex { flex: 1; min-width: 0; }
.gov-sql-result { margin-top: 15px; }
.gov-sql-result-hd { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 11px; color: var(--text-primary); background: #f6f1e7; border: 1px solid var(--border-color); border-radius: 10px 10px 0 0; font-size: 10px; font-weight: 700; }
.gov-sql-rows { color: var(--accent-strong); font-family: var(--font-mono); font-size: 9px; }
.gov-sql-result .gov-tbl-wrap { border-top: 0; border-radius: 0 0 10px 10px; }
.gov-import-section { margin-top: 15px; padding-top: 14px; border-top: 1px solid var(--border-color); }
.gov-cb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 6px; }
.gov-cb-item { display: flex; align-items: center; gap: 7px; min-width: 0; padding: 7px 9px; color: var(--text-secondary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: 8px; font-size: 10px; cursor: pointer; }
.gov-cb-item:hover { background: var(--panel-hover); }
.gov-cb-item span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gov-cb-item input[type='checkbox'] { width: 14px; height: 14px; flex: none; margin: 0; accent-color: var(--accent); cursor: pointer; }
.gov-import-details { display: grid; gap: 5px; margin-top: 12px; }
.gov-import-detail { display: flex; align-items: flex-start; gap: 10px; padding: 8px 10px; border: 1px solid; border-radius: 8px; font-size: 10px; line-height: 1.6; }
.gov-detail-success { color: #176e52; background: var(--success-soft); border-color: #d9e4d0; }
.gov-detail-error { color: #b04b45; background: var(--danger-soft); border-color: #f0d6cc; }
.gov-detail-skipped { color: #8c5c17; background: var(--warning-soft); border-color: #eddbb7; }
.gov-detail-empty { color: var(--text-secondary); background: var(--panel-muted); border-color: var(--border-color); }
.gov-detail-tbl { flex: none; min-width: 110px; font-family: var(--font-mono); font-weight: 650; overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .attr-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .dev-header { align-items: flex-start; }
  .gov-section { grid-template-columns: 1fr; }
  .gov-card, .gov-card:first-child, .gov-card:nth-child(2), .gov-card:nth-child(3), .gov-card:nth-child(4), .gov-card:nth-child(5) { grid-column: 1; }
  .history-header { align-items: flex-start; }
  .history-actions { width: 100%; }
  .gov-grid-2 { grid-template-columns: 1fr; gap: 0; }
  .gov-inline-row { align-items: stretch; flex-direction: column; }
  .gov-inline-row .gov-btn-primary { width: 100%; }
  .gov-tbl-list { grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .dev-header { gap: 10px; }
  .dev-title { font-size: 18px; }
  .dev-subtitle { max-width: 230px; }
  .dev-guide-btn { padding-inline: 10px; }
  .dev-tabs { margin-bottom: 14px; }
  .dev-tab { padding-inline: 7px; font-size: 10px; }
  .test-tab > .top-bar { padding: 11px; }
  .attr-panel { padding: 13px; }
  .attr-field { grid-template-columns: 1fr; gap: 1px; }
  .history-header, .gov-card-bd { padding: 13px; }
  .history-actions { gap: 6px; }
  .redis-status { flex-basis: 100%; }
  .gov-card-hd { padding: 11px 13px; }
  .gov-cb-grid { grid-template-columns: 1fr; }
  .gov-btn-row .gov-btn-primary, .gov-btn-row .gov-btn-ghost { flex: 1 1 calc(50% - 7px); }
  .gov-import-detail { flex-direction: column; gap: 3px; }
  .gov-detail-tbl { min-width: 0; }
}
</style>
