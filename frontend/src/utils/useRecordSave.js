import { insertRecord } from '../api/index.js'
import { toStorageDate } from './date.js'
import { generateSubmissionId } from './crypto.js'

export async function buildAndSaveRecord(params) {
    const { form, downloadUrl, bundleId, attributions, appId, newCurrentTargetNum, isFrozen, emit } = params

    if (!form.exception_type.trim()) {
        emit('error', '请选择异常类型')
        return null
    }

    if (newCurrentTargetNum.value === 0 || newCurrentTargetNum.value === null) {
        attributions.value = []
        alert('无新增事件token，即使原本的token有归因，也不会被设置在字段内')
    }

    let finalRemark = form.remark.trim()
    if (isFrozen.value) finalRemark += isFrozen.value
    if (newCurrentTargetNum.value > 0 && attributions.value.length === 0) {
        finalRemark += ',无事件归因'
    }

    const finalRecordData = form.record_data.includes('T')
        ? form.record_data
        : form.record_data.includes('/')
            ? form.record_data
            : toStorageDate(form.record_data)

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

    return json
}