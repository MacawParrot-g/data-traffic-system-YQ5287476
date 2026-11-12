<script setup>
import { ref } from 'vue'

const emit = defineEmits(['close'])
const displayName = ref(localStorage.getItem('userName') || '新用户')
const accType = ref(localStorage.getItem('accType') || 'USER')

const contentRef = ref(null)
const activeSection = ref('s-intro')

function close() { emit('close') }

function go(id) {
  activeSection.value = id
  const root = contentRef.value
  if (!root) return
  const el = root.querySelector('#' + id)
  if (el) root.scrollTo({ top: el.offsetTop - 12, behavior: 'smooth' })
}

const toc = [
  { id: 's-intro', cn: '系统是什么', en: 'OVERVIEW' },
  { id: 's-login', cn: '登录与界面', en: 'LOGIN' },
  { id: 's-task', cn: '刷新下载任务', en: 'PULL TASK' },
  { id: 's-scan', cn: '扫码与越狱', en: 'INSTALL' },
  { id: 's-operate', cn: '操作与查事件', en: 'OPERATE' },
  { id: 's-has-event', cn: '有事件 · 入库', en: 'HAS EVENT' },
  { id: 's-no-event', cn: '无事件 · 冻结', en: 'FREEZE' },
  { id: 's-grade', cn: '应用评级 ABCD', en: 'RATING' },
  { id: 's-manual', cn: '手动模式', en: 'MANUAL' },
  { id: 's-tools', cn: '看板·备注·通知', en: 'TOOLS' },
  { id: 's-role', cn: '权限地图', en: 'ROLES' },
  { id: 's-egg', cn: '彩蛋 & 速查', en: 'CHEATSHEET' },
]
</script>

<template>
  <div class="guide-overlay" @click.self="close">
    <div class="guide-modal">
      <!-- ===== Header ===== -->
      <div class="guide-header">
        <div class="guide-header-left">
          <span class="guide-header-icon" aria-hidden="true">
            <svg viewBox="0 0 28 28" fill="none"><path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H14v19H7.5A2.5 2.5 0 0 0 5 24.5v-19Zm18 0A2.5 2.5 0 0 0 20.5 3H14v19h6.5a2.5 2.5 0 0 1 2.5 2.5v-19Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M8 8h3m-3 4h3m6-4h3m-3 4h3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
          </span>
          <div>
            <span class="guide-header-kicker">TDM ONBOARDING / 新人上手手册</span>
            <h2>新人指南</h2>
            <div class="guide-header-sub">移动应用广告事件归因测试平台 · 图文操作手册</div>
          </div>
        </div>
        <button class="guide-close-btn" @click="close" aria-label="关闭新人指南" title="关闭">
          <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m6 6 8 8m0-8-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
        </button>
      </div>

      <!-- ===== Body ===== -->
      <div ref="contentRef" class="guide-body">
        <div class="guide-content">
          <!-- TOC -->
          <nav class="guide-toc" aria-label="手册目录">
            <h3><span>CONTENTS</span>目录</h3>
            <ul>
              <li v-for="t in toc" :key="t.id">
                <a :class="{ on: activeSection === t.id }" @click="go(t.id)">
                  <em>{{ t.en }}</em>{{ t.cn }}
                </a>
              </li>
            </ul>
          </nav>

          <!-- Welcome -->
          <div class="guide-welcome">
            <p>亲爱的 <strong>{{ displayName }}</strong>，欢迎加入<strong>深圳市慧动创想科技有限公司</strong>测试数据团队！</p>
            <p>这份手册会带你从零跑通<strong>移动应用广告事件归因测试</strong>的完整流程。别担心，跟着图一步步点就行——你只需要判断一件事：<strong>被测应用有没有把广告事件上报回来</strong>。应用本身好不好用、有没有 bug，跟你无关。</p>
            <p>你的当前角色：<span class="role-badge" :class="'role-' + accType.toLowerCase()">{{ accType === 'ADMIN' ? '管理员 ADMIN' : accType === 'DEVELOPER' ? '开发者 root' : '普通用户 USER' }}</span></p>
          </div>

          <!-- ============ 01 系统是什么 ============ -->
          <section id="s-intro" class="guide-section">
            <h3>一、这个系统到底在测什么</h3>
            <p>TDM（测试数据管理平台）服务于<strong>广告事件归因</strong>测试。每个被测应用接入了 AppFlyer / Adjust / Singular / Tenjin 等归因 SDK，我们的任务是：把应用装上、按规范操作一番，然后回平台查询——看它<strong>有没有把用户行为事件（currentTargetNum）上报回来</strong>，并把结果入库留档。</p>

            <div class="callout callout-info">
              <span class="callout-tag">核心原则</span>
              <p>你测的是「<strong>事件有没有上报</strong>」，不是「应用有没有 bug」。<strong>应用闪退、卡顿、逻辑混乱都不用你管</strong>——只要它还能正常打开、正常操作，就照常测事件。只有当它<strong>根本没法正常使用</strong>（闪退、越狱检测、要求更高系统版本、地区不支持、必须付费等）时，才走「冻结入库」这条路。</p>
            </div>

            <h4>自动模式主流程（一图看懂）</h4>
            <div class="flow">
              <div class="flow-node"><b>①</b>刷新下载任务</div>
              <div class="flow-arrow">→</div>
              <div class="flow-node"><b>②</b>手机扫码装 App</div>
              <div class="flow-arrow">→</div>
              <div class="flow-node"><b>③</b>确认已越狱</div>
              <div class="flow-arrow">→</div>
              <div class="flow-node"><b>④</b>操作应用</div>
              <div class="flow-arrow">→</div>
              <div class="flow-node"><b>⑤</b>查询事件</div>
              <div class="flow-arrow">→</div>
              <div class="flow-branch">
                <div class="flow-node ok"><b>有事件</b>直接入库 / 评级</div>
                <div class="flow-node warn"><b>无事件</b>冻结 · 评 D 级</div>
              </div>
            </div>
            <p class="muted">下面每一节都对应上面的一步，配真实界面截图，照着做即可。</p>
          </section>

          <!-- ============ 02 登录与界面 ============ -->
          <section id="s-login" class="guide-section">
            <h3>二、登录与界面导览</h3>
            <p>在浏览器地址栏输入系统分配的访问地址（生产环境为内网 IP，例如 <code>10.1.9.56</code>，具体以主管发你的为准），输入<strong>分配给你的账号（UID）和密码</strong>，点「进入系统」。登录采用 Token + Cookie，有效期 30 天，同一账号最多 5 个会话，超出的最早会话会被踢下线。</p>

            <figure class="shot">
              <img src="/guide/01-login.png" alt="登录后的自动模式主界面" loading="lazy" />
              <figcaption>▲ 登录后进入「自动模式」主界面。左侧暖橙导航栏，右侧为工作区。</figcaption>
            </figure>

            <h4>界面分区</h4>
            <ul>
              <li><strong>顶部导航栏</strong>：左上角 Logo「DataEye / TDM CONSOLE」；标题区显示当前模式；右上角环境徽标（<code>PROD ENVIRONMENT</code> 生产 / <code>TEST ENVIRONMENT</code> 测试服）。</li>
              <li><strong>左侧功能导航</strong>：普通用户可见 自动模式(AU)、手动模式(MN)、二维码生成(QR)、数据看板&导出(DB)、备注管理(RM)、通知中心(NT)、评级管理(GR)。管理员 / 开发者会多出审计日志、定时任务、通知管理、开发者模式、监控面板等入口。</li>
              <li><strong>收起侧边栏</strong>：点击 Logo 右侧的<strong>「三条杠」按钮</strong>即可把导航栏收成一条窄列（只剩 AU/MN/QR… 图标），腾出更大的操作空间，再点一下展开。</li>
              <li><strong>底部用户区</strong>：显示你的姓名、角色，以及「新人指南」（打开本手册）和「退出登录」。</li>
            </ul>
            <p class="muted">💡 建议测试时把侧边栏收起，屏幕更宽敞，二维码和入库表单都更好点。</p>
          </section>

          <!-- ============ 03 刷新下载任务 ============ -->
          <section id="s-task" class="guide-section">
            <h3>三、刷新下载任务 & 看应用评级</h3>
            <p>进入自动模式后，若任务区为空，会显示「当前没有任何测试条目，请点击刷新按钮刷新第一条数据」。点击右上角的<strong>「刷新下载任务」</strong>按钮，系统会从后端拉取一条待测应用任务。注意：<strong>如果上一条还没入库就点刷新，会弹出「刷新会导致数据丢失」的确认框</strong>，请先确认已入库。</p>

            <figure class="shot">
              <img src="/guide/02-task-qr.png" alt="刷新任务后出现二维码与应用信息" loading="lazy" />
              <figcaption>▲ 刷新后：左侧出现下载二维码，右侧是 App ID / Bundle ID / 原始 CurrentTargetNum / 应用评级卡。</figcaption>
            </figure>

            <h4>任务卡里的关键信息</h4>
            <ul>
              <li><strong>App ID / Bundle ID</strong>：应用的数字 ID 与唯一包名标识，后续查事件、评级都靠它。</li>
              <li><strong>原始 CurrentTargetNum 数</strong>：测试前的基准事件数。查事件时就是拿「最新值」和它对比，看有没有增长。</li>
              <li><strong>版本比对</strong>：<code>current</code> 与 <code>check</code> 两个版本号，不一致时会出现「版本不一致」和「停止二次下发」按钮。</li>
              <li><strong>应用评级卡</strong>：显示该应用历史评级（如「D级」）与测试要求；没评过则显示「无评级」。</li>
              <li><strong>实时库存 / 今日入库</strong>：右上角数据条每 10 秒自动刷新，方便掌握进度。</li>
            </ul>

            <div class="callout callout-warn">
              <span class="callout-tag">省时技巧</span>
              <p>如果评级卡显示的是 <strong>D 级</strong>，说明这个应用历史上就出不了事件或根本没法用——<strong>可以直接跳过</strong>，点「刷新下载任务」拿下一条，不必浪费时间。A / B 级的应用则有<strong>固定的出事件操作套路</strong>（A 级出事件快，B 级要玩得久或过特定关卡）。</p>
            </div>
          </section>

          <!-- ============ 04 扫码与越狱 ============ -->
          <section id="s-scan" class="guide-section">
            <h3>四、扫码安装 & 越狱状态检查</h3>
            <p>页面左侧的二维码就是被测应用的安装入口。用<strong>测试手机</strong>的相机或扫码工具扫描它，跳转到下载页按提示安装即可。</p>

            <div class="callout callout-danger">
              <span class="callout-tag">每天开工第一件事</span>
              <p>请务必确认手机<strong>处于越狱状态</strong>。判断方法：打开桌上的<strong>「水滴」App</strong>（越狱管理工具），<strong>点一下没闪退，就说明越狱还在</strong>。如果水滴闪退 / 打不开，说明越狱掉了——<strong>立刻联系你的直属主管更换设备</strong>，别硬测，否则事件根本归因不上。</p>
            </div>
            <p class="muted">为什么强调越狱？因为这套测试环境依赖越狱设备，越狱失效会导致事件无法正常上报，测出来的「无事件」是假象。</p>
          </section>

          <!-- ============ 05 操作与查事件 ============ -->
          <section id="s-operate" class="guide-section">
            <h3>五、操作应用 & 查询事件</h3>
            <p>装好应用后，按下面的方式操作，把用户行为「跑」出来，事件才有机会上报：</p>

            <div class="op-grid">
              <div class="op-card">
                <h5>🎮 游戏类应用</h5>
                <p>登录 → 通关 → 看广告 → <strong>点击付费但不真的付款</strong>。这一套组合拳下来，<strong>有很大概率触发事件上报</strong>。</p>
              </div>
              <div class="op-card">
                <h5>🛠️ 非游戏类应用</h5>
                <p>商城 / 工具 / 需登录类，正常走一遍核心流程即可，出事件通常更快。</p>
              </div>
            </div>

            <h4>怎么查事件</h4>
            <ul>
              <li>点<strong>「查询事件」</strong>按钮，系统会同时请求事件接口和四个归因平台，结果显示在下方「查询的事件会在这里显示」区域。</li>
              <li>嫌手动点麻烦？用<strong>「定时查询」</strong>：输入间隔秒数（自动模式默认 60 秒，范围 1~3600），点「开始定时」，倒计时结束后自动帮你查一次，可「取消」。</li>
              <li>想看原始返回？点<strong>「查看 JSON」</strong>。</li>
            </ul>

            <h4>时间怎么算</h4>
            <p>围绕评级来把握时长：<strong>A 级</strong>出事件快，可适当缩短；<strong>B 级</strong>建议拉长到 10 分钟以上或玩到指定关卡；<strong>C 级</strong>成因不明，超 10 分钟没上报就联系主管改评 D；<strong>D 级</strong>直接跳过。核心红线是异常类型里的「<code>超过10分钟0上报</code>」——操作满 10 分钟仍 0 事件，就可以走冻结流程了。</p>
          </section>

          <!-- ============ 06 有事件入库 ============ -->
          <section id="s-has-event" class="guide-section">
            <h3>六、有事件 → 填写入库信息</h3>
            <p>当查询结果显示<strong>「最新 CurrentTargetNum」</strong>并且数值相对原始值发生了变化（例如「原始值：11 → 最新值：6」），说明有新事件上报，右侧会亮起归因平台标签（appflyer / adjust / singular / tenjin）。这时就可以入库了。</p>

            <figure class="shot">
              <img src="/guide/03-has-event.png" alt="有事件时显示最新值、归因标签与入库表单" loading="lazy" />
              <figcaption>▲ 有事件：绿色结果卡显示最新值与归因标签；下方「填写入库信息」表单出现，异常类型自动标为「正常」。</figcaption>
            </figure>

            <h4>入库表单字段</h4>
            <ul>
              <li><strong>异常类型</strong>：有事件时自动打「正常」标签；无事件时从下拉选择（见速查表）。</li>
              <li><strong>备注</strong>：可手动输入，也可从「模板（可选）」下拉选一条。<span class="muted">注意：选了模板会覆盖手动输入的内容。</span></li>
              <li><strong>记录人</strong>：默认是你的登录名，一般不用改。</li>
              <li><strong>记录日期</strong>：默认当天；补录历史可改（仅开发者角色可切换）。</li>
            </ul>
            <p>填好点<strong>「直接入库」</strong>，数据立即写入数据库，成功后按钮变「已入库」并出现绿色「入库成功」标签。入库后想给这个应用打个评级？点它右边的<strong>「应用评分」</strong>（见第七节）。</p>
          </section>

          <!-- ============ 07 无事件冻结 ============ -->
          <section id="s-no-event" class="guide-section">
            <h3>七、无事件 → 冻结应用</h3>
            <p>如果操作满 10 分钟仍未上报，或应用<strong>根本无法正常运行</strong>（闪退、越狱检测、要求更高 iOS 版本、地区不支持、必须付费才能用等），查询结果会显示「<strong>无事件</strong>」，旁边出现<strong>「冻结应用」</strong>按钮。</p>

            <figure class="shot">
              <img src="/guide/04-no-event.png" alt="无事件时出现冻结应用按钮与冻结理由输入" loading="lazy" />
              <figcaption>▲ 无事件：点「冻结应用」前，必须先在输入框填写 / 下拉选择「冻结理由」，否则无法冻结。</figcaption>
            </figure>

            <div class="callout callout-warn">
              <span class="callout-tag">冻结必写理由</span>
              <p>冻结操作<strong>强制要求填写冻结理由</strong>：可在输入框自定义（200 字以内），或从「请选择冻结理由」下拉里选一条。<strong>不写理由会直接报错「请选择或填写冻结理由」，无法完成冻结。</strong>备注请一并写在冻结理由框内。</p>
            </div>

            <figure class="shot">
              <img src="/guide/05-frozen.png" alt="冻结成功后应用状态变为已冻结并显示D级" loading="lazy" />
              <figcaption>▲ 冻结成功后：应用状态变「已冻结」、评级卡显示「D级」、结果区显示「冻结结果：成功」；入库表单提示「该应用已存在评级记录，无法评分」。</figcaption>
            </figure>

            <p>冻结成功后，按大纲要求：<strong>点击「直接入库」旁的「应用评分」，把它评为 D 级，并在测试要求里写上理由</strong>（例如「需要 iOS17 以上」）。这样下次再遇到同一个 Bundle ID，评级卡会直接显示 D 级，可以秒跳过。</p>

            <h4>冻结理由下拉长什么样</h4>
            <figure class="shot shot-sm">
              <img src="/guide/08-freeze-options.png" alt="备注类型下拉：异常类型/备注模板/冻结理由/冻结理由屏蔽词" loading="lazy" />
              <figcaption>▲ 备注管理里的「类型」下拉，冻结理由等模板就维护在这里（详见第九节）。</figcaption>
            </figure>
          </section>

          <!-- ============ 08 评级 ABCD ============ -->
          <section id="s-grade" class="guide-section">
            <h3>八、应用评级 A / B / C / D</h3>
            <p>评级入口就在<strong>「直接入库」按钮右侧的「应用评分」</strong>。点开后是「应用评级」弹窗，选等级、写测试要求、保存即可。评级以 <strong>Bundle ID</strong> 为维度，一个应用只需评一次；<strong>已评级的应用再次进入会提示「无法评分」</strong>，避免重复。</p>

            <figure class="shot">
              <img src="/guide/06-rating-dialog.png" alt="应用评级弹窗：ABCD四级与测试要求输入" loading="lazy" />
              <figcaption>▲ 应用评级弹窗：顶部 Bundle ID，中间 A/B/C/D 四个等级按钮，下方「测试要求 / 备注」输入框 + 备注库下拉（与冻结理由共用），评分人自动填当前用户。</figcaption>
            </figure>

            <h4>四个等级意味着什么</h4>
            <div class="grade-grid">
              <div class="grade-card g-a"><b>A 级</b><p>很容易出事件，游玩时可适当缩短测试时间。</p></div>
              <div class="grade-card g-b"><b>B 级</b><p>需要玩较长时间，或玩到指定关卡才出事件，建议拉长到 10 分钟以上。</p></div>
              <div class="grade-card g-c"><b>C 级</b><p>出事件成因不明；若超 10 分钟没上报，联系管理员把评级改为 D。</p></div>
              <div class="grade-card g-d"><b>D 级</b><p>超时 10 分钟无法出事件，或需更高系统 / 越狱检测 / 卡死进不去 / 需付费 / 网络问题 / 地区不支持——建议跳过。</p></div>
            </div>

            <h4>评级管理页</h4>
            <p>侧边栏「评级管理」可以查询、编辑、删除全部评级记录，支持按 Bundle ID、等级、记录人、备注关键字筛选。</p>
            <figure class="shot">
              <img src="/guide/07-grade-manage.png" alt="应用评级管理列表页" loading="lazy" />
              <figcaption>▲ 评级管理：列表展示 Bundle ID / 评级 / 记录人 / 备注，可编辑或删除（删除会同时清理数据库与 Redis 缓存）。</figcaption>
            </figure>
          </section>

          <!-- ============ 09 手动模式 ============ -->
          <section id="s-manual" class="guide-section">
            <h3>九、手动模式（指定 App ID）</h3>
            <p>手动模式用于自动队列覆盖不到的场景，比如<strong>群里临时发的测试链接、需要指定某个 App 测试</strong>。进入「手动模式」，在「指定应用任务」输入框粘贴 App ID，点<strong>「生成」</strong>，后续流程（扫码 → 操作 → 查事件 → 入库）与自动模式一致。</p>

            <figure class="shot">
              <img src="/guide/09-manual.png" alt="手动模式：输入App ID并生成" loading="lazy" />
              <figcaption>▲ 手动模式：输入 App ID → 点「生成」拉取任务；空态提示「请输入 App ID 并点击生成按钮」。</figcaption>
            </figure>

            <h4>与自动模式的区别</h4>
            <div class="guide-table-wrap">
            <table class="guide-table">
              <thead><tr><th>对比项</th><th>自动模式</th><th>手动模式</th></tr></thead>
              <tbody>
                <tr><td>任务来源</td><td>从队列自动拉取</td><td>手动输入 App ID 生成</td></tr>
                <tr><td>自动去重</td><td>支持（重复会自动跳过重试）</td><td>不生效</td></tr>
                <tr><td>评级卡</td><td>显示</td><td>无</td></tr>
                <tr><td>实时库存 / 今日入库</td><td>显示</td><td>无</td></tr>
                <tr><td>库存耗尽复测</td><td>支持（弹窗「复测 / 取消」）</td><td>无</td></tr>
                <tr><td>定时查询默认间隔</td><td>60 秒</td><td>30 秒</td></tr>
              </tbody>
            </table>
            </div>
          </section>

          <!-- ============ 10 工具页 ============ -->
          <section id="s-tools" class="guide-section">
            <h3>十、日常工具页速览</h3>

            <h4>二维码生成器</h4>
            <p>纯前端小工具：把任意网址 / 文本转成可扫描二维码，内容变更后自动渲染，尺寸可调（128~512px）。适合临时把某个链接变成二维码给设备扫。</p>
            <figure class="shot shot-sm">
              <img src="/guide/10-qrcode.png" alt="手动二维码生成器" loading="lazy" />
              <figcaption>▲ 手动二维码生成器：左侧输入内容与尺寸，右侧实时预览。</figcaption>
            </figure>

            <h4>数据看板 & 导出</h4>
            <p>普通用户的「数据看板」查看、筛选、导出<strong>自己名下</strong>的入库记录。可按归因平台（appflyer / adjust / singular / tenjin）与日期筛选，支持「按当前日期导出」「导出选中行」「刷新未导出」。列表含 URL、Bundle ID、归因、事件数、异常类型、记录人、备注、导出状态等列。</p>
            <figure class="shot">
              <img src="/guide/12-dashboard.png" alt="数据看板与导出" loading="lazy" />
              <figcaption>▲ 数据看板：归因筛选 + 日期范围 + 导出操作 + 记录明细表。</figcaption>
            </figure>

            <h4>备注管理（模板库）</h4>
            <p>统一维护四类可复用文本：<strong>异常类型 / 备注模板 / 冻结理由 / 冻结理由屏蔽词</strong>。所有人可操作，可新增、编辑、删除条目；入库和冻结时的下拉选项就来自这里。</p>
            <figure class="shot">
              <img src="/guide/11-remark.png" alt="备注管理模板库" loading="lazy" />
              <figcaption>▲ 备注管理：新增条目（选类型 + 内容），下方列表管理全部模板。</figcaption>
            </figure>

            <h4>通知中心</h4>
            <p>接收系统与管理员推送的通知，右上角铃铛有未读角标（每 5 秒轮询）。重要通知会以全局强制弹窗出现，点「我已了解」关闭。</p>
            <figure class="shot shot-sm">
              <img src="/guide/13-notify.png" alt="通知中心" loading="lazy" />
              <figcaption>▲ 通知中心：个人收件箱，「消息已读完毕 / 暂无通知」为正常空态。</figcaption>
            </figure>
          </section>

          <!-- ============ 11 权限地图 ============ -->
          <section id="s-role" class="guide-section">
            <h3>十一、角色权限地图</h3>
            <p>三种角色看到的菜单不同。作为新人，你大概率是<strong>普通用户</strong>，重点用前六节即可；下面两张表帮你了解其他角色能碰哪些高级功能。</p>

            <h4>各角色可用功能</h4>
            <div class="guide-table-wrap">
            <table class="guide-table">
              <thead><tr><th>功能模块</th><th>普通用户</th><th>管理员</th><th>开发者</th></tr></thead>
              <tbody>
                <tr><td>自动 / 手动模式</td><td>✓</td><td>✓</td><td>✓</td></tr>
                <tr><td>二维码生成</td><td>✓</td><td>✓</td><td>✓</td></tr>
                <tr><td>数据看板（个人）</td><td>✓</td><td>✓</td><td>✓</td></tr>
                <tr><td>评级管理 / 备注管理</td><td>✓</td><td>✓</td><td>✓</td></tr>
                <tr><td>通知中心</td><td>✓</td><td>✓</td><td>✓</td></tr>
                <tr><td>管理员看板（用户/数据管理）</td><td>—</td><td>✓</td><td>✓</td></tr>
                <tr><td>通知管理 / 审计日志 / 定时任务</td><td>—</td><td>✓</td><td>✓</td></tr>
                <tr><td>开发者模式（专业测试 + 数据治理）</td><td>—</td><td>—</td><td>✓</td></tr>
                <tr><td>监控面板 / MQ 监控 / DeepSeek 助手 / Python 脚本</td><td>—</td><td>—</td><td>✓</td></tr>
              </tbody>
            </table>
            </div>

            <h4>管理员 / 开发者高级功能一览</h4>
            <ul>
              <li><strong>管理员看板</strong>：数据报表、数据管理（含从 WPS 粘贴一键导入、批量删除）、App 信息查询、复测数据缓存（Redis DB12）、用户管理（创建用户 / 重置密码 / 踢下线封禁）。会话机制：单用户最多 5 会话，踢下线可设封禁秒数。</li>
              <li><strong>开发者模式</strong>：「专业测试」几乎复刻自动模式并附 7 日测试历史；「数据建表和治理」可建表 / 删表 / 增删改列 / 执行自定义 SQL / 跨库批量导入。</li>
              <li><strong>监控面板</strong>：服务器状态（oshi 指标）、实时日志流、Redis 键空间监控、远程终端。</li>
              <li><strong>MQ 监控</strong>：RabbitMQ 队列拓扑与死信记录（重试 / 清理）。<strong>定时任务</strong>：任务编排与执行。<strong>审计日志</strong>：操作留痕查询与清理。</li>
              <li><strong>DeepSeek 助手</strong>：AI 对话工作台。<strong>Python 脚本</strong>：上传 / 执行运维脚本。<strong>数据源切换</strong>：多租户 MySQL 数据源管理。<strong>URL 执行器</strong>：经后端代理打开目标页并用 CSS 选择器操作。</li>
            </ul>
            <p class="muted">⚠️ 数据治理里的自定义 SQL、删表、批量删除都是<strong>不可逆的高危操作</strong>，非开发者请勿触碰；开发者在生产库操作前务必三思并确认备份。</p>
          </section>

          <!-- ============ 12 彩蛋 & 速查 ============ -->
          <section id="s-egg" class="guide-section">
            <h3>十二、彩蛋 & 速查小抄</h3>

            <div class="callout callout-info">
              <span class="callout-tag">彩蛋 🥚</span>
              <p>本系统由「<strong>蓝黄金刚鹦鹉</strong>」开发维护。据说深夜跑批时对着屏幕念一句「让每一条测试数据都有迹可循」，第二天的 D 级应用会少一半。（别信，但也别不信。）</p>
            </div>

            <h4>异常类型下拉（入库用）</h4>
            <p><code>正常</code> · <code>iOS16闪退</code> · <code>iOS13/14/16均闪退</code> · <code>需要iOS18以上</code> · <code>地区不支持</code> · <code>硬件版本过低</code> · <code>超过10分钟0上报</code> · <code>越狱检测</code> · <code>其他</code> · <code>验证已解决</code> · <code>测试</code></p>

            <h4>常见冻结理由</h4>
            <p><code>需要付费下载</code> · <code>VPN检测</code> · <code>软件不可用</code> · <code>设备不兼容</code> · <code>非常卡顿，无法正常使用</code> · <code>看了广告玩通关也不出事件</code> · <code>越狱检测</code> · <code>超过10分钟0上报</code> · <code>硬件版本过低</code> · <code>地区不支持</code> · <code>需要iOS17以上</code> · <code>iOS13/14/16均闪退</code> …（完整清单在「备注管理」维护）</p>

            <h4>归因平台标签</h4>
            <div class="attr-legend">
              <span class="attr-tag attr-appflyer">appflyer</span>
              <span class="attr-tag attr-adjust">adjust</span>
              <span class="attr-tag attr-singular">singular</span>
              <span class="attr-tag attr-tenjin">tenjin</span>
            </div>

            <h4>新手最常踩的坑</h4>
            <ul>
              <li>没确认越狱就开测 → 测出一堆假「无事件」。先点水滴 App 验身。</li>
              <li>上一条没入库就点刷新 → 数据丢失弹窗。先入库再刷新。</li>
              <li>冻结时没写理由 → 报「请选择或填写冻结理由」。理由必填。</li>
              <li>给已评级应用重复评分 → 提示「无法评分」。评级按 Bundle ID 唯一。</li>
              <li>把「应用有 bug」当成测试目标 → 记住：你只关心事件有没有上报。</li>
            </ul>
          </section>

          <div class="guide-divider"></div>

          <div class="guide-footer">
            <div class="guide-footer-signature">
              <p>本手册由</p>
              <p class="guide-dev-name">蓝黄金刚鹦鹉</p>
              <p>开发维护 · 深圳市慧动创想科技有限公司</p>
              <div class="guide-contact"><p>联系开发者：2448248501@qq.com</p></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 主题色沿用系统 CSS 变量（暖白 Claude 风），结构样式为手册专用 ===== */
.guide-overlay {
  position: fixed; inset: 0; z-index: 9999;
  display: flex; align-items: center; justify-content: center;
  padding: 24px; box-sizing: border-box;
  background: rgba(4, 18, 18, 0.5);
  backdrop-filter: blur(14px) saturate(120%);
  -webkit-backdrop-filter: blur(14px) saturate(120%);
  animation: guide-fade-in 220ms ease both;
}
.guide-modal {
  display: flex; flex-direction: column;
  width: min(1160px, 100%);
  height: min(920px, calc(100vh - 48px)); height: min(920px, calc(100dvh - 48px));
  max-height: calc(100vh - 48px); max-height: calc(100dvh - 48px);
  overflow: hidden; color: var(--text-primary); font-family: var(--font-sans);
  background: rgba(248, 252, 250, 0.94);
  border: 1px solid rgba(255,255,255,0.72); border-radius: 24px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.96), inset 0 -1px 0 rgba(201,106,69,0.05), var(--shadow-lg);
  backdrop-filter: blur(28px) saturate(145%); -webkit-backdrop-filter: blur(28px) saturate(145%);
  animation: guide-slide-up 320ms cubic-bezier(0.16, 1, 0.3, 1) both;
}
.guide-header {
  position: relative; display: flex; align-items: center; justify-content: space-between; gap: 18px;
  flex-shrink: 0; padding: 20px 26px; border-bottom: 1px solid rgba(221,211,193,0.85);
  background: linear-gradient(115deg, rgba(248,233,223,0.72), rgba(255,255,255,0.6) 55%, rgba(255,255,255,0.82));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.9);
}
.guide-header::after { content: ''; position: absolute; left: 26px; right: 26px; bottom: -1px; height: 1px; background: linear-gradient(90deg, var(--accent-light), transparent 38%); opacity: 0.55; }
.guide-header-left { display: flex; align-items: center; gap: 14px; min-width: 0; }
.guide-header-icon { display: grid; place-items: center; width: 46px; height: 46px; flex: 0 0 46px; color: var(--accent-strong); background: rgba(255,255,255,0.66); border: 1px solid #dfdbd3; border-radius: 14px; box-shadow: inset 0 1px 0 #fff, 0 8px 20px rgba(70,55,35,0.05); }
.guide-header-icon svg { width: 27px; height: 27px; }
.guide-header-kicker { display: block; margin-bottom: 4px; color: var(--accent); font-size: 8px; line-height: 1.2; font-weight: 800; letter-spacing: 1.6px; }
.guide-header h2 { margin: 0; color: var(--text-primary); font-size: 21px; line-height: 1.25; font-weight: 750; letter-spacing: -0.5px; }
.guide-header-sub { margin-top: 4px; color: var(--text-secondary); font-size: 10px; line-height: 1.5; }
.guide-close-btn { display: grid; place-items: center; width: 36px; height: 36px; flex: 0 0 36px; padding: 8px; color: var(--text-secondary); background: rgba(255,255,255,0.48); border: 1px solid rgba(221,211,193,0.85); border-radius: 10px; cursor: pointer; box-shadow: inset 0 1px 0 rgba(255,255,255,0.8); transition: color var(--transition), background var(--transition), border-color var(--transition); }
.guide-close-btn svg { width: 18px; height: 18px; }
.guide-close-btn:hover { color: var(--danger); background: var(--danger-soft); border-color: #f0d6cc; }

.guide-body { flex: 1; min-height: 0; overflow-y: auto; overflow-x: hidden; overscroll-behavior: contain; padding: 22px 26px 30px; scroll-behavior: smooth; scrollbar-gutter: stable; }
.guide-body::-webkit-scrollbar, .guide-toc::-webkit-scrollbar, .guide-table-wrap::-webkit-scrollbar { width: 6px; height: 6px; }
.guide-body::-webkit-scrollbar-thumb, .guide-toc::-webkit-scrollbar-thumb, .guide-table-wrap::-webkit-scrollbar-thumb { background: #cecac3; border-radius: 6px; }

.guide-welcome {
  position: relative; margin-bottom: 18px; padding: 20px 22px 18px; overflow: hidden; color: #4c473d;
  background: linear-gradient(125deg, rgba(248,233,223,0.9), rgba(255,255,255,0.7));
  border: 1px solid #e3dfd9; border-radius: var(--radius); box-shadow: inset 0 1px 0 rgba(255,255,255,0.9);
  font-size: 12px; line-height: 1.9;
}
.guide-welcome::before { content: 'START HERE'; display: block; margin-bottom: 9px; color: var(--accent); font-size: 8px; line-height: 1.2; font-weight: 800; letter-spacing: 1.6px; }
.guide-welcome::after { content: ''; position: absolute; width: 180px; height: 180px; right: -100px; top: -110px; border: 1px solid rgba(201,106,69,0.1); border-radius: 50%; box-shadow: 0 0 0 34px rgba(201,106,69,0.025); pointer-events: none; }
.guide-welcome p { position: relative; z-index: 1; margin: 0 0 7px; }
.guide-welcome p:last-child { margin-bottom: 0; }
.guide-welcome strong { color: #352d1e; font-weight: 700; }
.role-badge { display: inline-flex; align-items: center; margin-left: 4px; padding: 3px 9px; border: 1px solid transparent; border-radius: 6px; font-size: 10px; line-height: 1.3; font-weight: 700; }
.role-user { color: var(--info); background: var(--info-soft); border-color: #e5dfd5; }
.role-admin { color: var(--warning); background: var(--warning-soft); border-color: #eaddca; }
.role-developer { color: var(--success); background: var(--success-soft); border-color: #e1dcd3; }

.guide-content { display: grid; grid-template-columns: 196px minmax(0, 1fr); column-gap: 24px; align-items: start; animation: guide-content-in 220ms ease both; }
.guide-toc { grid-column: 1; grid-row: 1 / span 60; position: sticky; top: 0; align-self: start; max-height: calc(100dvh - 220px); overflow-y: auto; padding: 16px 12px 12px; background: rgba(237,245,242,0.76); border: 1px solid #e3e0dc; border-radius: 12px; box-shadow: inset 0 1px 0 rgba(255,255,255,0.75); }
.guide-toc h3 { margin: 0 6px 12px; padding-bottom: 11px; color: var(--text-primary); border-bottom: 1px solid #e1deda; font-size: 13px; font-weight: 750; }
.guide-toc h3 span { display: block; margin-bottom: 4px; color: var(--accent); font-size: 7px; letter-spacing: 1.4px; }
.guide-toc ul { display: flex; flex-direction: column; gap: 2px; list-style: none; padding: 0; margin: 0; }
.guide-toc li { min-width: 0; }
.guide-toc li a { position: relative; display: block; padding: 7px 7px 7px 14px; color: #6d6860; border-radius: 7px; cursor: pointer; font-size: 10.5px; line-height: 1.5; text-decoration: none; overflow-wrap: anywhere; transition: color var(--transition), background var(--transition); }
.guide-toc li a em { display: block; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 6.5px; font-style: normal; letter-spacing: 0.8px; }
.guide-toc li a::before { content: ''; position: absolute; left: 3px; top: 13px; width: 3px; height: 3px; border-radius: 50%; background: #b3aea5; }
.guide-toc li a:hover { color: var(--accent-strong); background: rgba(255,255,255,0.72); }
.guide-toc li a:hover::before, .guide-toc li a.on::before { background: var(--accent); }
.guide-toc li a.on { color: var(--accent-strong); background: rgba(255,255,255,0.78); font-weight: 700; }

.guide-section { grid-column: 2; min-width: 0; margin-bottom: 16px; padding: 24px 26px; color: #48433a; background: rgba(255,255,255,0.84); border: 1px solid #e0d5c2; border-radius: var(--radius); box-shadow: 0 4px 18px rgba(70,55,35,0.025), inset 0 1px 0 rgba(255,255,255,0.9); scroll-margin-top: 12px; }
.guide-section h3 { margin: 0 0 17px; padding-bottom: 13px; color: #3a3020; border-bottom: 1px solid #eaddca; font-size: 17px; line-height: 1.5; font-weight: 750; letter-spacing: -0.25px; }
.guide-section h4 { position: relative; margin: 22px 0 9px; padding-left: 11px; color: var(--accent-strong); font-size: 13px; line-height: 1.6; font-weight: 750; }
.guide-section h4::before { content: ''; position: absolute; left: 0; top: 0.42em; width: 3px; height: 1em; border-radius: 2px; background: var(--accent-light); }
.guide-section h4:first-of-type { margin-top: 5px; }
.guide-section p { margin: 0 0 11px; color: #7d7261; font-size: 12.5px; line-height: 1.95; overflow-wrap: anywhere; }
.guide-section ul, .guide-section ol { margin: 7px 0 13px; padding-left: 21px; }
.guide-section li { margin-bottom: 5px; padding-left: 2px; color: #7d7261; font-size: 12.5px; line-height: 1.85; overflow-wrap: anywhere; }
.guide-section li::marker { color: var(--accent); font-weight: 700; }
.guide-section li strong, .guide-section p strong { color: #3a3020; font-weight: 700; }
.guide-section code { padding: 2px 5px; color: var(--accent-strong); background: #f6f1e7; border: 1px solid #e6e3df; border-radius: 5px; font-family: var(--font-mono); font-size: 0.88em; overflow-wrap: anywhere; }
.muted { color: var(--text-tertiary) !important; font-size: 11.5px !important; }

/* 截图 */
.shot { margin: 14px 0 18px; padding: 10px; background: #f6f1e7; border: 1px solid #e6ddd0; border-radius: 12px; box-shadow: inset 0 1px 0 rgba(255,255,255,0.8); }
.shot img { display: block; width: 100%; height: auto; border-radius: 8px; border: 1px solid #e0d5c2; background: #fff; }
.shot-sm { max-width: 460px; }
.shot figcaption { margin-top: 9px; color: var(--text-tertiary); font-size: 10.5px; line-height: 1.6; text-align: center; }

/* 流程图 */
.flow { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 14px 0 16px; }
.flow-node { display: inline-flex; align-items: center; gap: 6px; padding: 8px 12px; color: #4a4234; background: rgba(255,255,255,0.82); border: 1px solid #e0d5c2; border-radius: 10px; font-size: 11.5px; font-weight: 650; box-shadow: inset 0 1px 0 rgba(255,255,255,0.8); }
.flow-node b { display: grid; place-items: center; width: 18px; height: 18px; color: #fff; background: var(--accent); border-radius: 50%; font-size: 10px; font-weight: 800; }
.flow-node.ok { border-color: #cfe0cd; background: var(--success-soft); } .flow-node.ok b { background: var(--success); }
.flow-node.warn { border-color: #eaddca; background: var(--warning-soft); } .flow-node.warn b { background: var(--warning); }
.flow-arrow { color: var(--accent-light); font-weight: 800; font-size: 14px; }
.flow-branch { display: flex; flex-direction: column; gap: 6px; }

/* 提示卡 */
.callout { position: relative; margin: 14px 0; padding: 15px 16px 13px 16px; border-radius: 11px; border: 1px solid #e3ded6; background: #f6f5f2; }
.callout p { margin: 0 !important; color: #5c5549; font-size: 12px; line-height: 1.85; }
.callout-tag { display: inline-block; margin-bottom: 7px; padding: 2px 9px; border-radius: 6px; font-size: 10px; font-weight: 800; letter-spacing: 0.3px; }
.callout-info { background: var(--info-soft); border-color: #d7e4ee; } .callout-info .callout-tag { color: #fff; background: var(--info); }
.callout-warn { background: var(--warning-soft); border-color: #e6d6c0; } .callout-warn .callout-tag { color: #fff; background: var(--warning); }
.callout-danger { background: var(--danger-soft); border-color: #f0d6cc; } .callout-danger .callout-tag { color: #fff; background: var(--danger); }

/* 操作卡 */
.op-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 10px; margin: 12px 0 16px; }
.op-card { padding: 14px 15px; background: rgba(255,255,255,0.7); border: 1px solid #e3dfd9; border-radius: 11px; }
.op-card h5 { margin: 0 0 6px; color: #3a3020; font-size: 12.5px; font-weight: 750; }
.op-card p { margin: 0 !important; font-size: 12px; line-height: 1.8; }

/* 评级卡 */
.grade-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 10px; margin: 12px 0 16px; }
.grade-card { padding: 13px 15px; border-radius: 11px; border: 1px solid #e3dfd9; background: rgba(255,255,255,0.72); }
.grade-card b { display: inline-block; margin-bottom: 6px; padding: 2px 10px; border-radius: 6px; color: #fff; font-size: 12px; font-weight: 800; }
.grade-card p { margin: 0 !important; color: #63533f; font-size: 11.5px; line-height: 1.75; }
.g-a b { background: var(--success); } .g-a { border-color: #cfe0cd; }
.g-b b { background: var(--info); } .g-b { border-color: #d7e4ee; }
.g-c b { background: var(--warning); } .g-c { border-color: #eaddca; }
.g-d b { background: var(--danger); } .g-d { border-color: #f0d6cc; }

/* 归因标签（沿用系统归因配色） */
.attr-legend { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0 4px; }
.attr-tag { padding: 4px 12px; border-radius: 7px; font-family: var(--font-mono); font-size: 11px; font-weight: 700; border: 1px solid transparent; }
.attr-appflyer { color: #fff; background: var(--success); }
.attr-adjust { color: #fff; background: var(--accent); }
.attr-singular { color: #24475c; background: #bcd6e6; border-color: #a6c8dc; }
.attr-tenjin { color: #fff; background: #7c5cbf; }

/* 表格 */
.guide-table-wrap { width: 100%; max-width: 100%; margin: 13px 0 17px; overflow-x: auto; overscroll-behavior-x: contain; border: 1px solid #e0d5c2; border-radius: 10px; }
.guide-table { width: 100%; min-width: 460px; border-collapse: collapse; font-size: 11px; text-align: left; }
.guide-table th { padding: 11px 13px; color: #5c5549; background: #eaddca; border-bottom: 1px solid #e0d5c2; font-size: 10px; font-weight: 750; white-space: nowrap; }
.guide-table td { padding: 10px 13px; color: #7d7261; border-bottom: 1px solid #f1f0ee; line-height: 1.65; overflow-wrap: anywhere; }
.guide-table tbody tr:last-child td { border-bottom: 0; }
.guide-table tbody tr:hover { background: #f6f1e7; }

.guide-divider { grid-column: 2; height: 1px; margin: 26px 0 8px; background: linear-gradient(90deg, transparent, #ddd0bb 20%, #ddd0bb 80%, transparent); }
.guide-footer { grid-column: 2; margin-top: 8px; }
.guide-footer-signature { padding: 19px 0 8px; color: var(--text-tertiary); text-align: center; font-size: 10px; line-height: 1.8; }
.guide-footer-signature p { margin: 0; }
.guide-dev-name { margin: 4px 0 !important; color: var(--accent-strong); font-size: 18px; font-weight: 750; letter-spacing: -0.3px; }
.guide-contact { margin-top: 10px; color: var(--text-secondary); font-size: 10px; }
.guide-contact p { margin: 2px 0; overflow-wrap: anywhere; }

@keyframes guide-fade-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes guide-slide-up { from { opacity: 0; transform: translateY(18px) scale(0.99); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes guide-content-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }

@media (max-width: 900px) {
  .guide-content { grid-template-columns: 168px minmax(0, 1fr); column-gap: 16px; }
  .guide-section { padding: 20px; }
  .op-grid, .grade-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .guide-overlay { padding: 12px; }
  .guide-modal { height: calc(100vh - 24px); height: calc(100dvh - 24px); max-height: calc(100vh - 24px); max-height: calc(100dvh - 24px); border-radius: 19px; }
  .guide-header { padding: 16px 18px; }
  .guide-body { padding: 16px 18px 22px; scrollbar-gutter: auto; }
  .guide-content { display: block; }
  .guide-toc { position: static; max-height: none; margin-bottom: 16px; padding: 15px 14px; overflow: visible; }
  .guide-toc ul { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 3px 8px; }
  .guide-divider, .guide-footer { display: block; }
  .shot-sm { max-width: 100%; }
}
@media (max-width: 390px) {
  .guide-overlay { padding: 8px; }
  .guide-modal { border-radius: 16px; }
  .guide-header h2 { font-size: 17px; }
  .guide-body { padding: 12px 11px 18px; }
  .guide-toc ul { grid-template-columns: 1fr; }
  .guide-section { margin-bottom: 11px; padding: 17px 15px; border-radius: 12px; }
  .guide-section h3 { font-size: 15px; }
  .guide-section p, .guide-section li { font-size: 11.5px; line-height: 1.85; }
  .guide-table { min-width: 420px; }
}
@media (prefers-reduced-motion: reduce) {
  .guide-overlay, .guide-modal, .guide-content { animation: none; }
  .guide-body { scroll-behavior: auto; }
}
</style>
