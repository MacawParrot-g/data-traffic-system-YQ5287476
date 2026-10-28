// 文件路径: frontend/src/utils/markdown.js
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
    gfm: true,    // 支持表格、删除线等 GitHub 风格
    breaks: true  // 单个换行转 <br>，贴合聊天排版
})

/**
 * 将 AI 输出的 Markdown 文本渲染为安全的 HTML。
 * 先 marked 解析，再 DOMPurify 消毒，防止模型返回的 HTML/脚本造成 XSS。
 */
export function renderMarkdown(text) {
    if (!text) return ''
    const html = marked.parse(String(text))
    return DOMPurify.sanitize(html, { ADD_ATTR: ['target', 'rel'] })
}