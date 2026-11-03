import { fetchEvent, fetchAllAttributions } from '../api/index.js'

const DEFAULT_MAX_RETRIES = 5

function trimApplyTime(raw) {
    if (!raw) return ''
    const idx = raw.indexOf('.')
    return idx > -1 ? raw.substring(0, idx) : raw
}

/**
 * 通用任务获取逻辑（fetchData / fetchByAppId）
 * fetcher: 实际请求函数（fetchTask 或 fetchObtain）
 * beforeFetch: 请求前校验，返回 false 则中止
 * onBeforeReset: 请求前重置组件状态
 * onSuccess: 数据填充成功后执行组件特有逻辑
 * onNoData: 接口未返回 data 时的处理
 */
export function useTaskFetch(options) {
    const {
        emit,
        loading,
        bundleId,
        appId,
        downloadUrl,
        taskId,
        applyTime,
        originalCurrentTargetNum,
        duplicateTip,
        setTaskStatus,
        fetcher,
        beforeFetch = () => true,
        onBeforeReset = () => {},
        onSuccess = () => {},
        onNoData = () => {},
        enableDuplicateRetry = true,
        maxRetries = DEFAULT_MAX_RETRIES,
        abortMsg = '请求超时，远程服务器响应太慢'
    } = options

    let retryCount = 0
    let retryTimer = null

    async function fetchData() {
        if (!beforeFetch()) return
        loading.value = true
        emit('error', '')
        onBeforeReset()
        if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
        retryCount = 0
        try {
            const json = await fetcher()
            if (json.data) {
                if (json.duplicate && enableDuplicateRetry) {
                    retryCount++
                    if (retryCount > maxRetries) {
                        emit('error', '已连续 ' + maxRetries + ' 次检测到重复URL，请稍后再试')
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
                appId.value = json.data.appId || ''
                taskId.value = json.data.id ?? null
                applyTime.value = trimApplyTime(json.data.applyTime || '')
                originalCurrentTargetNum.value = json.data.currentTargetNum ?? null
                setTaskStatus(json.data.status)
                if (originalCurrentTargetNum.value <= 1 || originalCurrentTargetNum.value === 0) {
                    alert('初始originalCurrentTargetNum数小于等于1或等于0，后续测试期间可能会没有归因，请注意')
                }
                onSuccess(json)
            } else {
                onNoData(json)
            }
        } catch (e) {
            if (e.name === 'AbortError') { emit('error', abortMsg) }
            else { emit('error', '请求失败：' + e.message) }
        } finally {
            loading.value = false
        }
    }

    return { fetchData }
}

/**
 * 通用事件查询逻辑（queryEvent / queryEventWithFullMsg）
 * tenjin 归因平台特殊处理：token 不在 currentTargetNum 字段，而在 tenjin 接口返回的 data 数组内，
 * data 数组不为空时，事件数量 = currentTargetNum + data 数组长度
 * onQueryStart: 查询开始前清理组件特有状态
 * onEventData: 事件接口返回 data 时执行组件特有逻辑（如版本比对）
 * onHasEvent: 判定为有事件时执行
 * onRecordData: 设置记录日期
 * onFinally: 查询结束收尾
 */
export function useEventQuery(options) {
    const {
        emit,
        bundleId,
        originalCurrentTargetNum,
        eventLoading,
        eventResult,
        newCurrentTargetNum,
        attributions,
        eventId,
        frozenMsg,
        lastReportTime,
        onQueryStart = () => {},
        onEventData = () => {},
        onHasEvent = () => {},
        onRecordData = () => {},
        onFinally = () => {}
    } = options

    async function queryEvent() {
        if (!bundleId.value) return
        eventLoading.value = true
        emit('error', '')
        eventResult.value = ''
        newCurrentTargetNum.value = null
        attributions.value = []
        eventId.value = null
        frozenMsg.value = ''
        lastReportTime.value = ''
        onQueryStart()
        try {
            const [eventJson, attrResults] = await Promise.all([
                fetchEvent(bundleId.value),
                fetchAllAttributions(bundleId.value)
            ])
            let newCurrent = null
            let hasEventData = false
            if (eventJson.success && eventJson.data) {
                newCurrent = eventJson.data.currentTargetNum
                eventId.value = eventJson.data.id ?? null
                hasEventData = true
                onEventData(eventJson.data)
            } else {
                emit('error', '事件接口返回异常：' + (eventJson.resultMsg || '未知错误'))
            }

            // tenjin 归因平台特殊处理：token 不在 currentTargetNum，而在 data 数组内
            const tenjinResult = attrResults.find(r => r.type === 'tenjin')
            if (tenjinResult && tenjinResult.json && tenjinResult.json.success &&
                Array.isArray(tenjinResult.json.data) && tenjinResult.json.data.length > 0) {
                newCurrent = Number(newCurrent ?? 0) + tenjinResult.json.data.length
            }

            if (hasEventData) {
                if (originalCurrentTargetNum.value !== null) {
                    if (newCurrent !== originalCurrentTargetNum.value) {
                        eventResult.value = 'has_event'
                        newCurrentTargetNum.value = newCurrent
                        onHasEvent()
                    } else {
                        eventResult.value = 'no_event'
                    }
                } else {
                    newCurrentTargetNum.value = newCurrent
                    if (newCurrent != null && newCurrent > 0) {
                        eventResult.value = 'has_event'
                        onHasEvent()
                    } else {
                        eventResult.value = 'no_event'
                    }
                }
            }

            const found = []
            for (const { type, json } of attrResults) {
                if (json.success && json.data) {
                    if ((Array.isArray(json.data) && json.data.length > 0) ||
                        (!Array.isArray(json.data) && Object.keys(json.data).length > 0)) {
                        found.push(type)
                    }
                }
            }
            attributions.value = found

            let lrt = ''
            const appflyerResult = attrResults.find(r => r.type === 'appflyer')
            if (appflyerResult && appflyerResult.json && appflyerResult.json.success &&
                Array.isArray(appflyerResult.json.data) && appflyerResult.json.data.length > 0) {
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
            onRecordData(lrt)
        } catch (e) {
            emit('error', '事件查询失败：' + e.message)
        } finally {
            onFinally()
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

    return { queryEvent, queryEventWithFullMsg }
}