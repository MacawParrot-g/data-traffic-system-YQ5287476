import { ref } from 'vue'
import { fetchTemplateOptions } from '../api/index.js'

export const TEMPLATE_TYPES = ['异常类型', '备注模板', '冻结理由', '冻结理由屏蔽词']

const CACHE_TTL = 5 * 60 * 1000
const cacheMap = new Map()

const refs = {}
function getRef(type) {
    if (!refs[type]) refs[type] = ref([])
    return refs[type]
}

function doLoad(type, force = false) {
    const entry = cacheMap.get(type)
    if (!force && entry && Date.now() - entry.ts < CACHE_TTL) {
        entry.ref.value = entry.data
        return Promise.resolve(entry.ref)
    }
    if (!entry) {
        cacheMap.set(type, { ts: 0, data: [], ref: getRef(type), loading: false })
    }
    const e = cacheMap.get(type)
    if (e.loading) return Promise.resolve(e.ref)
    e.loading = true
    return fetchTemplateOptions(type).then(json => {
        if (json.success && Array.isArray(json.data)) {
            e.data = json.data
            e.ts = Date.now()
            e.ref.value = json.data
        }
        return e.ref
    }).catch(() => e.ref).finally(() => { e.loading = false })
}

export function invalidateTemplateCache(type) {
    const e = cacheMap.get(type)
    if (e) { e.ts = 0; e.data = [] }
}

export function useTemplateOptions() {
    return {
        exceptionOptions: getRef('异常类型'),
        remarkTemplateOptions: getRef('备注模板'),
        frozenReasonOptions: getRef('冻结理由'),
        frozenRemarkForbidden: getRef('冻结理由屏蔽词'),
        loadTemplateOptions: doLoad,
        preloadAllTemplateOptions: () => TEMPLATE_TYPES.forEach(t => doLoad(t)),
        refreshTemplateOptions: (type, force = true) => doLoad(type, force)
    }
}