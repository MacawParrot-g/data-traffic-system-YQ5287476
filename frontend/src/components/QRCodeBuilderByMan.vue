<script setup>
import { ref, watch, nextTick } from 'vue'
import QRCode from 'qrcode'

const inputText = ref('')
const qrCanvas = ref(null)
const qrSize = ref(256)

async function renderQR() {
  await nextTick()
  if (!qrCanvas.value) return
  if (!inputText.value.trim()) {
    const ctx = qrCanvas.value.getContext('2d')
    ctx.clearRect(0, 0, qrCanvas.value.width, qrCanvas.value.height)
    return
  }
  try {
    await QRCode.toCanvas(qrCanvas.value, inputText.value, {
      width: Number(qrSize.value) || 256,
      margin: 2,
      color: { dark: '#000000', light: '#ffffff' }
    })
  } catch (e) {
    console.error('QR码生成失败:', e)
  }
}

watch([inputText, qrSize], () => { renderQR() })
</script>

<template>
  <div class="qr-builder">
    <header class="builder-header">
      <div>
        <span class="builder-kicker">UTILITY / QR ENCODER</span>
        <h2>手动二维码生成器</h2>
        <p>将链接或文本转换为可扫描的二维码，内容变更后自动更新。</p>
      </div>
      <span class="builder-badge">LOCAL PREVIEW</span>
    </header>

    <div class="builder-workspace">
      <section class="builder-controls" aria-label="二维码参数">
        <div class="control-heading">
          <span class="control-index">01</span>
          <div>
            <h3>编码参数</h3>
            <p>输入内容并设置输出尺寸</p>
          </div>
        </div>

        <div class="input-section">
          <label>输入内容</label>
          <textarea
              v-model="inputText"
              placeholder="请输入要转换为二维码的内容（如网址、文本等）"
              rows="4"
          ></textarea>
          <div class="field-hint">支持网址、文本及其他二维码可编码内容</div>
        </div>

        <div class="size-section">
          <label>二维码尺寸</label>
          <div class="size-control">
            <input type="number" v-model.number="qrSize" min="128" max="512" step="32" />
            <span>px</span>
          </div>
          <div class="size-range">128–512 px</div>
        </div>
      </section>

      <section class="preview-panel" aria-label="二维码预览">
        <div class="preview-heading">
          <div>
            <span class="control-index">02</span>
            <h3>实时预览</h3>
          </div>
          <span class="preview-status"><i></i>自动渲染</span>
        </div>
        <div class="preview-stage">
          <div class="qr-preview" v-if="inputText.trim()">
            <canvas ref="qrCanvas"></canvas>
          </div>
          <div class="qr-placeholder" v-else>
            <span class="placeholder-grid" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
            <p>输入内容后自动生成二维码</p>
            <span>等待编码内容</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.qr-builder {
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  color: var(--text-primary);
  font-family: var(--font-sans);
}
.qr-builder *, .qr-builder *::before, .qr-builder *::after { box-sizing: border-box; }
.builder-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 16px; padding: 0 2px; }
.builder-kicker { display: block; margin-bottom: 5px; color: var(--accent); font-family: var(--font-mono); font-size: 9px; line-height: 1.5; font-weight: 700; letter-spacing: 1.25px; }
.builder-header h2 { margin: 0; color: var(--text-primary); font-size: 20px; line-height: 1.4; font-weight: 750; letter-spacing: -0.35px; }
.builder-header p { margin: 5px 0 0; color: var(--text-secondary); font-size: 11px; line-height: 1.7; }
.builder-badge { flex: none; padding: 6px 9px; color: var(--text-tertiary); background: rgba(255,255,255,0.6); border: 1px solid var(--border-color); border-radius: 7px; font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.65px; }
.builder-workspace { display: grid; grid-template-columns: minmax(0, 0.9fr) minmax(320px, 1.1fr); overflow: hidden; background: var(--bg-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.builder-controls { min-width: 0; padding: 20px; border-right: 1px solid var(--border-color); }
.control-heading { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.control-index { display: inline-grid; place-items: center; flex: none; width: 27px; height: 27px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #e6ddcb; border-radius: 8px; font-family: var(--font-mono); font-size: 9px; font-weight: 750; }
.control-heading h3, .preview-heading h3 { margin: 0; color: var(--text-primary); font-size: 12px; line-height: 1.5; font-weight: 700; }
.control-heading p { margin: 2px 0 0; color: var(--text-tertiary); font-size: 9px; }
.input-section { margin-bottom: 17px; }
.input-section label, .size-section > label { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 10px; font-weight: 650; }
.input-section textarea {
  width: 100%;
  min-height: 116px;
  padding: 11px 12px;
  resize: vertical;
  color: var(--text-primary);
  background: #fffdf6;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  outline: none;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.8;
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}
.input-section textarea::placeholder { color: var(--text-tertiary); font-family: var(--font-sans); }
.input-section textarea:focus, .size-control input:focus { background: #fff; border-color: var(--accent-light); box-shadow: 0 0 0 3px var(--accent-glow); }
.field-hint { margin-top: 6px; color: var(--text-tertiary); font-size: 9px; line-height: 1.6; }
.size-section { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 5px 12px; padding-top: 15px; border-top: 1px solid var(--border-color); }
.size-section > label { grid-column: 1 / -1; margin: 0; }
.size-control { display: flex; align-items: center; gap: 8px; }
.size-control input { width: 96px; min-height: 38px; padding: 8px 10px; color: var(--text-primary); background: #fffdf6; border: 1px solid var(--border-color); border-radius: 9px; outline: none; font-family: var(--font-mono); font-size: 11px; }
.size-control span { color: var(--text-secondary); font-family: var(--font-mono); font-size: 10px; }
.size-range { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 9px; }
.preview-panel { min-width: 0; padding: 20px; background: #faf6ee; }
.preview-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 15px; }
.preview-heading > div { display: flex; align-items: center; gap: 10px; }
.preview-status { display: inline-flex; align-items: center; gap: 6px; color: var(--text-tertiary); font-size: 9px; }
.preview-status i { width: 5px; height: 5px; background: var(--accent); border-radius: 50%; box-shadow: 0 0 0 3px rgba(201,106,69,0.09); }
.preview-stage { display: grid; place-items: center; min-height: 330px; padding: 20px; overflow: hidden; background-color: #fffdf6; background-image: linear-gradient(rgba(201,106,69,0.035) 1px, transparent 1px), linear-gradient(90deg, rgba(201,106,69,0.035) 1px, transparent 1px); background-size: 20px 20px; border: 1px solid var(--border-color); border-radius: 14px; }
.qr-preview { display: flex; justify-content: center; width: 100%; min-width: 0; }
.qr-preview canvas { display: block; max-width: 100%; height: auto; padding: 10px; background: #fff; border: 1px solid #e0d5c2; border-radius: 13px; box-shadow: 0 12px 30px rgba(70,55,35,0.08), inset 0 1px 0 #fff; }
.qr-placeholder { display: flex; flex-direction: column; align-items: center; padding: 28px 12px; text-align: center; }
.placeholder-grid { display: grid; grid-template-columns: repeat(2, 25px); grid-template-rows: repeat(2, 25px); gap: 5px; margin-bottom: 16px; padding: 9px; background: var(--accent-soft); border: 1px solid #e6ddcb; border-radius: 13px; transform: rotate(3deg); }
.placeholder-grid i { border: 2px solid #dcb69a; border-radius: 3px; }
.placeholder-grid i:nth-child(2), .placeholder-grid i:nth-child(3) { border-style: dotted; opacity: 0.65; }
.qr-placeholder p { margin: 0 0 5px; color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.qr-placeholder > span:last-child { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 8px; letter-spacing: 0.6px; }
@media (max-width: 700px) {
  .builder-workspace { grid-template-columns: 1fr; }
  .builder-controls { border-right: 0; border-bottom: 1px solid var(--border-color); }
  .preview-stage { min-height: 280px; }
}
@media (max-width: 480px) {
  .builder-header { gap: 10px; }
  .builder-header h2 { font-size: 18px; }
  .builder-badge { display: none; }
  .builder-controls, .preview-panel { padding: 14px; }
  .builder-workspace { border-radius: 14px; }
  .preview-heading { align-items: flex-start; }
  .preview-status { padding-top: 7px; }
  .preview-stage { min-height: 240px; padding: 13px; }
}
</style>
