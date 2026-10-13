import { fetchEvent, fetchAllAttributions } from '../api/index.js'

export function useEventQuery({ bundleId, originalCurrentTargetNum, emit }) {

    async function queryEventCore() {
        if (!bundleId.value) return
        const [eventJson, attrResults] = await Promise.all([
            fetchEvent(bundleId.value),
            fetchAllAttributions(bundleId.value)
        ])

        let result = { eventResult: '', newCurrent: null, eventId: null, attributions: [], lastReportTime: '' }

        if (eventJson.success && eventJson.data) {
            const newCurrent = eventJson.data.currentTargetNum
            result.eventId = eventJson.data.id ?? null
            result.newCurrent = newCurrent
            if (originalCurrentTargetNum.value !== null) {
                result.eventResult = newCurrent !== originalCurrentTargetNum.value ? 'has_event' : 'no_event'
            } else {
                result.eventResult = newCurrent != null && newCurrent > 0 ? 'has_event' : 'no_event'
            }
        }

        // 归因解析
        const found = []
        for (const { type, json } of attrResults) {
            if (json.success && json.data) {
                if ((Array.isArray(json.data) && json.data.length > 0) ||
                    (!Array.isArray(json.data) && Object.keys(json.data).length > 0)) {
                    found.push(type)
                }
            }
        }
        result.attributions = found

        // lastReportTime 提取
        let lrt = ''
        const appflyerResult = attrResults.find(r => r.type === 'appflyer')
        if (appflyerResult?.json?.success && Array.isArray(appflyerResult.json.data) && appflyerResult.json.data.length > 0) {
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
        result.lastReportTime = lrt
        return result
    }

    async function queryEventWithFullMsg() {
        try {
            const data = await fetchEvent(bundleId.value)
            if (data.success) alert(JSON.stringify(data, null, 2))
        } catch (e) {
            alert('服务器无响应，请联系技术人员')
        }
    }

    return { queryEventCore, queryEventWithFullMsg }
}