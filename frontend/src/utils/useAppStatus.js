import { ref, computed } from 'vue'

// 上游任务接口 data.status 约定：1=正常，-1=冻结，其余（缺失/未知值）=无状态
export const TASK_STATUS_NORMAL = 1
export const TASK_STATUS_FROZEN = -1

export function useAppStatus() {
    const taskStatus = ref(null)

    const statusInfo = computed(() => {
        const s = Number(taskStatus.value)
        if (s === TASK_STATUS_NORMAL) return { text: '应用状态：正常', cls: 'normal' }
        if (s === TASK_STATUS_FROZEN) return { text: '应用状态：已冻结', cls: 'frozen' }
        return { text: '应用状态：无状态', cls: 'unknown' }
    })

    function setTaskStatus(raw) {
        taskStatus.value = (raw === undefined || raw === null || raw === '') ? null : Number(raw)
    }

    function resetTaskStatus() {
        taskStatus.value = null
    }

    const isTaskFrozen = computed(() => taskStatus.value !== null && Number(taskStatus.value) === TASK_STATUS_FROZEN)

    return { taskStatus, statusInfo, isTaskFrozen, setTaskStatus, resetTaskStatus }
}